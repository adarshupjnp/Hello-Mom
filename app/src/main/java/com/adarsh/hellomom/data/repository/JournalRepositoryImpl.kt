package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.JournalDao
import com.adarsh.hellomom.data.local.entity.JournalEntity
import com.adarsh.hellomom.domain.repository.JournalRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val journalDao: JournalDao,
    private val firestore: FirebaseFirestore
) : JournalRepository {

    override fun getEntries(userId: String): Flow<List<JournalEntity>> {
        return journalDao.getJournalEntries(userId)
    }

    override suspend fun insertEntry(entry: JournalEntity): Result<Unit> {
        return try {
            // Offline-first: persist locally first so the screen updates instantly.
            journalDao.insertEntry(entry)
            SyncLogger.local("ADD journal", "journal_entries", "id=${entry.entryId} userId=${entry.userId}")
            firestore.collection("users").document(entry.userId)
                .collection("journal").document(entry.entryId).set(entry)
            SyncLogger.firebaseWrite("ADD journal", "users/${entry.userId}/journal/${entry.entryId}", "date=${entry.date}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD journal failed id=${entry.entryId}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateEntry(entry: JournalEntity): Result<Unit> {
        return try {
            // insertEntry uses REPLACE, so it doubles as an upsert keyed on entryId.
            journalDao.insertEntry(entry)
            SyncLogger.local("EDIT journal", "journal_entries", "id=${entry.entryId} userId=${entry.userId}")
            firestore.collection("users").document(entry.userId)
                .collection("journal").document(entry.entryId).set(entry)
            SyncLogger.firebaseWrite("EDIT journal", "users/${entry.userId}/journal/${entry.entryId}", "date=${entry.date}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT journal failed id=${entry.entryId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteEntry(entry: JournalEntity): Result<Unit> {
        return try {
            journalDao.deleteEntry(entry)
            SyncLogger.local("DELETE journal", "journal_entries", "id=${entry.entryId} userId=${entry.userId}")
            firestore.collection("users").document(entry.userId)
                .collection("journal").document(entry.entryId).delete()
            SyncLogger.firebaseWrite("DELETE journal", "users/${entry.userId}/journal/${entry.entryId}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE journal failed id=${entry.entryId}", e)
            Result.failure(e)
        }
    }
}
