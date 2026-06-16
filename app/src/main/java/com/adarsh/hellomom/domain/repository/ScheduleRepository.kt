package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.DailyScheduleStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * Daily done/pending marks for the Today's Schedule section. Offline-first like every other
 * repository: writes hit Room immediately, then best-effort Firestore. Only the owner ever
 * writes (callers gate on RoleManager.isOwner); family members read the same data.
 */
interface ScheduleRepository {
    fun getDailyStatuses(userId: String, date: String): Flow<List<DailyScheduleStatusEntity>>

    suspend fun setStatus(
        userId: String,
        date: String,
        type: String,
        refId: String,
        isDone: Boolean
    ): Result<Unit>
}
