package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

/**
 * One "done / pending" mark for a single Today's Schedule row on a specific day.
 *
 * The schedule rows themselves (medicines, meals, wake/sleep routine) are derived from existing
 * data — this entity stores ONLY the daily completion state, keyed by date so it resets naturally:
 * a new day simply has no rows yet, so everything starts as pending. Owner writes it; family
 * members read it (Firestore rules already restrict writes to the owner's own tree).
 *
 * All fields default so Firestore deserialization (toObjects → no-arg constructor) works.
 */
@Entity(tableName = "daily_schedule_status")
data class DailyScheduleStatusEntity(
    /** Deterministic id: "{date}_{type}_{refId}" so the same row on the same day upserts in place. */
    @PrimaryKey
    val id: String = "",
    /** The OWNER's userId whose schedule this belongs to (family read the same id). */
    val userId: String = "",
    /** Day bucket, yyyy-MM-dd — the daily-reset key. */
    val date: String = "",
    /** ScheduleItemType name: MEDICINE | MEAL | ROUTINE. */
    val type: String = "",
    /** Source id: medicineId / mealId / routine key ("wakeup" | "sleep"). */
    val refId: String = "",
    val isDone: Boolean = false,
    val doneAt: Long? = null,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)
