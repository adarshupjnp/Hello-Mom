package com.adarsh.hellomom.presentation.appointment

import androidx.lifecycle.viewModelScope
import com.adarsh.hellomom.core.BaseViewModel
import com.adarsh.hellomom.core.RoleManager
import com.adarsh.hellomom.core.utils.PregnancyProgress
import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.entity.AppointmentEntity
import com.adarsh.hellomom.domain.repository.AppointmentRepository
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AppointmentViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val roleManager: RoleManager,
    private val syncRepository: SyncRepository
) : BaseViewModel<AppointmentIntent, AppointmentState, AppointmentEffect>() {

    override fun createInitialState(): AppointmentState = AppointmentState()

    init {
        handleIntent(AppointmentIntent.LoadAppointments)
    }

    override fun handleIntent(intent: AppointmentIntent) {
        when (intent) {
            AppointmentIntent.LoadAppointments -> loadAppointments()
            is AppointmentIntent.OnAddAppointment -> addAppointment(intent)
            is AppointmentIntent.OnDeleteAppointment -> deleteAppointment(intent.appointment)
            is AppointmentIntent.OnUpdateAppointment -> updateAppointment(intent.appointment)
            is AppointmentIntent.OnDateFilterChanged -> {
                setState { copy(selectedDate = intent.date) }
                applyFilter()
            }
        }
    }

    private fun loadAppointments() {
        // Throttled background sync so family members see the owner's latest appointments just
        // by navigating here — the Room flow below re-emits when the pull lands.
        viewModelScope.launch { runCatching { syncRepository.syncIfStale() } }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val access = runCatching { roleManager.resolveAccess() }
                .getOrElse { e ->
                    SyncLogger.error("Appointment resolveAccess failed", e)
                    setState { copy(isLoading = false) }
                    return@launch
                }
            val user = access.user
            if (user != null) {
                // Family members read the owner's data (activeUserId); owners read their own.
                // The week always comes from the owner's start date (family has none of its own).
                val week = PregnancyProgress.week(access.owner?.pregnancyStartDate ?: user.pregnancyStartDate)
                val ownerHospital = access.owner?.hospitalName
                
                setState { copy(isOwner = access.isOwner) }
                appointmentRepository.getAppointments(access.activeUserId)
                    .catch { e ->
                        SyncLogger.error("Appointment flow failed", e)
                        setState { copy(isLoading = false) }
                    }
                    .collectLatest { list ->
                        setState {
                            copy(
                                appointments = list,
                                userName = user.fullName,
                                userHospitalName = ownerHospital,
                                pregnancyWeek = week,
                                isOwner = access.isOwner,
                                isLoading = false
                            )
                        }
                        applyFilter()
                    }
            } else {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun applyFilter() {
        val selectedDate = uiState.value.selectedDate
        val all = uiState.value.appointments
        
        val filtered = if (selectedDate == null) {
            all
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val filterDateStr = sdf.format(Date(selectedDate))
            all.filter { sdf.format(Date(it.appointmentTime)) == filterDateStr }
        }
        
        setState { copy(filteredAppointments = filtered.sortedBy { it.appointmentTime }) }
    }

    private fun addAppointment(intent: AppointmentIntent.OnAddAppointment) {
        viewModelScope.launch {
            // Only owners may write. Family members have read-only access.
            val access = roleManager.resolveAccess()
            if (!access.isOwner) return@launch
            val appointment = AppointmentEntity(
                appointmentId = UUID.randomUUID().toString(),
                userId = access.activeUserId,
                doctorName = intent.doctorName,
                hospitalName = intent.hospitalName,
                appointmentTime = intent.time,
                location = intent.location,
                notes = intent.notes
            )
            appointmentRepository.insertAppointment(appointment)
        }
    }

    private fun deleteAppointment(appointment: AppointmentEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            appointmentRepository.deleteAppointment(appointment)
        }
    }

    private fun updateAppointment(appointment: AppointmentEntity) {
        viewModelScope.launch {
            if (!roleManager.resolveAccess().isOwner) return@launch
            appointmentRepository.updateAppointment(appointment)
        }
    }
}
