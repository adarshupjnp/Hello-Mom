package com.adarsh.hellomom.presentation.reports

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.ReportEntity
import com.adarsh.hellomom.domain.repository.ReportRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<ReportsIntent, ReportsState, ReportsEffect>() {

    override fun createInitialState(): ReportsState = ReportsState()

    init {
        handleIntent(ReportsIntent.LoadReports)
    }

    override fun handleIntent(intent: ReportsIntent) {
        when (intent) {
            ReportsIntent.LoadReports -> loadReports()
            is ReportsIntent.OnUploadReport -> uploadReport(intent)
            is ReportsIntent.OnDeleteReport -> deleteReport(intent.report)
            is ReportsIntent.OnUpdateReport -> updateReport(intent.report)
            is ReportsIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                applyFilter()
            }
        }
    }

    private fun loadReports() {
        // Throttled background sync so family members see the owner's latest report list on
        // navigation — the Room flow below re-emits when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Reports resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            val user = access.user
            if (user != null) {
                // The week always comes from the owner's start date (family has none of its own).
                val week = PregnancyProgress.week(access.owner?.pregnancyStartDate ?: user.pregnancyStartDate)
                // Family members view the owner's reports (activeUserId).
                reportRepository.getReports(access.activeUserId)
                    .catch { e ->
                        SyncLogger.error("Reports flow failed", e)
                        setState { copy(isLoading = false) }
                    }
                    .collectLatest { list ->
                        setState {
                            copy(
                                reports = list,
                                userName = user.fullName,
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
        val allReports = uiState.value.reports
        
        val filtered = if (selectedDate == null) {
            allReports
        } else {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val filterDateStr = sdf.format(java.util.Date(selectedDate))
            allReports.filter { sdf.format(java.util.Date(it.date)) == filterDateStr }
        }
        
        setState { copy(filteredReports = filtered) }
    }

    private fun uploadReport(intent: ReportsIntent.OnUploadReport) {
        viewModelScope.launch {
            val access = roleManager.resolveAccess()
            if (!access.isOwner) return@launch
            setState { copy(isLoading = true) }
            val report = ReportEntity(
                reportId = UUID.randomUUID().toString(),
                userId = access.activeUserId,
                title = intent.title,
                category = intent.category,
                fileUrl = "",
                localPath = intent.fileUri,
                date = System.currentTimeMillis()
            )
            reportRepository.uploadReport(report, intent.fileUri)
        }
    }

    private fun deleteReport(report: ReportEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            reportRepository.deleteReport(report)
        }
    }

    private fun updateReport(report: ReportEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            reportRepository.updateReport(
                report.copy(
                    syncStatus = com.adarsh.hellomom.data.local.SyncStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
