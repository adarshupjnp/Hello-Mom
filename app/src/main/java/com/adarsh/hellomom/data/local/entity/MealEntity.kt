package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "meals")
data class MealEntity(
    // All fields default so Firestore deserialization (toObjects) works — see AppointmentEntity.
    @PrimaryKey
    val mealId: String = "",
    val userId: String = "",
    val mealType: String = "", // Breakfast, Lunch, Dinner, Snack
    val foodItems: String = "",
    val timing: String = "",
    val daysOfWeek: String = "", // comma-separated weekdays the meal is planned for, e.g. "Mon,Wed,Fri"
    // Pin the Firestore field name so the "meal taken" flag round-trips to family devices
    // (see DailyScheduleStatusEntity for why Kotlin's is-prefixed booleans need this).
    @get:PropertyName("isTaken") @set:PropertyName("isTaken")
    var isTaken: Boolean = false,
    val waterIntake: Int = 0,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false
)
