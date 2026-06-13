package com.adarsh.hellomom.presentation.dashboard

import com.adarsh.hellomom.core.*
import com.adarsh.hellomom.data.local.entity.*
import com.adarsh.hellomom.domain.repository.MotherHealthData
import com.adarsh.hellomom.domain.repository.PregnancyWeekData

sealed class DashboardIntent : UiIntent {
    object LoadData : DashboardIntent()
    object IncrementKicks : DashboardIntent()
    data class UpdateMood(val mood: String) : DashboardIntent()
    data class UpdateWater(val glasses: Int) : DashboardIntent()
    data class UpdateSleep(val hours: Float) : DashboardIntent()
    data class UpdateWeight(val kg: Float) : DashboardIntent()
    data class UpdateSteps(val steps: Int) : DashboardIntent()
    object Refresh : DashboardIntent()
}

data class DashboardState(
    val user: UserEntity? = null,
    val ownerName: String = "",
    val ownerMobile: String = "",
    val hasFullAccess: Boolean = false,
    val pregnancyWeek: Int = 1,
    val pregnancyDay: Int = 1,
    val trimester: Int = 1,
    val weekData: PregnancyWeekData = PregnancyWeekData(),
    val healthData: MotherHealthData = MotherHealthData(),
    val appointments: List<AppointmentEntity> = emptyList(),
    val medicines: List<MedicineEntity> = emptyList(),
    val symptoms: List<SymptomLogEntity> = emptyList(),
    val familyMembers: List<FamilyMemberEntity> = emptyList(),
    val kickCount: Int = 0,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val syncFailed: Boolean = false,
    val error: String? = null,
    val quote: String = "Your baby is growing and so is your love."
) : UiState

sealed class DashboardEffect : UiEffect {
    data class ShowError(val message: String) : DashboardEffect()
}
