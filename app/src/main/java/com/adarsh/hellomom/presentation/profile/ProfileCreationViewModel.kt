package com.adarsh.hellomom.presentation.profile

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileCreationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : BaseViewModel<ProfileCreationIntent, ProfileCreationState, ProfileCreationEffect>() {

    override fun createInitialState(): ProfileCreationState = ProfileCreationState()

    override fun handleIntent(intent: ProfileCreationIntent) {
        when (intent) {
            is ProfileCreationIntent.OnPregnancyStartDateChanged -> setState { copy(pregnancyStartDate = intent.date) }
            is ProfileCreationIntent.OnDueDateChanged -> setState { copy(dueDate = intent.date) }
            is ProfileCreationIntent.OnBloodGroupChanged -> setState { copy(bloodGroup = intent.bloodGroup) }
            is ProfileCreationIntent.OnEmergencyContactChanged -> setState { copy(emergencyContact = intent.contact) }
            is ProfileCreationIntent.OnDoctorNameChanged -> setState { copy(doctorName = intent.name) }
            is ProfileCreationIntent.OnHospitalNameChanged -> setState { copy(hospitalName = intent.name) }
            is ProfileCreationIntent.OnWeightChanged -> setState { copy(weight = intent.weight) }
            is ProfileCreationIntent.OnHeightChanged -> setState { copy(height = intent.height) }
            is ProfileCreationIntent.OnAllergiesChanged -> setState { copy(allergies = intent.allergies) }
            ProfileCreationIntent.OnSaveClicked -> saveProfile()
        }
    }

    private fun saveProfile() {
        viewModelScope.launch {
            if (uiState.value.pregnancyStartDate == null) {
                setEffect { ProfileCreationEffect.ShowError("Pregnancy Start Date is mandatory") }
                return@launch
            }

            setState { copy(isLoading = true) }
            val currentUser = authRepository.getCurrentUser().first()
            if (currentUser != null) {
                val updatedUser = currentUser.copy(
                    pregnancyStartDate = uiState.value.pregnancyStartDate,
                    dueDate = uiState.value.dueDate,
                    bloodGroup = uiState.value.bloodGroup,
                    emergencyContact = uiState.value.emergencyContact,
                    doctorName = uiState.value.doctorName,
                    hospitalName = uiState.value.hospitalName,
                    weight = uiState.value.weight.toFloatOrNull(),
                    height = uiState.value.height.toFloatOrNull(),
                    allergies = uiState.value.allergies
                )
                val result = userRepository.updateUser(updatedUser)
                result.onSuccess {
                    setEffect { ProfileCreationEffect.NavigateToHome }
                }.onFailure { e ->
                    setState { copy(isLoading = false, error = e.message) }
                    setEffect { ProfileCreationEffect.ShowError(e.message ?: "Failed to save profile") }
                }
            } else {
                setState { copy(isLoading = false, error = "User not found") }
                setEffect { ProfileCreationEffect.ShowError("User not found") }
            }
        }
    }
}
