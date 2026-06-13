package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

interface FamilyRepository {
    fun getFamilyMembers(userId: String): Flow<List<FamilyMemberEntity>>
    suspend fun inviteMember(member: FamilyMemberEntity): Result<Unit>
    suspend fun joinFamily(familyId: String, userId: String, ownerUserId: String, role: String = "member"): Result<Unit>
    suspend fun createFamily(userId: String): Result<String>
}
