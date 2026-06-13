package com.adarsh.hellomom.data.local.dao

import androidx.room.*
import com.adarsh.hellomom.data.local.entity.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members WHERE userId = :userId")
    fun getFamilyMembers(userId: String): Flow<List<FamilyMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMember(member: FamilyMemberEntity)

    @Query("DELETE FROM family_members WHERE memberId = :memberId")
    suspend fun deleteFamilyMember(memberId: String)

    // Reconciliation after a pull: drop members removed on another device.
    @Query("DELETE FROM family_members WHERE userId = :userId AND memberId NOT IN (:keepIds)")
    suspend fun deleteFamilyMembersNotIn(userId: String, keepIds: List<String>)
}
