package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.BillingEntity
import kotlinx.coroutines.flow.Flow

interface BillingRepository {
    fun getBills(userId: String): Flow<List<BillingEntity>>
    suspend fun insertBill(bill: BillingEntity): Result<Unit>
    suspend fun updateBill(bill: BillingEntity): Result<Unit>
    suspend fun deleteBill(bill: BillingEntity): Result<Unit>
}
