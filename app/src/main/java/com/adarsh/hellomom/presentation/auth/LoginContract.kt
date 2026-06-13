package com.adarsh.hellomom.presentation.auth

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class LoginIntent : UiIntent {
    data class OnEmailChanged(val email: String) : LoginIntent()
    data class OnPasswordChanged(val password: String) : LoginIntent()
    object OnLoginClicked : LoginIntent()
    data class OnGoogleSignInSuccess(val idToken: String) : LoginIntent()
    data class OnGoogleSignInError(val message: String) : LoginIntent()
    object OnGoogleSignInClicked : LoginIntent()
    object OnForgotPasswordClicked : LoginIntent()
    data class OnResetPassword(val email: String) : LoginIntent()
    object OnRegisterClicked : LoginIntent()
}

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class LoginEffect : UiEffect {
    object NavigateToHome : LoginEffect()
    object NavigateToRegister : LoginEffect()
    object NavigateToForgotPassword : LoginEffect()
    object NavigateToProfileCreation : LoginEffect()
    object LaunchGoogleSignIn : LoginEffect()
    data class ShowError(val message: String) : LoginEffect()
    data class ShowMessage(val message: String) : LoginEffect()
}
