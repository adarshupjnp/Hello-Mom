package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.BuildConfig
import com.adarsh.hellomom.data.remote.ai.GeminiApiService
import com.adarsh.hellomom.data.remote.ai.GeminiContent
import com.adarsh.hellomom.data.remote.ai.GeminiPart
import com.adarsh.hellomom.data.remote.ai.GeminiRequest
import com.adarsh.hellomom.data.remote.ai.text
import com.adarsh.hellomom.domain.repository.AiRepository
import com.adarsh.hellomom.domain.repository.PrescriptionMedicine
import com.adarsh.hellomom.domain.repository.RiskAssessment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor(
    private val geminiApi: GeminiApiService
) : AiRepository {

    /**
     * Single-turn text generation against the Gemini REST API. Returns the model's
     * text, or null if the response carried no candidate (e.g. blocked prompt).
     * Network/HTTP errors propagate as exceptions for callers to catch.
     */
    private suspend fun generateContent(prompt: String): String? {
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))
        )
        return geminiApi.generateContent(BuildConfig.GEMINI_API_KEY, request).text
    }

    override suspend fun getChatResponse(prompt: String): Flow<Result<String>> = flow {
        try {
            val responseText = generateContent(prompt)

            if (responseText != null && !responseText.contains("Unexpected Response") && !responseText.contains("error")) {
                emit(Result.success(responseText))
            } else if (responseText != null) {
                emit(Result.failure(Exception(responseText)))
            } else {
                emit(Result.success("I'm sorry, I couldn't understand that."))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override suspend fun analyzeSymptomRisk(symptom: String, week: Int): Result<RiskAssessment> {
        return try {
            val prompt = """
                Analyze the following pregnancy symptom for a mother in week $week:
                Symptom: $symptom

                Respond ONLY in JSON format:
                {
                  "severity": "Low/Medium/High/Emergency",
                  "recommendation": "Short advice",
                  "alertUser": true/false
                }
            """.trimIndent()

            val json = generateContent(prompt) ?: return Result.failure(Exception("Empty AI response"))

            // Simple parsing (could use Moshi/Gson for production)
            val severity = Regex("\"severity\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: "Unknown"
            val recommendation = Regex("\"recommendation\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: "Consult a doctor"
            val alertUser = json.contains("\"alertUser\": true")

            Result.success(RiskAssessment(severity, recommendation, alertUser))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun parsePrescription(text: String): Result<List<PrescriptionMedicine>> {
        return try {
            val prompt = """
                Extract medicine details from this prescription text:
                $text

                Respond ONLY in JSON array format:
                [
                  {"name": "...", "dosage": "...", "timing": "..."}
                ]
            """.trimIndent()

            val json = generateContent(prompt) ?: return Result.failure(Exception("Empty AI response"))

            // Minimal regex parsing for simplicity
            val medicines = mutableListOf<PrescriptionMedicine>()
            val matches = Regex("\\{\"name\":\\s*\"(.*?)\",\\s*\"dosage\":\\s*\"(.*?)\",\\s*\"timing\":\\s*\"(.*?)\"\\}").findAll(json)
            matches.forEach { match ->
                medicines.add(PrescriptionMedicine(match.groupValues[1], match.groupValues[2], match.groupValues[3]))
            }

            Result.success(medicines)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNutritionRecommendation(week: Int): Result<String> {
        return try {
            val prompt = "Give a short daily nutrition recommendation for a pregnant mother in week $week. Focus on specific foods like Coconut water, spinach etc."
            val text = generateContent(prompt)
            Result.success(text ?: "Eat a balanced diet with folic acid and iron.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
