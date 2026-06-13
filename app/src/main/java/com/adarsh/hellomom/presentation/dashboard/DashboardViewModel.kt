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
    private val roleManager: RoleManager
) : BaseViewModel<DashboardIntent, DashboardState, DashboardEffect>() {

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
                userRepository.getUser(targetUserId)
            ) { arrayOfFlows ->
                val health = arrayOfFlows[0] as MotherHealthData
                val kicks = arrayOfFlows[1] as Int
                val appointments = arrayOfFlows[2] as List<AppointmentEntity>
                val meds = arrayOfFlows[3] as List<MedicineEntity>
                val symptoms = arrayOfFlows[4] as List<SymptomLogEntity>
                var family = arrayOfFlows[5] as List<FamilyMemberEntity>
                // Prefer the freshest owner profile from Room, falling back to the resolved one.
                val ownerUser = (arrayOfFlows[6] as UserEntity?) ?: access.owner

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

                DashboardState(
                    user = user,
                    ownerName = if (isFamily) (ownerUser?.fullName ?: "") else "",
                    ownerMobile = if (isFamily) (ownerUser?.mobileNumber ?: "") else "",
                    hasFullAccess = hasFullAccess,
                    pregnancyWeek = week,
                    pregnancyDay = day,
                    trimester = trimester,
                    weekData = weekData,
                    healthData = health,
                    kickCount = kicks,
                    appointments = appointments.filter { it.appointmentTime > System.currentTimeMillis() }.sortedBy { it.appointmentTime },
                    medicines = meds,
                    symptoms = symptoms,
                    familyMembers = family,
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

    /** Compute and apply the pregnancy week/day/trimester from a start date (used for the initial seed). */
    private fun applyWeek(startDate: Long?, source: String) {
        val week = PregnancyProgress.week(startDate)
        val day = PregnancyProgress.dayOfWeek(startDate)
        val trimester = PregnancyProgress.trimester(week)
        SyncLogger.resolve("Week ($source): startDate=$startDate → week=$week day=$day trimester=$trimester")
        setState { copy(pregnancyWeek = week, pregnancyDay = day, trimester = trimester) }
    }
}
