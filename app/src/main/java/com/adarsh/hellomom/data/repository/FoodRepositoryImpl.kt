package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.MealDao
import com.adarsh.hellomom.data.local.dao.WaterIntakeDao
import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import com.adarsh.hellomom.domain.repository.FoodRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
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

    override fun getWaterIntake(userId: String, date: String): Flow<WaterIntakeEntity?> {
        return waterIntakeDao.getWaterIntake(userId, date)
    }

    override suspend fun updateWaterIntake(waterIntake: WaterIntakeEntity): Result<Unit> {
        return try {
            waterIntakeDao.insertOrUpdate(waterIntake)
            SyncLogger.local("UPDATE water", "water_intake", "userId=${waterIntake.userId} date=${waterIntake.date} glasses=${waterIntake.glassesDrank}/${waterIntake.goalGlasses}")
            firestore.collection("users").document(waterIntake.userId)
                .collection("water_intake").document(waterIntake.date).set(waterIntake)
            SyncLogger.firebaseWrite("UPDATE water", "users/${waterIntake.userId}/water_intake/${waterIntake.date}", "glasses=${waterIntake.glassesDrank}/${waterIntake.goalGlasses}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("UPDATE water failed userId=${waterIntake.userId}", e)
            Result.failure(e)
        }
    }
}
