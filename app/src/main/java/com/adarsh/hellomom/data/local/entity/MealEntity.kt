package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

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
    val isTaken: Boolean = false,
    val waterIntake: Int = 0,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)
