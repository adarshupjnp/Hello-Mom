package com.adarsh.hellomom.presentation.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.BuildConfig
import com.adarsh.hellomom.core.utils.ApkDownloader
import com.adarsh.hellomom.core.utils.ApkInstaller
import com.adarsh.hellomom.domain.model.AppUpdateInfo
import com.adarsh.hellomom.domain.usecase.CheckForUpdateUseCase
import com.adarsh.hellomom.domain.usecase.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives the in-app update flow with a single [UpdateUiState] stream
 * (loading / error / available / downloading / downloaded) — MVVM + StateFlow.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val apkDownloader: ApkDownloader,
    private val apkInstaller: ApkInstaller
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.value = UpdateUiState.Loading
            _state.value = when (val result = checkForUpdateUseCase(BuildConfig.VERSION_CODE)) {
                is UpdateCheckResult.Available -> UpdateUiState.UpdateAvailable(result.info)
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Error -> UpdateUiState.Error(result.message)
            }
        }
    }

    /** Starts (or restarts) the APK download for [info] and streams progress into state. */
    fun startDownload(info: AppUpdateInfo) {
        if (info.apkUrl.isBlank()) {
            _state.value = UpdateUiState.Error("No download link available", info)
            return
        }
        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(info, progress = -1)
            apkDownloader.download(info.apkUrl, info.latestVersionName).collect { progress ->
                _state.value = when (progress) {
                    is ApkDownloader.DownloadProgress.Running ->
                        UpdateUiState.Downloading(info, progress.percent)
                    is ApkDownloader.DownloadProgress.Success ->
                        UpdateUiState.Downloaded(info, progress.file)
                    is ApkDownloader.DownloadProgress.Failed ->
                        UpdateUiState.Error(progress.reason, info)
                }
            }
        }
    }

    /** Re-runs whatever failed: the download if we have update info, otherwise the check. */
    fun retry() {
        when (val s = _state.value) {
            is UpdateUiState.Error -> {
                val info = s.info
                if (info != null) startDownload(info) else checkForUpdate()
            }
            else -> checkForUpdate()
        }
    }

    /** Dismisses an optional update so the dialog goes away. */
    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }

    // --- Install passthroughs (Android specifics live in ApkInstaller) ---

    /** Whether the app may launch the package installer (install-unknown-apps granted). */
    fun canInstall(): Boolean = apkInstaller.canRequestInstall()

    /** Intent to open this app's "Install unknown apps" settings screen. */
    fun installPermissionIntent(): Intent = apkInstaller.unknownSourcesSettingsIntent()

    /** Hands the downloaded APK to the system package installer. */
    fun install(file: File) = apkInstaller.install(file)
}
