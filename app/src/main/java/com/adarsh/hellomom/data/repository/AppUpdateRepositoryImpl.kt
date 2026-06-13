package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.data.remote.update.AppConfigService
import com.adarsh.hellomom.domain.model.AppUpdateInfo
import com.adarsh.hellomom.domain.repository.AppUpdateRepository
import javax.inject.Inject

class AppUpdateRepositoryImpl @Inject constructor(
    private val service: AppConfigService
) : AppUpdateRepository {

    override suspend fun getAppUpdateInfo(): AppUpdateInfo {
        val row = service.getAppConfig().firstOrNull()
            ?: throw IllegalStateException("No app configuration found on the server")

        return AppUpdateInfo(
            latestVersionCode = row.latestVersionCode,
            latestVersionName = row.latestVersionName,
            apkUrl = row.apkUrl,
            forceUpdate = row.forceUpdate
        )
    }
}
