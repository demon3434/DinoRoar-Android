package com.example.dinoroar.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.dinoroar.data.local.DinoConfigDao
import com.example.dinoroar.data.local.DinoConfigEntity
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    object Success : SyncState
    data class Error(val error: String) : SyncState
}

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: DinoApiService,
    private val securePrefs: SecurePrefs,
    private val dinoConfigDao: DinoConfigDao,
    private val relationshipSyncer: RelationshipSyncer,
    private val diaryLogSyncer: DiaryLogSyncer,
    private val attachmentFileSyncer: AttachmentFileSyncer,
    private val stickerAssetSyncer: StickerAssetSyncer
) {
    private val TAG = "SyncManager"

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncDinoConfig() = withContext(Dispatchers.IO) {
        try {
            val serverConfigs = apiService.getDinoConfig()
            val entities = serverConfigs.map { dto ->
                DinoConfigEntity(
                    id = dto.id,
                    legacyKey = dto.legacy_key,
                    name = dto.name,
                    moodLabel = dto.mood_label,
                    moodTip = dto.mood_tip,
                    imageUrl = dto.image_url,
                    moodScore = dto.mood_score,
                    sortOrder = dto.sort_order,
                    isActive = dto.is_active
                )
            }
            dinoConfigDao.insertOrUpdateAll(entities)
            Log.i(TAG, "Dinosaur configs synced successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Dinosaur config synchronization failed: ${e.message}", e)
        }
    }

    suspend fun sync(isManual: Boolean = false): SyncState = withContext(Dispatchers.IO) {
        if (securePrefs.token == null) {
            val err = "Cannot sync: User not authenticated."
            Log.e(TAG, err)
            return@withContext SyncState.Error(err)
        }

        if (!isNetworkConnected()) {
            val err = "No network connection. Sync queued."
            Log.w(TAG, err)
            return@withContext SyncState.Error(err)
        }

        _syncState.value = SyncState.Syncing

        try {
            // 阶段0：配置与贴纸拉取
            syncDinoConfig()
            stickerAssetSyncer.syncStickerInventoryDown()

            // 阶段1：关系人/分类同步
            relationshipSyncer.syncRelationship()

            // 阶段2：日志同步
            diaryLogSyncer.syncLogs(isManual)

            // 阶段3：附件物理文件上传与MD5秒传同步
            attachmentFileSyncer.syncAttachments(isManual)

            // 阶段4：贴纸与蛋能量库存资产同步上报
            stickerAssetSyncer.syncStickerInventoryUp()

            _syncState.value = SyncState.Success
            SyncState.Success
        } catch (e: Exception) {
            val errMsg = "Sync exception: ${e.message ?: "Unknown error"}"
            Log.e(TAG, errMsg, e)
            _syncState.value = SyncState.Error(errMsg)
            SyncState.Error(errMsg)
        }
    }

    fun scheduleBackgroundSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "DinoRoarSyncWork",
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest
                )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background sync with WorkManager: ${e.message}", e)
        }
    }
}
