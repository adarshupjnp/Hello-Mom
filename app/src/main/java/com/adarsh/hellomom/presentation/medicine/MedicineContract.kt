package com.adarsh.hellomom.presentation.medicine

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.MedicineEntity

sealed class MedicineIntent : UiIntent {
    object LoadMedicines : MedicineIntent()
    data class OnDeleteMedicine(val medicineId: String) : MedicineIntent()
    data class OnUpdateMedicine(val medicine: MedicineEntity) : MedicineIntent()
    data class OnToggleMedicineStatus(val medicine: MedicineEntity) : MedicineIntent()
    object OnAddMedicineClicked : MedicineIntent()
    data class OnDateFilterChanged(val date: Long?) : MedicineIntent()
}

data class MedicineState(
    val medicines: List<MedicineEntity> = emptyList(),
    val filteredMedicines: List<MedicineEntity> = emptyList(),
    val selectedDate: Long? = null,
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val userName: String = "",
    val pregnancyWeek: Int = 1,
    val error: String? = null
) : UiState

sealed class MedicineEffect : UiEffect {
    object NavigateToAddMedicine : MedicineEffect()
    data class ShowError(val message: String) : MedicineEffect()
}
