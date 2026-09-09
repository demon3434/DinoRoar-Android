package com.example.dinoroar.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun getSyncManager(): SyncManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val syncManager = entryPoint.getSyncManager()
        
        return try {
            val state = syncManager.sync()
            if (state is SyncState.Success) {
                Result.success()
            } else if (state is SyncState.Error && state.error.contains("User not authenticated")) {
                Log.w("SyncWorker", "Background sync cancelled: user not authenticated.")
                Result.failure()
            } else {
                Log.w("SyncWorker", "Background sync state failed: $state")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync exception: ${e.message}", e)
            Result.retry()
        }
    }
}
