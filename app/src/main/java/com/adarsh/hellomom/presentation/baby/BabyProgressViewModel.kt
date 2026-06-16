package com.adarsh.hellomom.presentation.baby

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyDataEngine
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BabyProgressViewModel @Inject constructor(
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository,
    private val userRepository: UserRepository
) : BaseViewModel<BabyProgressIntent, BabyProgressState, BabyProgressEffect>() {

    override fun createInitialState(): BabyProgressState = BabyProgressState()

    init {
        handleIntent(BabyProgressIntent.Load)
    }

    override fun handleIntent(intent: BabyProgressIntent) {
        when (intent) {
            BabyProgressIntent.Load -> load()
        }
    }

    private fun load() {
        // Throttled background sync so this screen pulls the owner's latest dates from Firestore
        // into Room. For family members this also self-heals the owner link inside resolveAccess().
        // The Room-backed flow below then re-emits automatically once the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }

        viewModelScope.launch {
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("BabyProgress resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }

            val user = access.user
            if (user == null) {
                SyncLogger.warn("BabyProgress: no logged-in user")
                setState { copy(isLoading = false) }
                return@launch
            }

            // Whose pregnancy data to display: own id for owners, the linked owner's id for family.
            val targetUserId = access.activeUserId.ifEmpty { user.userId }

            // Seed immediately from the resolved owner profile so the ring doesn't flash week 1/1
            // before Room emits (resolveAccess already cached the owner doc locally for family).
            access.owner?.let { applyOwner(it, user.fullName) }

            // Collect the owner profile from Room. The week, due date and progress recompute on
            // every emission, so the moment syncAll() / syncIfStale() writes the owner's latest
            // pregnancyStartDate or dueDate into Room, this screen repaints by itself — owners see
            // their own profile edits live, family members see the owner's updates after a pull.
            userRepository.getUser(targetUserId)
                .catch { e ->
                    SyncLogger.error("BabyProgress user flow failed", e)
                    setState { copy(isLoading = false) }
                }
                .collect { roomUser ->
                    // Prefer the freshest Room copy, falling back to the resolved owner profile.
                    applyOwner(roomUser ?: access.owner, user.fullName)
                }
        }
    }

    /** Recompute and publish the full Baby Progress state from the owner's profile. */
    private fun applyOwner(owner: UserEntity?, viewerName: String) {
        val startDate = owner?.pregnancyStartDate
        val storedDueDate = owner?.dueDate
        // Fall back to LMP + 280 days when no explicit EDD was saved.
        val dueDate = storedDueDate
            ?: startDate?.takeIf { it > 0 }?.plus(FULL_TERM_DAYS * DAY_MILLIS)

        val now = System.currentTimeMillis()
        val week = PregnancyProgress.week(startDate, now)
        val totalDays = PregnancyProgress.totalDays(startDate, now)
        val daysToGo = dueDate?.let { (((it - now) / DAY_MILLIS).toInt()).coerceAtLeast(0) }

        SyncLogger.resolve(
            "BabyProgress recompute: startDate=$startDate dueDate=$dueDate → week=$week totalDays=$totalDays"
        )

        setState {
            copy(
                isLoading = false,
                week = week,
                dayOfWeek = PregnancyProgress.dayOfWeek(startDate, now),
                trimester = PregnancyProgress.trimester(week),
                totalDays = totalDays,
                progress = (totalDays / FULL_TERM_DAYS.toFloat()).coerceIn(0f, 1f),
                dueDate = dueDate,
                daysToGo = daysToGo,
                weekData = PregnancyDataEngine.getWeekData(week),
                motherChanges = PregnancyDataEngine.getMotherBodyChanges(week),
                userName = viewerName
            )
        }
    }

    companion object {
        private const val FULL_TERM_DAYS = 280L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
