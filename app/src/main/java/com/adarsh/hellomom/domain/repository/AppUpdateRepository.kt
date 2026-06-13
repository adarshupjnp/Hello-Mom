package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.domain.model.AppUpdateInfo

/**
 * Source of truth for the latest published app build (backed by Supabase REST).
 */
interface AppUpdateRepository {

    /**
     * Fetches the latest published [AppUpdateInfo] from the backend.
     *
     * @throws Exception on network, HTTP or parsing failures so callers can
     * surface an error / retry state.
     */
    suspend fun getAppUpdateInfo(): AppUpdateInfo
}
