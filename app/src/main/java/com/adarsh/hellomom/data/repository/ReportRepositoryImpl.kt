package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.ReportDao
import com.adarsh.hellomom.data.local.entity.ReportEntity
import com.adarsh.hellomom.domain.repository.ReportRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao,
    private val firestore: FirebaseFirestore
) : ReportRepository {

    override fun getReports(userId: String): Flow<List<ReportEntity>> {
        return reportDao.getReports(userId)
    }

    override suspend fun uploadReport(report: ReportEntity, fileUri: String): Result<Unit> {
        return try {
            // Note: Since we want to stay 100% free, we are not using Firebase Storage.
            // Files remain stored locally on the device (localPath).
            // We only sync the metadata (Title, Category, Date) to Firestore.
            
            // Offline-first: persist locally first, then push metadata best-effort.
            reportDao.insertReport(report)
            SyncLogger.local("ADD report", "reports", "id=${report.reportId} userId=${report.userId} title=${report.title} category=${report.category}")
            firestore.collection("users").document(report.userId)
                .collection("reports").document(report.reportId).set(report)
            SyncLogger.firebaseWrite("ADD report", "users/${report.userId}/reports/${report.reportId}", "title=${report.title} category=${report.category} (metadata only, file stays local)")

            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD report failed id=${report.reportId}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateReport(report: ReportEntity): Result<Unit> {
        return try {
            // reportDao.insertReport uses REPLACE, so it upserts on reportId.
            reportDao.insertReport(report)
            SyncLogger.local("EDIT report", "reports", "id=${report.reportId} userId=${report.userId} title=${report.title}")
            firestore.collection("users").document(report.userId)
                .collection("reports").document(report.reportId).set(report)
            SyncLogger.firebaseWrite("EDIT report", "users/${report.userId}/reports/${report.reportId}", "title=${report.title} category=${report.category}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT report failed id=${report.reportId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteReport(report: ReportEntity): Result<Unit> {
        return try {
            reportDao.deleteReport(report)
            SyncLogger.local("DELETE report", "reports", "id=${report.reportId} userId=${report.userId}")
            firestore.collection("users").document(report.userId)
                .collection("reports").document(report.reportId).delete()
            SyncLogger.firebaseWrite("DELETE report", "users/${report.userId}/reports/${report.reportId}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE report failed id=${report.reportId}", e)
            Result.failure(e)
        }
    }
}
