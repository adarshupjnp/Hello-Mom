package com.adarsh.hellomom.presentation.baby

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.domain.repository.PregnancyWeekData

sealed class BabyProgressIntent : UiIntent {
    object Load : BabyProgressIntent()
}

data class BabyProgressState(
    val isLoading: Boolean = true,
    val week: Int = 1,
    val dayOfWeek: Int = 1,
    val trimester: Int = 1,
    val totalDays: Int = 0,
    /** 0f..1f fraction of the 280-day (40-week) journey. */
    val progress: Float = 0f,
    /** Expected delivery date in millis; null when neither EDD nor LMP is known. */
    val dueDate: Long? = null,
    /** Days remaining until the due date; null when the due date is unknown. */
    val daysToGo: Int? = null,
    val weekData: PregnancyWeekData = PregnancyWeekData(),
    val motherChanges: String = "",
    val userName: String = ""
) : UiState

sealed class BabyProgressEffect : UiEffect
