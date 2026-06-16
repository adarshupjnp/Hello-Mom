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

    val displayedReminders: StateFlow<List<ReminderEntity>> =
        combine(_familyReminders, _selectedDate) { list, date ->
            if (date == null) {
                list
            } else {
                val dayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(date))
                list.filter { it.date == dayStr }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDateFilter(date: Long?) {
        _selectedDate.value = date
    }

    val reminders = reminderRepository.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            val twoDaysAgo = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000)
            reminderRepository.deleteOldReminders(twoDaysAgo)
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

    private fun observeFamilyReminders() {
        viewModelScope.launch {
            // Resolve whose reminders to watch: owners watch their own, family members watch the
            // linked owner's. Filtering server-side avoids downloading every user's reminders.
            val targetUserId = runCatching { roleManager.resolveAccess().activeUserId }
                .getOrNull()?.takeIf { it.isNotEmpty() }
            if (targetUserId == null) {
                SyncLogger.warn("observeFamilyReminders: no resolvable user, listener not attached")
                return@launch
            }
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
}

data class ReminderUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
