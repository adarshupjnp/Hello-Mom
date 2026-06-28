package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class PregnancyWeekData(
    val week: Int = 1,
    val babySize: String = "",
    val babyWeight: String = "",
    val babyLength: String = "",
    val organDevelopment: String = "",
    val weeklyMilestone: String = "",
    val babyIllustrationUrl: String? = null
)

data class MotherHealthData(
    val mood: String = "Neutral",
    val sleepHours: Float = 0f,
    val waterIntake: Int = 0,
    val steps: Int = 0,
    val weight: Float = 0f
)

interface DashboardRepository {
    fun getPregnancyWeekData(week: Int): Flow<PregnancyWeekData>
    fun getMotherHealthData(userId: String): Flow<MotherHealthData>
    suspend fun updateMotherHealthData(userId: String, healthData: MotherHealthData): Result<Unit>
    fun getDailyKickCount(userId: String): Flow<Int>
    suspend fun incrementKickCount(userId: String): Result<Unit>
    fun getConnectedFamilyMembers(userId: String): Flow<List<FamilyMemberEntity>>
    fun getUpcomingAppointments(userId: String): Flow<List<AppointmentEntity>>
    fun getMedicinesToday(userId: String): Flow<List<MedicineEntity>>
    fun getRecentSymptoms(userId: String): Flow<List<SymptomLogEntity>>
    suspend fun updateMemberLocation(ownerId: String, memberId: String, lat: Double?, lon: Double?, time: Long?): Result<Unit>
}
