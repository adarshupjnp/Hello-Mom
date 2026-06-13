package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adarsh.hellomom.data.local.SyncStatus

@Entity(tableName = "journal_entries")
data class JournalEntity(
    // All fields default so Firestore deserialization (toObjects) works — see BillingEntity.
    @PrimaryKey
    val entryId: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val mood: String = "",
    val date: Long = 0L,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val updatedAt: Long = System.currentTimeMillis()
)
