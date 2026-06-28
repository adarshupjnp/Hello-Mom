package com.adarsh.hellomom.presentation.billing

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.BillingEntity
import com.adarsh.hellomom.domain.repository.BillingRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<BillingIntent, BillingState, BillingEffect>() {

    override fun createInitialState(): BillingState = BillingState()

    init {
        handleIntent(BillingIntent.LoadBills)
    }

    override fun handleIntent(intent: BillingIntent) {
        when (intent) {
            BillingIntent.LoadBills -> loadBills()
            is BillingIntent.OnAddBill -> addBill(intent)
            is BillingIntent.OnDeleteBill -> deleteBill(intent.bill)
            is BillingIntent.OnUpdateBill -> updateBill(intent.bill)
            is BillingIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                applyFilter()
            }
        }
    }

    private fun loadBills() {
        // Throttled background sync so family members see the owner's latest bills on
        // navigation — the Room flow below re-emits when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            // Family members view the owner's bills (activeUserId) read-only.
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Billing resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            val user = access.user
            if (user != null) {
                // Use the owner's pregnancy start date so the PDF header week is correct for family too.
                val week = PregnancyProgress.week(access.owner?.pregnancyStartDate ?: user.pregnancyStartDate)
                val ownerHospital = access.owner?.hospitalName
                val ownerDoctor = access.owner?.doctorName
                
                billingRepository.getBills(access.activeUserId)
                    .catch { e ->
                        SyncLogger.error("Billing flow failed", e)
                        setState { copy(isLoading = false) }
                    }
                    .collectLatest { list ->
                        setState {
                            copy(
                                bills = list,
                                userName = access.owner?.fullName ?: user.fullName,
                                userHospitalName = ownerHospital,
                                userDoctorName = ownerDoctor,
                                pregnancyWeek = week,
                                isOwner = access.isOwner,
                                isLoading = false
                            )
                        }
                        applyFilter()
                    }
            } else {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun applyFilter() {
        val selectedDate = uiState.value.selectedDate
        val allBills = uiState.value.bills
        
        val filtered = if (selectedDate == null) {
            allBills
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val filterDateStr = sdf.format(Date(selectedDate))
            allBills.filter { sdf.format(Date(it.date)) == filterDateStr }
        }
        
        val total = filtered.sumOf { it.amount }
        setState { copy(filteredBills = filtered, totalExpense = total) }
    }

    private fun addBill(intent: BillingIntent.OnAddBill) {
        viewModelScope.launch {
            // Read-only family members must never write.
            val access = roleManager.resolveAccess()
            if (!access.isOwner) return@launch
            val bill = BillingEntity(
                billId = UUID.randomUUID().toString(),
                userId = access.activeUserId,
                title = intent.title,
                amount = intent.amount,
                category = intent.category,
                billImageUrl = null,
                date = intent.date
            )
            billingRepository.insertBill(bill)
        }
    }

    private fun deleteBill(bill: BillingEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            billingRepository.deleteBill(bill)
        }
    }

    private fun updateBill(bill: BillingEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            billingRepository.updateBill(
                bill.copy(
                    syncStatus = com.adarsh.hellomom.data.local.SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
