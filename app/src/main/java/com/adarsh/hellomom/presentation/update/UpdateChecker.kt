package com.adarsh.hellomom.presentation.update

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.adarsh.hellomom.domain.model.AppUpdateInfo
import java.io.File

/**
 * Drop-in, reusable composable that runs the full in-app update flow:
 *
 *   auto-check on first composition → update dialog → in-app download (progress)
 *   → "install unknown apps" permission (if needed) → launch the package installer.
 *
 * Place it once near the root of the UI (e.g. inside MainActivity's `setContent`).
 * It renders nothing until an update is actually available.
 */
@Composable
fun UpdateChecker(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Holds the APK waiting for the install permission while the user is in Settings.
    var pendingInstall by remember { mutableStateOf<File?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstall
        pendingInstall = null
        if (file != null) {
            if (viewModel.canInstall()) {
                viewModel.install(file)
            } else {
                Toast.makeText(
                    context,
                    "Permission denied. Enable \"Install unknown apps\" to update.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun installOrRequestPermission(file: File) {
        if (viewModel.canInstall()) {
            viewModel.install(file)
        } else {
            pendingInstall = file
            installPermissionLauncher.launch(viewModel.installPermissionIntent())
        }
    }

    // Kick off the check once when this composable enters the tree.
    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    when (val s = state) {
        is UpdateUiState.UpdateAvailable -> {
            val info = s.info
            UpdateDialog(
                info = info,
                onUpdateClick = { viewModel.startDownload(info) },
                onDismiss = if (info.forceUpdate) null else ({ viewModel.dismiss() })
            )
        }

        is UpdateUiState.Downloading -> {
            DownloadProgressDialog(
                versionName = s.info.latestVersionName,
                progress = s.progress
            )
        }

        is UpdateUiState.Downloaded -> {
            // Trigger installation exactly once when the download completes.
            LaunchedEffect(s.file.absolutePath) {
                installOrRequestPermission(s.file)
            }
        }

        is UpdateUiState.Error -> {
            // Errors that carry update info happened mid-update → surface with retry.
            // Plain check failures on startup stay silent so they never interrupt the user.
            val info: AppUpdateInfo? = s.info
            if (info != null) {
                UpdateErrorDialog(
                    message = s.message,
                    dismissible = !info.forceUpdate,
                    onRetry = { viewModel.retry() },
                    onDismiss = { viewModel.dismiss() }
                )
            }
        }

        // Idle / Loading / UpToDate render nothing.
        else -> Unit
    }
}
