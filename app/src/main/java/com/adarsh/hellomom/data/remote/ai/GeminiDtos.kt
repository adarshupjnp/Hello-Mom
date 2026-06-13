package com.adarsh.hellomom.data.remote.ai

import com.google.gson.annotations.SerializedName

/**
 * Request/response DTOs for the Gemini Developer REST API
 * (`generativelanguage.googleapis.com/v1/models/{model}:generateContent`).
 *
 * These replace the discontinued `com.google.ai.client.generativeai` SDK, which
 * bundled Ktor 2.x and crashed at runtime once Supabase pulled in Ktor 3.x onto
 * the same classpath. Going through Retrofit/Gson keeps the AI calls on the same
 * networking stack as the rest of the app and removes the Ktor dependency entirely.
 */
data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>
)

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>,
    @SerializedName("role") val role: String? = null
)

data class GeminiPart(
    @SerializedName("text") val text: String
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null,
    @SerializedName("promptFeedback") val promptFeedback: GeminiPromptFeedback? = null
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null
)

data class GeminiPromptFeedback(
    @SerializedName("blockReason") val blockReason: String? = null
)

/** Convenience accessor mirroring the old SDK's `response.text`. */
val GeminiResponse.text: String?
    get() = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
