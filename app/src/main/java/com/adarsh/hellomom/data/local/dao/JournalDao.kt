package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.JournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE userId = :userId ORDER BY date DESC")
    fun getJournalEntries(userId: String): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal_entries WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncEntries(): List<JournalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntity)

    @Delete
    suspend fun deleteEntry(entry: JournalEntity)

    // Reconciliation after a pull: drop rows deleted remotely; keep unpushed PENDING edits.
    @Query("DELETE FROM journal_entries WHERE userId = :userId AND syncStatus != 'PENDING' AND entryId NOT IN (:keepIds)")
    suspend fun deleteEntriesNotIn(userId: String, keepIds: List<String>)
}
