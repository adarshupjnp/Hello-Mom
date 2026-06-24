package com.adarsh.hellomom.presentation.auth

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class RegisterIntent : UiIntent {
    data class OnFullNameChanged(val name: String) : RegisterIntent()
    data class OnEmailChanged(val email: String) : RegisterIntent()
    data class OnPasswordChanged(val password: String) : RegisterIntent()
    data class OnConfirmPasswordChanged(val confirmPassword: String) : RegisterIntent()
    data class OnMobileChanged(val mobile: String) : RegisterIntent()
    data class OnDobChanged(val dob: Long) : RegisterIntent()
    object OnRegisterClicked : RegisterIntent()
    object OnLoginClicked : RegisterIntent()
}

data class RegisterState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val mobile: String = "",
    val dob: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState {
    /** Full name must be 2-50 characters after trimming surrounding whitespace. */
    val isFullNameValid: Boolean
        get() = fullName.trim().length in FULL_NAME_MIN_LENGTH..FULL_NAME_MAX_LENGTH

    /** True only when [email] is a well-formed email address. */
    val isEmailValid: Boolean
        get() = email.isNotBlank() &&
            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Mobile number must be exactly 10 digits. */
    val isMobileValid: Boolean
        get() = mobile.length == MOBILE_LENGTH && mobile.all { it.isDigit() }

    /** At least 8 characters with one uppercase, one lowercase and one digit. */
    val isPasswordValid: Boolean
        get() = password.length >= PASSWORD_MIN_LENGTH &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() }

    /** Confirm password is non-empty and matches [password]. */
    val doPasswordsMatch: Boolean
        get() = confirmPassword.isNotEmpty() && password == confirmPassword

    /** Registration is allowed only when every field passes validation. */
    val isRegisterEnabled: Boolean
        get() = isFullNameValid && isEmailValid && isMobileValid &&
            isPasswordValid && doPasswordsMatch

    companion object {
        const val FULL_NAME_MIN_LENGTH = 2
        const val FULL_NAME_MAX_LENGTH = 50
        const val MOBILE_LENGTH = 10
        const val PASSWORD_MIN_LENGTH = 8
        const val PASSWORD_MAX_LENGTH = 32
    }
}

sealed class RegisterEffect : UiEffect {
    object NavigateToProfileCreation : RegisterEffect()
    object NavigateToHome : RegisterEffect()
    object NavigateToLogin : RegisterEffect()
    data class ShowError(val message: String) : RegisterEffect()
}
