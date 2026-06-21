package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "medicines")
data class MedicineEntity(
    // All fields default so Firestore deserialization (toObjects) works — see AppointmentEntity.
    @PrimaryKey
    val medicineId: String = "",
    val userId: String = "",
    val name: String = "",
    val dosage: String = "",
    val timing: String = "", // e.g. "08:00,14:00,20:00"
    val frequency: String = "", // e.g. "Daily", "Weekly"
    val daysOfWeek: String = "", // comma-separated weekdays the medicine is taken, e.g. "Mon,Wed,Fri"
    val beforeAfterMeal: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val notes: String? = null,
    // Pin the Firestore field name so the "course completed" flag round-trips to family devices
    // (Kotlin's isCompleted getter otherwise serializes as "completed" but reads back by the
    // "isCompleted" field, losing a true value — see DailyScheduleStatusEntity for the full note).
    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false
)
