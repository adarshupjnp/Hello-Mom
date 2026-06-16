package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.data.local.SyncStatus
import com.adarsh.hellomom.data.local.dao.AppointmentDao
import com.adarsh.hellomom.data.local.dao.BillingDao
import com.adarsh.hellomom.data.local.dao.ContractionDao
import com.adarsh.hellomom.data.local.dao.DailyScheduleStatusDao
import com.adarsh.hellomom.data.local.dao.FamilyMemberDao
import com.adarsh.hellomom.data.local.dao.JournalDao
import com.adarsh.hellomom.data.local.dao.MealDao
import com.adarsh.hellomom.data.local.dao.MedicineDao
import com.adarsh.hellomom.data.local.dao.ReminderDao
import com.adarsh.hellomom.data.local.dao.ReportDao
import com.adarsh.hellomom.data.local.dao.SymptomDao
import com.adarsh.hellomom.data.local.dao.UserDao
import com.adarsh.hellomom.data.local.dao.WaterIntakeDao
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import com.adarsh.hellomom.data.local.entity.BillingEntity
import com.adarsh.hellomom.data.local.entity.ContractionEntity
import com.adarsh.hellomom.data.local.entity.DailyScheduleStatusEntity
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import com.adarsh.hellomom.data.local.entity.JournalEntity
import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.MedicineEntity
import com.adarsh.hellomom.data.local.entity.ReminderEntity
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.data.local.entity.ReportEntity
import com.adarsh.hellomom.data.local.entity.SymptomLogEntity
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.domain.repository.SyncRepository
import com.adarsh.hellomom.notification.AppointmentReminderScheduler
import com.adarsh.hellomom.notification.ReminderManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SyncRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val appointmentDao: AppointmentDao,
    private val medicineDao: MedicineDao,
    private val symptomDao: SymptomDao,
    private val familyMemberDao: FamilyMemberDao,
    private val reminderDao: ReminderDao,
    private val reportDao: ReportDao,
    private val mealDao: MealDao,
    private val waterIntakeDao: WaterIntakeDao,
    private val billingDao: BillingDao,
    private val contractionDao: ContractionDao,
    private val journalDao: JournalDao,
    private val dailyScheduleStatusDao: DailyScheduleStatusDao,
    private val reminderManager: ReminderManager,
    private val appointmentReminderScheduler: AppointmentReminderScheduler
) : SyncRepository {

    // Serialises concurrent syncAll() calls (screen loads + WorkManager can overlap) and lets
    // syncIfStale() skip work when the cache is fresh or a sync is already in flight.
    private val syncMutex = Mutex()

    @Volatile
    private var lastSuccessfulSyncAt = 0L

    override suspend fun syncIfStale(maxAgeMillis: Long): Result<Unit> {
        // Family members get a much shorter freshness window so navigating any screen re-pulls the
        // owner's latest data almost immediately; owners (the writers) keep the requested window.
        val effectiveMaxAge = familyAwareStaleness(maxAgeMillis)
        val age = System.currentTimeMillis() - lastSuccessfulSyncAt
        if (age < effectiveMaxAge) {
            SyncLogger.info("syncIfStale: cache is fresh (${age}ms old, window=${effectiveMaxAge}ms), skipping")
            return Result.success(Unit)
        }
        if (syncMutex.isLocked) {
            SyncLogger.info("syncIfStale: a sync is already running, skipping")
            return Result.success(Unit)
        }
        return syncAll()
    }

    /**
     * Returns the freshness window to honour for the current user: family members use the shorter
     * [SyncRepository.FAMILY_SYNC_STALENESS_MS] so they pull the owner's latest data on almost
     * every navigation; owners keep the requested (default 60s) window. Falls back to the requested
     * window on any error so a lookup failure never blocks a sync.
     */
    private suspend fun familyAwareStaleness(requested: Long): Long = runCatching {
        val uid = firebaseAuth.currentUser?.uid ?: return@runCatching requested
        val self = userDao.getUserByIdOnce(uid) ?: return@runCatching requested
        val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(self.fullName, self.email)
        if (isOwner) requested else minOf(requested, SyncRepository.FAMILY_SYNC_STALENESS_MS)
    }.getOrDefault(requested)

    override suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        try {
            val uid = firebaseAuth.currentUser?.uid
            if (uid.isNullOrEmpty()) {
                SyncLogger.warn("syncAll: no authenticated user, skipping")
                return@withContext Result.success(Unit)
            }
            SyncLogger.info("syncAll START uid=$uid")

            // 1. Refresh the current user's own profile (remote wins, but keep local-only fields
            //    like the locally-stored profile picture URI).
            val remoteSelf = runCatching {
                firestore.collection("users").document(uid).get().await()
                    .toObject(UserEntity::class.java)
            }.getOrNull()
            val localSelf = userDao.getUserByIdOnce(uid)
            val self = mergeUser(remoteSelf, localSelf) ?: localSelf
            ?: return@withContext Result.success(Unit)
            userDao.insertUser(self)

            val isOwner = com.adarsh.hellomom.core.RoleManager.isOwnerUser(self.fullName, self.email)
            SyncLogger.info("syncAll user='${self.fullName}' isOwner=$isOwner startDate=${self.pregnancyStartDate} storedLink=${self.linkedPrimaryUserId}")

            if (isOwner) {
                // Owner: push anything that hasn't reached Firestore yet so family sees it,
                // then pull back (covers edits made on another device).
                pushPendingData(uid)
                pullOwnerDataInternal(uid, isOwnerDevice = true)
            } else {
                // Family member: figure out which owner to track and pull their data.
                // Validate the stored link the same way RoleManager does: it must point at a real
                // owner doc with a pregnancyStartDate, otherwise re-discover (heals stale links).
                var ownerId = self.linkedPrimaryUserId
                val linkValid = !ownerId.isNullOrEmpty() && isValidOwner(ownerId)
                if (!linkValid) {
                    ownerId = findOwnerId()
                    if (!ownerId.isNullOrEmpty()) {
                        SyncLogger.resolve("syncAll FAMILY re-linked to ownerId=$ownerId")
                        // Persist the discovered link locally and remotely for next time.
                        userDao.insertUser(self.copy(linkedPrimaryUserId = ownerId))
                        runCatching {
                            firestore.collection("users").document(uid)
                                .update("linkedPrimaryUserId", ownerId).await()
                        }
                    }
                }
                if (!ownerId.isNullOrEmpty()) {
                    SyncLogger.info("syncAll FAMILY pulling owner data ownerId=$ownerId")
                    pullOwnerDataInternal(ownerId, isOwnerDevice = false)
                } else {
                    SyncLogger.warn("syncAll FAMILY: no owner resolved, nothing to pull")
                }
            }
            SyncLogger.info("syncAll DONE uid=$uid")
            lastSuccessfulSyncAt = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("syncAll failed", e)
            Result.failure(e)
        }
        }
    }

    /** True if the given userId is a real owner doc in Firestore that has a pregnancyStartDate. */
    private suspend fun isValidOwner(userId: String): Boolean = runCatching {
        val doc = firestore.collection("users").document(userId).get().await()
            .toObject(UserEntity::class.java)
        doc != null && com.adarsh.hellomom.core.RoleManager.isOwnerUser(doc.fullName, doc.email) && doc.pregnancyStartDate != null
    }.getOrDefault(false)

    override suspend fun pullOwnerData(ownerUserId: String): Result<Unit> {
        val isOwnerDevice = firebaseAuth.currentUser?.uid == ownerUserId
        return pullOwnerDataInternal(ownerUserId, isOwnerDevice)
    }

    /**
     * Pull every shared collection from Firestore into Room (remote wins) and reconcile
     * deletions: rows that vanished remotely are removed locally so family members don't keep
     * seeing "ghost" records the owner already deleted. Unpushed local PENDING rows survive.
     *
     * [isOwnerDevice] guards the symptom reconciliation — symptoms carry no sync flag, so on the
     * owner's device a freshly-logged (not yet mirrored) symptom must never be deleted.
     */
    private suspend fun pullOwnerDataInternal(
        ownerUserId: String,
        isOwnerDevice: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            SyncLogger.info("pullOwnerData START ownerId=$ownerUserId")

            // Owner profile (pregnancyStartDate drives the dashboard week).
            runCatching {
                firestore.collection("users").document(ownerUserId).get().await()
                    .toObject(UserEntity::class.java)
            }.getOrNull()?.let { owner ->
                SyncLogger.firebaseRead("PULL profile", "users/$ownerUserId", "name=${owner.fullName} startDate=${owner.pregnancyStartDate} dueDate=${owner.dueDate}")
                val existing = userDao.getUserByIdOnce(ownerUserId)
                val merged = mergeUser(owner, existing) ?: owner
                userDao.insertUser(merged)
                SyncLogger.local("PULL profile", "users", "id=$ownerUserId startDate=${merged.pregnancyStartDate}")
            } ?: SyncLogger.warn("pullOwnerData: owner profile users/$ownerUserId not found / not readable")

            // Appointments
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("appointments").get().await()
                    .toObjects(AppointmentEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL appointments", "users/$ownerUserId/appointments", "count=${list.size}")
                list.forEach { appt ->
                    appointmentDao.insertAppointment(appt.copy(syncStatus = SyncStatus.SYNCED))
                    // Both owner and family devices get the 1-day-before and 1-hour-before
                    // notifications; scheduling is idempotent so repeated pulls are safe.
                    appointmentReminderScheduler.schedule(appt)
                }
                // Remove rows deleted on another device (and their pending alarms).
                val keepIds = list.map { it.appointmentId }
                appointmentDao.getStaleAppointmentIds(ownerUserId, keepIds).forEach { staleId ->
                    appointmentReminderScheduler.cancel(staleId)
                    SyncLogger.local("RECONCILE appointment removed", "appointments", "id=$staleId")
                }
                appointmentDao.deleteAppointmentsNotIn(ownerUserId, keepIds)
            }

            // Medicines
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("medicines").get().await()
                    .toObjects(MedicineEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL medicines", "users/$ownerUserId/medicines", "count=${list.size}")
                list.forEach { medicineDao.insertMedicine(it.copy(syncStatus = SyncStatus.SYNCED)) }
                medicineDao.deleteMedicinesNotIn(ownerUserId, list.map { it.medicineId })
            }

            // Symptoms
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("symptoms").get().await()
                    .toObjects(SymptomLogEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL symptoms", "users/$ownerUserId/symptoms", "count=${list.size}")
                list.forEach { symptomDao.insertSymptomLog(it) }
                // Family devices are read-only for symptoms, so mirroring deletions is safe there.
                // On the owner's device a local not-yet-mirrored log must survive (no sync flag).
                if (!isOwnerDevice) {
                    symptomDao.deleteSymptomLogsNotIn(ownerUserId, list.map { it.logId })
                }
            }

            // Reports (metadata only — the file itself stays on the owner's device).
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("reports").get().await()
                    .toObjects(ReportEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL reports", "users/$ownerUserId/reports", "count=${list.size}")
                list.forEach { reportDao.insertReport(it.copy(syncStatus = SyncStatus.SYNCED)) }
                reportDao.deleteReportsNotIn(ownerUserId, list.map { it.reportId })
            }

            // Meals
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("meals").get().await()
                    .toObjects(MealEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL meals", "users/$ownerUserId/meals", "count=${list.size}")
                list.forEach { mealDao.insertMeal(it.copy(syncStatus = SyncStatus.SYNCED)) }
                mealDao.deleteMealsNotIn(ownerUserId, list.map { it.mealId })
            }

            // Water intake (per-day documents).
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("water_intake").get().await()
                    .toObjects(WaterIntakeEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL water_intake", "users/$ownerUserId/water_intake", "count=${list.size}")
                list.forEach { waterIntakeDao.insertOrUpdate(it) }
            }

            // Bills / expenses
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("bills").get().await()
                    .toObjects(BillingEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL bills", "users/$ownerUserId/bills", "count=${list.size}")
                list.forEach { billingDao.insertBill(it.copy(syncStatus = SyncStatus.SYNCED)) }
                billingDao.deleteBillsNotIn(ownerUserId, list.map { it.billId })
            }

            // Family members
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("family_members").get().await()
                    .toObjects(FamilyMemberEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL family_members", "users/$ownerUserId/family_members", "count=${list.size}")
                list.forEach { familyMemberDao.insertFamilyMember(it) }
                familyMemberDao.deleteFamilyMembersNotIn(ownerUserId, list.map { it.memberId })
            }

            // Reminders (stored in a top-level collection keyed by userId).
            runCatching {
                firestore.collection("reminders")
                    .whereEqualTo("userId", ownerUserId).get().await()
                    .toObjects(ReminderEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL reminders", "reminders[userId=$ownerUserId]", "count=${list.size}")
                val now = System.currentTimeMillis()
                list.forEach { reminder ->
                    reminderDao.insertReminder(reminder.copy(synced = true))
                    // Schedule a local alarm so family members get the SAME notification the owner
                    // gets. Only future, still-active reminders are scheduled; re-scheduling is
                    // idempotent (PendingIntent keyed by reminder.id), so this is safe for the owner too.
                    if ((reminder.status == ReminderStatus.PENDING || reminder.status == ReminderStatus.SNOOZED) &&
                        reminder.time > now
                    ) {
                        runCatching { reminderManager.scheduleReminder(reminder) }
                    }
                }
                // Remove reminders deleted on another device and cancel their local alarms.
                // Unsynced local reminders (synced = 0) are kept until they're pushed.
                val keepIds = list.map { it.id }
                reminderDao.getStaleReminders(ownerUserId, keepIds).forEach { stale ->
                    runCatching { reminderManager.cancelReminder(stale.id) }
                    SyncLogger.local("RECONCILE reminder removed", "reminders", "id=${stale.id} title=${stale.title}")
                }
                reminderDao.deleteRemindersNotIn(ownerUserId, keepIds)
            }

            // Contractions
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("contractions").get().await()
                    .toObjects(ContractionEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL contractions", "users/$ownerUserId/contractions", "count=${list.size}")
                list.forEach { contractionDao.insertContraction(it.copy(syncStatus = SyncStatus.SYNCED)) }
                contractionDao.deleteContractionsNotIn(ownerUserId, list.map { it.contractionId })
            }

            // Journal (shared read-only with family).
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("journal").get().await()
                    .toObjects(JournalEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL journal", "users/$ownerUserId/journal", "count=${list.size}")
                list.forEach { journalDao.insertEntry(it.copy(syncStatus = SyncStatus.SYNCED)) }
                journalDao.deleteEntriesNotIn(ownerUserId, list.map { it.entryId })
            }

            // Daily schedule status (today's done/pending marks; family read-only).
            runCatching {
                firestore.collection("users").document(ownerUserId)
                    .collection("daily_schedule_status").get().await()
                    .toObjects(DailyScheduleStatusEntity::class.java)
            }.getOrNull()?.let { list ->
                SyncLogger.firebaseRead("PULL schedule status", "users/$ownerUserId/daily_schedule_status", "count=${list.size}")
                list.forEach { dailyScheduleStatusDao.upsert(it.copy(syncStatus = SyncStatus.SYNCED)) }
                dailyScheduleStatusDao.deleteStatusesNotIn(ownerUserId, list.map { it.id })
            }

            SyncLogger.info("pullOwnerData DONE ownerId=$ownerUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("pullOwnerData failed ownerId=$ownerUserId", e)
            Result.failure(e)
        }
    }

    /** Push the owner's locally-pending data to Firestore (covers offline edits). */
    private suspend fun pushPendingData(uid: String) {
        SyncLogger.info("pushPendingData START uid=$uid")
        // Pending appointments
        runCatching {
            val pending = appointmentDao.getPendingSyncAppointments()
            if (pending.isNotEmpty()) SyncLogger.info("pushing ${pending.size} pending appointments")
            pending.forEach { appt ->
                firestore.collection("users").document(appt.userId)
                    .collection("appointments").document(appt.appointmentId).set(appt).await()
                SyncLogger.firebaseWrite("PUSH appointment", "users/${appt.userId}/appointments/${appt.appointmentId}", "doctor=${appt.doctorName}")
                appointmentDao.updateAppointment(appt.copy(syncStatus = SyncStatus.SYNCED))
            }
        }.onFailure { SyncLogger.warn("push appointments failed", it) }

        // Pending medicines
        runCatching {
            val pending = medicineDao.getPendingSyncMedicines()
            if (pending.isNotEmpty()) SyncLogger.info("pushing ${pending.size} pending medicines")
            pending.forEach { med ->
                firestore.collection("users").document(med.userId)
                    .collection("medicines").document(med.medicineId).set(med).await()
                SyncLogger.firebaseWrite("PUSH medicine", "users/${med.userId}/medicines/${med.medicineId}", "name=${med.name}")
                medicineDao.updateMedicine(
                    med.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis())
                )
            }
        }.onFailure { SyncLogger.warn("push medicines failed", it) }

        // Pending bills
        runCatching {
            val pending = billingDao.getPendingSyncBills()
            if (pending.isNotEmpty()) SyncLogger.info("pushing ${pending.size} pending bills")
            pending.forEach { bill ->
                firestore.collection("users").document(bill.userId)
                    .collection("bills").document(bill.billId).set(bill).await()
                SyncLogger.firebaseWrite("PUSH bill", "users/${bill.userId}/bills/${bill.billId}", "title=${bill.title}")
                billingDao.insertBill(bill.copy(syncStatus = SyncStatus.SYNCED))
            }
        }.onFailure { SyncLogger.warn("push bills failed", it) }

        // Unsynced reminders
        runCatching {
            val unsynced = reminderDao.getUnsyncedReminders()
            if (unsynced.isNotEmpty()) SyncLogger.info("pushing ${unsynced.size} unsynced reminders")
            unsynced.forEach { reminder ->
                firestore.collection("reminders").document(reminder.id.toString())
                    .set(reminder.copy(synced = true)).await()
                SyncLogger.firebaseWrite("PUSH reminder", "reminders/${reminder.id}", "title=${reminder.title}")
                reminderDao.updateReminder(reminder.copy(synced = true))
            }
        }.onFailure { SyncLogger.warn("push reminders failed", it) }

        // Pending contractions
        runCatching {
            val pending = contractionDao.getPendingSyncContractions()
            if (pending.isNotEmpty()) SyncLogger.info("pushing ${pending.size} pending contractions")
            pending.forEach { c ->
                firestore.collection("users").document(c.userId)
                    .collection("contractions").document(c.contractionId).set(c).await()
                SyncLogger.firebaseWrite("PUSH contraction", "users/${c.userId}/contractions/${c.contractionId}", "duration=${c.durationMillis}")
                contractionDao.insertContraction(c.copy(syncStatus = SyncStatus.SYNCED))
            }
        }.onFailure { SyncLogger.warn("push contractions failed", it) }

        // Pending journal entries
        runCatching {
            val pending = journalDao.getPendingSyncEntries()
            if (pending.isNotEmpty()) SyncLogger.info("pushing ${pending.size} pending journal entries")
            pending.forEach { e ->
                firestore.collection("users").document(e.userId)
                    .collection("journal").document(e.entryId).set(e).await()
                SyncLogger.firebaseWrite("PUSH journal", "users/${e.userId}/journal/${e.entryId}", "date=${e.date}")
                journalDao.insertEntry(e.copy(syncStatus = SyncStatus.SYNCED))
            }
        }.onFailure { SyncLogger.warn("push journal failed", it) }

        // Pending daily schedule status marks
        runCatching {
            val pending = dailyScheduleStatusDao.getPendingSync()
            if (pending.isNotEmpty()) SyncLogger.info("pushing ${pending.size} pending schedule status marks")
            pending.forEach { status ->
                firestore.collection("users").document(status.userId)
                    .collection("daily_schedule_status").document(status.id)
                    .set(status.copy(syncStatus = SyncStatus.SYNCED)).await()
                SyncLogger.firebaseWrite("PUSH schedule status", "users/${status.userId}/daily_schedule_status/${status.id}", "done=${status.isDone}")
                dailyScheduleStatusDao.upsert(
                    status.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis())
                )
            }
        }.onFailure { SyncLogger.warn("push schedule status failed", it) }

        // Symptoms (no sync flag — mirror all of the owner's local logs).
        runCatching {
            val logs = symptomDao.getSymptomLogsOnce(uid)
            if (logs.isNotEmpty()) SyncLogger.info("mirroring ${logs.size} symptom logs")
            logs.forEach { log ->
                firestore.collection("users").document(uid)
                    .collection("symptoms").document(log.logId).set(log).await()
            }
        }.onFailure { SyncLogger.warn("push symptoms failed", it) }
        SyncLogger.info("pushPendingData DONE uid=$uid")
    }

    /** Locate the pregnancy owner by the hardcoded name convention (adarsh / riya). */
    private suspend fun findOwnerId(): String? = runCatching {
        firestore.collection("users").get().await().documents.find { doc ->
            com.adarsh.hellomom.core.RoleManager.isOwnerUser(doc.getString("fullName"), doc.getString("email"))
        }?.id
    }.getOrNull()

    /**
     * Prefer the remote record, but retain a locally-stored profile picture URI when the remote
     * copy doesn't have one (pictures are kept as local URIs, see [UserRepositoryImpl]).
     */
    private fun mergeUser(remote: UserEntity?, local: UserEntity?): UserEntity? {
        if (remote == null) return local
        return if (local?.profilePicture != null && remote.profilePicture == null) {
            remote.copy(profilePicture = local.profilePicture)
        } else remote
    }
}
