package com.adarsh.hellomom.presentation.symptoms

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.SymptomLogEntity

sealed class SymptomIntent : UiIntent {
    object LoadLogs : SymptomIntent()
    data class OnAddSymptom(val name: String, val severity: Int) : SymptomIntent()
}

data class SymptomState(
    val logs: List<SymptomLogEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val error: String? = null
) : UiState

sealed class SymptomEffect : UiEffect {
    data class ShowError(val message: String) : SymptomEffect()
    object OnSymptomAdded : SymptomEffect()
}
