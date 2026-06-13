package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<UserEntity>
    suspend fun loginWithGoogle(idToken: String): Result<UserEntity>
    suspend fun register(user: UserEntity, password: String): Result<UserEntity>
    suspend fun logout(): Result<Unit>
    fun getCurrentUser(): Flow<UserEntity?>
    suspend fun resetPassword(email: String): Result<Unit>
}
