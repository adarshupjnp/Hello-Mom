package com.adarsh.hellomom.presentation.schedule

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.ReminderDedup
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.SyncStatus
import com.adarsh.hellomom.data.local.entity.DailyScheduleStatusEntity
import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.MedicineEntity
import com.adarsh.hellomom.data.local.entity.ReminderEntity
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.FoodRepository
import com.adarsh.hellomom.domain.repository.MedicineRepository
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.adarsh.hellomom.domain.repository.ScheduleRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Today's Schedule — a self-contained section hosted inside the Your Health tab. It derives a
 * single time-ordered timeline (wake-up → medicines → meals → sleep) from existing synced data
 * and overlays today's done/pending marks. Everything is Room-backed so the section repaints by
 * itself when a sync pull lands; only the owner may toggle marks or edit wake/sleep times.
 *
 * It deliberately does NOT depend on DashboardViewModel/State, so the dashboard's data pipeline
 * is untouched.
 */
@HiltViewModel
class TodayScheduleViewModel @Inject constructor(
    private val roleManager: RoleManager,
    private val medicineRepository: MedicineRepository,
    private val foodRepository: FoodRepository,
    private val userRepository: UserRepository,
    private val reminderRepository: ReminderRepository,
    private val scheduleRepository: ScheduleRepository,
    private val syncRepository: SyncRepository
) : BaseViewModel<TodayScheduleIntent, TodayScheduleState, TodayScheduleEffect>() {

    /** Day bucket computed once on load — matches the daily-reset key used everywhere else. */
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun createInitialState(): TodayScheduleState = TodayScheduleState()

    init {
        handleIntent(TodayScheduleIntent.Load)
    }

    override fun handleIntent(intent: TodayScheduleIntent) {
        when (intent) {
            TodayScheduleIntent.Load -> load()
            is TodayScheduleIntent.ToggleDone -> toggle(intent.item)
            is TodayScheduleIntent.UpdateRoutineTimes -> updateRoutineTimes(intent.wakeUpTime, intent.sleepTime)
        }
    }

    private fun load() {
        // Throttled background sync so family members pull the owner's latest meds/meals/marks.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }

        viewModelScope.launch {
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("TodaySchedule resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            val user = access.user
            if (user == null) {
                setState { copy(isLoading = false) }
                return@launch
            }
            // Family members read the owner's data (activeUserId); owners read their own.
            val targetUserId = access.activeUserId.ifEmpty { user.userId }
            setState { copy(isOwner = access.isOwner) }

            combine(
                medicineRepository.getMedicines(targetUserId),
                foodRepository.getMeals(targetUserId),
                userRepository.getUser(targetUserId),
                scheduleRepository.getDailyStatuses(targetUserId, today),
                reminderRepository.getAllReminders()
            ) { meds, meals, owner, statuses, reminders ->
                buildSchedule(meds, meals, owner, statuses, reminders.filter { it.userId == targetUserId && it.date == today })
            }.catch { e ->
                SyncLogger.error("TodaySchedule flow failed", e)
                setState { copy(isLoading = false) }
            }.collect { built ->
                setState {
                    copy(
                        isLoading = false,
                        items = built.items,
                        wakeUpTime = built.wake,
                        sleepTime = built.sleep,
                        doneCount = built.items.count { it.isDone },
                        totalCount = built.items.size
                    )
                }
            }
        }
    }

    private fun toggle(item: ScheduleItem) {
        viewModelScope.launch {
            val access = runCatching { roleManager.resolveAccess() }.getOrNull()
            // Read-only guard: only the owner may mark items done (belt-and-suspenders alongside UI).
            if (access?.isOwner != true) {
                SyncLogger.warn("Blocked schedule toggle by read-only family member")
                return@launch
            }
            val ownerUserId = access.activeUserId.ifEmpty { access.user?.userId.orEmpty() }
            if (ownerUserId.isEmpty()) return@launch
            scheduleRepository.setStatus(ownerUserId, today, item.type.name, item.refId, !item.isDone)
        }
    }

    private fun updateRoutineTimes(wakeUpTime: String, sleepTime: String) {
        viewModelScope.launch {
            val access = runCatching { roleManager.resolveAccess() }.getOrNull()
            if (access?.isOwner != true) return@launch
            val owner = access.owner ?: access.user ?: return@launch
            userRepository.updateUser(
                owner.copy(
                    wakeUpTime = wakeUpTime,
                    sleepTime = sleepTime,
                    syncStatus = SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Result of assembling the timeline: ordered rows plus the resolved wake/sleep times. */
    private data class Built(val items: List<ScheduleItem>, val wake: String, val sleep: String)

    private fun buildSchedule(
        meds: List<MedicineEntity>,
        meals: List<MealEntity>,
        owner: UserEntity?,
        statuses: List<DailyScheduleStatusEntity>,
        reminders: List<ReminderEntity>
    ): Built {
        val wake = owner?.wakeUpTime?.takeIf { it.isNotBlank() } ?: TodayScheduleState.DEFAULT_WAKE
        val sleep = owner?.sleepTime?.takeIf { it.isNotBlank() } ?: TodayScheduleState.DEFAULT_SLEEP

        // Index today's marks by "TYPE:refId" so a missing entry means pending.
        val doneByKey = statuses.associate { "${it.type}:${it.refId}" to it.isDone }
        fun done(type: ScheduleItemType, refId: String): Boolean = doneByKey["${type.name}:$refId"] == true

        val items = mutableListOf<ScheduleItem>()

        // Wake-up bookend.
        items += ScheduleItem(
            refId = "wakeup",
            type = ScheduleItemType.ROUTINE,
            title = "Wake up",
            subtitle = "Start your day",
            time = wake,
            sortMinutes = parseMinutes(wake),
            isDone = done(ScheduleItemType.ROUTINE, "wakeup")
        )

        // Medicines active today, morning → night.
        meds.filter { isMedicineActiveToday(it) }.forEach { m ->
            val subtitle = listOf(m.dosage, m.beforeAfterMeal)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            items += ScheduleItem(
                refId = m.medicineId,
                type = ScheduleItemType.MEDICINE,
                title = m.name.ifBlank { "Medicine" },
                subtitle = subtitle,
                time = m.timing,
                sortMinutes = parseMinutes(m.timing),
                isDone = done(ScheduleItemType.MEDICINE, m.medicineId)
            )
        }

        // Today's meals, morning → night.
        meals.filter { isToday(it.updatedAt) }.forEach { meal ->
            items += ScheduleItem(
                refId = meal.mealId,
                type = ScheduleItemType.MEAL,
                title = meal.mealType.ifBlank { "Meal" },
                subtitle = meal.foodItems,
                time = meal.timing,
                sortMinutes = parseMinutes(meal.timing),
                isDone = done(ScheduleItemType.MEAL, meal.mealId)
            )
        }

        // Add Reminders (Coconut Water, Lunch meal, evening meal, dinner, etc.), de-duplicated so a
        // duplicate auto-reminder row never shows as two schedule items.
        ReminderDedup.dedupe(reminders).forEach { r ->
            val displayTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(r.time))
            items += ScheduleItem(
                refId = r.id.toString(),
                type = ScheduleItemType.REMINDER,
                title = r.title,
                subtitle = r.description,
                time = displayTime,
                sortMinutes = parseMinutes(displayTime),
                isDone = done(ScheduleItemType.REMINDER, r.id.toString())
            )
        }

        // Sleep bookend.
        items += ScheduleItem(
            refId = "sleep",
            type = ScheduleItemType.ROUTINE,
            title = "Sleep",
            subtitle = "Wind down & rest",
            time = sleep,
            sortMinutes = parseMinutes(sleep),
            isDone = done(ScheduleItemType.ROUTINE, "sleep")
        )

        val sorted = items.sortedWith(compareBy({ it.sortMinutes }, { it.title }))
        return Built(sorted, wake, sleep)
    }

    /** A medicine belongs on today's schedule unless its start is in the future or it has ended. */
    private fun isMedicineActiveToday(m: MedicineEntity): Boolean {
        if (m.startDate > 0L && m.startDate > endOfToday()) return false
        if (m.endDate > 0L && m.endDate < startOfToday()) return false
        return true
    }

    private fun isToday(epochMillis: Long): Boolean =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis)) == today

    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfToday(): Long = startOfToday() + 24L * 60 * 60 * 1000 - 1

    /** Parse the first time token of a timing string to minutes-since-midnight for ordering. */
    private fun parseMinutes(raw: String): Int {
        val token = raw.trim().split(",", ";", "/", "-").firstOrNull()?.trim().orEmpty()
        if (token.isEmpty()) return Int.MAX_VALUE
        for (pattern in listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }.parse(token)
            }.getOrNull()
            if (parsed != null) {
                val c = Calendar.getInstance().apply { time = parsed }
                return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            }
        }
        return Int.MAX_VALUE
    }
}
