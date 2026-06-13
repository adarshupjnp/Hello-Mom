package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun getReports(userId: String): Flow<List<ReportEntity>>
    suspend fun uploadReport(report: ReportEntity, fileUri: String): Result<Unit>
    suspend fun updateReport(report: ReportEntity): Result<Unit>
    suspend fun deleteReport(report: ReportEntity): Result<Unit>
}
