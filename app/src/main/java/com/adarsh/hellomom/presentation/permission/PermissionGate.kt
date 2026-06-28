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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Drives the app's mandatory runtime permissions (Location, Notifications, Audio, Camera) 
 * with a persistent UI that checks and prompts the user every time the app opens.
 *
 * For runtime permissions, it uses the system popup; if permanently denied, it shows 
 * a rationale dialog leading to Settings. For the Exact Alarm permission (Android 12+), 
 * it shows a rationale dialog first as it is a system settings toggle.
 */
@Composable
fun PermissionGate() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Primary permission states
    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var audioGranted by remember { mutableStateOf(hasAudioPermission(context)) }
    var cameraGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    var alarmGranted by remember { mutableStateOf(canScheduleExactAlarms(context)) }

    // Rationale dialog visibility (shown when system popup is exhausted)
    var showNotifRationale by remember { mutableStateOf(false) }
    var showLocationRationale by remember { mutableStateOf(false) }
    var showAudioRationale by remember { mutableStateOf(false) }
    var showCameraRationale by remember { mutableStateOf(false) }

    // Tick-based triggers for re-evaluating and re-prompting missing permissions
    var requestTick by remember { mutableIntStateOf(0) }
    var requesting by remember { mutableStateOf(false) }

    // Single launcher for all mandatory runtime permissions
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        requesting = false
        notifGranted = hasNotificationPermission(context)
        locationGranted = hasLocationPermission(context)
        audioGranted = hasAudioPermission(context)
        cameraGranted = hasCameraPermission(context)

        // Identify permissions that were denied and no longer show the system popup
        if (activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                results[Manifest.permission.POST_NOTIFICATIONS] == false &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                showNotifRationale = true
            }
            if ((results[Manifest.permission.ACCESS_FINE_LOCATION] == false || results[Manifest.permission.ACCESS_COARSE_LOCATION] == false) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showLocationRationale = true
            }
            if (results[Manifest.permission.RECORD_AUDIO] == false &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
                showAudioRationale = true
            }
            if (results[Manifest.permission.CAMERA] == false &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                showCameraRationale = true
            }
        }
    }

    // Automatically trigger system prompts for any missing permissions
    LaunchedEffect(requestTick) {
        if (requesting) return@LaunchedEffect

        val toRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!hasLocationPermission(context)) {
            toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            toRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!hasAudioPermission(context)) {
            toRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (!hasCameraPermission(context)) {
            toRequest.add(Manifest.permission.CAMERA)
        }

        if (toRequest.isNotEmpty()) {
            requesting = true
            launcher.launch(toRequest.toTypedArray())
        }
    }

    // Re-check and re-trigger whenever the user returns to the app (e.g. from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = hasNotificationPermission(context)
                locationGranted = hasLocationPermission(context)
                audioGranted = hasAudioPermission(context)
                cameraGranted = hasCameraPermission(context)
                alarmGranted = canScheduleExactAlarms(context)

                // Reset rationale states to allow the logic to re-trigger if needed
                showNotifRationale = false
                showLocationRationale = false
                showAudioRationale = false
                showCameraRationale = false

                requestTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- SEQUENTIAL PERMISSION UI (Prevents dialog stacking) ---
    when {
        // 1. Mandatory Rationale: Notifications
        !notifGranted && showNotifRationale -> {
            RequiredPermissionDialog(
                icon = Icons.Default.Notifications,
                title = "Notifications Required",
                message = "Hello Mom+ needs notification access to send you important pregnancy updates and reminders. Since this was previously denied, please enable it in App Settings to continue.",
                confirmLabel = "Allow",
                onConfirm = { context.openAppNotificationSettings() }
            )
        }

        // 2. Mandatory Rationale: Location
        !locationGranted && showLocationRationale -> {
            RequiredPermissionDialog(
                icon = Icons.Default.LocationOn,
                title = "Location Access Required",
                message = "Hello Mom+ uses your location for family distance tracking and local health resources. Please enable location access in App Settings to continue.",
                confirmLabel = "Allow",
                onConfirm = { context.openAppNotificationSettings() }
            )
        }

        // 3. Mandatory Rationale: Audio
        !audioGranted && showAudioRationale -> {
            RequiredPermissionDialog(
                icon = Icons.Default.Mic,
                title = "Microphone Access Required",
                message = "Microphone access is required for your AI voice assistant. Please enable microphone access in App Settings to continue.",
                confirmLabel = "Allow",
                onConfirm = { context.openAppNotificationSettings() }
            )
        }

        // 4. Mandatory Rationale: Camera
        !cameraGranted && showCameraRationale -> {
            RequiredPermissionDialog(
                icon = Icons.Default.CameraAlt,
                title = "Camera Access Required",
                message = "Camera access is required for report scanning and AI features. Please enable camera access in App Settings to continue.",
                confirmLabel = "Allow",
                onConfirm = { context.openAppNotificationSettings() }
            )
        }

        // 5. Alarms & Reminders (Restored previous logic exactly)
        notifGranted && !alarmGranted -> {
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

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

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
