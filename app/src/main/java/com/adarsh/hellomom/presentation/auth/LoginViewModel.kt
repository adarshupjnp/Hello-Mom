package com.adarsh.hellomom.presentation.auth

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel<LoginIntent, LoginState, LoginEffect>() {

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
                val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(user.fullName, user.email)

                if (isOwner && user.pregnancyStartDate == null) {
                    setEffect { LoginEffect.NavigateToProfileCreation }
                } else {
                    setEffect { LoginEffect.NavigateToHome }
                }
            }.onFailure { e ->
                setState { copy(isLoading = false, error = e.message) }
                setEffect { LoginEffect.ShowError(e.message ?: "Google Login failed") }
            }
        }
    }
}
