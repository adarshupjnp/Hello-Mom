package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.MedicineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE userId = :userId AND isDeleted = 0")
    fun getMedicines(userId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE medicineId = :medicineId LIMIT 1")
    suspend fun getMedicineById(medicineId: String): MedicineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity)

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Query("UPDATE medicines SET isDeleted = 1 WHERE medicineId = :medicineId")
    suspend fun deleteMedicine(medicineId: String)
    
    @Query("SELECT * FROM medicines WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncMedicines(): List<MedicineEntity>

    // Reconciliation after a pull: drop rows deleted remotely; keep unpushed PENDING edits.
    @Query("DELETE FROM medicines WHERE userId = :userId AND syncStatus != 'PENDING' AND medicineId NOT IN (:keepIds)")
    suspend fun deleteMedicinesNotIn(userId: String, keepIds: List<String>)
}
