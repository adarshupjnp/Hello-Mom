package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.MealEntity
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun getMeals(userId: String): Flow<List<MealEntity>>
    suspend fun insertMeal(meal: MealEntity): Result<Unit>
    suspend fun updateMeal(meal: MealEntity): Result<Unit>
    suspend fun deleteMeal(meal: MealEntity): Result<Unit>
    
    fun getWaterIntake(userId: String, date: String): Flow<WaterIntakeEntity?>
    suspend fun updateWaterIntake(waterIntake: WaterIntakeEntity): Result<Unit>
}
