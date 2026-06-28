package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    // All fields default so Firestore deserialization (toObjects) works — see AppointmentEntity.
    @PrimaryKey
    val memberId: String = "",
    val userId: String = "", // ID of the primary user
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "", // Used for the WhatsApp quick-contact action
    val role: String = "", // Husband, Parent, etc.
    val permissions: String = "", // Comma separated list of permissions
    val status: String = "", // Pending, Accepted

    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationUpdatedAt: Long? = null,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis()
)
