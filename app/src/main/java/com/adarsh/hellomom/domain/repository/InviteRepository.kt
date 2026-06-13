package com.adarsh.hellomom.domain.repository

data class Invite(
    val code: String = "",
    val familyId: String = "",
    val used: Boolean = false,
    val creatorId: String = "",
    val createdAt: Any? = null
)

interface InviteRepository {
    suspend fun getInvite(code: String): Result<Invite>
    suspend fun markInviteAsUsed(code: String): Result<Unit>
    suspend fun createInvite(familyId: String, creatorId: String): Result<String>
}
