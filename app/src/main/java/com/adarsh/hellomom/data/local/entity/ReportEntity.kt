package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

@Entity(tableName = "reports")
data class ReportEntity(
    // All fields default so Firestore deserialization (toObjects) works — see AppointmentEntity.
    @PrimaryKey
    val reportId: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "", // Sonography, Blood Test, etc.
    val fileUrl: String = "", // Firebase Storage URL
    val localPath: String? = null,
    val date: Long = 0,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis()
)
