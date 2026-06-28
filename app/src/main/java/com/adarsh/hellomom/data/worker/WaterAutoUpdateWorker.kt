package com.adarsh.hellomom.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.adarsh.hellomom.domain.repository.DashboardRepository
import com.adarsh.hellomom.domain.repository.MotherHealthData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Worker that runs daily at 7:00 PM. If the owner has not logged at least 5 glasses
 * of water today, it automatically sets the intake to 5 (average value).
 */
@HiltWorker
class WaterAutoUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val currentUser = authRepository.getCurrentUser().firstOrNull() ?: return Result.success()
        val userId = currentUser.userId

        // Only process for the owner
        val isOwner = (currentUser.fullName.lowercase().contains("adarsh") || 
                      currentUser.fullName.lowercase().contains("riya") ||
                      currentUser.email.lowercase().contains("adarsh") ||
                      currentUser.email.lowercase().contains("riya"))

        if (!isOwner) return Result.success()

        val healthData = dashboardRepository.getMotherHealthData(userId).firstOrNull() ?: MotherHealthData()
        
        if (healthData.waterIntake < 5) {
            dashboardRepository.updateMotherHealthData(userId, healthData.copy(waterIntake = 5))
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "WaterAutoUpdateWorker"

        fun schedule(context: Context) {
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            // Set target time to 7:00 PM today
            calendar.set(Calendar.HOUR_OF_DAY, 19)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            
            if (calendar.timeInMillis <= now) {
                // If 7 PM has already passed, schedule for tomorrow
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val delay = calendar.timeInMillis - now

            val request = PeriodicWorkRequestBuilder<WaterAutoUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
