package com.adarsh.hellomom.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.constants.ReminderConstants
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.adarsh.hellomom.service.ReminderService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Day-change notification cleanup.
 *
 * Within a single day a reminder/appointment notification stays in the tray until the user reacts
 * to it (Done/Snooze) — see [ReminderService]'s `setOngoing(true)` / `setAutoCancel(false)`. The
 * ONLY time a still-unacted notification is auto-cleared is when the day changes: every popup left
 * showing from a previous day is dismissed, and the matching reminder rows are marked EXPIRED so
 * they read as missed in Reminder Logs (and the status syncs to linked family members).
 *
 * Triggers:
 *  - `ACTION_DATE_CHANGED` / `TIME_SET` / `TIMEZONE_CHANGED` (manifest intent-filter) — the precise
 *    midnight / clock-change rollover.
 *  - An explicit self-broadcast from [com.adarsh.hellomom.HelloMomApp] on launch — a catch-up for
 *    when the device was off or the broadcast was missed at midnight.
 *
 * The cleanup is idempotent and date-scoped: notifications/reminders from today (or the future)
 * are never touched, so running it on every launch is safe.
 */
@AndroidEntryPoint
class DayChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var reminderManager: ReminderManager
    @Inject lateinit var authRepository: AuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val startOfToday = startOfTodayMillis()

        // Dismiss every notification still showing from a previous day. "Still showing" means the
        // user never reacted to it. This covers reminders, appointment reminders and any other
        // popup the app posts (they all share the single reminder channel).
        runCatching {
            notificationManager.activeNotifications
                .filter { it.postTime < startOfToday }
                .forEach { notificationManager.cancel(it.tag, it.id) }
        }.onFailure { SyncLogger.warn("Day-change tray sweep failed", it) }

        // Mark the reminder rows that fired on a previous day and were never acted on as EXPIRED,
        // and clear any leftover alarms/notifications still tied to them. DB + Firestore writes are
        // async, so hold the receiver alive with goAsync()/finish().
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val expiredIds = reminderRepository.expireStalePendingBefore(startOfToday)
                expiredIds.forEach { id ->
                    notificationManager.cancel(id)        // belt-and-braces if it slipped the sweep
                    reminderManager.cancelReminder(id)    // drop any not-yet-fired alarm for it
                    reminderManager.cancelSnoozeCheck(id) // drop its 1-hour auto-snooze safety check
                }

                // Owner only: roll today's reminders in and purge anything past the retention window
                // right at the midnight rollover, so a fresh day's reminders exist (and old ones are
                // gone from Room + Firestore) even if nobody opens the app. Family members are viewers
                // and receive these through their normal pull/sync.
                val user = runCatching { authRepository.getCurrentUser().first() }.getOrNull()
                if (user != null && RoleManager.isOwnerUser(user.fullName, user.email)) {
                    runCatching { reminderRepository.ensureDailyReminders(user.userId, user.fullName) }
                        .onFailure { SyncLogger.warn("DayChange: ensureDailyReminders failed", it) }
                    val cutoff = startOfToday - (ReminderConstants.RETENTION_DAYS - 1) * DAY_MILLIS
                    runCatching { reminderRepository.deleteOldReminders(cutoff, deleteRemote = true) }
                        .onFailure { SyncLogger.warn("DayChange: reminder purge failed", it) }
                }

                // Stop the foreground service in case it was still narrating a now-stale reminder.
                context.stopService(Intent(context, ReminderService::class.java))
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Local midnight (start of the current day) in millis. */
    private fun startOfTodayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
