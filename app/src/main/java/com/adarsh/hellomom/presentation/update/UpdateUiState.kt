package com.adarsh.hellomom.presentation.update

import com.adarsh.hellomom.domain.model.AppUpdateInfo
import java.io.File

/**
 * Exhaustive UI state for the in-app update flow:
 *
 *   Idle -> Loading -> (UpToDate | UpdateAvailable | Error)
 *   UpdateAvailable --user taps Update--> Downloading -> (Downloaded | Error)
 */
sealed interface UpdateUiState {
    /** No check has run yet / dialog dismissed. */
    object Idle : UpdateUiState

    /** A check is in flight. */
    object Loading : UpdateUiState

    /** Installed build is the latest. */
    object UpToDate : UpdateUiState

    /** A newer build is published — show the update dialog. */
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateUiState

    /** APK download in progress. [progress] is 0..100, or -1 when total size is unknown. */
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Int
    ) : UpdateUiState

    /** Download finished; [file] is ready to be handed to the package installer. */
    data class Downloaded(
        val info: AppUpdateInfo,
        val file: File
    ) : UpdateUiState

    /**
     * The check or download failed.
     * [info] is non-null when the failure happened mid-update (so the user can retry the download).
     */
    data class Error(
        val message: String,
        val info: AppUpdateInfo? = null
    ) : UpdateUiState
}
