package com.adarsh.hellomom.data.remote.update

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for the Supabase REST `app_config` table.
 *
 * The `apikey` and `Authorization` headers are added globally by the
 * auth interceptor configured in [com.adarsh.hellomom.di.NetworkModule],
 * so they are not declared here.
 */
interface AppConfigService {

    /**
     * GET /rest/v1/app_config?id=eq.1&select=*
     *
     * Returns a single-element array for the configured config row.
     */
    @GET("rest/v1/app_config")
    suspend fun getAppConfig(
        @Query("id") id: String = "eq.1",
        @Query("select") select: String = "*"
    ): List<AppConfigDto>
}
