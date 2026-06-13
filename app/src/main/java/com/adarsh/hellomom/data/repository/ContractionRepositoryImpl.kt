package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.ContractionDao
import com.adarsh.hellomom.data.local.entity.ContractionEntity
import com.adarsh.hellomom.domain.repository.ContractionRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ContractionRepositoryImpl @Inject constructor(
    private val contractionDao: ContractionDao,
    private val firestore: FirebaseFirestore
) : ContractionRepository {

    override fun getContractions(userId: String): Flow<List<ContractionEntity>> {
        return contractionDao.getContractions(userId)
    }

    override suspend fun getLatestContraction(userId: String): ContractionEntity? {
        return contractionDao.getLatestContraction(userId)
    }

    override suspend fun insertContraction(contraction: ContractionEntity): Result<Unit> {
        return try {
            // Offline-first: persist locally first so the screen updates instantly.
            contractionDao.insertContraction(contraction)
            SyncLogger.local("ADD contraction", "contractions", "id=${contraction.contractionId} userId=${contraction.userId}")
            // Best-effort remote push; pushPendingData flips the record to SYNCED once it lands.
            firestore.collection("users").document(contraction.userId)
                .collection("contractions").document(contraction.contractionId).set(contraction)
            SyncLogger.firebaseWrite("ADD contraction", "users/${contraction.userId}/contractions/${contraction.contractionId}", "duration=${contraction.durationMillis}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD contraction failed id=${contraction.contractionId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteContraction(contraction: ContractionEntity): Result<Unit> {
        return try {
            contractionDao.deleteContraction(contraction)
            SyncLogger.local("DELETE contraction", "contractions", "id=${contraction.contractionId} userId=${contraction.userId}")
            firestore.collection("users").document(contraction.userId)
                .collection("contractions").document(contraction.contractionId).delete()
            SyncLogger.firebaseWrite("DELETE contraction", "users/${contraction.userId}/contractions/${contraction.contractionId}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE contraction failed id=${contraction.contractionId}", e)
            Result.failure(e)
        }
    }
}
