package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

@Entity(tableName = "appointments")
data class AppointmentEntity(
    // All fields default so Firestore's toObject()/toObjects() can deserialize (it requires a
    // no-argument constructor, which Kotlin only generates when every parameter has a default).
    @PrimaryKey
    val appointmentId: String = "",
    val userId: String = "",
    val doctorName: String = "",
    val hospitalName: String = "",
    val appointmentTime: Long = 0,
    val notes: String? = null,
    val location: String? = null, // Address or coordinates

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
