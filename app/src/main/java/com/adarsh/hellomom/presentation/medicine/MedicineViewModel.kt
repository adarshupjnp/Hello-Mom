package com.adarsh.hellomom.presentation.medicine

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.domain.repository.MedicineRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicineViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<MedicineIntent, MedicineState, MedicineEffect>() {

    override fun createInitialState(): MedicineState = MedicineState()

    init {
        handleIntent(MedicineIntent.LoadMedicines)
    }

    override fun handleIntent(intent: MedicineIntent) {
        when (intent) {
            MedicineIntent.LoadMedicines -> loadMedicines()
            is MedicineIntent.OnDeleteMedicine -> deleteMedicine(intent.medicineId)
            is MedicineIntent.OnUpdateMedicine -> updateMedicine(intent.medicine)
            is MedicineIntent.OnToggleMedicineStatus -> toggleMedicineStatus(intent.medicine)
            MedicineIntent.OnAddMedicineClicked -> setEffect { MedicineEffect.NavigateToAddMedicine }
            is MedicineIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                applyFilter()
            }
        }
    }

    private fun loadMedicines() {
        // Throttled background sync: navigating here is enough for family members to get the
        // owner's latest medicines — the Room flow below re-emits when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Medicine resolveAccess failed", e)
                    setState { copy(isLoading = false, error = "Could not load user") }
                    return@launch
                }
            val currentUser = access.user
            if (currentUser != null) {
                // The week always comes from the owner's start date (family has none of its own).
                val week = PregnancyProgress.week(access.owner?.pregnancyStartDate ?: currentUser.pregnancyStartDate)
                // Family members view the owner's medicines (activeUserId).
                medicineRepository.getMedicines(access.activeUserId)
                    .catch { e ->
                        SyncLogger.error("Medicine flow failed", e)
                        setState { copy(isLoading = false, error = e.message) }
                    }
                    .collect { medicines ->
                        setState {
                            copy(
                                medicines = medicines,
                                userName = currentUser.fullName,
                                pregnancyWeek = week,
                                isOwner = access.isOwner,
                                isLoading = false
                            )
                        }
                        applyFilter()
                    }
            } else {
                setState { copy(isLoading = false, error = "User not found") }
            }
        }
    }

    private fun applyFilter() {
        val selectedDate = uiState.value.selectedDate
        val all = uiState.value.medicines
        
        val filtered = if (selectedDate == null) {
            all
        } else {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val filterDateStr = sdf.format(java.util.Date(selectedDate))
            // Note: MedicineEntity typically doesn't have a specific date field if it's recurring,
            // but for filtering we might check createdAt or it might be logs.
            // If it's a fixed schedule we might not filter by "creation date".
            // Assuming we filter by createdAt for now if no specific "dose date" exists.
            all.filter { sdf.format(java.util.Date(it.updatedAt)) == filterDateStr }
        }
        
        setState { copy(filteredMedicines = filtered) }
    }

    private fun deleteMedicine(medicineId: String) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            medicineRepository.deleteMedicine(medicineId)
        }
    }

    private fun toggleMedicineStatus(medicine: com.adarsh.hellomom.data.local.entity.MedicineEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            medicineRepository.updateMedicine(medicine.copy(isCompleted = !medicine.isCompleted))
        }
    }

    private fun updateMedicine(medicine: com.adarsh.hellomom.data.local.entity.MedicineEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            medicineRepository.updateMedicine(
                medicine.copy(
                    syncStatus = com.adarsh.hellomom.data.local.SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
