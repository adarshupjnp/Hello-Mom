package com.adarsh.hellomom.presentation.medicine

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState

sealed class AddMedicineIntent : UiIntent {
    data class OnNameChanged(val name: String) : AddMedicineIntent()
    data class OnDosageChanged(val dosage: String) : AddMedicineIntent()
    // User tapped one of the predefined dosage chips.
    data class OnDosageOptionSelected(val dosage: String) : AddMedicineIntent()
    // User tapped the "Other" chip — switches dosage to a manual text field.
    object OnCustomDosageSelected : AddMedicineIntent()
    data class OnTimingChanged(val timing: String) : AddMedicineIntent()
    data class OnFrequencyChanged(val frequency: String) : AddMedicineIntent()
    data class OnBeforeAfterMealChanged(val value: String) : AddMedicineIntent()
    data class OnToggleDay(val day: String) : AddMedicineIntent()
    object OnToggleAllDays : AddMedicineIntent()
    data class OnStartDateChanged(val date: Long) : AddMedicineIntent()
    data class OnEndDateChanged(val date: Long) : AddMedicineIntent()
    data class OnNotesChanged(val notes: String) : AddMedicineIntent()
    data class OnScanPrescription(val uri: android.net.Uri) : AddMedicineIntent()
    object OnSaveClicked : AddMedicineIntent()
}

data class AddMedicineState(
    val name: String = "",
    val dosage: String = "",
    // True when the user chose "Other" and is typing a custom dosage instead of picking a chip.
    val isCustomDosage: Boolean = false,
    val timing: String = "",
    val frequency: String = "Daily",
    val beforeAfterMeal: String = "After Meal",
    // Weekdays the medicine should be taken. Empty = nothing chosen yet (selection is mandatory).
    val selectedDays: Set<String> = emptySet(),
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000),
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class AddMedicineEffect : UiEffect {
    object NavigateBack : AddMedicineEffect()
    data class ShowError(val message: String) : AddMedicineEffect()
}
