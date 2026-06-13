package com.adarsh.hellomom.presentation.auth

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class RegisterIntent : UiIntent {
    data class OnFullNameChanged(val name: String) : RegisterIntent()
    data class OnEmailChanged(val email: String) : RegisterIntent()
    data class OnPasswordChanged(val password: String) : RegisterIntent()
    data class OnMobileChanged(val mobile: String) : RegisterIntent()
    data class OnDobChanged(val dob: Long) : RegisterIntent()
    object OnRegisterClicked : RegisterIntent()
    object OnLoginClicked : RegisterIntent()
}

data class RegisterState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val mobile: String = "",
    val dob: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class RegisterEffect : UiEffect {
    object NavigateToProfileCreation : RegisterEffect()
    object NavigateToHome : RegisterEffect()
    object NavigateToLogin : RegisterEffect()
    data class ShowError(val message: String) : RegisterEffect()
}
