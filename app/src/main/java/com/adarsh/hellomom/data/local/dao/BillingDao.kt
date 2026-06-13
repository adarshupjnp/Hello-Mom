package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.BillingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillingDao {
    @Query("SELECT * FROM billing WHERE userId = :userId")
    fun getBills(userId: String): Flow<List<BillingEntity>>

    @Query("SELECT * FROM billing WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncBills(): List<BillingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillingEntity)

    @Delete
    suspend fun deleteBill(bill: BillingEntity)

    // Reconciliation after a pull: drop rows deleted remotely; keep unpushed PENDING edits.
    @Query("DELETE FROM billing WHERE userId = :userId AND syncStatus != 'PENDING' AND billId NOT IN (:keepIds)")
    suspend fun deleteBillsNotIn(userId: String, keepIds: List<String>)
}
