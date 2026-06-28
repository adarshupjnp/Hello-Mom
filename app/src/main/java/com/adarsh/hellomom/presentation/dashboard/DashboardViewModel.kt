package com.adarsh.hellomom.presentation.dashboard

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.*
import com.adarsh.hellomom.domain.repository.*
import com.adarsh.hellomom.core.utils.PregnancyDataEngine
import com.adarsh.hellomom.core.utils.PregnancyProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val dashboardRepository: DashboardRepository,
    private val reminderRepository: ReminderRepository,
    private val syncRepository: SyncRepository,
    private val scheduleRepository: ScheduleRepository,
    private val roleManager: RoleManager
) : BaseViewModel<DashboardIntent, DashboardState, DashboardEffect>() {

    private val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date())

    override fun createInitialState(): DashboardState = DashboardState()

    init {
        handleIntent(DashboardIntent.LoadData)
    }

    override fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.LoadData -> loadData()
            DashboardIntent.IncrementKicks -> incrementKicks()
            is DashboardIntent.UpdateMood -> updateMood(intent.mood)
            is DashboardIntent.UpdateWater -> updateWater(intent.glasses)
            is DashboardIntent.UpdateSleep -> updateSleep(intent.hours)
            is DashboardIntent.UpdateWeight -> updateWeight(intent.kg)
            is DashboardIntent.UpdateSteps -> updateSteps(intent.steps)
            is DashboardIntent.ToggleUpcomingDone -> toggleUpcomingDone(intent.type, intent.refId, intent.isDone)
            DashboardIntent.Refresh -> syncData()
        }
    }

    private fun syncData(force: Boolean = true) {
        viewModelScope.launch {
            setState { copy(isSyncing = true, syncFailed = false) }
            // Full two-way sync: pulls the owner's latest data (profile, appointments, medicines,
            // symptoms, reminders, family members) into Room and pushes any pending local changes.
            // The Room-backed flows in loadData() then re-emit automatically with the fresh data.
            // Screen loads use the throttled variant; the explicit Refresh intent forces a sync.
            val result = if (force) syncRepository.syncAll() else syncRepository.syncIfStale()
            result.fold(
                onSuccess = { setState { copy(lastSyncTime = System.currentTimeMillis(), syncFailed = false) } },
                onFailure = { setState { copy(syncFailed = true) } }
            )
            setState { copy(isSyncing = false) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }

            // Kick off a background sync on every load. For family members this pulls the owner's
            // latest data; for the owner it pushes pending changes. If offline, syncAll() fails
            // silently and the Room cache below still serves the last stored data.
            syncData(force = false)

            // Resolve the role + the userId whose pregnancy data we display. For family members
            // this validates (and self-heals) the linked owner and returns the owner's profile so
            // the pregnancy week is correct even on the very first load.
            val access = roleManager.resolveAccess()
            val user = access.user
            if (user == null) {
                setState { copy(isLoading = false, error = "User not logged in") }
                return@launch
            }

            val hasFullAccess = access.isOwner
            val targetUserId = access.activeUserId.ifEmpty { user.userId }

            SyncLogger.resolve(
                "Dashboard load: user=${user.fullName} isOwner=$hasFullAccess targetUserId=$targetUserId " +
                    "ownerStartDate=${access.owner?.pregnancyStartDate}"
            )

            setState { copy(user = user, hasFullAccess = hasFullAccess) }

            // Family members: attach a real-time Firestore mirror so any owner change (add / edit /
            // delete / mark done / mark pending) lands in Room — and therefore on screen — instantly,
            // with no manual refresh. Owners are skipped: they already see their own writes via the
            // local Room cache, and re-pulling could clobber a not-yet-pushed local edit. The
            // collection runs in viewModelScope, so the listeners detach automatically in onCleared.
            if (!hasFullAccess && targetUserId.isNotEmpty()) {
                viewModelScope.launch {
                    syncRepository.observeOwnerRealtime(targetUserId)
                        .catch { e -> SyncLogger.warn("Dashboard realtime mirror stopped", e) }
                        .collect { /* Room flows below repaint on their own */ }
                }
            }

            // Seed the week immediately from the resolved owner profile so the UI doesn't flash 1/1.
            applyWeek(access.owner?.pregnancyStartDate, "seed")

            // Combine the dashboard flows. The owner profile is read from Room so the week recomputes
            // automatically as soon as syncAll() pulls the owner's latest pregnancyStartDate into Room.
            combine(
                dashboardRepository.getMotherHealthData(targetUserId),
                dashboardRepository.getDailyKickCount(targetUserId),
                dashboardRepository.getUpcomingAppointments(targetUserId),
                dashboardRepository.getMedicinesToday(targetUserId),
                dashboardRepository.getRecentSymptoms(targetUserId),
                dashboardRepository.getConnectedFamilyMembers(targetUserId),
                userRepository.getUser(targetUserId),
                scheduleRepository.getDailyStatuses(targetUserId, todayDate),
                reminderRepository.getAllReminders()
            ) { arrayOfFlows ->
                val health = arrayOfFlows[0] as MotherHealthData
                val kicks = arrayOfFlows[1] as Int
                val appointments = arrayOfFlows[2] as List<AppointmentEntity>
                val meds = arrayOfFlows[3] as List<MedicineEntity>
                val symptoms = arrayOfFlows[4] as List<SymptomLogEntity>
                var family = arrayOfFlows[5] as List<FamilyMemberEntity>
                // Prefer the freshest owner profile from Room, falling back to the resolved one.
                val ownerUser = (arrayOfFlows[6] as UserEntity?) ?: access.owner
                @Suppress("UNCHECKED_CAST")
                val dailyStatuses = arrayOfFlows[7] as List<DailyScheduleStatusEntity>
                @Suppress("UNCHECKED_CAST")
                val allReminders = arrayOfFlows[8] as List<ReminderEntity>

                // refIds the owner has ticked done today (drives the Upcoming green check + family view).
                val doneToday = dailyStatuses
                    .filter { it.isDone && !it.isDeleted }
                    .map { it.refId }
                    .toSet()

                val startDate = ownerUser?.pregnancyStartDate ?: access.owner?.pregnancyStartDate
                val week = PregnancyProgress.week(startDate)
                val day = PregnancyProgress.dayOfWeek(startDate)
                val trimester = PregnancyProgress.trimester(week)

                SyncLogger.resolve(
                    "Week recompute: targetUserId=$targetUserId startDate=$startDate → week=$week day=$day"
                )

                // If current user is a family member, add them to the list for visibility
                if (!hasFullAccess) {
                    val self = FamilyMemberEntity(
                        memberId = user.userId,
                        userId = targetUserId,
                        name = user.fullName,
                        email = user.email,
                        role = "Family Member",
                        permissions = "view",
                        status = "Accepted"
                    )
                    if (!family.any { it.memberId == user.userId }) {
                        family = family + self
                    }
                }

                val weekData = PregnancyDataEngine.getWeekData(week)
                val isFamily = targetUserId != user.userId

                // "Upcoming" filtering rules:
                //  • Appointments — by calendar DAY: an appointment dated today stays visible all
                //    day and only drops off once the date rolls over to tomorrow.
                //  • Medicines — by exact dose TIME with a 15-minute grace buffer: an 08:00 AM dose
                //    stays visible until 08:15 AM, then hides.
                val now = System.currentTimeMillis()
                val startOfToday = startOfDayMillis()

                // Ensure the weight in health section stays in sync with the profile weight.
                // If health tracking weight is 0, use the profile weight.
                val syncedHealth = if (health.weight == 0f && ownerUser?.weight != null && ownerUser.weight > 0f) {
                    health.copy(weight = ownerUser.weight)
                } else {
                    health
                }

                DashboardState(
                    user = user,
                    ownerName = if (isFamily) (ownerUser?.fullName ?: "") else "",
                    ownerMobile = if (isFamily) (ownerUser?.mobileNumber ?: "") else "",
                    hasFullAccess = hasFullAccess,
                    pregnancyWeek = week,
                    pregnancyDay = day,
                    trimester = trimester,
                    weekData = weekData,
                    healthData = syncedHealth,
                    kickCount = kicks,
                    appointments = appointments.filter { it.appointmentTime >= startOfToday }.sortedBy { it.appointmentTime },
                    medicines = meds
                        .filter { isMedicineUpcomingToday(it, now, startOfToday) }
                        .sortedBy { nextDoseMillisToday(it, now, startOfToday) },
                    reminders = allReminders
                        .filter { it.userId == targetUserId && it.date == todayDate && it.time >= now }
                        .sortedBy { it.time },
                    symptoms = symptoms,
                    familyMembers = family,
                    doneToday = doneToday,
                    isLoading = false,
                    quote = getDailyQuote()
                )
            }.catch { e ->
                SyncLogger.error("Dashboard combine failed", e)
                setState { copy(isLoading = false, error = e.message) }
            }.collectLatest { newState ->
                setState { newState }
            }
        }
    }

    private fun getDailyQuote(): String {
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        return when (day) {
            java.util.Calendar.MONDAY -> "Starting a new week with a healthy body and a happy baby. ❤️"
            java.util.Calendar.TUESDAY -> "Every kick is a reminder that you're never alone in this journey. ✨"
            java.util.Calendar.WEDNESDAY -> "Mid-week check: You're doing an amazing job, Mom! Stay strong. 💪"
            java.util.Calendar.THURSDAY -> "Your baby is growing stronger every day, and so are you. 🌟"
            java.util.Calendar.FRIDAY -> "Embrace the changes; your body is a miracle in progress. 🤰"
            java.util.Calendar.SATURDAY -> "Relax and bond with your little one this weekend. Peace is power. 🧘"
            java.util.Calendar.SUNDAY -> "Rest well, Mom. Tomorrow is a new beginning for both of you. 🌙"
            else -> "Your baby is growing and so is your love. ❤️"
        }
    }

    /**
     * Read-only guard: family members may only TRACK the owner's pregnancy, never write.
     * Returns the owner's own userId when the caller is allowed to write, else null.
     * (Belt-and-suspenders alongside the UI gating — a family member must never mutate data.)
     */
    private fun writableUserId(): String? {
        if (!uiState.value.hasFullAccess) {
            SyncLogger.warn("Blocked dashboard write attempt by read-only family member")
            return null
        }
        return uiState.value.user?.userId
    }

    private fun incrementKicks() {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            dashboardRepository.incrementKickCount(userId)
        }
    }

    private fun updateMood(mood: String) {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            val currentHealth = uiState.value.healthData
            dashboardRepository.updateMotherHealthData(userId, currentHealth.copy(mood = mood))
        }
    }

    private fun updateWater(glasses: Int) {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            val currentHealth = uiState.value.healthData
            dashboardRepository.updateMotherHealthData(userId, currentHealth.copy(waterIntake = glasses))
        }
    }

    private fun updateWeight(kg: Float) {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            val currentHealth = uiState.value.healthData
            dashboardRepository.updateMotherHealthData(userId, currentHealth.copy(weight = kg))
        }
    }

    private fun updateSleep(hours: Float) {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            val currentHealth = uiState.value.healthData
            dashboardRepository.updateMotherHealthData(userId, currentHealth.copy(sleepHours = hours))
        }
    }

    private fun updateSteps(steps: Int) {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            val currentHealth = uiState.value.healthData
            dashboardRepository.updateMotherHealthData(userId, currentHealth.copy(steps = steps))
        }
    }

    /**
     * Owner marks an Upcoming card (appointment / medicine) done for today. Writes a
     * daily_schedule_status row (Room first, then best-effort Firestore), so the green tick
     * reflects on the owner's device AND syncs to linked family members. Read-only family members
     * are blocked here (belt-and-suspenders alongside the UI hiding the checkbox).
     */
    private fun toggleUpcomingDone(type: String, refId: String, isDone: Boolean) {
        viewModelScope.launch {
            val userId = writableUserId() ?: return@launch
            scheduleRepository.setStatus(userId, todayDate, type, refId, isDone)
        }
    }

    /** Epoch millis for 00:00 of the current device-local day. */
    private fun startOfDayMillis(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Grace window after a medicine's dose time before it's treated as "passed". */
    private val doseBufferMs = 15L * 60 * 1000

    /**
     * Parse a medicine's dose time(s) — stored as "hh:mm a" (e.g. "08:00 AM"), with tolerance for
     * legacy comma-separated and 24-hour values — into today's epoch millis.
     */
    private fun parseDoseTimesToday(timing: String, startOfToday: Long): List<Long> {
        val formats = listOf(
            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH),
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH)
        )
        return timing.split(",").mapNotNull { raw ->
            val token = raw.trim()
            if (token.isEmpty()) return@mapNotNull null
            for (fmt in formats) {
                val parsed = runCatching { fmt.parse(token) }.getOrNull() ?: continue
                val c = java.util.Calendar.getInstance().apply { time = parsed }
                val minutes = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
                return@mapNotNull startOfToday + minutes * 60_000L
            }
            null
        }
    }

    /**
     * A medicine belongs in "Upcoming" when its course is active, it's scheduled for today's
     * weekday, and it still has a dose whose time (+15 min buffer) hasn't passed.
     */
    private fun isMedicineUpcomingToday(med: MedicineEntity, now: Long, startOfToday: Long): Boolean {
        if (med.isCompleted) return false
        // Course already finished before today.
        if (med.endDate != 0L && med.endDate < startOfToday) return false
        // Respect the scheduled weekdays (blank = every day).
        if (med.daysOfWeek.isNotBlank()) {
            val today = java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH).format(java.util.Date(now))
            if (med.daysOfWeek.split(",").map { it.trim() }.none { it.equals(today, ignoreCase = true) }) return false
        }
        val doses = parseDoseTimesToday(med.timing, startOfToday)
        // No parseable time → keep visible for the whole scheduled day.
        if (doses.isEmpty()) return true
        return doses.any { now <= it + doseBufferMs }
    }

    /** Millis of the soonest not-yet-passed dose today, used to order the Upcoming medicines. */
    private fun nextDoseMillisToday(med: MedicineEntity, now: Long, startOfToday: Long): Long =
        parseDoseTimesToday(med.timing, startOfToday)
            .filter { now <= it + doseBufferMs }
            .minOrNull() ?: Long.MAX_VALUE

    /** Compute and apply the pregnancy week/day/trimester from a start date (used for the initial seed). */
    private fun applyWeek(startDate: Long?, source: String) {
        val week = PregnancyProgress.week(startDate)
        val day = PregnancyProgress.dayOfWeek(startDate)
        val trimester = PregnancyProgress.trimester(week)
        SyncLogger.resolve("Week ($source): startDate=$startDate → week=$week day=$day trimester=$trimester")
        setState { copy(pregnancyWeek = week, pregnancyDay = day, trimester = trimester) }
    }
}
