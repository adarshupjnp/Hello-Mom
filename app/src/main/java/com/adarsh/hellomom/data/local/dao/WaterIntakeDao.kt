package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterIntakeDao {
    @Query("SELECT * FROM water_intake WHERE userId = :userId AND date = :date")
    fun getWaterIntake(userId: String, date: String): Flow<WaterIntakeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(waterIntake: WaterIntakeEntity)
}
