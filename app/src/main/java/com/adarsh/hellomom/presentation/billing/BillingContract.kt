package com.adarsh.hellomom.presentation.billing

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.BillingEntity

sealed class BillingIntent : UiIntent {
    object LoadBills : BillingIntent()
    data class OnAddBill(val title: String, val amount: Double, val category: String, val date: Long = System.currentTimeMillis()) : BillingIntent()
    data class OnDeleteBill(val bill: BillingEntity) : BillingIntent()
    data class OnUpdateBill(val bill: BillingEntity) : BillingIntent()
    data class OnDateFilterChanged(val date: Long?) : BillingIntent()
}

data class BillingState(
    val bills: List<BillingEntity> = emptyList(),
    val filteredBills: List<BillingEntity> = emptyList(),
    val totalExpense: Double = 0.0,
    val selectedDate: Long? = null,
    val isLoading: Boolean = false,
    val isOwner: Boolean = true,
    val userName: String = "",
    val userHospitalName: String? = null,
    val userDoctorName: String? = null,
    val pregnancyWeek: Int = 1,
    val error: String? = null
) : UiState

sealed class BillingEffect : UiEffect {
    data class ShowError(val message: String) : BillingEffect()
}
