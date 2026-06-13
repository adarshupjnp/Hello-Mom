package com.adarsh.hellomom.presentation.reports

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.ReportEntity

sealed class ReportsIntent : UiIntent {
    object LoadReports : ReportsIntent()
    data class OnUploadReport(val title: String, val category: String, val fileUri: String) : ReportsIntent()
    data class OnDeleteReport(val report: ReportEntity) : ReportsIntent()
    data class OnUpdateReport(val report: ReportEntity) : ReportsIntent()
    data class OnDateFilterChanged(val date: Long?) : ReportsIntent()
}

data class ReportsState(
    val reports: List<ReportEntity> = emptyList(),
    val filteredReports: List<ReportEntity> = emptyList(),
    val selectedDate: Long? = null,
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val userName: String = "",
    val pregnancyWeek: Int = 1,
    val error: String? = null
) : UiState

sealed class ReportsEffect : UiEffect {
    data class ShowError(val message: String) : ReportsEffect()
}
