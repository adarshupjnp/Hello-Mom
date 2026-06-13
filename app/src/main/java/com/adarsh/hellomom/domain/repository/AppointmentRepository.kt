package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

interface AppointmentRepository {
    fun getAppointments(userId: String): Flow<List<AppointmentEntity>>
    suspend fun insertAppointment(appointment: AppointmentEntity): Result<Unit>
    suspend fun updateAppointment(appointment: AppointmentEntity): Result<Unit>
    suspend fun deleteAppointment(appointment: AppointmentEntity): Result<Unit>
}
