package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.MealDao
import com.adarsh.hellomom.data.local.dao.WaterIntakeDao
import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import com.adarsh.hellomom.domain.repository.FoodRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(
    private val mealDao: MealDao,
    private val waterIntakeDao: WaterIntakeDao,
    private val firestore: FirebaseFirestore
) : FoodRepository {

    override fun getMeals(userId: String): Flow<List<MealEntity>> {
        return mealDao.getMeals(userId)
    }

    override suspend fun insertMeal(meal: MealEntity): Result<Unit> {
        return try {
            // Offline-first: persist locally first, then push best-effort (Firestore queues offline).
            mealDao.insertMeal(meal)
            SyncLogger.local("ADD meal", "meals", "id=${meal.mealId} userId=${meal.userId} type=${meal.mealType} items=${meal.foodItems}")
            firestore.collection("users").document(meal.userId)
                .collection("meals").document(meal.mealId).set(meal)
            SyncLogger.firebaseWrite("ADD meal", "users/${meal.userId}/meals/${meal.mealId}", "type=${meal.mealType} items=${meal.foodItems}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("ADD meal failed id=${meal.mealId}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMeal(meal: MealEntity): Result<Unit> {
        return try {
            mealDao.updateMeal(meal)
            SyncLogger.local("EDIT meal", "meals", "id=${meal.mealId} userId=${meal.userId} type=${meal.mealType} items=${meal.foodItems}")
            firestore.collection("users").document(meal.userId)
                .collection("meals").document(meal.mealId).set(meal)
            SyncLogger.firebaseWrite("EDIT meal", "users/${meal.userId}/meals/${meal.mealId}", "type=${meal.mealType} items=${meal.foodItems}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("EDIT meal failed id=${meal.mealId}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteMeal(meal: MealEntity): Result<Unit> {
        return try {
            mealDao.deleteMeal(meal.mealId)
            SyncLogger.local("DELETE meal", "meals", "id=${meal.mealId} userId=${meal.userId}")
            firestore.collection("users").document(meal.userId)
                .collection("meals").document(meal.mealId).delete()
            SyncLogger.firebaseWrite("DELETE meal", "users/${meal.userId}/meals/${meal.mealId}", "removed")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("DELETE meal failed id=${meal.mealId}", e)
            Result.failure(e)
        }
    }

    override fun getWaterIntake(userId: String, date: String): Flow<WaterIntakeEntity?> = callbackFlow {
        val subscription = firestore.collection("users").document(userId)
            .collection("water_intake").document(date)
            .addSnapshotListener { snapshot, _ ->
                val water = snapshot?.toObject(WaterIntakeEntity::class.java)
                trySend(water)
            }
        awaitClose { subscription.remove() }
    }
        .onStart { emit(null) }
        .catch { emit(null) }

    override suspend fun updateWaterIntake(waterIntake: WaterIntakeEntity): Result<Unit> {
        return try {
            waterIntakeDao.insertOrUpdate(waterIntake)
            SyncLogger.local("UPDATE water", "water_intake", "userId=${waterIntake.userId} date=${waterIntake.date} glasses=${waterIntake.glassesDrank}/${waterIntake.goalGlasses}")
            
            // 1. Update primary water collection
            firestore.collection("users").document(waterIntake.userId)
                .collection("water_intake").document(waterIntake.date).set(waterIntake).await()
            
            // 2. Also update health_metrics to keep Dashboard in sync
            // Note: We only update waterIntake field in health_metrics
            try {
                firestore.collection("users").document(waterIntake.userId)
                    .collection("health_metrics").document(waterIntake.date)
                    .update("waterIntake", waterIntake.glassesDrank).await()
            } catch (e: Exception) {
                // If document doesn't exist yet, create it with just waterIntake
                firestore.collection("users").document(waterIntake.userId)
                    .collection("health_metrics").document(waterIntake.date)
                    .set(mapOf("waterIntake" to waterIntake.glassesDrank)).await()
            }

            SyncLogger.firebaseWrite("UPDATE water (synced)", "users/${waterIntake.userId}/water_intake/${waterIntake.date}", "glasses=${waterIntake.glassesDrank}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("UPDATE water failed userId=${waterIntake.userId}", e)
            Result.failure(e)
        }
    }
}
