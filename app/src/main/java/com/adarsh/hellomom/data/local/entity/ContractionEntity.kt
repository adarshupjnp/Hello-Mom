package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

@Entity(tableName = "contractions")
data class ContractionEntity(
    // All fields default so Firestore deserialization (toObjects) works — see BillingEntity.
    @PrimaryKey
    val contractionId: String = "",
    val userId: String = "",
    val startTime: Long = 0L,
    val durationMillis: Long = 0L,
    val intervalMillis: Long = 0L,
    val timestamp: Long = 0L,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis()
)
