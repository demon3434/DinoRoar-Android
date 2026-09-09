package com.example.dinoroar.data.sync

import android.util.Log
import com.example.dinoroar.data.local.AttachmentDao
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogDao
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.LogPersonDao
import com.example.dinoroar.data.local.LogPersonCrossRef
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.LogCreate
import com.example.dinoroar.network.LogSyncPayload
import com.example.dinoroar.network.LogResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import androidx.room.withTransaction
import com.example.dinoroar.data.local.DinoDatabase
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.local.LogCanvasDao
import com.example.dinoroar.data.local.LogCanvasEntity
import com.example.dinoroar.data.local.CanvasInstanceDao
import com.example.dinoroar.data.local.CanvasInstanceEntity

@Singleton
class DiaryLogSyncer @Inject constructor(
    private val database: DinoDatabase,
    private val logDao: LogDao,
    private val attachmentDao: AttachmentDao,
    private val logPersonDao: LogPersonDao,
    private val logCanvasDao: LogCanvasDao,
    private val canvasInstanceDao: CanvasInstanceDao,
    private val apiService: DinoApiService,
    private val securePrefs: SecurePrefs
) {
    private val TAG = "DiaryLogSyncer"

    suspend fun syncLogs(isManual: Boolean): Set<String> = withContext(Dispatchers.IO) {
        val userId = securePrefs.currentUserId
        // ==================== 阶段二：同步日志库 ====================
        Log.i(TAG, "Syncing log database for user $userId...")
        val unsyncedLocal = logDao.getUnsyncedLogs(userId)
        
        // 健壮性自愈：在本地将老数据中由于版本迁移遗留的空 updatedAt 字段自动订正
        database.withTransaction {
            unsyncedLocal.forEach {
                if (it.updatedAt.isBlank() || it.userId.isBlank()) {
                    val fallbackTime = if (it.incidentDate.isNotBlank()) it.incidentDate else java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                    logDao.insertOrUpdate(it.copy(updatedAt = fallbackTime, userId = userId))
                }
            }
        }

        // 重新从本地数据库加载已被自愈订正的数据以构成最新的同步 payload
        val healedUnsyncedLocal = logDao.getUnsyncedLogs(userId)
        
        val toSyncLogs = healedUnsyncedLocal.filter { 
            !it.isDeleted && (isManual || !it.isLocalOnly)
        }.map {
            val personUuids = logPersonDao.getPersonsForLog(it.uuid).map { p -> p.uuid }
            val logCanvas = logCanvasDao.getLogCanvasByUuid(it.uuid)
            LogCreate(
                uuid = it.uuid,
                title = it.title,
                incident_date = it.incidentDate,
                mood_dino_id = it.moodDinoId,
                content = it.content,
                own_thoughts = it.ownThoughts,
                updated_at = it.updatedAt,
                version = it.version,
                person_uuids = personUuids,
                canvas_instance_id = logCanvas?.canvasInstanceId,
                canvas_aspect_ratio = logCanvas?.canvasAspectRatio ?: "2:1"
            )
        }

        val deletedUuids = unsyncedLocal.filter { it.isDeleted }.map { it.uuid }

        // 2. 发起日志同步请求
        val syncPayload = LogSyncPayload(logs = toSyncLogs, deleted_uuids = deletedUuids)
        val activeServerLogs = apiService.syncLogs(syncPayload)

        // 3. 删除本地已成功上报的已删除日志及其关联关系（先物理删除附件，再清除数据库记录）
        healedUnsyncedLocal.forEach {
            if (it.isDeleted) {
                purgeLogAndAttachments(it.uuid)
            }
        }

        // 4. 合并服务端日志（原子文档 LWW 规则：谁最新全量静默覆盖，0 弹窗打扰）
        val serverUuids = activeServerLogs.map { it.uuid }.toSet()
        
        activeServerLogs.forEach { serverLog ->
            val localLog = logDao.getLogByUuid(serverLog.uuid)
            
            if (localLog == null || localLog.isSynced) {
                // 本地无此日志或本地已同步（本地无未提交修改），云端权威直接覆盖
                persistServerLog(serverLog, userId)
            } else {
                // 本地存在未同步修改（!localLog.isSynced）
                val isContentIdentical = (serverLog.title ?: "") == (localLog.title ?: "") &&
                        serverLog.content == localLog.content &&
                        serverLog.mood_dino_id == localLog.moodDinoId &&
                        (serverLog.own_thoughts ?: "") == (localLog.ownThoughts ?: "")

                val isServerNewer = serverLog.version > localLog.version || 
                        serverLog.updated_at >= localLog.updatedAt

                if (isServerNewer) {
                    // 云端版本号更高或更新时间更新：以日记整体最新版本为准，全量静默覆盖本地，自动收敛消除冲突
                    persistServerLog(serverLog, userId)
                    Log.i(TAG, "Log ${serverLog.uuid}: Server version is newer (${serverLog.version} >= ${localLog.version}). Silently overwritten with server authority.")
                } else if (isContentIdentical) {
                    // 本地内容与云端已一致，直接安全标记已同步
                    val syncedLog = localLog.copy(
                        serverId = serverLog.id,
                        version = serverLog.version,
                        updatedAt = serverLog.updated_at,
                        isSynced = true,
                        isConflict = false,
                        isLocalOnly = false,
                        serverContent = null,
                        serverUpdatedAt = null,
                        serverVersion = 0
                    )
                    database.withTransaction {
                        logDao.insertOrUpdate(syncedLog)
                    }
                    Log.i(TAG, "Log ${serverLog.uuid} content is identical to server. Marked isSynced = true.")
                } else {
                    // 本地修改时间更新且内容不一致：本地是最新编辑的成果，保留本地，等待本轮推送云端自增落库
                    Log.i(TAG, "Log ${serverLog.uuid}: Local version is newer (${localLog.updatedAt} > ${serverLog.updated_at}). Keeping local modifications to push upstream.")
                }
            }

            // 增量同步附件 metadata
            syncAttachmentsMetadata(serverLog, userId)
        }

        // 5. 层叠物理删除：删除本地不在服务端活跃列表中的日志
        val allLocalLogs = logDao.getAllActiveLogs(userId)
        allLocalLogs.forEach { local ->
            if (local.isSynced && !serverUuids.contains(local.uuid)) {
                Log.i(TAG, "Cascading deletion for log: ${local.uuid}")
                purgeLogAndAttachments(local.uuid)
            }
        }

        return@withContext serverUuids
    }

    /**
     * 安全物理级联清理：先物理删除本地图片/视频文件，再彻底清理数据库中的日志及其外键关联记录
     */
    private suspend fun purgeLogAndAttachments(logUuid: String) {
        val attachments = attachmentDao.getAttachmentsForLog(logUuid)
        attachments.forEach { att ->
            att.localFilePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete physical file for attachment ${att.uuid}: ${e.message}")
                }
            }
        }
        database.withTransaction {
            attachmentDao.deleteAttachmentsForLog(logUuid)
            logDao.hardDeleteLog(logUuid)
            logPersonDao.deleteCrossRefsForLog(logUuid)
            logCanvasDao.deleteLogCanvas(logUuid)
        }
    }

    private suspend fun persistServerLog(serverLog: LogResponse, userId: String) {
        val logEntity = LogEntity(
            uuid = serverLog.uuid,
            serverId = serverLog.id,
            userId = userId,
            title = serverLog.title,
            incidentDate = serverLog.incident_date,
            moodDinoId = serverLog.mood_dino_id,
            content = serverLog.content,
            ownThoughts = serverLog.own_thoughts,
            createdAt = serverLog.created_at,
            updatedAt = serverLog.updated_at,
            isDeleted = false,
            isSynced = true,
            version = serverLog.version,
            isConflict = false,
            serverContent = null,
            serverUpdatedAt = null,
            serverVersion = 0,
            isLocalOnly = false
        )

        val canvasInstanceId = serverLog.canvas_instance_id
        val imageUrl = serverLog.canvas_image_url
        val virtualInstance = if (canvasInstanceId != null && !imageUrl.isNullOrBlank()) {
            val dbInstance = canvasInstanceDao.getInstanceById(canvasInstanceId)
            if (dbInstance == null) {
                CanvasInstanceEntity(
                    id = canvasInstanceId,
                    canvasSetId = 3001,
                    aspectRatio = serverLog.canvas_aspect_ratio,
                    imageUrl = imageUrl,
                    width = 1440,
                    height = 720,
                    isActive = true,
                    isDeleted = false,
                    createdAt = serverLog.created_at
                )
            } else null
        } else null

        val logCanvasEntity = LogCanvasEntity(
            logUuid = serverLog.uuid,
            canvasInstanceId = serverLog.canvas_instance_id,
            canvasAspectRatio = serverLog.canvas_aspect_ratio
        )

        val refs = serverLog.person_uuids.map { personUuid ->
            LogPersonCrossRef(logUuid = serverLog.uuid, personUuid = personUuid, userId = userId)
        }

        database.withTransaction {
            logDao.insertOrUpdate(logEntity)
            virtualInstance?.let { canvasInstanceDao.insertOrUpdateAll(listOf(it)) }
            logCanvasDao.insertOrUpdate(logCanvasEntity)
            logPersonDao.deleteCrossRefsForLog(serverLog.uuid)
            logPersonDao.insertCrossRefs(refs)
        }
    }

    /**
     * 同步并校准日记附件元数据与云端下载 URL
     */
    private suspend fun syncAttachmentsMetadata(serverLog: LogResponse, userId: String) {
        database.withTransaction {
            serverLog.attachments.forEach { serverAtt ->
                val downloadUrl = "api/attachments/download/${serverAtt.uuid}"
                val localAtt = attachmentDao.getAttachmentByUuid(serverAtt.uuid)
                if (localAtt == null) {
                    val attEntity = AttachmentEntity(
                        uuid = serverAtt.uuid,
                        userId = userId,
                        logUuid = serverLog.uuid,
                        fileName = serverAtt.file_name,
                        mimeType = serverAtt.mime_type,
                        fileSize = serverAtt.file_size,
                        localFilePath = null,
                        remoteUrl = downloadUrl,
                        createdAt = serverAtt.created_at,
                        isDeleted = false,
                        isSynced = true,
                        title = serverAtt.title
                    )
                    attachmentDao.insertOrUpdate(attEntity)
                } else if (localAtt.isDeleted) {
                    // Skipping server attachment because it was deleted locally
                } else {
                    val shouldUpdateTitle = localAtt.isSynced && localAtt.title != serverAtt.title
                    val shouldUpdateRemoteUrl = localAtt.remoteUrl.isNullOrBlank()
                    if (shouldUpdateTitle || shouldUpdateRemoteUrl) {
                        attachmentDao.insertOrUpdate(
                            localAtt.copy(
                                title = if (shouldUpdateTitle) serverAtt.title else localAtt.title,
                                remoteUrl = if (shouldUpdateRemoteUrl) downloadUrl else localAtt.remoteUrl
                            )
                        )
                        Log.i(TAG, "Aligned attachment ${serverAtt.uuid} metadata (remoteUrl/title) from server.")
                    }
                }
            }
        }
    }

    /**
     * 单篇日记云端强制预拉取与 ACID 落库（Fetch-Before-Edit 机制）
     * 在进入日记编辑页面前调用，在线时先从服务端获取权威最新记录并写入 Room，杜绝在旧缓存上编辑。
     * 自卫加固：若本地存在尚未同步上报的心血修改（!localLog.isSynced），绝不被陈旧的云端数据覆盖！
     */
    suspend fun fetchAndPersistLatestLog(uuid: String): Boolean = withContext(Dispatchers.IO) {
        val userId = securePrefs.currentUserId
        val token = securePrefs.token ?: return@withContext false
        try {
            val localLog = logDao.getLogByUuid(uuid)
            val serverLog = apiService.getLogDetail(uuid)

            if (localLog != null && !localLog.isSynced) {
                val isServerReallyNewer = serverLog.version > localLog.version || 
                        serverLog.updated_at > localLog.updatedAt

                if (!isServerReallyNewer) {
                    // 本地心血较新或版本不落后：服务端此时存的还是旧版本（本地修改尚未推上云）
                    // 坚决阻断覆盖，保护本地心血！
                    Log.i(TAG, "Fetch-Before-Edit: Protected local unsynced draft for $uuid (local v${localLog.version} >= server v${serverLog.version}).")
                    return@withContext true
                }
            }

            persistServerLog(serverLog, userId)
            syncAttachmentsMetadata(serverLog, userId)
            Log.i(TAG, "Fetch-Before-Edit: Successfully fetched and persisted latest log $uuid (version ${serverLog.version}) from server.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fetch-Before-Edit: Failed to fetch latest log $uuid from server, will fallback to local Room: ${e.message}")
            false
        }
    }
}
