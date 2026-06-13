package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.ReminderDao
import com.adarsh.hellomom.data.local.entity.ReminderEntity
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val firestore: FirebaseFirestore
) : ReminderRepository {

    override fun getAllReminders(): Flow<List<ReminderEntity>> = reminderDao.getAllReminders()

    override suspend fun getReminderById(id: Int): ReminderEntity? = reminderDao.getReminderById(id)

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        val id = reminderDao.insertReminder(reminder)
        val updatedReminder = reminder.copy(id = id.toInt(), synced = true)
        SyncLogger.local("ADD reminder", "reminders", "id=$id userId=${reminder.userId} title=${reminder.title} time=${reminder.time}")
        syncToFirebase(updatedReminder)
        return id
    }

    override suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder.copy(synced = true))
        SyncLogger.local("EDIT reminder", "reminders", "id=${reminder.id} userId=${reminder.userId} title=${reminder.title} status=${reminder.status}")
        syncToFirebase(reminder.copy(synced = true))
    }

    override suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
        SyncLogger.local("DELETE reminder", "reminders", "id=${reminder.id} userId=${reminder.userId}")
        firestore.collection("reminders").document(reminder.id.toString()).delete().await()
        SyncLogger.firebaseWrite("DELETE reminder", "reminders/${reminder.id}", "removed")
    }

    override suspend fun syncReminders(): Result<Unit> {
        return try {
            val unsynced = reminderDao.getUnsyncedReminders()
            unsynced.forEach { reminder ->
                syncToFirebase(reminder)
                reminderDao.updateReminder(reminder.copy(synced = true))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncToFirebase(reminder: ReminderEntity) {
        try {
            firestore.collection("reminders").document(reminder.id.toString()).set(reminder).await()
            SyncLogger.firebaseWrite("PUSH reminder", "reminders/${reminder.id}", "userId=${reminder.userId} title=${reminder.title} time=${reminder.time}")
        } catch (e: Exception) {
            // Silently fail if offline, Room will track synced status
            SyncLogger.warn("PUSH reminder failed (offline?) id=${reminder.id}", e)
            reminderDao.updateReminder(reminder.copy(synced = false))
        }
    }

    override suspend fun getRemindersByStatus(status: ReminderStatus): Flow<List<ReminderEntity>> = 
        reminderDao.getRemindersByStatus(status)

    override suspend fun markAsDone(id: Int) {
        val reminder = reminderDao.getReminderById(id)
        reminder?.let {
            val updated = it.copy(
                status = ReminderStatus.COMPLETED,
                completedTime = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                synced = true
            )
            reminderDao.updateReminder(updated)
            syncToFirebase(updated)
        }
    }

    override suspend fun snooze(id: Int, customTimeMillis: Long?) {
        val reminder = reminderDao.getReminderById(id)
        reminder?.let {
            val snoozeTime = customTimeMillis ?: (System.currentTimeMillis() + 600000) // 10 mins
            val updated = it.copy(
                status = ReminderStatus.SNOOZED,
                time = snoozeTime,
                snoozeCount = it.snoozeCount + 1,
                updatedAt = System.currentTimeMillis(),
                synced = true
            )
            reminderDao.updateReminder(updated)
            syncToFirebase(updated)
        }
    }

    override suspend fun markAsMissed(id: Int) {
        val reminder = reminderDao.getReminderById(id)
        reminder?.let {
            val updated = it.copy(
                status = ReminderStatus.EXPIRED,
                updatedAt = System.currentTimeMillis(),
                synced = true
            )
            reminderDao.updateReminder(updated)
            syncToFirebase(updated)
        }
    }

    override suspend fun getExistingReminder(userId: String, title: String, date: String, time: Long): ReminderEntity? {
        return reminderDao.getExistingReminder(userId, title, date, time)
    }

    override suspend fun deleteOldReminders(expiryTime: Long) {
        reminderDao.deleteOldReminders(expiryTime)
        // Note: Full sync cleanup for Firebase could be added here if needed
    }

    override suspend fun getAutoGeneratedRemindersForDate(date: String): List<ReminderEntity> {
        return reminderDao.getAutoGeneratedRemindersForDate(date)
    }
}
