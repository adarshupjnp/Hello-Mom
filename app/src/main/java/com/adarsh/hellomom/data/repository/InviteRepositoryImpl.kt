package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.domain.repository.Invite
import com.adarsh.hellomom.domain.repository.InviteRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.random.Random

class InviteRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : InviteRepository {

    override suspend fun getInvite(code: String): Result<Invite> {
        return try {
            val doc = firestore.collection("invites").document(code).get().await()
            if (doc.exists()) {
                val invite = doc.toObject(Invite::class.java)
                if (invite != null) Result.success(invite)
                else Result.failure(Exception("Failed to parse invite"))
            } else {
                Result.failure(Exception("Invalid invite code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markInviteAsUsed(code: String): Result<Unit> {
        return try {
            firestore.collection("invites").document(code).update("used", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createInvite(familyId: String, creatorId: String): Result<String> {
        return try {
            val code = generateCode()
            val inviteData = hashMapOf(
                "code" to code,
                "familyId" to familyId,
                "used" to false,
                "creatorId" to creatorId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("invites").document(code).set(inviteData).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
