package com.adarsh.hellomom

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Intent
import com.adarsh.hellomom.data.worker.SyncWorker
import com.adarsh.hellomom.notification.DayChangeReceiver
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class HelloMomApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundSync()
        clearStaleNotificationsOnLaunch()
    }

    /**
     * Catch-up for the day-change cleanup. [DayChangeReceiver] fires on the system's midnight
     * DATE_CHANGED broadcast, but that can be missed if the device was off overnight, so we also
     * run the same cleanup once on every launch. It is idempotent and only touches notifications /
     * reminders from previous days, so it never clears anything still valid for today.
     */
    private fun clearStaleNotificationsOnLaunch() {
        runCatching {
            sendBroadcast(Intent(this, DayChangeReceiver::class.java))
        }
    }

    /**
     * Keeps Room in sync with Firestore in the background: an immediate sync on launch plus a
     * periodic sync, both gated on connectivity. This is what lets a family member see the
     * owner's latest data shortly after opening the app, and lets the owner's offline edits
     * reach Firestore once the device is back online.
     */
    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workManager = WorkManager.getInstance(this)

        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val oneTime = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            STARTUP_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }

    companion object {
        private const val PERIODIC_SYNC_WORK = "hello_mom_periodic_sync"
        private const val STARTUP_SYNC_WORK = "hello_mom_startup_sync"
    }
}
