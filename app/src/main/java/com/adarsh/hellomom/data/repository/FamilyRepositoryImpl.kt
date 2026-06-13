package com.adarsh.hellomom.data.repository

import com.adarsh.hellomom.data.local.dao.FamilyMemberDao
import com.adarsh.hellomom.data.local.dao.UserDao
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import com.adarsh.hellomom.domain.repository.FamilyRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class FamilyRepositoryImpl @Inject constructor(
    private val familyMemberDao: FamilyMemberDao,
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) : FamilyRepository {

    override fun getFamilyMembers(userId: String): Flow<List<FamilyMemberEntity>> = flow {
        // Sync from Firestore first so registered members are always visible
        try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("family_members").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(FamilyMemberEntity::class.java)
            }.forEach { familyMemberDao.insertFamilyMember(it) }
        } catch (_: Exception) {
            // Fall through to local Room data if offline
        }
        emitAll(familyMemberDao.getFamilyMembers(userId))
    }

    override suspend fun inviteMember(member: FamilyMemberEntity): Result<Unit> {
        return try {
            familyMemberDao.insertFamilyMember(member)
            firestore.collection("users").document(member.userId)
                .collection("family_members").document(member.memberId).set(member).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinFamily(familyId: String, userId: String, ownerUserId: String, role: String): Result<Unit> {
        return try {
            val memberData = hashMapOf(
                "userId" to userId,
                "role" to role,
                "joinedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("families").document(familyId)
                .collection("members").document(userId).set(memberData).await()

            // Store the owner's userId (not familyId) so the dashboard can fetch owner's pregnancy data
            firestore.collection("users").document(userId).update("linkedPrimaryUserId", ownerUserId).await()

            // Also update local Room so the app reflects the linked user immediately
            val joiningUser = userDao.getUserByIdOnce(userId)
            joiningUser?.let { localUser ->
                userDao.updateUser(localUser.copy(linkedPrimaryUserId = ownerUserId))
            }

            // Register this user under the owner's family_members so the owner sees them
            // (with phone number for the WhatsApp quick-contact). Skip when the owner joins
            // their own family group during createFamily().
            if (userId != ownerUserId) {
                val member = FamilyMemberEntity(
                    memberId = userId,
                    userId = ownerUserId,
                    name = joiningUser?.fullName ?: "",
                    email = joiningUser?.email ?: "",
                    phoneNumber = joiningUser?.mobileNumber ?: "",
                    role = role.replaceFirstChar { it.uppercase() },
                    permissions = "view",
                    status = "Accepted"
                )
                familyMemberDao.insertFamilyMember(member)
                runCatching {
                    firestore.collection("users").document(ownerUserId)
                        .collection("family_members").document(userId).set(member).await()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFamily(userId: String): Result<String> {
        return try {
            val familyId = UUID.randomUUID().toString()
            val familyData = hashMapOf(
                "familyId" to familyId,
                "creatorId" to userId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("families").document(familyId).set(familyData).await()
            
            // Add creator as owner — owner's linkedPrimaryUserId points to themselves
            joinFamily(familyId, userId, userId, "owner")
            
            Result.success(familyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
