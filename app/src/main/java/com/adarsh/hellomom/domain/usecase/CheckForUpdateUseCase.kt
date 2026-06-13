package com.adarsh.hellomom.domain.usecase

import com.adarsh.hellomom.domain.model.AppUpdateInfo
import com.adarsh.hellomom.domain.repository.AppUpdateRepository
import javax.inject.Inject

/** Outcome of a single update check. */
sealed interface UpdateCheckResult {
    data class Available(val info: AppUpdateInfo) : UpdateCheckResult
    object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Fetches the latest published build and compares it against the currently
 * installed version code. Pure decision logic — no Android dependencies — so
 * it stays unit-testable.
 */
class CheckForUpdateUseCase @Inject constructor(
    private val repository: AppUpdateRepository
) {
    suspend operator fun invoke(currentVersionCode: Int): UpdateCheckResult {
        return try {
            val info = repository.getAppUpdateInfo()
            if (info.latestVersionCode > currentVersionCode) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Couldn't check for updates")
        }
    }
}
