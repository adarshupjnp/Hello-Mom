package com.adarsh.hellomom.presentation.schedule

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

/** The kinds of rows that make up Today's Schedule. The name is also the sync `type`. */
enum class ScheduleItemType { ROUTINE, MEDICINE, MEAL, REMINDER }

/** A single timeline row (a wake/sleep marker, a medicine, or a meal) with today's done state. */
data class ScheduleItem(
    val refId: String,
    val type: ScheduleItemType,
    val title: String,
    val subtitle: String,
    /** Display time string, e.g. "08:00 AM" (may be blank). */
    val time: String,
    /** Minutes since midnight for ordering; [Int.MAX_VALUE] when the time can't be parsed. */
    val sortMinutes: Int,
    val isDone: Boolean
)

sealed class TodayScheduleIntent : UiIntent {
    object Load : TodayScheduleIntent()
    data class ToggleDone(val item: ScheduleItem) : TodayScheduleIntent()
    data class UpdateRoutineTimes(val wakeUpTime: String, val sleepTime: String) : TodayScheduleIntent()
}

data class TodayScheduleState(
    val isLoading: Boolean = true,
    /** Only the owner may mark items done or edit wake/sleep times. */
    val isOwner: Boolean = false,
    val items: List<ScheduleItem> = emptyList(),
    val wakeUpTime: String = DEFAULT_WAKE,
    val sleepTime: String = DEFAULT_SLEEP,
    val doneCount: Int = 0,
    val totalCount: Int = 0
) : UiState {
    companion object {
        const val DEFAULT_WAKE = "07:00 AM"
        const val DEFAULT_SLEEP = "10:00 PM"
    }
}

sealed class TodayScheduleEffect : UiEffect
