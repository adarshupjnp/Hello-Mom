package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.AppDatabase
import com.adarsh.hellomom.data.local.PreferenceManager
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase,
    private val preferenceManager: PreferenceManager
) : AuthRepository {

    private val userDao = database.userDao()

    override suspend fun login(email: String, password: String): Result<UserEntity> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Login failed"))
            
            // Fetch full user data from Firestore
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            val remoteUser = doc.toObject(UserEntity::class.java)
            SyncLogger.firebaseRead("LOGIN fetch", "users/${firebaseUser.uid}", "found=${remoteUser != null} name=${remoteUser?.fullName} startDate=${remoteUser?.pregnancyStartDate}")

            val user = remoteUser ?: UserEntity(
                userId = firebaseUser.uid,
                fullName = firebaseUser.displayName ?: "",
                dob = 0,
                mobileNumber = "",
                email = email,
                profilePicture = null,
                pregnancyStartDate = null,
                dueDate = null,
                bloodGroup = null,
                emergencyContact = null,
                doctorName = null,
                hospitalName = null,
                weight = null,
                height = null,
                allergies = null,
                linkedPrimaryUserId = null
            )
            userDao.insertUser(user)
            SyncLogger.local("LOGIN user", "users", "id=${user.userId} name=${user.fullName} startDate=${user.pregnancyStartDate}")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<UserEntity> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Google Sign-In failed"))
            
            // Fetch full user data from Firestore
            val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
            val remoteUser = doc.toObject(UserEntity::class.java)

            val user = remoteUser ?: UserEntity(
                userId = firebaseUser.uid,
                fullName = firebaseUser.displayName ?: "",
                dob = 0,
                mobileNumber = "",
                email = firebaseUser.email ?: "",
                profilePicture = firebaseUser.photoUrl?.toString(),
                pregnancyStartDate = null,
                dueDate = null,
                bloodGroup = null,
                emergencyContact = null,
                doctorName = null,
                hospitalName = null,
                weight = null,
                height = null,
                allergies = null,
                linkedPrimaryUserId = null
            )
            userDao.insertUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(user: UserEntity, password: String): Result<UserEntity> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(user.email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Registration failed"))
            val newUser = user.copy(
                userId = firebaseUser.uid,
                linkedPrimaryUserId = null
            )
            userDao.insertUser(newUser)
            SyncLogger.local("REGISTER user", "users", "id=${newUser.userId} name=${newUser.fullName} email=${newUser.email}")

            // Create the Firestore user doc immediately so owner-discovery and family links work
            // even before the user reaches the profile screen. ProfileCreation will fill the rest.
            try {
                firestore.collection("users").document(newUser.userId).set(newUser).await()
                SyncLogger.firebaseWrite("REGISTER user", "users/${newUser.userId}", "name=${newUser.fullName} email=${newUser.email}")
            } catch (e: Exception) {
                SyncLogger.warn("REGISTER: failed to write user doc to Firestore (will retry on profile save)", e)
            }
            Result.success(newUser)
        } catch (e: Exception) {
            SyncLogger.error("REGISTER failed for ${user.email}", e)
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val previousUid = firebaseAuth.currentUser?.uid
            firebaseAuth.signOut()
            withContext(Dispatchers.IO) {
                // Wipe ALL local Room data so a different account (e.g. owner → family) signing in
                // on the same device starts clean and re-syncs everything fresh from Firebase.
                database.clearAllTables()
                preferenceManager.clear()
            }
            SyncLogger.info("LOGOUT uid=$previousUid → cleared all local Room tables & preferences")
            Result.success(Unit)
        } catch (e: Exception) {
            SyncLogger.error("LOGOUT failed", e)
            Result.failure(e)
        }
    }

    override fun getCurrentUser(): Flow<UserEntity?> {
        val uid = firebaseAuth.currentUser?.uid ?: ""
        return userDao.getUserById(uid)
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
