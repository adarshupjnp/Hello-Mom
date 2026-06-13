package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.BillingDao
import com.adarsh.hellomom.data.local.entity.BillingEntity
import com.adarsh.hellomom.domain.repository.BillingRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BillingRepositoryImpl @Inject constructor(
    private val billingDao: BillingDao,
    private val firestore: FirebaseFirestore
) : BillingRepository {

    override fun getBills(userId: String): Flow<List<BillingEntity>> {
        return billingDao.getBills(userId)
    }

    override suspend fun insertBill(bill: BillingEntity): Result<Unit> {
        return try {
            // Offline-first: persist locally first so the screen updates instantly.
            billingDao.insertBill(bill)
            SyncLogger.local("ADD bill", "billing", "id=${bill.billId} userId=${bill.userId} title=${bill.title} amount=${bill.amount}")
            // Best-effort remote push; Firestore's offline persistence queues it when offline and
            // pushPendingData flips the record to SYNCED once it lands.
            firestore.collection("users").document(bill.userId)
                .collection("bills").document(bill.billId).set(bill)
            SyncLogger.firebaseWrite("ADD bill", "users/${bill.userId}/bills/${bill.billId}", "title=${bill.title} amount=${bill.amount}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD bill failed id=${bill.billId}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateBill(bill: BillingEntity): Result<Unit> {
        return try {
            // billingDao.insertBill uses REPLACE, so it doubles as an upsert keyed on billId.
            billingDao.insertBill(bill)
            SyncLogger.local("EDIT bill", "billing", "id=${bill.billId} userId=${bill.userId} title=${bill.title} amount=${bill.amount}")
            firestore.collection("users").document(bill.userId)
                .collection("bills").document(bill.billId).set(bill)
            SyncLogger.firebaseWrite("EDIT bill", "users/${bill.userId}/bills/${bill.billId}", "title=${bill.title} amount=${bill.amount}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT bill failed id=${bill.billId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteBill(bill: BillingEntity): Result<Unit> {
        return try {
            billingDao.deleteBill(bill)
            SyncLogger.local("DELETE bill", "billing", "id=${bill.billId} userId=${bill.userId}")
            firestore.collection("users").document(bill.userId)
                .collection("bills").document(bill.billId).delete()
            SyncLogger.firebaseWrite("DELETE bill", "users/${bill.userId}/bills/${bill.billId}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE bill failed id=${bill.billId}", e)
            Result.failure(e)
        }
    }
}
