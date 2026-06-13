package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.ContractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractionDao {
    @Query("SELECT * FROM contractions WHERE userId = :userId ORDER BY startTime DESC")
    fun getContractions(userId: String): Flow<List<ContractionEntity>>

    @Query("SELECT * FROM contractions WHERE userId = :userId ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestContraction(userId: String): ContractionEntity?

    @Query("SELECT * FROM contractions WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncContractions(): List<ContractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContraction(contraction: ContractionEntity)

    @Delete
    suspend fun deleteContraction(contraction: ContractionEntity)

    @Query("DELETE FROM contractions WHERE userId = :userId")
    suspend fun clearContractions(userId: String)

    // Reconciliation after a pull: drop rows deleted remotely; keep unpushed PENDING edits.
    @Query("DELETE FROM contractions WHERE userId = :userId AND syncStatus != 'PENDING' AND contractionId NOT IN (:keepIds)")
    suspend fun deleteContractionsNotIn(userId: String, keepIds: List<String>)
}
