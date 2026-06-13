package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.AppointmentDao
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import com.adarsh.hellomom.domain.repository.AppointmentRepository
import com.adarsh.hellomom.notification.AppointmentReminderScheduler
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppointmentRepositoryImpl @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val firestore: FirebaseFirestore,
    private val appointmentReminderScheduler: AppointmentReminderScheduler
) : AppointmentRepository {

    override fun getAppointments(userId: String): Flow<List<AppointmentEntity>> {
        return appointmentDao.getAppointments(userId)
    }

    override suspend fun insertAppointment(appointment: AppointmentEntity): Result<Unit> {
        return try {
            // Offline-first: persist locally first so the UI updates instantly, then push best-effort.
            // Firestore's offline persistence queues the write; pushPendingData syncs status later.
            appointmentDao.insertAppointment(appointment)
            // Local notifications 1 day and 1 hour before the appointment (family devices get
            // theirs scheduled when the appointment is pulled during sync).
            appointmentReminderScheduler.schedule(appointment)
            SyncLogger.local("ADD appointment", "appointments", "id=${appointment.appointmentId} userId=${appointment.userId} doctor=${appointment.doctorName} hospital=${appointment.hospitalName} time=${appointment.appointmentTime}")
            firestore.collection("users").document(appointment.userId)
                .collection("appointments").document(appointment.appointmentId).set(appointment)
            SyncLogger.firebaseWrite("ADD appointment", "users/${appointment.userId}/appointments/${appointment.appointmentId}", "doctor=${appointment.doctorName} hospital=${appointment.hospitalName} time=${appointment.appointmentTime}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD appointment failed id=${appointment.appointmentId}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateAppointment(appointment: AppointmentEntity): Result<Unit> {
        return try {
            appointmentDao.updateAppointment(appointment)
            // Re-schedule so a changed appointment time moves both notification alarms.
            appointmentReminderScheduler.schedule(appointment)
            SyncLogger.local("EDIT appointment", "appointments", "id=${appointment.appointmentId} userId=${appointment.userId} doctor=${appointment.doctorName} hospital=${appointment.hospitalName} time=${appointment.appointmentTime}")
            firestore.collection("users").document(appointment.userId)
                .collection("appointments").document(appointment.appointmentId).set(appointment)
            SyncLogger.firebaseWrite("EDIT appointment", "users/${appointment.userId}/appointments/${appointment.appointmentId}", "doctor=${appointment.doctorName} hospital=${appointment.hospitalName} time=${appointment.appointmentTime}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT appointment failed id=${appointment.appointmentId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteAppointment(appointment: AppointmentEntity): Result<Unit> {
        return try {
            appointmentDao.deleteAppointment(appointment)
            appointmentReminderScheduler.cancel(appointment.appointmentId)
            SyncLogger.local("DELETE appointment", "appointments", "id=${appointment.appointmentId} userId=${appointment.userId}")
            firestore.collection("users").document(appointment.userId)
                .collection("appointments").document(appointment.appointmentId).delete()
            SyncLogger.firebaseWrite("DELETE appointment", "users/${appointment.userId}/appointments/${appointment.appointmentId}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE appointment failed id=${appointment.appointmentId}", e)
            Result.failure(e)
        }
    }
}
