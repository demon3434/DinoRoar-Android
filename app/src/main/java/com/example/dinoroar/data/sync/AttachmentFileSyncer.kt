package com.example.dinoroar.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.dinoroar.data.local.AttachmentDao
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.CheckMd5Payload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AttachmentSyncResult(
    val totalPending: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0
) {
    val isAllSuccess: Boolean get() = failedCount == 0
}

@Singleton
class AttachmentFileSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: AttachmentDao,
    private val apiService: DinoApiService,
    private val securePrefs: SecurePrefs
) {
    private val TAG = "AttachmentFileSyncer"

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun syncAttachments(isManual: Boolean): AttachmentSyncResult = withContext(Dispatchers.IO) {
        val userId = securePrefs.currentUserId
        // ==================== 阶段六：同步已删除的附件到云端 (物理删除) ====================
        try {
            val pendingDeletes = attachmentDao.getPendingDeleteAttachments(userId)
            for (att in pendingDeletes) {
                try {
                    val response = apiService.deleteAttachment(att.uuid)
                    if (response.isSuccessful || response.code() == 404) {
                        // 服务器已删除，或本身就不存在。现在可以物理删除本地文件并清理数据库了！
                        att.localFilePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                        attachmentDao.hardDeleteAttachment(att.uuid)
                        Log.i(TAG, "Successfully deleted attachment locally and remotely: ${att.uuid}")
                    } else {
                        Log.e(TAG, "Server delete failed for ${att.uuid}: code=${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync delete attachment ${att.uuid}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing pending deletes attachments: ${e.message}", e)
        }

        // ==================== 阶段七：上传本地未同步附件 (限制条件检查) ====================
        val isWifi = isWifiConnected()
        val wifiOnly = securePrefs.isWifiOnlyEnabled

        if (!isManual && wifiOnly && !isWifi) {
            Log.i(TAG, "Skipping attachment upload: wifiOnly enabled but on cellular.")
            return@withContext AttachmentSyncResult()
        }

        val unsyncedAttachments = attachmentDao.getUnsyncedAttachments(userId)
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0

        for (att in unsyncedAttachments) {
            val filePath = att.localFilePath
            if (filePath.isNullOrBlank()) {
                if (!att.remoteUrl.isNullOrBlank()) {
                    attachmentDao.markSynced(att.uuid)
                    successCount++
                } else {
                    Log.w(TAG, "Attachment ${att.uuid} has empty path and no remoteUrl. Self-healing by hard delete.")
                    attachmentDao.hardDeleteAttachment(att.uuid)
                    skippedCount++
                }
                continue
            }

            val file = File(filePath)
            if (!file.exists()) {
                if (!att.remoteUrl.isNullOrBlank()) {
                    Log.i(TAG, "Attachment ${att.uuid} physical file missing but remoteUrl exists. Self-healing markSynced.")
                    attachmentDao.markSynced(att.uuid)
                    successCount++
                } else {
                    Log.w(TAG, "Attachment ${att.uuid} physical file missing and no remoteUrl. Self-healing by hard delete to prevent permanently poisoning sync queue.")
                    attachmentDao.hardDeleteAttachment(att.uuid)
                    skippedCount++
                }
                continue
            }

            try {
                var isUploaded = false

                // 1. 如果存在 MD5, 先向秘密基地查询是否可以秒传 (MD5 查重去重)
                if (!att.md5.isNullOrBlank()) {
                    try {
                        val checkPayload = CheckMd5Payload(
                            md5 = att.md5,
                            uuid = att.uuid,
                            log_uuid = att.logUuid ?: "",
                            file_name = att.fileName,
                            mime_type = att.mimeType,
                            file_size = att.fileSize,
                            title = att.title
                        )
                        val downloadUrl = "api/attachments/download/${att.uuid}"
                        val checkResponse = apiService.checkMd5(checkPayload)
                        if (checkResponse.hit) {
                            Log.i(TAG, "MD5 hit! Fast attachment sync (秒传) successful for ${att.uuid}")
                            attachmentDao.markSyncedWithRemoteUrl(att.uuid, downloadUrl)
                            isUploaded = true
                            successCount++
                        }
                    } catch (checkEx: Exception) {
                        Log.w(TAG, "check-md5 lookup failed for ${att.uuid}, fallback to binary upload: ${checkEx.message}")
                    }
                }

                // 2. 秒传未命中，则进行实际物理二进制上传
                if (!isUploaded) {
                    val downloadUrl = "api/attachments/download/${att.uuid}"
                    val fileBody = file.asRequestBody(att.mimeType.toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", att.fileName, fileBody)
                    
                    val uuidPart = att.uuid.toRequestBody("text/plain".toMediaTypeOrNull())
                    val logUuidPart = att.logUuid?.toRequestBody("text/plain".toMediaTypeOrNull())
                    val titlePart = att.title?.toRequestBody("text/plain".toMediaTypeOrNull())

                    apiService.uploadAttachment(filePart, uuidPart, logUuidPart, titlePart)
                    
                    attachmentDao.markSyncedWithRemoteUrl(att.uuid, downloadUrl)
                    Log.i(TAG, "Uploaded attachment successfully: ${att.uuid}")
                    successCount++
                }
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "Failed to upload/register attachment ${att.uuid}: ${e.message}")
            }
        }

        AttachmentSyncResult(
            totalPending = unsyncedAttachments.size,
            successCount = successCount,
            failedCount = failedCount,
            skippedCount = skippedCount
        )
    }
}
