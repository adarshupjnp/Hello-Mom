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
    data class OnFamilyRoleChanged(val role: String) : RegisterIntent()
    object OnRegisterClicked : RegisterIntent()
    object OnLoginClicked : RegisterIntent()
    data class OnLocationPermissionResult(val granted: Boolean) : RegisterIntent()
}

data class RegisterState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val mobile: String = "",
    val dob: Long = 0,
    val familyRole: String = "",
    val isLoading: Boolean = false,
    val showSuccessAnimation: Boolean = false,
    val error: String? = null
) : UiState {
    /** True if the current inputs identify this user as a pregnancy owner (Adarsh/Riya). */
    val isOwnerCandidate: Boolean
        get() = com.adarsh.hellomom.core.RoleManager.isOwnerUser(fullName, email)

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

    /** Family role must be selected if NOT an owner candidate. */
    val isFamilyRoleValid: Boolean
        get() = isOwnerCandidate || familyRole.isNotBlank()

    /** Registration is allowed only when every field passes validation. */
    val isRegisterEnabled: Boolean
        get() = isFullNameValid && isEmailValid && isMobileValid &&
            isPasswordValid && doPasswordsMatch && isFamilyRoleValid

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
    object RequestLocation : RegisterEffect()
    data class ShowError(val message: String) : RegisterEffect()
    data class ShowSuccess(val message: String) : RegisterEffect()
}
