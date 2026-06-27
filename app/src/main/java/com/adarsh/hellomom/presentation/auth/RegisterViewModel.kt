package com.adarsh.hellomom.presentation.auth

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel<RegisterIntent, RegisterState, RegisterEffect>() {

    override fun createInitialState(): RegisterState = RegisterState()

    override fun handleIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.OnFullNameChanged -> setState { copy(fullName = intent.name) }
            is RegisterIntent.OnEmailChanged -> setState { copy(email = intent.email) }
            is RegisterIntent.OnPasswordChanged -> setState { copy(password = intent.password) }
            is RegisterIntent.OnConfirmPasswordChanged -> setState { copy(confirmPassword = intent.confirmPassword) }
            is RegisterIntent.OnMobileChanged -> setState { copy(mobile = intent.mobile) }
            is RegisterIntent.OnDobChanged -> setState { copy(dob = intent.dob) }
            is RegisterIntent.OnFamilyRoleChanged -> setState { copy(familyRole = intent.role) }
            RegisterIntent.OnRegisterClicked -> register()
            RegisterIntent.OnLoginClicked -> setEffect { RegisterEffect.NavigateToLogin }
        }
    }

    private fun register() {
        viewModelScope.launch {
            if (!uiState.value.isRegisterEnabled) {
                val error = if (!uiState.value.isFamilyRoleValid) "Please select your relationship"
                else "Please fix the highlighted fields before continuing"
                setEffect { RegisterEffect.ShowError(error) }
                return@launch
            }

            setState { copy(isLoading = true, error = null) }
            val user = UserEntity(
                userId = "", // Will be set by AuthRepository
                fullName = uiState.value.fullName,
                dob = uiState.value.dob,
                mobileNumber = uiState.value.mobile,
                email = uiState.value.email,
                profilePicture = null,
                pregnancyStartDate = if (uiState.value.dob > 0) uiState.value.dob else null, // Using dob field as pregnancy start date if set
                dueDate = null,
                bloodGroup = null,
                emergencyContact = null,
                doctorName = null,
                hospitalName = null,
                weight = null,
                height = null,
                allergies = null,
                familyRole = if (uiState.value.isOwnerCandidate) null else uiState.value.familyRole
            )
            val result = authRepository.register(user, uiState.value.password)
            result.onSuccess {
                setState { copy(isLoading = false, showSuccessAnimation = true) }
                val msg = if (uiState.value.isOwnerCandidate) 
                    "Welcome Mom! Your account has been created successfully. Let's set up your profile."
                else 
                    "Success! Your family account is ready. Welcome to the journey!"
                
                setEffect { RegisterEffect.ShowSuccess(msg) }
                
                kotlinx.coroutines.delay(2500) // Show animation and message
                
                val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(user.fullName, user.email)
                if (isOwner) {
                    setEffect { RegisterEffect.NavigateToProfileCreation }
                } else {
                    setEffect { RegisterEffect.NavigateToHome }
                }
            }.onFailure { e ->
                setState { copy(isLoading = false, error = e.message) }
                setEffect { RegisterEffect.ShowError(e.message ?: "Registration failed") }
            }
        }
    }
}
