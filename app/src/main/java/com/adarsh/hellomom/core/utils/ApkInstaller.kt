package com.adarsh.hellomom.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launches the system package installer for a downloaded APK.
 *
 * Handles the two things that break APK installs on modern Android:
 *  1. A `content://` URI via [FileProvider] (a raw `file://` URI throws
 *     FileUriExposedException on Android 7+).
 *  2. The "install unknown apps" permission, which on Android 8+ (API 26+)
 *     is granted per-app via Settings.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val authority: String = "${context.packageName}.fileprovider"

    /** True if the app is allowed to request package installs (always handle Android 8+). */
    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Intent that opens this app's "Install unknown apps" settings screen so the
     * user can grant permission. Launch it with an ActivityResultLauncher.
     */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * Starts the package installer for [apkFile]. Caller must ensure
     * [canRequestInstall] is true first.
     */
    fun install(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
