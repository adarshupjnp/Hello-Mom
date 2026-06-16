package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.SyncStatus
import com.adarsh.hellomom.data.local.dao.DailyScheduleStatusDao
import com.adarsh.hellomom.data.local.entity.DailyScheduleStatusEntity
import com.adarsh.hellomom.domain.repository.ScheduleRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val dao: DailyScheduleStatusDao,
    private val firestore: FirebaseFirestore
) : ScheduleRepository {

    override fun getDailyStatuses(userId: String, date: String): Flow<List<DailyScheduleStatusEntity>> =
        dao.getStatusesForDate(userId, date)

    override suspend fun setStatus(
        userId: String,
        date: String,
        type: String,
        refId: String,
        isDone: Boolean
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val id = "${date}_${type}_$refId"
        // Offline-first: write Room first (UI updates instantly) as PENDING, then best-effort push.
        val pending = DailyScheduleStatusEntity(
            id = id,
            userId = userId,
            date = date,
            type = type,
            refId = refId,
            isDone = isDone,
            doneAt = if (isDone) now else null,
            syncStatus = SyncStatus.PENDING,
            updatedAt = now
        )
        return try {
            dao.upsert(pending)
            SyncLogger.local("SET schedule status", "daily_schedule_status", "id=$id done=$isDone")
            // Best-effort Firestore write; if it fails (offline), pushPendingData() retries later.
            runCatching {
                firestore.collection("users").document(userId)
                    .collection("daily_schedule_status").document(id)
                    .set(pending.copy(syncStatus = SyncStatus.SYNCED)).await()
                dao.upsert(pending.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis()))
                SyncLogger.firebaseWrite("PUSH schedule status", "users/$userId/daily_schedule_status/$id", "done=$isDone")
            }.onFailure { SyncLogger.warn("schedule status remote write deferred (offline?)", it) }
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("SET schedule status failed id=$id", e)
            Result.failure(e)
        }
    }
}
