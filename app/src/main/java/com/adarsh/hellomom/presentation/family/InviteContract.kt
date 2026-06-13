package com.adarsh.hellomom.presentation.family

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class InviteIntent : UiIntent {
    data class JoinFamily(val code: String) : InviteIntent()
}

data class InviteState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isJoining: Boolean = false,
    val inviteCode: String = ""
) : UiState

sealed class InviteEffect : UiEffect {
    object NavigateToHome : InviteEffect()
    data class ShowError(val message: String) : InviteEffect()
}
