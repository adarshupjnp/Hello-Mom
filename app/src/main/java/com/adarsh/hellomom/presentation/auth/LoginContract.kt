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

    /** Submitted from the mandatory WhatsApp-number prompt shown to Google sign-in users. */
    data class OnWhatsAppNumberSubmitted(val number: String, val role: String?) : LoginIntent()
    data class OnFamilyRoleChanged(val role: String) : LoginIntent()
}

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    /**
     * Set after a successful Google sign-in when the account has no mobile number yet. While true,
     * the UI shows a mandatory WhatsApp-number prompt before the user can continue, so every owner
     * and family member has a number on file for invites and sync.
     */
    val requiresWhatsAppNumber: Boolean = false,
    val isOwnerCandidate: Boolean = false,
    val familyRole: String = "",
    val error: String? = null
) : UiState {

    /** A WhatsApp/mobile number is valid when it is exactly 10 digits. */
    fun isWhatsAppNumberValid(number: String): Boolean =
        number.length == WHATSAPP_NUMBER_LENGTH && number.all { it.isDigit() }

    /** True only when [email] is a well-formed email address. */
    val isEmailValid: Boolean
        get() = email.isNotBlank() &&
            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Login is allowed only with a valid email and a non-blank password. */
    val isLoginEnabled: Boolean
        get() = isEmailValid && password.isNotBlank()

    companion object {
        const val WHATSAPP_NUMBER_LENGTH = 10
    }
}

sealed class LoginEffect : UiEffect {
    object NavigateToHome : LoginEffect()
    object NavigateToRegister : LoginEffect()
    object NavigateToForgotPassword : LoginEffect()
    object NavigateToProfileCreation : LoginEffect()
    object LaunchGoogleSignIn : LoginEffect()
    data class ShowError(val message: String) : LoginEffect()
    data class ShowMessage(val message: String) : LoginEffect()
}
