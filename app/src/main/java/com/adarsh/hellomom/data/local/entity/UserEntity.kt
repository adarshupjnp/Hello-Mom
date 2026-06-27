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
    /** The role of this user if they are a family member (e.g. Husband, Brother). */
    val familyRole: String? = null,
    /** Daily routine wake-up time for Today's Schedule, e.g. "07:00 AM" (owner-configurable). */
    val wakeUpTime: String? = null,
    /** Daily routine sleep time for Today's Schedule, e.g. "10:00 PM" (owner-configurable). */
    val sleepTime: String? = null,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)
