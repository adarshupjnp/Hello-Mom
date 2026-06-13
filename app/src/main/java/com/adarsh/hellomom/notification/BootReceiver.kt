package com.adarsh.hellomom.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adarsh.hellomom.data.local.dao.ReminderDao
import com.adarsh.hellomom.data.local.entity.ReminderStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderDao: ReminderDao

    @Inject
    lateinit var reminderManager: ReminderManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val currentTime = System.currentTimeMillis()
                // Get all pending reminders
                val pendingReminders = reminderDao.getPendingRemindersBefore(Long.MAX_VALUE)
                pendingReminders.forEach { reminder ->
                    if (reminder.time > currentTime) {
                        reminderManager.scheduleReminder(reminder)
                    } else {
                        // If the time has already passed while device was off, maybe mark as MISSED or trigger now?
                        // User requirement says auto-missed logic if ignored.
                        // For simplicity, if it's within 1 hour, trigger it, otherwise mark missed.
                        if (currentTime - reminder.time < 3600000) {
                            reminderManager.scheduleReminder(reminder.copy(time = currentTime + 5000))
                        } else {
                            reminderDao.updateReminder(reminder.copy(status = ReminderStatus.EXPIRED))
                        }
                    }
                }
            }
        }
    }
}
