package com.adarsh.hellomom.presentation.medicine

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.data.local.entity.MedicineEntity
import com.adarsh.hellomom.domain.repository.AiRepository
import com.adarsh.hellomom.domain.repository.MedicineRepository
import com.adarsh.hellomom.domain.repository.OcrRepository
import com.adarsh.hellomom.notification.ReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddMedicineViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val ocrRepository: OcrRepository,
    private val aiRepository: AiRepository,
    private val reminderManager: ReminderManager,
    private val roleManager: RoleManager
) : BaseViewModel<AddMedicineIntent, AddMedicineState, AddMedicineEffect>() {

    override fun createInitialState(): AddMedicineState = AddMedicineState()

    companion object {
        /** Canonical weekday order used for both the picker and the stored value. */
        val WEEK_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        /** Predefined dosages shown as selectable chips; "Other" lets the user type a custom value. */
        val DOSAGE_OPTIONS = listOf(
            "250 mg", "500 mg", "650 mg", "1 tablet", "2 tablets", "5 ml", "10 ml", "1 drop"
        )

        /** Meal-timing options shown as selectable chips. */
        val MEAL_OPTIONS = listOf("Before Meal", "After Meal", "Without Meal")
    }

    override fun handleIntent(intent: AddMedicineIntent) {
        when (intent) {
            is AddMedicineIntent.OnNameChanged -> setState { copy(name = intent.name) }
            is AddMedicineIntent.OnDosageChanged -> setState { copy(dosage = intent.dosage) }
            is AddMedicineIntent.OnDosageOptionSelected -> setState {
                copy(dosage = intent.dosage, isCustomDosage = false)
            }
            AddMedicineIntent.OnCustomDosageSelected -> setState {
                copy(dosage = "", isCustomDosage = true)
            }
            is AddMedicineIntent.OnTimingChanged -> setState { copy(timing = intent.timing) }
            is AddMedicineIntent.OnFrequencyChanged -> setState { copy(frequency = intent.frequency) }
            is AddMedicineIntent.OnBeforeAfterMealChanged -> setState { copy(beforeAfterMeal = intent.value) }
            is AddMedicineIntent.OnToggleDay -> setState {
                copy(selectedDays = selectedDays.toMutableSet().apply {
                    if (!add(intent.day)) remove(intent.day)
                })
            }
            AddMedicineIntent.OnToggleAllDays -> setState {
                copy(selectedDays = if (selectedDays.containsAll(WEEK_DAYS)) emptySet() else WEEK_DAYS.toSet())
            }
            is AddMedicineIntent.OnStartDateChanged -> setState { copy(startDate = intent.date) }
            is AddMedicineIntent.OnEndDateChanged -> setState { copy(endDate = intent.date) }
            is AddMedicineIntent.OnNotesChanged -> setState { copy(notes = intent.notes) }
            is AddMedicineIntent.OnScanPrescription -> scanPrescription(intent.uri)
            AddMedicineIntent.OnSaveClicked -> saveMedicine()
        }
    }

    private fun scanPrescription(uri: android.net.Uri) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val ocrResult = ocrRepository.extractTextFromImage(uri)
            ocrResult.onSuccess { text ->
                val aiResult = aiRepository.parsePrescription(text)
                aiResult.onSuccess { medicines ->
                    if (medicines.isNotEmpty()) {
                        val firstMed = medicines.first()
                        setState {
                            copy(
                                name = firstMed.name,
                                dosage = firstMed.dosage,
                                // Scanned dosage is free-form, so surface it via the manual field
                                // unless it happens to match one of the predefined chips.
                                isCustomDosage = firstMed.dosage.isNotBlank() &&
                                    firstMed.dosage !in DOSAGE_OPTIONS,
                                timing = firstMed.timing,
                                isLoading = false
                            )
                        }
                    } else {
                        setState { copy(isLoading = false) }
                        setEffect { AddMedicineEffect.ShowError("Could not extract medicine details. Please enter manually.") }
                    }
                }.onFailure { e ->
                    setState { copy(isLoading = false) }
                    setEffect { AddMedicineEffect.ShowError("AI Parsing failed: ${e.message}") }
                }
            }.onFailure { e ->
                setState { copy(isLoading = false) }
                setEffect { AddMedicineEffect.ShowError("OCR failed: ${e.message}") }
            }
        }
    }

    private fun saveMedicine() {
        viewModelScope.launch {
            if (uiState.value.name.isEmpty()) {
                setEffect { AddMedicineEffect.ShowError("Please enter medicine name") }
                return@launch
            }

            // At least one weekday must be selected so we know which days the medicine is taken.
            if (uiState.value.selectedDays.isEmpty()) {
                setEffect { AddMedicineEffect.ShowError("Please select at least one day of the week") }
                return@launch
            }

            setState { copy(isLoading = true) }
            val access = roleManager.resolveAccess()
            val currentUser = access.user
            // Defense-in-depth: only owners may create medicines.
            if (!access.isOwner) {
                setState { copy(isLoading = false) }
                setEffect { AddMedicineEffect.ShowError("Read-only access: you cannot add medicines.") }
                return@launch
            }
            if (currentUser != null) {
                val medicineId = UUID.randomUUID().toString()
                val medicine = MedicineEntity(
                    medicineId = medicineId,
                    userId = access.activeUserId,
                    name = uiState.value.name,
                    dosage = uiState.value.dosage,
                    timing = uiState.value.timing,
                    frequency = uiState.value.frequency,
                    beforeAfterMeal = uiState.value.beforeAfterMeal,
                    // Persist the chosen weekdays in canonical Mon→Sun order.
                    daysOfWeek = WEEK_DAYS.filter { it in uiState.value.selectedDays }.joinToString(","),
                    startDate = uiState.value.startDate,
                    endDate = uiState.value.endDate,
                    notes = uiState.value.notes
                )
                val result = medicineRepository.insertMedicine(medicine)
                result.onSuccess {
                    // Schedule reminder (for simplicity, scheduling it 1 minute from now as a test)
                    reminderManager.scheduleReminder(
                         id = medicineId.hashCode(),
                        title = "Medicine Reminder",
                        message = "Hey ${currentUser.fullName}, it's time to take your ${medicine.name}.",
                        timeInMillis = System.currentTimeMillis() + 60000
                    )
                    setEffect { AddMedicineEffect.NavigateBack }
                }.onFailure { e ->
                    setState { copy(isLoading = false, error = e.message) }
                    setEffect { AddMedicineEffect.ShowError(e.message ?: "Failed to save medicine") }
                }
            }
        }
    }
}
