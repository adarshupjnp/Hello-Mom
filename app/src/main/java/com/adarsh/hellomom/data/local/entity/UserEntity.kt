package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String = "",
    val fullName: String = "",
    val dob: Long = 0,
    val mobileNumber: String = "",
    val email: String = "",
    val profilePicture: String? = null,
    val pregnancyStartDate: Long? = null,
    val dueDate: Long? = null,
    val bloodGroup: String? = null,
    val emergencyContact: String? = null,
    val doctorName: String? = null,
    val hospitalName: String? = null,
    val weight: Float? = null,
    val height: Float? = null,
    val allergies: String? = null,
    val linkedPrimaryUserId: String? = null,
    
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)
