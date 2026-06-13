package com.adarsh.hellomom.presentation.baby

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyDataEngine
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BabyProgressViewModel @Inject constructor(
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
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
        // Throttled background refresh so family members see the owner's latest dates.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("BabyProgress resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }

            // The week always comes from the owner's start date (family has none of its own).
            val startDate = access.owner?.pregnancyStartDate ?: access.user?.pregnancyStartDate
            val storedDueDate = access.owner?.dueDate ?: access.user?.dueDate
            // Fall back to LMP + 280 days when no explicit EDD was saved.
            val dueDate = storedDueDate
                ?: startDate?.takeIf { it > 0 }?.plus(FULL_TERM_DAYS * DAY_MILLIS)

            val now = System.currentTimeMillis()
            val week = PregnancyProgress.week(startDate, now)
            val totalDays = PregnancyProgress.totalDays(startDate, now)
            val daysToGo = dueDate?.let { (((it - now) / DAY_MILLIS).toInt()).coerceAtLeast(0) }

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
                    userName = access.user?.fullName ?: ""
                )
            }
        }
    }

    companion object {
        private const val FULL_TERM_DAYS = 280L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
