package com.adarsh.hellomom.domain.repository

import kotlinx.coroutines.flow.Flow

interface AiRepository {
    suspend fun getChatResponse(prompt: String): Flow<Result<String>>
    suspend fun analyzeSymptomRisk(symptom: String, week: Int): Result<RiskAssessment>
    suspend fun parsePrescription(text: String): Result<List<PrescriptionMedicine>>
    suspend fun getNutritionRecommendation(week: Int): Result<String>
}

data class RiskAssessment(
    val severity: String, // Low, Medium, High, Emergency
    val recommendation: String,
    val alertUser: Boolean
)

data class PrescriptionMedicine(
    val name: String,
    val dosage: String,
    val timing: String
)
