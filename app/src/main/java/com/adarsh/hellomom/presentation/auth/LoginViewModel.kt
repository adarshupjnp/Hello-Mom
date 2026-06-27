package com.adarsh.hellomom.presentation.auth

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : BaseViewModel<LoginIntent, LoginState, LoginEffect>() {

    /**
     * The Google-authenticated user awaiting a WhatsApp number. Held while the mandatory prompt is
     * shown so we can save the number against the right account and then route the user onward.
     */
    private var pendingGoogleUser: UserEntity? = null

    override fun createInitialState(): LoginState = LoginState()

    override fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.OnEmailChanged -> setState { copy(email = intent.email) }
            is LoginIntent.OnPasswordChanged -> setState { copy(password = intent.password) }
            LoginIntent.OnLoginClicked -> login()
            is LoginIntent.OnGoogleSignInSuccess -> loginWithGoogle(intent.idToken)
            is LoginIntent.OnGoogleSignInError -> {
                setState { copy(isLoading = false, error = intent.message) }
                setEffect { LoginEffect.ShowError(intent.message) }
            }
            LoginIntent.OnGoogleSignInClicked -> {
                setState { copy(isLoading = true, error = null) }
                setEffect { LoginEffect.LaunchGoogleSignIn }
            }
            LoginIntent.OnForgotPasswordClicked -> setEffect { LoginEffect.NavigateToForgotPassword }
            is LoginIntent.OnResetPassword -> resetPassword(intent.email)
            LoginIntent.OnRegisterClicked -> setEffect { LoginEffect.NavigateToRegister }
            is LoginIntent.OnWhatsAppNumberSubmitted -> submitWhatsAppNumber(intent.number, intent.role)
            is LoginIntent.OnFamilyRoleChanged -> setState { copy(familyRole = intent.role) }
        }
    }

    private fun resetPassword(email: String) {
        viewModelScope.launch {
            if (email.isEmpty()) {
                setEffect { LoginEffect.ShowError("Please enter your email") }
                return@launch
            }
            setState { copy(isLoading = true) }
            val result = authRepository.resetPassword(email)
            result.onSuccess {
                setState { copy(isLoading = false) }
                setEffect { LoginEffect.ShowMessage("Password reset email sent!") }
            }.onFailure { e ->
                setState { copy(isLoading = false) }
                setEffect { LoginEffect.ShowError(e.message ?: "Failed to send reset email") }
            }
        }
    }

    private fun login() {
        viewModelScope.launch {
            if (uiState.value.email.isEmpty() || uiState.value.password.isEmpty()) {
                setEffect { LoginEffect.ShowError("Please fill all fields") }
                return@launch
            }
            
            setState { copy(isLoading = true, error = null) }
            val result = authRepository.login(uiState.value.email, uiState.value.password)
            result.onSuccess { user ->
                val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(user.fullName, user.email)

                if (isOwner && user.pregnancyStartDate == null) {
                    setEffect { LoginEffect.NavigateToProfileCreation }
                } else {
                    setEffect { LoginEffect.NavigateToHome }
                }
            }.onFailure { e ->
                setState { copy(isLoading = false, error = e.message) }
                setEffect { LoginEffect.ShowError(e.message ?: "Login failed") }
            }
        }
    }

    private fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            val result = authRepository.loginWithGoogle(idToken)
            result.onSuccess { user ->
                // Google accounts never carry a phone number, so first-time Google users land here
                // without one. Block onward navigation until they provide a WhatsApp number; returning
                // users whose number is already on file skip the prompt entirely.
                if (user.mobileNumber.isBlank()) {
                    pendingGoogleUser = user
                    val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(user.fullName, user.email)
                    setState { 
                        copy(
                            isLoading = false, 
                            requiresWhatsAppNumber = true,
                            isOwnerCandidate = isOwner
                        ) 
                    }
                } else {
                    navigateAfterAuth(user)
                }
            }.onFailure { e ->
                setState { copy(isLoading = false, error = e.message) }
                setEffect { LoginEffect.ShowError(e.message ?: "Google Login failed") }
            }
        }
    }

    private fun submitWhatsAppNumber(number: String, role: String?) {
        val user = pendingGoogleUser ?: return
        if (!uiState.value.isWhatsAppNumberValid(number)) {
            setEffect { LoginEffect.ShowError("Enter a valid ${LoginState.WHATSAPP_NUMBER_LENGTH}-digit WhatsApp number") }
            return
        }
        
        if (!uiState.value.isOwnerCandidate && role.isNullOrBlank()) {
            setEffect { LoginEffect.ShowError("Please select your relationship") }
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            val updatedUser = user.copy(
                mobileNumber = number,
                familyRole = if (uiState.value.isOwnerCandidate) null else role
            )
            userRepository.updateUser(updatedUser)
                .onSuccess {
                    pendingGoogleUser = null
                    setState { copy(isLoading = false, requiresWhatsAppNumber = false) }
                    navigateAfterAuth(updatedUser)
                }
                .onFailure { e ->
                    setState { copy(isLoading = false, error = e.message) }
                    setEffect { LoginEffect.ShowError(e.message ?: "Failed to save details") }
                }
        }
    }

    /** Routes a freshly authenticated [user] to onboarding (owner, no pregnancy date) or Home. */
    private fun navigateAfterAuth(user: UserEntity) {
        val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(user.fullName, user.email)
        if (isOwner && user.pregnancyStartDate == null) {
            setEffect { LoginEffect.NavigateToProfileCreation }
        } else {
            setEffect { LoginEffect.NavigateToHome }
        }
    }
}
