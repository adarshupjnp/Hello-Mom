package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.MealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE userId = :userId AND isDeleted = 0")
    fun getMeals(userId: String): Flow<List<MealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("UPDATE meals SET isDeleted = 1 WHERE mealId = :mealId")
    suspend fun deleteMeal(mealId: String)

    // Reconciliation after a pull: drop rows deleted remotely; keep unpushed PENDING edits.
    @Query("DELETE FROM meals WHERE userId = :userId AND syncStatus != 'PENDING' AND mealId NOT IN (:keepIds)")
    suspend fun deleteMealsNotIn(userId: String, keepIds: List<String>)
}
