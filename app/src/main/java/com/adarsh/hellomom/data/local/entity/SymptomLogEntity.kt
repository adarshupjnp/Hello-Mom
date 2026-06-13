package com.adarsh.hellomom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "symptom_logs")
data class SymptomLogEntity(
    // All fields default so Firestore deserialization (toObjects) works — see AppointmentEntity.
    @PrimaryKey
    val logId: String = "",
    val userId: String = "",
    val symptomName: String = "",
    val severity: Int = 0, // 1-10
    val riskLevel: String = "", // Low, Medium, High, Emergency
    val recommendation: String = "",
    val date: Long = System.currentTimeMillis()
)
