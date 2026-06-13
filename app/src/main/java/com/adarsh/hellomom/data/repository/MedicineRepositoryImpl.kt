package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.MedicineDao
import com.adarsh.hellomom.data.local.entity.MedicineEntity
import com.adarsh.hellomom.domain.repository.MedicineRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MedicineRepositoryImpl @Inject constructor(
    private val medicineDao: MedicineDao,
    private val firestore: FirebaseFirestore
) : MedicineRepository {

    override fun getMedicines(userId: String): Flow<List<MedicineEntity>> {
        return medicineDao.getMedicines(userId)
    }

    override suspend fun insertMedicine(medicine: MedicineEntity): Result<Unit> {
        return try {
            // Offline-first: persist locally first so the dashboard updates instantly.
            medicineDao.insertMedicine(medicine)
            SyncLogger.local("ADD medicine", "medicines", "id=${medicine.medicineId} userId=${medicine.userId} name=${medicine.name} dosage=${medicine.dosage}")
            // Best-effort remote push; Firestore's offline persistence queues it when offline and
            // the periodic SyncWorker / pushPendingData flips the record to SYNCED once it lands.
            firestore.collection("users").document(medicine.userId)
                .collection("medicines").document(medicine.medicineId).set(medicine)
            SyncLogger.firebaseWrite("ADD medicine", "users/${medicine.userId}/medicines/${medicine.medicineId}", "name=${medicine.name} dosage=${medicine.dosage}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD medicine failed id=${medicine.medicineId}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMedicine(medicine: MedicineEntity): Result<Unit> {
        return try {
            medicineDao.updateMedicine(medicine)
            SyncLogger.local("EDIT medicine", "medicines", "id=${medicine.medicineId} userId=${medicine.userId} name=${medicine.name} dosage=${medicine.dosage}")
            firestore.collection("users").document(medicine.userId)
                .collection("medicines").document(medicine.medicineId).set(medicine)
            SyncLogger.firebaseWrite("EDIT medicine", "users/${medicine.userId}/medicines/${medicine.medicineId}", "name=${medicine.name} dosage=${medicine.dosage}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT medicine failed id=${medicine.medicineId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteMedicine(medicineId: String): Result<Unit> {
        return try {
            // Capture the userId before deleting so we can remove the matching Firestore doc.
            val ownerId = medicineDao.getMedicineById(medicineId)?.userId
            medicineDao.deleteMedicine(medicineId)
            SyncLogger.local("DELETE medicine", "medicines", "id=$medicineId userId=$ownerId")
            if (ownerId != null) {
                firestore.collection("users").document(ownerId)
                    .collection("medicines").document(medicineId).delete()
                SyncLogger.firebaseWrite("DELETE medicine", "users/$ownerId/medicines/$medicineId", "removed")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE medicine failed id=$medicineId", e)
            Result.failure(e)
        }
    }

    override suspend fun syncPendingMedicines(): Result<Unit> {
        // Implement sync logic using WorkManager eventually
        return Result.success(Unit)
    }
}
