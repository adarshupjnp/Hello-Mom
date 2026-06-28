package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.constants.ReminderConstants
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.ReminderDao
import com.adarsh.hellomom.data.local.entity.ReminderEntity
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.data.local.entity.ReminderType
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.adarsh.hellomom.notification.ReminderManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val firestore: FirebaseFirestore,
    private val reminderManager: ReminderManager
) : ReminderRepository {

    // Serialises daily generation so two concurrent callers (e.g. a background sync and the
    // reminder screen opening) can't both pass the "missing for today?" check and double-insert.
    // ReminderRepository is a @Singleton, so this lock is process-wide.
    private val dailyGenMutex = Mutex()

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

    override suspend fun deleteOldReminders(expiryTime: Long, deleteRemote: Boolean) {
        // Owner-driven cleanup also removes the rows from Firestore so it doesn't grow unbounded;
        // family devices then drop the same rows on their next pull-reconcile. Done before the
        // local delete so we still have the ids/docs to remove remotely.
        if (deleteRemote) {
            val old = reminderDao.getRemindersOlderThan(expiryTime)
            old.forEach { reminder ->
                runCatching {
                    firestore.collection("reminders").document(reminder.id.toString()).delete().await()
                    SyncLogger.firebaseWrite("CLEANUP reminder", "reminders/${reminder.id}", "older than retention")
                }.onFailure { SyncLogger.warn("CLEANUP reminder delete failed id=${reminder.id} (offline?)", it) }
            }
        }
        reminderDao.deleteOldReminders(expiryTime)
    }

    override suspend fun getAutoGeneratedRemindersForDate(date: String): List<ReminderEntity> {
        return reminderDao.getAutoGeneratedRemindersForDate(date)
    }

    override suspend fun ensureDailyReminders(userId: String, userName: String): List<ReminderEntity> {
        if (userId.isEmpty()) return emptyList()
        return dailyGenMutex.withLock {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // 1. Cleanup: If old "Sleep Reminder" title exists, rename it to "Sleep" or delete duplicates.
            val userReminders = reminderDao.getRemindersForUser(userId)
            userReminders.filter { it.date == today && it.title == "Sleep Reminder" }.forEach { old ->
                // If "Sleep" already exists, delete the old "Sleep Reminder"
                if (userReminders.any { it.date == today && it.title == "Sleep" }) {
                    deleteReminder(old)
                } else {
                    // Otherwise just rename it
                    updateReminder(old.copy(title = "Sleep"))
                }
            }

            // 2. Idempotency check with the new title "Sleep"
            // Keyed on reminderType == PREDEFINED (a reliable enum) to avoid duplicates.
            val existingTitles = reminderDao.getRemindersForUser(userId)
                .filter { it.date == today && it.reminderType == ReminderType.PREDEFINED }
                .map { it.title }
                .toSet()

            val now = System.currentTimeMillis()
            val created = mutableListOf<ReminderEntity>()

            ReminderConstants.DAILY_AUTO_REMINDERS.forEachIndexed { index, predefined ->
                if (predefined.title in existingTitles) return@forEachIndexed

                // Anchor at today's fixed hour. Crucially we DO NOT bump past-due hours to tomorrow:
                // that old behaviour stamped some reminders with tomorrow's date, which then satisfied
                // the next day's "already generated?" guard and left the following day half-empty.
                val time = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, ReminderConstants.DAILY_REMINDER_HOURS.getOrElse(index) { 8 + index })
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val reminder = ReminderEntity(
                    title = predefined.title,
                    description = predefined.voiceMessage,
                    voiceMessage = predefined.voiceMessage,
                    time = time,
                    date = today,
                    userId = userId,
                    userName = userName,
                    reminderType = ReminderType.PREDEFINED,
                    isAutoGenerated = true,
                    category = categoryForTitle(predefined.title)
                )
                // insertReminder() persists to Room and pushes to Firestore (so family members pull it).
                val id = insertReminder(reminder)
                val saved = reminder.copy(id = id.toInt(), synced = true)
                // Only future reminders need an alarm; past-hour ones still appear in today's list and
                // are expired by the normal stale/day-change cleanup.
                if (time > now) runCatching { reminderManager.scheduleReminder(saved) }
                created += saved
                SyncLogger.local("DAILY generate", "reminders", "title=${saved.title} time=$time date=$today user=$userId")
            }

            if (created.isNotEmpty()) {
                SyncLogger.info("ensureDailyReminders created ${created.size} reminder(s) for $today (user=$userId)")
            }
            created
        }
    }

    private fun categoryForTitle(title: String): String = when {
        title.contains("Medicine", true) -> "Medicine"
        title.contains("Meal", true) || title.contains("Dinner", true) -> "Meal"
        title.contains("Water", true) -> "Water"
        else -> "General"
    }

    override suspend fun purgeDuplicateReminders(userId: String, deleteRemote: Boolean): Int {
        if (userId.isEmpty()) return 0
        return dailyGenMutex.withLock {
            // Group duplicates: daily auto-reminders by title+date (so a duplicate collapses even
            // after one copy was snoozed to a different time); custom reminders by id (never merged).
            val groups = reminderDao.getRemindersForUser(userId)
                .groupBy { r ->
                    if (r.reminderType == ReminderType.PREDEFINED) "${r.title}|${r.date}"
                    else "id|${r.id}"
                }
            var removed = 0
            groups.forEach { (_, copies) ->
                if (copies.size <= 1) return@forEach
                // Keep the freshest copy (latest status wins, matching the on-screen dedup).
                val keep = copies.maxByOrNull { it.updatedAt }!!
                copies.asSequence().filter { it.id != keep.id }.forEach { dup ->
                    runCatching { reminderManager.cancelReminder(dup.id) }
                    runCatching { reminderManager.cancelSnoozeCheck(dup.id) }
                    reminderDao.deleteReminder(dup)
                    if (deleteRemote) {
                        runCatching {
                            firestore.collection("reminders").document(dup.id.toString()).delete().await()
                        }.onFailure { SyncLogger.warn("DEDUP remote delete failed id=${dup.id} (offline?)", it) }
                    }
                    removed++
                }
            }
            if (removed > 0) {
                SyncLogger.local("DEDUP reminders", "reminders", "removed $removed duplicate row(s) for user=$userId")
            }
            removed
        }
    }

    override suspend fun expireStalePendingBefore(startOfDayMillis: Long): List<Int> {
        val stale = reminderDao.getStalePendingBefore(startOfDayMillis)
        if (stale.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        stale.forEach { reminder ->
            // updateReminder() persists to Room and pushes to Firestore so linked family members
            // see the same EXPIRED status; it also marks the row synced.
            updateReminder(reminder.copy(status = ReminderStatus.EXPIRED, updatedAt = now))
        }
        SyncLogger.local("DAY-CHANGE expire", "reminders", "expired ${stale.size} stale pending reminder(s)")
        return stale.map { it.id }
    }
}
