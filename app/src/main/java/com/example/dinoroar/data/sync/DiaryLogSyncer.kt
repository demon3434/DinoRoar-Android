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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.local.LogCanvasDao
import com.example.dinoroar.data.local.LogCanvasEntity
import com.example.dinoroar.data.local.CanvasInstanceDao
import com.example.dinoroar.data.local.CanvasInstanceEntity

@Singleton
class DiaryLogSyncer @Inject constructor(
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
        unsyncedLocal.forEach {
            if (it.updatedAt.isBlank() || it.userId.isBlank()) {
                val fallbackTime = if (it.incidentDate.isNotBlank()) it.incidentDate else java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
                logDao.insertOrUpdate(it.copy(updatedAt = fallbackTime, userId = userId))
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

        // 3. 删除本地已成功上报的已删除日志及其关联关系
        healedUnsyncedLocal.forEach {
            if (it.isDeleted) {
                logDao.hardDeleteLog(it.uuid)
                attachmentDao.deleteAttachmentsForLog(it.uuid)
                logPersonDao.deleteCrossRefsForLog(it.uuid)
                logCanvasDao.deleteLogCanvas(it.uuid)
            }
        }

        // 4. 合并服务端日志（LWW最后写入胜出规则）
        val serverUuids = activeServerLogs.map { it.uuid }.toSet()
        
        activeServerLogs.forEach { serverLog ->
            val localLog = logDao.getLogByUuid(serverLog.uuid)
            
            val isConflictDetected = localLog != null && !localLog.isSynced && 
                    serverLog.version > localLog.version &&
                    (serverLog.title != localLog.title || 
                     serverLog.content != localLog.content || 
                     serverLog.mood_dino_id != localLog.moodDinoId ||
                     serverLog.own_thoughts != localLog.ownThoughts)

            if (isConflictDetected) {
                // 检测到冲突！保留本地修改，但记录云端冲突数据
                val conflictedLog = localLog.copy(
                    isConflict = true,
                    serverContent = serverLog.content,
                    serverUpdatedAt = serverLog.updated_at,
                    serverVersion = serverLog.version,
                    isLocalOnly = false
                )
                logDao.insertOrUpdate(conflictedLog)
                Log.w(TAG, "Conflict detected for log ${serverLog.uuid}. Mark isConflict = true.")
            } else {
                // 无冲突：采用 LWW 策略决定是否用云端覆盖本地
                val shouldOverwrite = if (localLog == null) {
                    true
                } else {
                    localLog.isSynced || serverLog.updated_at >= localLog.updatedAt
                }

                if (shouldOverwrite) {
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
                    logDao.insertOrUpdate(logEntity)


                    // 覆盖本地的日志背景画布关联
                    val canvasInstanceId = serverLog.canvas_instance_id
                    val imageUrl = serverLog.canvas_image_url
                    if (canvasInstanceId != null && !imageUrl.isNullOrBlank()) {
                        val dbInstance = canvasInstanceDao.getInstanceById(canvasInstanceId)
                        if (dbInstance == null) {
                            val virtualInstance = CanvasInstanceEntity(
                                id = canvasInstanceId,
                                canvasSetId = 3001, // 基础免费套件 (必定存在)
                                aspectRatio = serverLog.canvas_aspect_ratio,
                                imageUrl = imageUrl,
                                width = 1440,
                                height = 720,
                                isActive = true,
                                isDeleted = false,
                                createdAt = serverLog.created_at ?: ""
                            )
                            canvasInstanceDao.insertOrUpdateAll(listOf(virtualInstance))
                        }
                    }

                    val logCanvasEntity = LogCanvasEntity(
                        logUuid = serverLog.uuid,
                        canvasInstanceId = serverLog.canvas_instance_id,
                        canvasAspectRatio = serverLog.canvas_aspect_ratio
                    )
                    logCanvasDao.insertOrUpdate(logCanvasEntity)

                    // 重新覆盖本地的日志-人物多对多关联
                    logPersonDao.deleteCrossRefsForLog(serverLog.uuid)
                    val refs = serverLog.person_uuids.map { personUuid ->
                        LogPersonCrossRef(logUuid = serverLog.uuid, personUuid = personUuid, userId = userId)
                    }
                    logPersonDao.insertCrossRefs(refs)
                }
            }

            // 增量同步附件 metadata
            serverLog.attachments.forEach { serverAtt ->
                val localAtt = attachmentDao.getAttachmentByUuid(serverAtt.uuid)
                if (localAtt == null) {
                    val downloadUrl = "api/attachments/download/${serverAtt.uuid}"
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
                    if (localAtt.isSynced && localAtt.title != serverAtt.title) {
                        attachmentDao.insertOrUpdate(localAtt.copy(title = serverAtt.title))
                        Log.i(TAG, "Synced attachment title from server for ${serverAtt.uuid}: ${serverAtt.title}")
                    }
                }
            }
        }

        // 补充校准：对于所有在 toSyncLogs 里并且已经被服务器接受的日志，
        // 只要它在本地目前非冲突且未被标记为已同步，就校准同步状态。
        toSyncLogs.forEach { syncedLog ->
            if (serverUuids.contains(syncedLog.uuid)) {
                val local = logDao.getLogByUuid(syncedLog.uuid)
                if (local != null && !local.isConflict && (!local.isSynced || local.isLocalOnly)) {
                    logDao.markSynced(syncedLog.uuid)
                    Log.i(TAG, "Post-sync state alignment: Marked log ${syncedLog.uuid} as synced and cleared isLocalOnly.")
                }
            }
        }

        // 5. 层叠物理删除：删除本地不在服务端活跃列表中的日志
        val allLocalLogs = logDao.getAllActiveLogs(userId)
        allLocalLogs.forEach { local ->
            if (local.isSynced && !serverUuids.contains(local.uuid)) {
                Log.i(TAG, "Cascading deletion for log: ${local.uuid}")
                
                val attachments = attachmentDao.getAttachmentsForLog(local.uuid)
                attachments.forEach { att ->
                    att.localFilePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    attachmentDao.deleteAttachment(att.uuid)
                }
                logDao.hardDeleteLog(local.uuid)
                logPersonDao.deleteCrossRefsForLog(local.uuid)
                logCanvasDao.deleteLogCanvas(local.uuid)
            }
        }

        return@withContext serverUuids
    }
}
