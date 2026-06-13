package com.adarsh.hellomom.presentation.appointment

import com.adarsh.hellomom.core.UiEffect
import com.adarsh.hellomom.core.UiIntent
import com.adarsh.hellomom.core.UiState
import com.adarsh.hellomom.data.local.entity.AppointmentEntity

sealed class AppointmentIntent : UiIntent {
    object LoadAppointments : AppointmentIntent()
    data class OnAddAppointment(
        val doctorName: String,
        val hospitalName: String,
        val time: Long,
        val location: String,
        val notes: String
    ) : AppointmentIntent()
    data class OnDeleteAppointment(val appointment: AppointmentEntity) : AppointmentIntent()
    data class OnUpdateAppointment(val appointment: AppointmentEntity) : AppointmentIntent()
    data class OnDateFilterChanged(val date: Long?) : AppointmentIntent()
}

data class AppointmentState(
    val appointments: List<AppointmentEntity> = emptyList(),
    val filteredAppointments: List<AppointmentEntity> = emptyList(),
    val selectedDate: Long? = null,
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val userName: String = "",
    val pregnancyWeek: Int = 1,
    val error: String? = null
) : UiState

sealed class AppointmentEffect : UiEffect {
    data class ShowError(val message: String) : AppointmentEffect()
}
