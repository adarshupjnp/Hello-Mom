package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.ContractionEntity
import kotlinx.coroutines.flow.Flow

interface ContractionRepository {
    fun getContractions(userId: String): Flow<List<ContractionEntity>>
    suspend fun getLatestContraction(userId: String): ContractionEntity?
    suspend fun insertContraction(contraction: ContractionEntity): Result<Unit>
    suspend fun deleteContraction(contraction: ContractionEntity): Result<Unit>
}
