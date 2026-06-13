package com.adarsh.hellomom.presentation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.adarsh.hellomom.domain.model.AppUpdateInfo

/**
 * Material 3 dialog shown when a newer version is available.
 *
 * @param onDismiss non-null for optional updates (renders a **Later** button and allows
 *                  back / outside dismissal); pass `null` for a forced, non-dismissible update.
 */
@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    onUpdateClick: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    val forced = info.forceUpdate || onDismiss == null

    AlertDialog(
        onDismissRequest = { if (!forced) onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = !forced,
            dismissOnClickOutside = !forced
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Update available") },
        text = {
            Column {
                Text("A new version (${info.latestVersionName}) of Hello Mom+ is available.")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (forced) {
                        "This update is required to keep using the app. Please update to continue."
                    } else {
                        "Update now to get the latest features and improvements."
                    },
                    color = if (forced) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdateClick) { Text("Update") }
        },
        dismissButton = if (!forced) {
            { TextButton(onClick = { onDismiss?.invoke() }) { Text("Later") } }
        } else {
            null
        }
    )
}

/**
 * Non-dismissible progress dialog shown while the APK downloads.
 * [progress] is 0..100, or -1 for indeterminate (unknown total size).
 */
@Composable
fun DownloadProgressDialog(
    versionName: String,
    progress: Int
) {
    AlertDialog(
        onDismissRequest = { /* not dismissible during download */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Downloading update") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Downloading version $versionName…")
                Spacer(Modifier.height(16.dp))
                if (progress in 0..100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$progress%",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Please wait")
            }
        }
    )
}

/**
 * Error dialog with retry, used for both check and download failures.
 *
 * @param dismissible false for a forced update that has not completed (user can't skip it).
 */
@Composable
fun UpdateErrorDialog(
    message: String,
    dismissible: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible
        ),
        title = { Text("Update failed") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onRetry) { Text("Retry") }
        },
        dismissButton = if (dismissible) {
            { TextButton(onClick = onDismiss) { Text("Dismiss") } }
        } else {
            null
        }
    )
}
