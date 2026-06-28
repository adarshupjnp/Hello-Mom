package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.*
import com.adarsh.hellomom.data.local.entity.*
import com.adarsh.hellomom.domain.repository.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DashboardRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val appointmentDao: AppointmentDao,
    private val medicineDao: MedicineDao,
    private val symptomDao: SymptomDao,
    private val waterIntakeDao: WaterIntakeDao,
    private val familyMemberDao: FamilyMemberDao
) : DashboardRepository {

    override fun getPregnancyWeekData(week: Int): Flow<PregnancyWeekData> = callbackFlow {
        val subscription = firestore.collection("pregnancy_progress")
            .document(week.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fail silently or log
                    return@addSnapshotListener
                }
                val data = snapshot?.toObject(PregnancyWeekData::class.java) ?: PregnancyWeekData(
                    week = week,
                    babySize = "Papaya",
                    babyWeight = "600g",
                    babyLength = "30cm",
                    organDevelopment = "Lungs are developing surfactant.",
                    weeklyMilestone = "Your baby can now hear your heartbeat and voice."
                )
                trySend(data)
            }
        awaitClose { subscription.remove() }
    }

    override fun getMotherHealthData(userId: String): Flow<MotherHealthData> = callbackFlow {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        
        var currentMetrics = MotherHealthData()
        var currentWater = 0

        fun emitCombined() {
            trySend(currentMetrics.copy(waterIntake = currentWater))
        }

        // Listen to health metrics (steps, sleep, mood, weight)
        val metricsSubscription = firestore.collection("users").document(userId)
            .collection("health_metrics").document(today)
            .addSnapshotListener { snapshot, _ ->
                currentMetrics = snapshot?.toObject(MotherHealthData::class.java) ?: MotherHealthData()
                emitCombined()
            }

        // Listen to water intake (to keep synchronized with Nutrition screen)
        val waterSubscription = firestore.collection("users").document(userId)
            .collection("water_intake").document(today)
            .addSnapshotListener { snapshot, _ ->
                currentWater = snapshot?.getLong("glassesDrank")?.toInt() ?: 0
                emitCombined()
            }

        awaitClose { 
            metricsSubscription.remove()
            waterSubscription.remove()
        }
    }
        .onStart { emit(MotherHealthData()) }
        .catch { emit(MotherHealthData()) }

    override suspend fun updateMotherHealthData(userId: String, healthData: MotherHealthData): Result<Unit> {
        return try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            
            // 1. Update health metrics (excluding waterIntake which has its own source of truth)
            firestore.collection("users").document(userId)
                .collection("health_metrics").document(today)
                .set(healthData).await()
            
            // 2. Update water intake in its dedicated collection to keep Nutrition screen in sync
            val waterIntake = WaterIntakeEntity(
                date = today,
                userId = userId,
                glassesDrank = healthData.waterIntake
            )
            firestore.collection("users").document(userId)
                .collection("water_intake").document(today).set(waterIntake).await()
            
            // 3. Update the weight in the root user document
            if (healthData.weight > 0f) {
                firestore.collection("users").document(userId)
                    .update("weight", healthData.weight).await()
            }

            SyncLogger.firebaseWrite("UPDATE health+water", "users/$userId/health_metrics/$today", "water=${healthData.waterIntake} steps=${healthData.steps}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("UPDATE health failed userId=$userId", e)
            Result.failure(e)
        }
    }
  override fun getDailyKickCount(userId: String): Flow<Int> = callbackFlow {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val subscription = firestore.collection("users").document(userId)
            .collection("kicks").document(today)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val count = snapshot?.getLong("count")?.toInt() ?: 0
                trySend(count)
            }
        awaitClose { subscription.remove() }
    }
        // Same reasoning as getMotherHealthData: never let the dashboard combine() stall offline.
        .onStart { emit(0) }
        .catch { emit(0) }

    override suspend fun incrementKickCount(userId: String): Result<Unit> {
        return try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val docRef = firestore.collection("users").document(userId)
                .collection("kicks").document(today)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentCount = snapshot.getLong("count") ?: 0
                transaction.set(docRef, mapOf("count" to currentCount + 1, "date" to today))
            }.await()
            SyncLogger.firebaseWrite("INCREMENT kicks", "users/$userId/kicks/$today", "+1")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("INCREMENT kicks failed userId=$userId", e)
            Result.failure(e)
        }
    }

    override fun getConnectedFamilyMembers(userId: String): Flow<List<FamilyMemberEntity>> {
        return familyMemberDao.getFamilyMembers(userId)
    }

    override fun getUpcomingAppointments(userId: String): Flow<List<AppointmentEntity>> {
        return appointmentDao.getAppointments(userId)
    }

    override fun getMedicinesToday(userId: String): Flow<List<MedicineEntity>> {
        return medicineDao.getMedicines(userId)
    }

    override fun getRecentSymptoms(userId: String): Flow<List<SymptomLogEntity>> {
        return symptomDao.getSymptomLogs(userId)
    }
}
