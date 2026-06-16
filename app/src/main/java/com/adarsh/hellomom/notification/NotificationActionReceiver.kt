package com.adarsh.hellomom.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.domain.repository.ReminderRepository
import com.adarsh.hellomom.service.ReminderService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var reminderManager: ReminderManager

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntOfExtra("reminder_id", -1)
        if (reminderId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId)

        // Stop foreground service and TTS
        val stopServiceIntent = Intent(context, ReminderService::class.java)
        context.stopService(stopServiceIntent)

        val action = intent.action
        val snoozeMinutes = intent.getIntExtra("snooze_minutes", 10)

        // BroadcastReceivers can be killed the moment onReceive() returns. The DB write + Firestore
        // push below are async, so we hold the receiver alive with goAsync()/finish() to guarantee
        // "Done" really persists and syncs (so linked family members see the latest status), and
        // that a snooze is actually rescheduled.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_DONE -> handleDone(reminderId)
                    ACTION_SNOOZE -> handleSnooze(reminderId, snoozeMinutes)
                    ACTION_REMIND_LATER -> { /* Navigation handled in MainActivity; service already stopped. */ }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Mark the reminder genuinely completed (Room + Firestore) and drop its pending auto-snooze check. */
    private suspend fun handleDone(reminderId: Int) {
        // markAsDone() updates Room and pushes to Firestore so linked family members get the update.
        reminderRepository.markAsDone(reminderId)
        // The reminder is finished — cancel the 1-hour auto-snooze safety check so it can't reopen it.
        reminderManager.cancelSnoozeCheck(reminderId)
    }

    /**
     * Snooze the reminder, but only up to [MAX_SNOOZE_COUNT] times. Once the cap is reached the
     * reminder is marked PENDING (and synced) instead of being snoozed again, so it surfaces for
     * the user to act on rather than being postponed forever.
     */
    private suspend fun handleSnooze(reminderId: Int, snoozeMinutes: Int) {
        val reminder = reminderRepository.getReminderById(reminderId) ?: return
        if (reminder.snoozeCount >= MAX_SNOOZE_COUNT) {
            // Snooze limit hit → mark PENDING automatically and stop rescheduling.
            reminderRepository.updateReminder(
                reminder.copy(
                    status = ReminderStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
            reminderManager.cancelSnoozeCheck(reminderId)
            return
        }

        val snoozeMillis = snoozeMinutes * 60 * 1000L
        val updated = reminder.copy(
            time = System.currentTimeMillis() + snoozeMillis,
            status = ReminderStatus.SNOOZED,
            snoozedUntil = System.currentTimeMillis() + snoozeMillis,
            snoozeCount = reminder.snoozeCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        // Persist + sync, then re-arm the popup to fire again after the snooze interval.
        reminderRepository.updateReminder(updated)
        reminderManager.scheduleReminder(updated)
    }

    private fun Intent.getIntOfExtra(name: String, defaultValue: Int): Int {
        return if (hasExtra(name)) getIntExtra(name, defaultValue) else defaultValue
    }

    companion object {
        const val ACTION_DONE = "com.adarsh.hellomom.ACTION_DONE"
        const val ACTION_SNOOZE = "com.adarsh.hellomom.ACTION_SNOOZE"
        const val ACTION_REMIND_LATER = "com.adarsh.hellomom.ACTION_REMIND_LATER"

        /** Maximum number of times a reminder may be snoozed before it is auto-marked PENDING. */
        const val MAX_SNOOZE_COUNT = 3
    }
}
