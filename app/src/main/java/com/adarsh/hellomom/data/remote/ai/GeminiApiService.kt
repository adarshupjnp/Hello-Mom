package com.adarsh.hellomom.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service for the Gemini Developer REST API.
 *
 * The API key is passed as a `?key=` query parameter (the Gemini Developer API's
 * auth model). Unlike the Supabase services in [com.adarsh.hellomom.di.NetworkModule],
 * this runs on its own Retrofit instance with a clean OkHttpClient (no Supabase
 * auth interceptor) — wired in [com.adarsh.hellomom.di.AiModule].
 */
interface GeminiApiService {

    /**
     * POST /v1/models/gemini-1.5-flash:generateContent?key={apiKey}
     */
    @POST("v1/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
