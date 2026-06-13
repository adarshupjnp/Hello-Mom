package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.MedicineEntity
import kotlinx.coroutines.flow.Flow

interface MedicineRepository {
    fun getMedicines(userId: String): Flow<List<MedicineEntity>>
    suspend fun insertMedicine(medicine: MedicineEntity): Result<Unit>
    suspend fun updateMedicine(medicine: MedicineEntity): Result<Unit>
    suspend fun deleteMedicine(medicineId: String): Result<Unit>
    suspend fun syncPendingMedicines(): Result<Unit>
}
