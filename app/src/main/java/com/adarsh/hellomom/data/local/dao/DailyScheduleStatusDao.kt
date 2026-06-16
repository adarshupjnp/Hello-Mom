package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.DailyScheduleStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyScheduleStatusDao {
    /** Today's marks for a given owner — the UI overlays these onto the derived schedule rows. */
    @Query("SELECT * FROM daily_schedule_status WHERE userId = :userId AND date = :date AND isDeleted = 0")
    fun getStatusesForDate(userId: String, date: String): Flow<List<DailyScheduleStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: DailyScheduleStatusEntity)

    @Query("SELECT * FROM daily_schedule_status WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSync(): List<DailyScheduleStatusEntity>

    // Reconciliation after a pull: drop rows deleted remotely; keep unpushed PENDING edits.
    @Query("DELETE FROM daily_schedule_status WHERE userId = :userId AND syncStatus != 'PENDING' AND id NOT IN (:keepIds)")
    suspend fun deleteStatusesNotIn(userId: String, keepIds: List<String>)
}
