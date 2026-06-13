package com.adarsh.hellomom.core

import com.adarsh.hellomom.core.utils.SyncLogger
import com.adarsh.hellomom.data.local.dao.UserDao
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import com.adarsh.hellomom.data.local.entity.UserEntity
import com.adarsh.hellomom.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Role-Based Access Control (RBAC).
 *
 * Owner accounts (Riya / Adarsh) get full read-write access to their own pregnancy data.
 * Every other user is a read-only family member who views the linked owner's data.
 *
 * Centralising the role check here replaces the `fullName.contains(...)` snippets that were
 * duplicated across the dashboard, reminder, sync, login and register code paths.
 */
@Singleton
class RoleManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore
) {
    /** Reactive owner flag for the currently logged-in user. */
    val isOwner: Flow<Boolean> = authRepository.getCurrentUser().map { isOwnerName(it?.fullName) }

    /**
     * Resolve the caller's role and the userId whose pregnancy data should be shown:
     * owners see their own data, family members see their linked owner's data.
     *
     * For family members this is now self-healing: the persisted [UserEntity.linkedPrimaryUserId]
     * is validated against Firestore (it must point at a real owner doc that actually has a
     * pregnancyStartDate). If the link is missing or stale (e.g. an old familyId UUID) we
     * re-discover the owner by name and persist the corrected link locally and remotely.
     */
    suspend fun resolveAccess(): AccessInfo = withContext(Dispatchers.IO) {
        val user = authRepository.getCurrentUser().first() ?: run {
            SyncLogger.resolve("resolveAccess: no logged-in user")
            return@withContext AccessInfo()
        }

        if (isOwnerName(user.fullName)) {
            SyncLogger.resolve(
                "OWNER '${user.fullName}' (id=${user.userId}) → activeUserId=${user.userId}, " +
                    "pregnancyStartDate=${user.pregnancyStartDate}"
            )
            return@withContext AccessInfo(
                isOwner = true,
                activeUserId = user.userId,
                user = user,
                owner = user
            )
        }

        // --- Family member: resolve & validate the owner whose data we display. ---
        var ownerId = user.linkedPrimaryUserId
        var ownerDoc = if (!ownerId.isNullOrEmpty()) fetchOwnerDoc(ownerId) else null
        val linkValid = ownerDoc != null && isOwnerName(ownerDoc.fullName) && ownerDoc.pregnancyStartDate != null

        SyncLogger.resolve(
            "FAMILY '${user.fullName}' (id=${user.userId}) storedLink=$ownerId " +
                "linkValid=$linkValid ownerName=${ownerDoc?.fullName} " +
                "ownerStartDate=${ownerDoc?.pregnancyStartDate}"
        )

        if (!linkValid) {
            val discovered = findOwner()
            if (discovered != null) {
                ownerId = discovered.userId
                ownerDoc = discovered
                SyncLogger.resolve("FAMILY re-discovered owner '${discovered.fullName}' id=${discovered.userId}")
                // Persist the corrected link locally and remotely so we don't re-scan next time.
                runCatching { userDao.insertUser(user.copy(linkedPrimaryUserId = ownerId)) }
                runCatching {
                    firestore.collection("users").document(user.userId)
                        .update("linkedPrimaryUserId", ownerId).await()
                    SyncLogger.firebaseWrite("UPDATE link", "users/${user.userId}", "linkedPrimaryUserId=$ownerId")
                }
            } else {
                SyncLogger.warn("FAMILY could not discover any owner with a pregnancyStartDate")
            }
        }

        // Cache the resolved owner profile locally so the dashboard's Room-backed week flow can emit it.
        ownerDoc?.let { runCatching { userDao.insertUser(it) } }

        // Make sure this family member is registered under the owner's family_members so the owner
        // sees their contact details (name + phone for WhatsApp) on the dashboard. This covers the
        // self-healing path where the member never joined via an invite code, and also refreshes
        // contact details on every login.
        ownerId?.let { registerSelfUnderOwner(user, it) }

        AccessInfo(
            isOwner = false,
            activeUserId = ownerId ?: user.userId,
            user = user,
            owner = ownerDoc
        )
    }

    /**
     * Upsert the family member's contact card under `users/{ownerId}/family_members/{memberId}`.
     * If the record already exists (e.g. created during an invite-join with a specific role) we only
     * refresh the contact fields so we don't clobber the role/permissions chosen at join time.
     */
    private suspend fun registerSelfUnderOwner(user: UserEntity, ownerId: String) {
        if (ownerId.isEmpty() || ownerId == user.userId) return
        runCatching {
            val ref = firestore.collection("users").document(ownerId)
                .collection("family_members").document(user.userId)
            val existing = ref.get().await()
            if (existing.exists()) {
                ref.update(
                    mapOf(
                        "name" to user.fullName,
                        "email" to user.email,
                        "phoneNumber" to user.mobileNumber,
                        "memberId" to user.userId,
                        "userId" to ownerId,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            } else {
                val member = FamilyMemberEntity(
                    memberId = user.userId,
                    userId = ownerId,
                    name = user.fullName,
                    email = user.email,
                    phoneNumber = user.mobileNumber,
                    role = "Family Member",
                    permissions = "view",
                    status = "Accepted"
                )
                ref.set(member).await()
            }
            SyncLogger.firebaseWrite(
                "REGISTER family contact", "users/$ownerId/family_members/${user.userId}",
                "name=${user.fullName} phone=${user.mobileNumber}"
            )
        }.onFailure { SyncLogger.warn("Failed to register family contact under owner", it) }
    }

    /** Fetch a single user document from Firestore (returns null if missing / offline). */
    private suspend fun fetchOwnerDoc(userId: String): UserEntity? = runCatching {
        firestore.collection("users").document(userId).get().await()
            .toObject(UserEntity::class.java)
            ?.also { SyncLogger.firebaseRead("GET owner", "users/$userId", "name=${it.fullName} startDate=${it.pregnancyStartDate}") }
    }.getOrNull()

    /**
     * Locate the pregnancy owner by the hardcoded name convention (adarsh / riya).
     * Prefers an owner doc that actually has a pregnancyStartDate so the dashboard week is correct.
     */
    private suspend fun findOwner(): UserEntity? = runCatching {
        val owners = firestore.collection("users").get().await().documents
            .filter { isOwnerName(it.getString("fullName")) }
            .mapNotNull { it.toObject(UserEntity::class.java) }
        SyncLogger.firebaseRead("SCAN owners", "users", "found=${owners.size} names=${owners.map { it.fullName }}")
        owners.firstOrNull { it.pregnancyStartDate != null } ?: owners.firstOrNull()
    }.getOrNull()

    companion object {
        /** Owner accounts are identified by the hardcoded name convention. */
        fun isOwnerName(fullName: String?): Boolean {
            val n = (fullName ?: "").lowercase()
            return n.contains("adarsh") || n.contains("riya")
        }
    }
}

/** Result of [RoleManager.resolveAccess]: who the user is and whose data to display. */
data class AccessInfo(
    val isOwner: Boolean = false,
    val activeUserId: String = "",
    val user: UserEntity? = null,
    /** The resolved owner profile (own profile for owners, linked owner for family). */
    val owner: UserEntity? = null
)
