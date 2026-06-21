package com.adarsh.hellomom.presentation.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.constants.ReminderConstants
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.ReminderEntity
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.data.local.entity.ReminderType
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.notification.ReminderManager
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val authRepository: AuthRepository,
    private val reminderManager: ReminderManager,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : ViewModel() {

    // Kept so the Firestore listener can be detached in onCleared() — otherwise every visit to
    // the reminder screen leaked one more permanently-running listener.
    private var familyRemindersListener: ListenerRegistration? = null

    private val _isOwner = MutableStateFlow(false)
    val isOwner = _isOwner.asStateFlow()

    private val _familyReminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
    val familyReminders = _familyReminders.asStateFlow()

    // Date filter (Today / Yesterday / Custom / All) for the reminder history.
    private val _selectedDate = MutableStateFlow<Long?>(null)
    val selectedDate = _selectedDate.asStateFlow()

    val reminders = reminderRepository.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displayedReminders: StateFlow<List<ReminderEntity>> =
        combine(_familyReminders, reminders, _selectedDate) { liveList, roomList, date ->
            // Show reminders from BOTH the live Firestore listener AND the Room-backed list that the
            // sync layer keeps populated (pullOwnerData). Family members rely on the synced Room copy
            // — exactly like every other shared screen (appointments, medicines …) — so their
            // reminders still appear even when the live listener's owner-id resolution lags or
            // resolves late on screen open. The listener is kept for instant updates on the owner's
            // device. Merge is deduped by id, preferring the freshest copy (highest updatedAt) so a
            // status change (e.g. Mark Done) from either source always wins.
            val merged = (liveList + roomList)
                .groupBy { it.id }
                .map { (_, copies) -> copies.maxByOrNull { it.updatedAt }!! }

            val filtered = if (date == null) {
                merged // "All"
            } else {
                // Match on the reminder's actual fire time falling inside the selected calendar
                // day [startOfDay, startOfDay + 24h). This is more reliable than comparing the
                // stored `date` string (which can drift when a reminder is snoozed to another day),
                // so Today / Yesterday / Custom filters always reflect when the reminder really fires.
                val dayStart = startOfDay(date)
                val dayEnd = dayStart + DAY_MILLIS
                merged.filter { it.time in dayStart until dayEnd }
            }
            // Always present the day as a real morning -> night timeline (earliest time first),
            // so the UI can render a single chronological list and reminders never jump around
            // when their status changes.
            filtered.sortedBy { it.time }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDateFilter(date: Long?) {
        _selectedDate.value = date
    }

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState = _uiState.asStateFlow()

    init {
        checkUserRole()
        observeFamilyReminders()
        startPeriodicTasks()
        // Throttled pull so a family member opening this screen sees the owner's latest
        // reminders in Room-backed lists too, without a manual sync.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
    }

    override fun onCleared() {
        familyRemindersListener?.remove()
        familyRemindersListener = null
        super.onCleared()
    }

    private fun startPeriodicTasks() {
        viewModelScope.launch {
            while (true) {
                // Daily check
                if (isOwner.value) {
                    initializeDailyReminders()
                }

                cleanupOldReminders()
                checkPendingStatus()
                delay(300000) // Every 5 minutes
            }
        }
    }

    private fun cleanupOldReminders() {
        viewModelScope.launch {
            // Retain only the last 7 days (today + the previous 6). Anything older than the start
            // of that window is purged so neither Room nor Firestore grows unbounded.
            // The owner owns the data, so only the owner deletes from Firestore; family devices
            // then drop the same rows on their next pull-reconcile. Family members do a harmless
            // local-only purge (re-synced if still present remotely).
            val cutoff = startOfDay(System.currentTimeMillis()) - (RETENTION_DAYS - 1) * DAY_MILLIS
            reminderRepository.deleteOldReminders(cutoff, deleteRemote = isOwner.value)
        }
    }

    private fun checkPendingStatus() {
        val currentTime = System.currentTimeMillis()
        reminders.value.forEach { reminder ->
            if (reminder.status == ReminderStatus.PENDING && reminder.time < currentTime - 3600000) {
                markAsExpired(reminder.id)
            }
        }
    }

    private fun checkUserRole() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { user ->
                user?.let {
                    val wasOwner = _isOwner.value
                    _isOwner.value = com.adarsh.hellomom.core.RoleManager.isOwnerUser(it.fullName, it.email)
                    // Trigger daily reminders immediately when owner status is first confirmed
                    if (_isOwner.value && !wasOwner) {
                        initializeDailyReminders()
                    }
                }
            }
        }
    }

    // userId the live reminders listener is currently bound to, so we only re-attach when it changes.
    private var familyRemindersUserId: String? = null

    private fun observeFamilyReminders() {
        viewModelScope.launch {
            // Re-resolve whose reminders to watch whenever the logged-in user (or their owner link)
            // changes. A family member's linkedPrimaryUserId can be empty right after login and only
            // heal once Firestore is reachable; collecting the user flow means we then re-attach the
            // listener to the correct owner instead of being stuck watching the member's own id.
            authRepository.getCurrentUser().collectLatest {
                // Owners watch their own reminders; family members watch the linked owner's.
                // Filtering server-side avoids downloading every user's reminders.
                val targetUserId = runCatching { roleManager.resolveAccess().activeUserId }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                if (targetUserId == null) {
                    SyncLogger.warn("observeFamilyReminders: no resolvable user, listener not attached")
                    return@collectLatest
                }
                if (targetUserId == familyRemindersUserId) return@collectLatest // already listening
                familyRemindersUserId = targetUserId
                attachFamilyRemindersListener(targetUserId)
            }
        }
    }

    private fun attachFamilyRemindersListener(targetUserId: String) {
        familyRemindersListener?.remove()
        familyRemindersListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("reminders")
            .whereEqualTo("userId", targetUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    SyncLogger.warn("reminders listener error", e)
                    return@addSnapshotListener
                }
                // toObjects can throw on malformed documents — never crash the app for that.
                val list = runCatching { snapshot?.toObjects(ReminderEntity::class.java) }
                    .getOrNull() ?: return@addSnapshotListener
                _familyReminders.value = list.sortedBy { r -> r.time }
                // Arm/cancel local alarms straight from the live stream so notifications fire on THIS
                // device too — family members (and the owner's other devices) no longer depend on a
                // throttled background pull to schedule them, which is why reminders sometimes showed
                // in the list but never notified.
                syncReminderAlarms(list)
            }
    }

    /**
     * Keep this device's local alarms in step with the latest reminder stream: schedule future
     * pending/snoozed reminders (scheduling is idempotent — keyed by reminder id) and cancel any
     * alarm for a reminder that's now completed or expired.
     */
    private fun syncReminderAlarms(list: List<ReminderEntity>) {
        val now = System.currentTimeMillis()
        list.forEach { reminder ->
            when {
                (reminder.status == ReminderStatus.PENDING || reminder.status == ReminderStatus.SNOOZED) &&
                    reminder.time > now ->
                    runCatching { reminderManager.scheduleReminder(reminder) }
                reminder.status == ReminderStatus.COMPLETED || reminder.status == ReminderStatus.EXPIRED ->
                    runCatching { reminderManager.cancelReminder(reminder.id) }
            }
        }
    }

    private fun initializeDailyReminders() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val user = authRepository.getCurrentUser().first() ?: return@launch
            
            val existingDaily = reminderRepository.getAutoGeneratedRemindersForDate(today)
            if (existingDaily.isEmpty()) {
                val reminderHours = listOf(8, 9, 12, 16, 19, 20) // 8AM, 9AM, 12PM, 4PM, 7PM, 8PM
                
                ReminderConstants.DAILY_AUTO_REMINDERS.forEachIndexed { index, pre ->
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, if (index < reminderHours.size) reminderHours[index] else 8 + index)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    
                    addCustomReminder(
                        title = pre.title,
                        description = pre.voiceMessage,
                        voiceMessage = pre.voiceMessage,
                        time = calendar.timeInMillis,
                        isAuto = true,
                        category = getCategoryForTitle(pre.title)
                    )
                }
            }
        }
    }

    private fun getCategoryForTitle(title: String): String {
        return when {
            title.contains("Medicine", true) -> "Medicine"
            title.contains("Meal", true) || title.contains("Dinner", true) -> "Meal"
            title.contains("Water", true) -> "Water"
            else -> "General"
        }
    }

    fun addCustomReminder(
        title: String, 
        description: String, 
        voiceMessage: String, 
        time: Long, 
        isAuto: Boolean = false,
        category: String = "Custom"
    ) {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser().first() ?: return@launch
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
            
            // Duplicate Prevention
            val existing = reminderRepository.getExistingReminder(user.userId, title, date, time)
            if (existing != null) return@launch

            val reminder = ReminderEntity(
                title = title,
                description = description,
                voiceMessage = voiceMessage,
                time = time,
                date = date,
                userId = user.userId,
                userName = user.fullName,
                reminderType = if (isAuto) ReminderType.PREDEFINED else ReminderType.CUSTOM,
                isAutoGenerated = isAuto,
                category = category
            )
            val id = reminderRepository.insertReminder(reminder)
            reminderManager.scheduleReminder(reminder.copy(id = id.toInt()))
        }
    }

    fun updateReminderTime(reminder: ReminderEntity, newTime: Long) {
        viewModelScope.launch {
            val newDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(newTime))
            val updatedReminder = reminder.copy(
                time = newTime,
                date = newDate,
                status = ReminderStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
            reminderRepository.updateReminder(updatedReminder)
            reminderManager.scheduleReminder(updatedReminder)
        }
    }

    fun updateReminderTime(id: Int, newTime: Long) {
        viewModelScope.launch {
            val reminder = reminderRepository.getReminderById(id)
            reminder?.let { updateReminderTime(it, newTime) }
        }
    }

    fun markAsDone(id: Int) {
        viewModelScope.launch {
            reminderRepository.markAsDone(id)
            reminderManager.cancelReminder(id)
        }
    }

    fun markAsExpired(id: Int) {
        viewModelScope.launch {
            reminderRepository.markAsMissed(id)
            reminderManager.cancelReminder(id)
        }
    }

    fun snooze(id: Int, customTimeMinutes: Int = 10) {
        viewModelScope.launch {
            val snoozeMillis = customTimeMinutes * 60 * 1000L
            val reminder = reminderRepository.getReminderById(id)
            reminder?.let {
                val updated = it.copy(
                    time = System.currentTimeMillis() + snoozeMillis,
                    status = ReminderStatus.SNOOZED,
                    snoozeCount = it.snoozeCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                reminderRepository.updateReminder(updated)
                reminderManager.scheduleReminder(updated)
            }
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminder)
            reminderManager.cancelReminder(reminder.id)
        }
    }

    /** Midnight (local) of the day containing [millis]. */
    private fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        /** Reminders are kept for the last 7 days (today + the previous 6) and then purged. */
        private const val RETENTION_DAYS = 7
    }
}

data class ReminderUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
