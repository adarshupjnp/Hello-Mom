package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUser(userId: String): Flow<UserEntity?>
    suspend fun updateUser(user: UserEntity): Result<Unit>
}
