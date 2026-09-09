package com.example.dinoroar.ui.diary

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogCanvasEntity
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.media.MediaCompressor
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.ui.main.StickerInfo
import com.example.dinoroar.ui.main.getDinoName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class LogCreateViewModel(
    private val repository: DataRepository,
    private val syncManager: SyncManager,
    private val audioRecorder: AudioRecorder,
    private val mediaCompressor: MediaCompressor,
    private val securePrefs: SecurePrefs,
    private val apiService: DinoApiService
) : ViewModel() {

    // 区分编辑会话
    var currentSessionUuid: String? = null
        private set

    private var editingLogUuid: String? = null

    private var isInitialized = false

    // 1. 核心表单状态
    var selectedMoodDinoId by mutableStateOf(1)
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var ownThoughts by mutableStateOf("")
    var selectedPersonUuids by mutableStateOf(setOf<String>())
    var isEditMode by mutableStateOf(false)
    var selectedVideoResolution by mutableStateOf("720p")

    val stickers = mutableStateListOf<StickerInfo>()
    var cleanContentHasBeenParsed by mutableStateOf(false)

    // 背景画布状态
    var canvasInstanceId by mutableStateOf<Int?>(null)
    var canvasAspectRatio by mutableStateOf("2:1")
    var canvasImageUrl by mutableStateOf<String?>(null)
    val initialStickerIds = mutableStateListOf<String>()

    // 贴纸配置与缓存映射
    var stickerCacheMap by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    // 2. 多媒体附件状态
    val selectedImageUris = mutableStateListOf<Uri>()
    val selectedVideoUris = mutableStateListOf<Uri>()
    val recordedFiles = mutableStateListOf<File>()
    val initialAttachments = mutableStateListOf<AttachmentEntity>()
    val tempAttachments = mutableStateListOf<TempAttachment>()

    // 3. 阻塞/加载状态
    var isCompressing by mutableStateOf(false)
    var isRefreshingRemote by mutableStateOf(false)

    init {
        // 初始化贴纸缓存并拉取最新配置
        val cacheStr = securePrefs.stickerConfigCache
        val initialMap = mutableMapOf<Int, String>()
        cacheStr.split(",").filter { it.isNotBlank() }.forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val id = parts[0].toIntOrNull()
                if (id != null) {
                    initialMap[id] = parts[1]
                }
            }
        }
        stickerCacheMap = initialMap

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = apiService.getStickersConfig()
                val newMap = mutableMapOf<Int, String>()
                list.flatMap { it.stickers }.forEach { st ->
                    newMap[st.id] = st.image_url
                }
                withContext(Dispatchers.Main) {
                    stickerCacheMap = newMap
                }
                val newCacheStr = list.flatMap { it.stickers }.joinToString(",") { "${it.id}:${it.image_url}" }
                securePrefs.stickerConfigCache = newCacheStr
            } catch (e: Exception) {
                Log.e("LogCreateViewModel", "Failed to refresh sticker configs: ${e.message}")
            }
        }
    }

    /**
     * 初始化会话。如果 sessionUuid 没变且已初始化，直接复用状态；
     * 否则，视为新会话，执行清空并根据 editingLogUuid 重新从 Room 中异步加载。
     */
    fun setupSession(sessionUuid: String, editingLogUuid: String?, cacheDir: File) {
        if (currentSessionUuid == sessionUuid && isInitialized) {
            return
        }

        currentSessionUuid = sessionUuid
        isInitialized = true
        resetForm()
        this.editingLogUuid = editingLogUuid

        viewModelScope.launch {
            try {
                // 0. Fetch-Before-Edit: 编辑既有日记时，若在线必须先从服务端拉取最新数据写入 Room，绝不在旧缓存上修改
                if (editingLogUuid != null && syncManager.isOnline()) {
                    isRefreshingRemote = true
                    try {
                        syncManager.refreshSingleLog(editingLogUuid)
                    } catch (e: Exception) {
                        Log.w("LogCreateViewModel", "Fetch-Before-Edit prefetch failed, will fallback to local: ${e.message}")
                    } finally {
                        isRefreshingRemote = false
                    }
                }

                // 1. 记忆或恢复背景画布逻辑
                if (editingLogUuid != null) {
                    val logCanvas = repository.getLogCanvasByUuid(editingLogUuid)
                    if (logCanvas != null) {
                        canvasInstanceId = logCanvas.canvasInstanceId
                        canvasAspectRatio = logCanvas.canvasAspectRatio
                        if (logCanvas.canvasInstanceId != null) {
                            val inst = repository.getCanvasInstanceById(logCanvas.canvasInstanceId)
                            canvasImageUrl = inst?.imageUrl
                        }
                    }
                } else {
                    val lastUsed = repository.getLastUsedLogCanvas()
                    if (lastUsed != null) {
                        canvasInstanceId = lastUsed.canvasInstanceId
                        canvasAspectRatio = lastUsed.canvasAspectRatio
                        if (lastUsed.canvasInstanceId != null) {
                            val inst = repository.getCanvasInstanceById(lastUsed.canvasInstanceId)
                            canvasImageUrl = inst?.imageUrl
                        }
                    } else {
                        canvasInstanceId = null
                        canvasAspectRatio = "2:1"
                        canvasImageUrl = null
                    }
                }

                // 2. 加载日志正文与贴纸
                if (editingLogUuid != null) {
                    isEditMode = true
                    val log = repository.getLogByUuid(editingLogUuid)
                    if (log != null) {
                        selectedMoodDinoId = log.moodDinoId
                        title = log.title ?: ""
                        ownThoughts = log.ownThoughts ?: ""

                        // 解析日记正文贴纸并清洗
                        val parsed = parseStickerList(log.content)
                        stickers.clear()
                        stickers.addAll(parsed)
                        initialStickerIds.clear()
                        initialStickerIds.addAll(parsed.map { it.dinoId })

                        // 载入贴纸后，根据当前背景的比例立即执行一次越界矫正，确保贴纸四边均不超出画布
                        val parts = canvasAspectRatio.split(":")
                        val wPart = parts.getOrNull(0)?.toFloatOrNull() ?: 2f
                        val hPart = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
                        val aspectFloat = wPart / hPart
                        val logicalHeight = STICKER_CANVAS_LOGICAL_W / aspectFloat
                        val maxLogicY = logicalHeight - STICKER_SIZE_DP
                        val maxLogicX = STICKER_CANVAS_LOGICAL_W - STICKER_SIZE_DP
                        for (i in stickers.indices) {
                            val st = stickers[i]
                            val clampedX = st.x.coerceIn(0f, maxLogicX)
                            val clampedY = st.y.coerceIn(0f, maxLogicY)
                            if (clampedX != st.x || clampedY != st.y) {
                                stickers[i] = st.copy(x = clampedX, y = clampedY)
                            }
                        }

                        content = stripStickerTags(log.content)
                        cleanContentHasBeenParsed = true

                        val persons = repository.getPersonsForLog(editingLogUuid)
                        selectedPersonUuids = persons.map { it.uuid }.toSet()

                        // 加载多媒体附件
                        val initialAtts = repository.getAttachmentsForLogFlow(editingLogUuid).first()
                        initialAttachments.clear()
                        tempAttachments.clear()
                        initialAttachments.addAll(initialAtts)
                        
                        initialAtts.forEach { att ->
                            val file = if (att.localFilePath != null) {
                                File(att.localFilePath)
                            } else {
                                val ext = att.mimeType.substringAfter("/")
                                File(cacheDir, "${att.uuid}.$ext")
                            }

                            if (att.localFilePath == null) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.insertAttachment(att.copy(localFilePath = file.absolutePath))
                                }
                            }

                            val tempAtt = TempAttachment(
                                uuid = att.uuid,
                                sourceUri = Uri.fromFile(file),
                                type = when {
                                    att.mimeType.startsWith("image/") -> AttachmentType.IMAGE
                                    att.mimeType.startsWith("video/") -> AttachmentType.VIDEO
                                    else -> AttachmentType.AUDIO
                                },
                                file = file,
                                status = "ready",
                                md5 = att.md5,
                                title = att.title
                            )
                            tempAttachments.add(tempAtt)

                            if (att.mimeType.startsWith("image/")) {
                                selectedImageUris.add(Uri.fromFile(file))
                            } else if (att.mimeType.startsWith("video/")) {
                                selectedVideoUris.add(Uri.fromFile(file))
                            } else if (att.mimeType.startsWith("audio/")) {
                                recordedFiles.add(file)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LogCreateViewModel", "Load attachments or canvas failed", e)
            }
        }
    }

    /**
     * 更新画布并处理贴纸的物理截断限位
     */
    fun updateCanvas(instanceId: Int?, ratio: String, imageUrl: String?) {
        canvasInstanceId = instanceId
        canvasAspectRatio = ratio
        canvasImageUrl = imageUrl

        // 当画布比例发生改变时，自动在 ViewModel 侧执行越界物理截断限位
        val parts = ratio.split(":")
        val wPart = parts.getOrNull(0)?.toFloatOrNull() ?: 2f
        val hPart = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
        val aspectFloat = wPart / hPart
        val logicalHeight = STICKER_CANVAS_LOGICAL_W / aspectFloat

        val maxLogicY = logicalHeight - STICKER_SIZE_DP
        val maxLogicX = STICKER_CANVAS_LOGICAL_W - STICKER_SIZE_DP
        for (i in stickers.indices) {
            val st = stickers[i]
            val clampedX = st.x.coerceIn(0f, maxLogicX)
            val clampedY = st.y.coerceIn(0f, maxLogicY)
            if (clampedX != st.x || clampedY != st.y) {
                stickers[i] = st.copy(x = clampedX, y = clampedY)
            }
        }
    }

    /**
     * 异步导入并压缩图片
     */
    fun processAndAddImage(context: Context, uri: Uri) {
        if (selectedImageUris.size >= 9) {
            Toast.makeText(context, "图片最多只能上传 9 张哦！", Toast.LENGTH_SHORT).show()
            return
        }
        selectedImageUris.add(uri)
        val attUuid = UUID.randomUUID().toString()
        val temp = TempAttachment(uuid = attUuid, sourceUri = uri, type = AttachmentType.IMAGE, status = "compressing")
        tempAttachments.add(temp)
        viewModelScope.launch(Dispatchers.IO) {
            val compressed = mediaCompressor.compressImage(uri)
            val index = tempAttachments.indexOfFirst { it.uuid == attUuid }
            if (index != -1) {
                if (compressed != null) {
                    val md5Val = mediaCompressor.calculateMd5(compressed)
                    tempAttachments[index] = tempAttachments[index].copy(
                        file = compressed,
                        status = "ready",
                        md5 = md5Val
                    )
                } else {
                    tempAttachments[index] = tempAttachments[index].copy(status = "failed")
                }
            }
        }
    }

    /**
     * 异步导入并压缩视频
     */
    fun processAndAddVideo(context: Context, uri: Uri) {
        if (selectedVideoUris.size >= 3) {
            Toast.makeText(context, "视频最多只能上传 3 个哦！", Toast.LENGTH_SHORT).show()
            return
        }
        selectedVideoUris.add(uri)
        val attUuid = UUID.randomUUID().toString()
        val temp = TempAttachment(uuid = attUuid, sourceUri = uri, type = AttachmentType.VIDEO, status = "compressing")
        tempAttachments.add(temp)
        viewModelScope.launch(Dispatchers.IO) {
            val compressed = mediaCompressor.compressVideo(uri, securePrefs.globalVideoQuality)
            val index = tempAttachments.indexOfFirst { it.uuid == attUuid }
            if (index != -1) {
                if (compressed != null) {
                    val md5Val = mediaCompressor.calculateMd5(compressed)
                    tempAttachments[index] = tempAttachments[index].copy(
                        file = compressed,
                        status = "ready",
                        md5 = md5Val
                    )
                } else {
                    tempAttachments[index] = tempAttachments[index].copy(status = "failed")
                }
            }
        }
    }

    /**
     * 异步导入录音
     */
    fun processAndAddAudio(context: Context, file: File) {
        if (recordedFiles.size >= 5) {
            Toast.makeText(context, "语音留言最多导入 5 段哦！", Toast.LENGTH_SHORT).show()
            return
        }
        recordedFiles.add(file)
        val attUuid = UUID.randomUUID().toString()
        val temp = TempAttachment(uuid = attUuid, sourceUri = Uri.fromFile(file), type = AttachmentType.AUDIO, status = "compressing")
        tempAttachments.add(temp)
        viewModelScope.launch(Dispatchers.IO) {
            val md5Val = mediaCompressor.calculateMd5(file)
            val index = tempAttachments.indexOfFirst { it.uuid == attUuid }
            if (index != -1) {
                tempAttachments[index] = tempAttachments[index].copy(
                    file = file,
                    status = "ready",
                    md5 = md5Val
                )
            }
        }
    }

    fun removeImage(uri: Uri) {
        selectedImageUris.remove(uri)
        tempAttachments.removeAll { it.sourceUri == uri || (uri.scheme == "file" && it.file?.absolutePath == uri.path) }
    }

    fun removeVideo(uri: Uri) {
        selectedVideoUris.remove(uri)
        tempAttachments.removeAll { it.sourceUri == uri || (uri.scheme == "file" && it.file?.absolutePath == uri.path) }
    }

    fun removeAudio(file: File) {
        recordedFiles.remove(file)
        val fileUri = Uri.fromFile(file)
        tempAttachments.removeAll { it.sourceUri == fileUri || it.file?.absolutePath == file.absolutePath }
    }

    /**
     * 保存日记业务逻辑，包含贴纸序列化、Room 插入/更新、贴纸消耗及后台同步调度
     */
    fun saveDiary(
        context: Context,
        skipSync: Boolean,
        onResult: (success: Boolean, message: String?) -> Unit
    ) {
        if (title.trim().isBlank()) {
            onResult(false, "日记标题不能为空哦！请先填写一个日记标题～")
            return
        }
        if (content.trim().isBlank()) {
            onResult(false, "事情经过不能为空哦！请倾诉你此刻的经过～")
            return
        }

        val hasCompressing = tempAttachments.any { it.status == "compressing" }
        if (hasCompressing) {
            onResult(false, "💡 记忆正在整理中，请等小恐龙打包完成哦～")
            return
        }

        isCompressing = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isExistingEdit = editingLogUuid != null && isEditMode
                val logUuid = if (isExistingEdit) editingLogUuid!! else UUID.randomUUID().toString()
                val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

                // 1. 清理已被物理删除的附件
                val currentUuids = tempAttachments.map { it.uuid }
                initialAttachments.forEach { initialAtt ->
                    if (initialAtt.uuid !in currentUuids) {
                        repository.softDeleteAttachment(initialAtt.uuid)
                        initialAtt.localFilePath?.let { path ->
                            try {
                                val f = File(path)
                                if (f.exists()) {
                                    f.delete()
                                }
                            } catch (e: Exception) {
                                Log.e("LogCreateViewModel", "Physical file cleanup failed for $path", e)
                            }
                        }
                    }
                }

                // 2. 插入新附带的附件
                tempAttachments.forEach { temp ->
                    val isExisting = initialAttachments.find { it.uuid == temp.uuid }
                    if (isExisting == null) {
                        if (temp.status == "ready" && temp.file != null) {
                            val attachment = AttachmentEntity(
                                uuid = temp.uuid,
                                logUuid = logUuid,
                                fileName = temp.file.name,
                                mimeType = when (temp.type) {
                                    AttachmentType.IMAGE -> "image/jpeg"
                                    AttachmentType.VIDEO -> "video/mp4"
                                    AttachmentType.AUDIO -> "audio/m4a"
                                },
                                fileSize = temp.file.length(),
                                localFilePath = temp.file.absolutePath,
                                remoteUrl = null,
                                createdAt = timeStamp,
                                isDeleted = false,
                                isSynced = false,
                                title = temp.title,
                                md5 = temp.md5
                            )
                            repository.insertAttachment(attachment)
                        }
                    } else {
                        if (isExisting.title != temp.title) {
                            repository.insertAttachment(
                                isExisting.copy(
                                    title = temp.title,
                                    isSynced = false
                                )
                            )
                        }
                    }
                }

                // 3. 构建日记实体并保存
                val currentLog = if (isExistingEdit) repository.getLogByUuid(logUuid) else null
                val createdAtVal = currentLog?.createdAt ?: timeStamp
                val updatedAtVal = timeStamp
                val versionVal = if (isExistingEdit) (currentLog?.version ?: 1) + 1 else 1

                val cleanedContent = sanitizeMultilineText(content)
                val cleanedOwnThoughts = sanitizeMultilineText(ownThoughts)


                // 贴纸序列化为占位标签，精度设为 Locale.US 保证统一格式化为 "x.y" 格式
                val stickersStr = stickers.joinToString("") {
                    "[sticker:${it.dinoId}:${"%.1f".format(Locale.US, it.x)},${"%.1f".format(Locale.US, it.y)},${"%.2f".format(Locale.US, it.scale)},${"%.1f".format(Locale.US, it.rotation)},${if (it.flipH) 1 else 0},${if (it.flipV) 1 else 0}]"
                }
                val finalContent = cleanedContent + (if (stickersStr.isNotEmpty()) "\n" + stickersStr else "")

                // 奖励能量（仅限新建日记）
                var energyReward = 0
                var nextDinoId: Int? = null
                if (!isEditMode) {
                    val hasMedia = (tempAttachments.size + recordedFiles.size) > 0
                    energyReward = if (hasMedia) 30 else 10
                    val oldEnergy = securePrefs.eggEnergy
                    val newEnergy = oldEnergy + energyReward
                    securePrefs.eggEnergy = newEnergy


                    // 达到100能量阈值，爆发出一个新贴纸
                    if (newEnergy / 100 > oldEnergy / 100) {
                        nextDinoId = (1..11).random()
                    }
                }

                val logEntity = LogEntity(
                    uuid = logUuid,
                    title = title.trim(),
                    incidentDate = createdAtVal,
                    moodDinoId = selectedMoodDinoId,
                    content = finalContent,
                    ownThoughts = cleanedOwnThoughts,
                    createdAt = createdAtVal,
                    updatedAt = updatedAtVal,
                    isDeleted = false,
                    isSynced = false,
                    version = versionVal,
                    isLocalOnly = skipSync
                )
                repository.insertLog(logEntity, selectedPersonUuids.toList())

                // 4. 保存背景画布关联关系
                val logCanvasEntity = LogCanvasEntity(
                    logUuid = logUuid,
                    canvasInstanceId = canvasInstanceId,
                    canvasAspectRatio = canvasAspectRatio
                )
                repository.insertLogCanvas(logCanvasEntity)

                // 5. 贴纸库存的扣减和增值事务
                val usedStickerIds = parseStickerList(finalContent).map { it.dinoId.trim() }
                val invMap = mutableMapOf<String, Int>()
                securePrefs.stickerInventory.split(",").filter { it.isNotBlank() }.forEach { item ->
                    val parts = item.split(":")
                    if (parts.size == 2) {
                        val sId = parts[0].trim()
                        val sCnt = parts[1].trim().toIntOrNull() ?: 0
                        if (sCnt > 0) {
                            invMap[sId] = sCnt
                        }
                    }
                }

                // 先加奖励贴纸
                if (nextDinoId != null) {
                    val nextDinoStr = nextDinoId.toString()
                    invMap[nextDinoStr] = (invMap[nextDinoStr] ?: 0) + 1
                }

                // 再扣减使用的贴纸
                usedStickerIds.forEach { usedId ->
                    if (usedId.isNotBlank()) {
                        val currentCnt = invMap[usedId] ?: 0
                        if (currentCnt > 1) {
                            invMap[usedId] = currentCnt - 1
                        } else {
                            invMap.remove(usedId)
                        }
                    }
                }

                val newInvStr = invMap.map { "${it.key}:${it.value}" }.joinToString(",")
                securePrefs.stickerInventory = newInvStr
                securePrefs.lastSyncedInventory = newInvStr

                // 6. 调度同步任务
                if (!skipSync) {
                    syncManager.scheduleBackgroundSync()
                }

                clearSession()

                withContext(Dispatchers.Main) {
                    isCompressing = false
                    if (nextDinoId != null) {
                        Toast.makeText(
                            context,
                            "🎉 守护蛋能量爆发！恭喜获得 1 张【${getDinoName(nextDinoId)}】贴纸！已送入您的手账仓库～",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onResult(true, null)
                }
            } catch (e: Exception) {
                Log.e("LogCreateViewModel", "Save failed", e)
                withContext(Dispatchers.Main) {
                    isCompressing = false
                    onResult(false, e.message ?: "未知保存错误")
                }
            }
        }
    }

    /**
     * 重置表单状态到初始值
     */
    private fun resetForm() {
        selectedMoodDinoId = 1
        title = ""
        content = ""
        ownThoughts = ""
        selectedPersonUuids = emptySet()
        isEditMode = false
        selectedVideoResolution = "720p"

        selectedImageUris.clear()
        selectedVideoUris.clear()
        recordedFiles.clear()
        initialAttachments.clear()
        tempAttachments.clear()
        stickers.clear()
        cleanContentHasBeenParsed = false
        canvasInstanceId = null
        canvasAspectRatio = "2:1"
        canvasImageUrl = null
        initialStickerIds.clear()
        isCompressing = false
        isRefreshingRemote = false
        editingLogUuid = null
    }

    /**
     * 新建随笔人物
     */
    fun createPerson(
        name: String,
        abbrev: String,
        relation: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pUuid = "p-" + UUID.randomUUID().toString()
                val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                val newPerson = com.example.dinoroar.data.local.PersonEntity(
                    uuid = pUuid,
                    name = name,
                    abbreviation = abbrev.uppercase(Locale.US),
                    relationship = relation,
                    createdAt = timeStamp
                )
                repository.insertPerson(newPerson)
                withContext(Dispatchers.Main) {
                    selectedPersonUuids = selectedPersonUuids + pUuid
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("LogCreateViewModel", "Failed to create person", e)
            }
        }
    }

    /**
     * 智能异步拷贝并导入外链音频文件
     */
    fun importAudio(context: Context, uri: Uri) {
        if (recordedFiles.size >= 5) {
            Toast.makeText(context, "语音留言最多导入 5 段哦！", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "imported_${UUID.randomUUID()}.m4a")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    processAndAddAudio(context, tempFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入音频失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 用户保存成功或确认放弃时，清理会话标识并清空状态
     */
    fun clearSession() {
        currentSessionUuid = null
        isInitialized = false
        resetForm()
    }
}
