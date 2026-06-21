package com.adarsh.hellomom.presentation.permission

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Drives the app's required runtime permissions with clear, insistent UI:
 *
 *  1. Notifications (Android 13+) — requested via the system dialog on first launch. If denied, a
 *     blocking explanation dialog appears stating why it's required and re-prompts; once the OS
 *     stops allowing the prompt (permanently denied) the same dialog routes the user to App Settings.
 *  2. Exact alarms (Android 12+) — can only be granted from a system Settings screen, so we first
 *     show an explanation popup (mirroring the notification prompt) and only then open that screen.
 *
 * Camera + microphone are still requested best-effort (used by chat / voice) but are not forced.
 * Permission state is re-checked on every ON_RESUME so the dialogs dismiss themselves the moment
 * the user comes back from Settings having granted access.
 */
@Composable
fun PermissionGate() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var requestingNotif by remember { mutableStateOf(false) }
    var alarmGranted by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    // Bumped to trigger another system popup (the launcher can't be referenced inside its own
    // result callback, so the re-request is routed through this tick + a LaunchedEffect).
    var notifRequestTick by remember { mutableIntStateOf(0) }

    // Best-effort (not forced) camera/microphone request.
    val basicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result ignored — these are optional */ }

    // Notifications use ONLY the system popup — no custom explanation dialog. If the user doesn't
    // allow, we immediately re-show the SAME system popup again, as long as Android still permits it
    // (shouldShowRequestPermissionRationale stays true until a permanent "don't ask again"). Never a
    // rationale dialog or Settings redirect for notifications.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
        requestingNotif = false
        if (!granted && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            notifRequestTick++ // ask again right away (OS will still show the popup)
        }
    }

    LaunchedEffect(Unit) {
        basicLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    // Drives both the initial notification request (tick 0) and every re-request (tick++).
    LaunchedEffect(notifRequestTick) {
        if (!notifGranted && !requestingNotif &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            requestingNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Re-check on resume; if notifications still aren't allowed, show the system popup AGAIN. The
    // `requestingNotif` guard prevents launching while one prompt is already in flight, so a
    // permanently-denied state can't spin into a loop. Also refreshes the exact-alarm state after
    // the user returns from the alarm Settings screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = hasNotificationPermission(context)
                alarmGranted = canScheduleExactAlarms(context)
                if (!notifGranted && !requestingNotif &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    requestingNotif = true
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The explanatory dialog is shown ONLY for alarms/reminders (exact alarms have no runtime
    // popup). Gated on notifGranted so it never stacks on top of the notification prompt.
    if (notifGranted && !alarmGranted) {
        RequiredPermissionDialog(
            icon = Icons.Default.Alarm,
            title = "Allow Alarms & Reminders",
            message = "To deliver appointment and medicine reminders at the exact time, Hello Mom+ " +
                "needs the \"Alarms & reminders\" permission. Tap Allow and enable it on the next " +
                "screen — without it, reminders may be delayed or missed.",
            confirmLabel = "Allow",
            onConfirm = { context.openExactAlarmSettings() }
        )
    }
}

@Composable
private fun RequiredPermissionDialog(
    icon: ImageVector,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    AlertDialog(
        // Empty handler => not dismissable by outside-tap or back press: the permission is required.
        onDismissRequest = { },
        icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } }
    )
}

/* ---------------------------------------------------------------------------- helpers ----- */

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(AlarmManager::class.java)
    return am?.canScheduleExactAlarms() ?: true
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }.onFailure {
        // Fallback to the app's details page if the dedicated screen isn't available.
        openAppNotificationSettings()
    }
}

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
