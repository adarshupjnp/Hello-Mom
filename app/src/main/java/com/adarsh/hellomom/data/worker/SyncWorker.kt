package com.adarsh.hellomom.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.adarsh.hellomom.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Two-way sync: push the owner's pending changes and pull the latest owner data
            // into Room so family members always see fresh data on the dashboard.
            syncRepository.syncAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
