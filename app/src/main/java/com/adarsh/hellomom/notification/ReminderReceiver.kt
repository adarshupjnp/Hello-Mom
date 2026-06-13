package com.adarsh.hellomom.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adarsh.hellomom.data.local.dao.ReminderDao
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import com.adarsh.hellomom.service.ReminderService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderDao: ReminderDao

    @Inject
    lateinit var reminderManager: ReminderManager

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", -1)
        if (reminderId == -1) return

        val isAutoSnoozeCheck = intent.getBooleanExtra("is_auto_snooze_check", false)

        if (isAutoSnoozeCheck) {
            handleAutoSnoozeCheck(context, reminderId)
        } else {
            handleReminderTrigger(context, intent, reminderId)
        }
    }

    private fun handleReminderTrigger(context: Context, intent: Intent, reminderId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val activeCount = reminderDao.getActiveNotificationCount(System.currentTimeMillis())
            if (activeCount >= 5) {
                // Reschedule for 1 minute later if we already have 5 active notifications
                val reminder = reminderDao.getReminderById(reminderId)
                reminder?.let {
                    reminderManager.scheduleReminder(it.copy(time = System.currentTimeMillis() + 60000))
                }
                return@launch
            }

            val title = intent.getStringExtra("title") ?: "Reminder"
            val message = intent.getStringExtra("message") ?: "Hello Mom! It's time."
            val voiceMessage = intent.getStringExtra("voice_message") ?: message

            val serviceIntent = Intent(context, ReminderService::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("voice_message", voiceMessage)
            }
            context.startForegroundService(serviceIntent)

            // Schedule auto-snooze check for 1 hour later
            reminderManager.scheduleSnoozeCheck(reminderId, System.currentTimeMillis() + 3600000)
        }
    }

    private fun handleAutoSnoozeCheck(context: Context, reminderId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val reminder = reminderDao.getReminderById(reminderId) ?: return@launch
            if (reminder.status == ReminderStatus.PENDING) {
                // Auto-snooze once
                val snoozedReminder = reminder.copy(
                    status = ReminderStatus.SNOOZED,
                    snoozedUntil = System.currentTimeMillis() + 3600000,
                    updatedAt = System.currentTimeMillis()
                )
                reminderDao.updateReminder(snoozedReminder)
                reminderManager.scheduleReminder(snoozedReminder)
            } else if (reminder.status == ReminderStatus.SNOOZED) {
                // If still snoozed after the second check (1h later), mark as EXPIRED
                reminderDao.updateReminder(reminder.copy(
                    status = ReminderStatus.EXPIRED,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    }
}
