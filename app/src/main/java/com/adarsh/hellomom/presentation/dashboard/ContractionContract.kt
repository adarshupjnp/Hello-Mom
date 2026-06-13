package com.adarsh.hellomom.presentation.dashboard

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.ContractionEntity

sealed class ContractionIntent : UiIntent {
    object Load : ContractionIntent()
    // A finished contraction: when it started and how long it lasted.
    data class OnRecord(val startTime: Long, val durationMillis: Long) : ContractionIntent()
    data class OnUpdate(val contraction: ContractionEntity) : ContractionIntent()
    data class OnDelete(val contraction: ContractionEntity) : ContractionIntent()
    data class OnDateFilterChanged(val date: Long?) : ContractionIntent()
}

data class ContractionState(
    val contractions: List<ContractionEntity> = emptyList(),
    val filtered: List<ContractionEntity> = emptyList(),
    val selectedDate: Long? = null,
    // Family members may view contractions but not record/edit/delete them.
    val isOwner: Boolean = false,
    val isLoading: Boolean = false
) : UiState

sealed class ContractionEffect : UiEffect {
    data class ShowError(val message: String) : ContractionEffect()
}
