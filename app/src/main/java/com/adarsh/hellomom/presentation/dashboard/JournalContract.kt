package com.adarsh.hellomom.presentation.dashboard

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.JournalEntity

sealed class JournalIntent : UiIntent {
    object Load : JournalIntent()
    data class OnAdd(val content: String, val mood: String = "") : JournalIntent()
    data class OnUpdate(val entry: JournalEntity) : JournalIntent()
    data class OnDelete(val entry: JournalEntity) : JournalIntent()
    data class OnDateFilterChanged(val date: Long?) : JournalIntent()
}

data class JournalState(
    val entries: List<JournalEntity> = emptyList(),
    val filtered: List<JournalEntity> = emptyList(),
    val selectedDate: Long? = null,
    // Only the owner can add/edit/delete; family members read the owner's journal.
    val isOwner: Boolean = false,
    // Used only for the PDF export header (see Appointments export).
    val userName: String = "",
    val pregnancyWeek: Int = 1,
    val isLoading: Boolean = false
) : UiState

sealed class JournalEffect : UiEffect {
    data class ShowError(val message: String) : JournalEffect()
}
