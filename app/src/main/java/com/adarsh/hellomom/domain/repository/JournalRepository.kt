package com.adarsh.hellomom.domain.repository

import com.adarsh.hellomom.data.local.entity.JournalEntity
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    fun getEntries(userId: String): Flow<List<JournalEntity>>
    suspend fun insertEntry(entry: JournalEntity): Result<Unit>
    suspend fun updateEntry(entry: JournalEntity): Result<Unit>
    suspend fun deleteEntry(entry: JournalEntity): Result<Unit>
}
