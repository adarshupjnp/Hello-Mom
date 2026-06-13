package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE userId = :userId AND isDeleted = 0")
    fun getAppointments(userId: String): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity)

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Delete
    suspend fun deleteAppointment(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointments WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncAppointments(): List<AppointmentEntity>

    // Reconciliation after a pull: rows that no longer exist remotely were deleted on another
    // device. PENDING rows are kept — they are local edits that haven't been pushed yet.
    @Query("SELECT appointmentId FROM appointments WHERE userId = :userId AND syncStatus != 'PENDING' AND appointmentId NOT IN (:keepIds)")
    suspend fun getStaleAppointmentIds(userId: String, keepIds: List<String>): List<String>

    @Query("DELETE FROM appointments WHERE userId = :userId AND syncStatus != 'PENDING' AND appointmentId NOT IN (:keepIds)")
    suspend fun deleteAppointmentsNotIn(userId: String, keepIds: List<String>)
}
