package com.adarsh.hellomom.di

import com.adarsh.hellomom.BuildConfig
import com.adarsh.hellomom.data.remote.ai.GeminiApiService
import com.adarsh.hellomom.data.repository.AiRepositoryImpl
import com.adarsh.hellomom.data.repository.OcrRepositoryImpl
import com.adarsh.hellomom.domain.repository.AiRepository
import com.adarsh.hellomom.domain.repository.OcrRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    /**
     * Dedicated Retrofit instance for the Gemini Developer REST API.
     *
     * Kept separate from the Supabase Retrofit in [NetworkModule] because that one
     * is bound to the Supabase base URL and attaches Supabase auth headers to every
     * request via an interceptor — neither of which applies to Gemini.
     */
    @Provides
    @Singleton
    fun provideGeminiApiService(): GeminiApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAiRepository(impl: AiRepositoryImpl): AiRepository = impl

    @Provides
    @Singleton
    fun provideOcrRepository(impl: OcrRepositoryImpl): OcrRepository = impl
}
