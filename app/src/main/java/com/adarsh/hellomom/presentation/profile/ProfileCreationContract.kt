package com.adarsh.hellomom.presentation.profile

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class ProfileCreationIntent : UiIntent {
    data class OnPregnancyStartDateChanged(val date: Long) : ProfileCreationIntent()
    data class OnDueDateChanged(val date: Long) : ProfileCreationIntent()
    data class OnBloodGroupChanged(val bloodGroup: String) : ProfileCreationIntent()
    data class OnEmergencyContactChanged(val contact: String) : ProfileCreationIntent()
    data class OnDoctorNameChanged(val name: String) : ProfileCreationIntent()
    data class OnHospitalNameChanged(val name: String) : ProfileCreationIntent()
    data class OnWeightChanged(val weight: String) : ProfileCreationIntent()
    data class OnHeightChanged(val height: String) : ProfileCreationIntent()
    data class OnAllergiesChanged(val allergies: String) : ProfileCreationIntent()
    object OnSaveClicked : ProfileCreationIntent()
}

data class ProfileCreationState(
    val pregnancyStartDate: Long? = null,
    val dueDate: Long? = null,
    val bloodGroup: String = "",
    val emergencyContact: String = "",
    val doctorName: String = "",
    val hospitalName: String = "",
    val weight: String = "",
    val height: String = "",
    val allergies: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class ProfileCreationEffect : UiEffect {
    object NavigateToHome : ProfileCreationEffect()
    data class ShowError(val message: String) : ProfileCreationEffect()
}
