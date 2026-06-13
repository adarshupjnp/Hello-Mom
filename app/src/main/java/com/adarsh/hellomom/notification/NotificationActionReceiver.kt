package com.adarsh.hellomom.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        when (intent.action) {
            ACTION_DONE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    reminderRepository.markAsDone(reminderId)
                }
            }
            ACTION_SNOOZE -> {
                val snoozeMinutes = intent.getIntExtra("snooze_minutes", 10)
                CoroutineScope(Dispatchers.IO).launch {
                    val reminder = reminderRepository.getReminderById(reminderId)
                    reminder?.let {
                        val snoozeMillis = snoozeMinutes * 60 * 1000L
                        val updated = it.copy(
                            time = System.currentTimeMillis() + snoozeMillis,
                            status = com.adarsh.hellomom.data.local.entity.ReminderStatus.SNOOZED,
                            snoozeCount = it.snoozeCount + 1,
                            updatedAt = System.currentTimeMillis()
                        )
                        reminderRepository.updateReminder(updated)
                        reminderManager.scheduleReminder(updated)
                    }
                }
            }
            ACTION_REMIND_LATER -> {
                // Navigate handled in MainActivity, but we ensure service stops
            }
        }
    }

    private fun Intent.getIntOfExtra(name: String, defaultValue: Int): Int {
        return if (hasExtra(name)) getIntExtra(name, defaultValue) else defaultValue
    }

    companion object {
        const val ACTION_DONE = "com.adarsh.hellomom.ACTION_DONE"
        const val ACTION_SNOOZE = "com.adarsh.hellomom.ACTION_SNOOZE"
        const val ACTION_REMIND_LATER = "com.adarsh.hellomom.ACTION_REMIND_LATER"
    }
}
