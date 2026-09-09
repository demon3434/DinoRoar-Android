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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request

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
    private val stickerAssetSyncer: StickerAssetSyncer,
    private val okHttpClient: okhttp3.OkHttpClient
) {
    private val TAG = "SyncManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private data class SyncRequest(
        val isManual: Boolean,
        val deferred: CompletableDeferred<SyncState>
    )

    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
    private val pendingRequests = mutableListOf<SyncRequest>()
    private val requestLock = Any()
    private var idleResetJob: Job? = null

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        registerNetworkCallback()
        startSyncConsumer()
    }

    private fun startSyncConsumer() {
        coroutineScope.launch {
            for (signal in triggerChannel) {
                while (true) {
                    val batch = synchronized(requestLock) {
                        if (pendingRequests.isEmpty()) {
                            null
                        } else {
                            val list = ArrayList(pendingRequests)
                            pendingRequests.clear()
                            list
                        }
                    } ?: break

                    val isManual = batch.any { it.isManual }
                    val result = try {
                        doSyncPipeline(isManual)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Fatal error in sync consumer pipeline: ${t.message}", t)
                        val err = SyncState.Error("同步遇到未预期异常: ${t.message ?: "未知错误"}")
                        _syncState.value = err
                        err
                    }
                    for (req in batch) {
                        try {
                            req.deferred.complete(result)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to notify caller deferred: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    Log.i(TAG, "Network restored (onAvailable). Triggering auto-sync for lock pattern and user data...")
                    coroutineScope.launch {
                        try {
                            sync()
                        } catch (e: Exception) {
                            Log.w(TAG, "Auto-sync on network restored failed: ${e.message}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

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

    suspend fun syncUserProfile(): Boolean = withContext(Dispatchers.IO) {
        val token = securePrefs.token ?: return@withContext false
        
        val primaryUrl = securePrefs.serverUrl
        val intranetUrl = securePrefs.intranetUrl
        val extranetUrl = securePrefs.extranetUrl

        val urlsToTry = mutableListOf<String>()
        primaryUrl?.takeIf { it.isNotBlank() }?.let { urlsToTry.add(it) }
        extranetUrl?.takeIf { it.isNotBlank() && !urlsToTry.contains(it) }?.let { urlsToTry.add(it) }
        intranetUrl?.takeIf { it.isNotBlank() && !urlsToTry.contains(it) }?.let { urlsToTry.add(it) }

        if (urlsToTry.isEmpty()) return@withContext false

        val shortClient = okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        for (baseUrl in urlsToTry) {
            try {
                val cleanUrl = baseUrl.removeSuffix("/")
                val request = Request.Builder()
                    .url("$cleanUrl/api/auth/me")
                    .header("Authorization", "Bearer $token")
                    .build()

                shortClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.contains("lock_pattern")) {
                            val patternMatcher = java.util.regex.Pattern.compile("\"lock_pattern\"\\s*:\\s*\"([^\"]+)\"")
                            val matcher = patternMatcher.matcher(bodyStr)
                            if (matcher.find()) {
                                val remotePattern = matcher.group(1)
                                if (!remotePattern.isNullOrBlank()) {
                                    if (securePrefs.serverUrl != baseUrl) {
                                        Log.i(TAG, "Switching active serverUrl to working URL: $baseUrl")
                                        securePrefs.serverUrl = baseUrl
                                    }
                                    val isPatternChanged = remotePattern != securePrefs.lockPattern
                                    if (isPatternChanged) {
                                        Log.i(TAG, "Lock pattern updated remotely: $remotePattern (was ${securePrefs.lockPattern})")
                                        securePrefs.lockPattern = remotePattern
                                        securePrefs.lockVersion = securePrefs.lockVersion + 1
                                    }

                                    if (bodyStr.contains("lock_reset_flag") && bodyStr.contains("default_requested")) {
                                        try {
                                            apiService.updateLockPattern(com.example.dinoroar.network.UserUpdateLock(lock_pattern = securePrefs.lockPattern))
                                            Log.i(TAG, "Confirmed lock reset to server. lock_reset_flag cleared.")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to confirm lock reset to server: ${e.message}")
                                        }
                                    }

                                    return@withContext isPatternChanged
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "syncUserProfile fast probe failed for $baseUrl: ${e.message}")
            }
        }
        return@withContext false
    }

    /**
     * 外部调用的统一同步入口：非阻塞发起、零竞态、绝对不丢更新
     * 所有并发触发自动合并批处理，每个调用方准确挂起等待当前批次执行完成并返回终态
     */
    suspend fun sync(isManual: Boolean = false): SyncState {
        val deferred = CompletableDeferred<SyncState>()
        synchronized(requestLock) {
            pendingRequests.add(SyncRequest(isManual, deferred))
        }
        triggerChannel.trySend(Unit)
        return deferred.await()
    }

    private suspend fun doSyncPipeline(isManual: Boolean): SyncState = withContext(Dispatchers.IO) {
        idleResetJob?.cancel()

        if (securePrefs.token == null) {
            val err = "Cannot sync: User not authenticated."
            Log.e(TAG, err)
            val errState = SyncState.Error(err)
            _syncState.value = errState
            return@withContext errState
        }

        if (!isNetworkConnected()) {
            val err = "No network connection. Sync queued."
            Log.w(TAG, err)
            val errState = SyncState.Error(err)
            _syncState.value = errState
            return@withContext errState
        }

        _syncState.value = SyncState.Syncing

        try {
            // 阶段0：用户个人信息与解锁序列拉取，配置与贴纸拉取
            syncUserProfile()
            syncDinoConfig()
            stickerAssetSyncer.syncStickerInventoryDown()

            // 阶段1：关系人/分类同步（优雅降级内部消化，不腰斩主干数据）
            relationshipSyncer.syncRelationship()

            // 阶段2：日志同步（受 Room withTransaction 事务保护）
            diaryLogSyncer.syncLogs(isManual)

            // 阶段3：附件物理文件上传与MD5秒传同步
            val attachmentResult = attachmentFileSyncer.syncAttachments(isManual)

            // 阶段4：贴纸与蛋能量库存资产同步上报
            stickerAssetSyncer.syncStickerInventoryUp()

            if (!attachmentResult.isAllSuccess) {
                val partialErrMsg = "日记已同步，但有 ${attachmentResult.failedCount} 个多媒体附件未能上传"
                Log.w(TAG, partialErrMsg)
                val partialErrState = SyncState.Error(partialErrMsg)
                _syncState.value = partialErrState
                return@withContext partialErrState
            }

            _syncState.value = SyncState.Success

            // 成功后 3 秒自动平滑复位为 Idle 状态
            idleResetJob?.cancel()
            idleResetJob = coroutineScope.launch {
                delay(3000)
                if (_syncState.value is SyncState.Success) {
                    _syncState.value = SyncState.Idle
                }
            }

            SyncState.Success
        } catch (e: Exception) {
            val errMsg = "Sync exception: ${e.message ?: "Unknown error"}"
            Log.e(TAG, errMsg, e)
            val errState = SyncState.Error(errMsg)
            _syncState.value = errState
            errState
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
                    ExistingWorkPolicy.KEEP,
                    syncWorkRequest
                )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background sync with WorkManager: ${e.message}", e)
        }
    }

    fun isOnline(): Boolean {
        return isNetworkConnected() && securePrefs.token != null
    }

    /**
     * 单篇日记云端强制预拉取代理（Fetch-Before-Edit 机制）
     */
    suspend fun refreshSingleLog(uuid: String): Boolean {
        return diaryLogSyncer.fetchAndPersistLatestLog(uuid)
    }
}
