package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.UserDao
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) : UserRepository {

    override fun getUser(userId: String): Flow<UserEntity?> {
        return userDao.getUserById(userId)
    }

    override suspend fun updateUser(user: UserEntity): Result<Unit> {
        return try {
            // Profile pictures are stored as Local URI paths to keep the app free.
            userDao.updateUser(user)
            SyncLogger.local("UPDATE profile", "users", "id=${user.userId} name=${user.fullName} startDate=${user.pregnancyStartDate} dueDate=${user.dueDate}")
            firestore.collection("users").document(user.userId).set(user).await()
            SyncLogger.firebaseWrite("UPDATE profile", "users/${user.userId}", "name=${user.fullName} startDate=${user.pregnancyStartDate} dueDate=${user.dueDate} blood=${user.bloodGroup}")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("UPDATE profile failed for ${user.userId}", e)
            Result.failure(e)
        }
    }
}
