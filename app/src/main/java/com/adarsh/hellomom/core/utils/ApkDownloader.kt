package com.adarsh.hellomom.core.utils

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an APK with the system [DownloadManager] and reports progress.
 *
 * The file is saved to the app's external "Downloads" directory
 * (`Android/data/<pkg>/files/Download`). That location:
 *  - needs no storage runtime permission on any API level (Android 10–14),
 *  - is wiped automatically on uninstall,
 *  - is exposed through our own FileProvider (`external-files-path`) for install.
 *
 * Progress is polled from the DownloadManager cursor because DownloadManager
 * does not push progress callbacks.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    sealed interface DownloadProgress {
        /** 0..100, or -1 when the server didn't report a content length. */
        data class Running(val percent: Int) : DownloadProgress
        data class Success(val file: File) : DownloadProgress
        data class Failed(val reason: String) : DownloadProgress
    }

    /** Destination file for a given version (stable name so a re-download overwrites). */
    fun apkFileFor(versionName: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return File(dir, "HelloMom-$versionName.apk")
    }

    /**
     * Enqueues the download and emits progress until it succeeds or fails.
     * A fresh download is started on every call (any stale file is removed first).
     */
    fun download(url: String, versionName: String): Flow<DownloadProgress> = flow {
        val target = apkFileFor(versionName)
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Hello Mom+ update")
            setDescription("Downloading version $versionName")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                target.name
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadId = downloadManager.enqueue(request)

        var finished = false
        while (!finished) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = downloadManager.query(query)

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                emit(DownloadProgress.Failed("Download was cancelled"))
                finished = true
                continue
            }

            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            when (cursor.getInt(statusIdx)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    cursor.close()
                    emit(DownloadProgress.Success(target))
                    finished = true
                }

                DownloadManager.STATUS_FAILED -> {
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIdx)
                    cursor.close()
                    emit(DownloadProgress.Failed("Download failed (code $reason)"))
                    finished = true
                }

                else -> {
                    val downloadedIdx =
                        cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx =
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloaded = cursor.getLong(downloadedIdx)
                    val total = cursor.getLong(totalIdx)
                    cursor.close()

                    val percent = if (total > 0) {
                        ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    emit(DownloadProgress.Running(percent))
                    delay(400)
                }
            }
        }
    }
}
