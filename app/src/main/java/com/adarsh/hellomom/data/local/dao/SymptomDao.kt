package com.adarsh.hellomom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adarsh.hellomom.data.local.entity.SymptomLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptom_logs WHERE userId = :userId ORDER BY date DESC")
    fun getSymptomLogs(userId: String): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_logs WHERE userId = :userId ORDER BY date DESC")
    suspend fun getSymptomLogsOnce(userId: String): List<SymptomLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptomLog(log: SymptomLogEntity)

    // Reconciliation after a pull (family devices only — symptoms have no sync flag, so on the
    // owner's device a not-yet-mirrored local log must never be removed).
    @Query("DELETE FROM symptom_logs WHERE userId = :userId AND logId NOT IN (:keepIds)")
    suspend fun deleteSymptomLogsNotIn(userId: String, keepIds: List<String>)
}
