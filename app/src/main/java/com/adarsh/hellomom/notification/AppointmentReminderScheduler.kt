package com.adarsh.hellomom.notification

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules local notification alarms for appointments: one 24 hours before and one 1 hour
 * before the appointment time.
 *
 * It runs on EVERY device that has the appointment in Room — the owner's device schedules on
 * insert/update, and family devices schedule when [com.adarsh.hellomom.data.repository.SyncRepositoryImpl]
 * pulls the owner's appointments. Re-scheduling is idempotent (PendingIntent request codes are
 * derived from the appointmentId), so repeated syncs are safe.
 *
 * Alarm ids live in dedicated high ranges (bit 0x20000000 / 0x40000000 set) so they can never
 * collide with the auto-increment Int ids used by [ReminderEntity] rows.
 */
@Singleton
class AppointmentReminderScheduler @Inject constructor(
    private val reminderManager: ReminderManager
) {
    /** (Re)schedule the 1-day-before and 1-hour-before alarms for one appointment. */
    fun schedule(appointment: AppointmentEntity) {
        runCatching {
            cancel(appointment.appointmentId)
            if (appointment.isDeleted || appointment.appointmentTime <= 0) return

            val now = System.currentTimeMillis()
            val time = appointment.appointmentTime
            if (time <= now) return

            val doctor = appointment.doctorName.ifBlank { "your doctor" }
            val hospital = appointment.hospitalName.ifBlank { "the hospital" }
            val whenText = timeFormat.format(Date(time))

            val dayBefore = time - DAY_MILLIS
            if (dayBefore > now) {
                reminderManager.scheduleReminder(
                    dayBeforeCode(appointment.appointmentId),
                    "Appointment tomorrow",
                    "Appointment with $doctor at $hospital tomorrow at $whenText.",
                    dayBefore
                )
            }

            val hourBefore = time - HOUR_MILLIS
            if (hourBefore > now) {
                reminderManager.scheduleReminder(
                    hourBeforeCode(appointment.appointmentId),
                    "Appointment in 1 hour",
                    "Appointment with $doctor at $hospital at $whenText. Time to get ready!",
                    hourBefore
                )
            }
            SyncLogger.info("Scheduled appointment alarms id=${appointment.appointmentId} time=$time")
        }.onFailure { SyncLogger.warn("Failed to schedule appointment alarms id=${appointment.appointmentId}", it) }
    }

    /** Cancel both alarms for an appointment (deleted or rescheduled). */
    fun cancel(appointmentId: String) {
        runCatching {
            reminderManager.cancelReminder(dayBeforeCode(appointmentId))
            reminderManager.cancelReminder(hourBeforeCode(appointmentId))
        }
    }

    private fun dayBeforeCode(appointmentId: String): Int =
        (appointmentId.hashCode() and 0x1FFFFFFF) or 0x20000000

    private fun hourBeforeCode(appointmentId: String): Int =
        (appointmentId.hashCode() and 0x1FFFFFFF) or 0x40000000

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val HOUR_MILLIS = 60 * 60 * 1000L
        private val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }
}
