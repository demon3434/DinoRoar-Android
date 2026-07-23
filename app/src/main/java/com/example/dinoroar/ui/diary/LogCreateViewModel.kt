package com.example.dinoroar.ui.diary

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.media.MediaCompressor
import com.example.dinoroar.ui.main.StickerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class LogCreateViewModel(
    private val repository: DataRepository,
    private val syncManager: SyncManager,
    private val audioRecorder: AudioRecorder,
    private val mediaCompressor: MediaCompressor
) : ViewModel() {

    // 区分编辑会话
    var currentSessionUuid: String? = null
        private set

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

    // 2. 多媒体附件状态
    val selectedImageUris = mutableStateListOf<Uri>()
    val selectedVideoUris = mutableStateListOf<Uri>()
    val recordedFiles = mutableStateListOf<File>()
    val initialAttachments = mutableStateListOf<AttachmentEntity>()
    val tempAttachments = mutableStateListOf<TempAttachment>()

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

        if (editingLogUuid != null) {
            isEditMode = true
            viewModelScope.launch {
                try {
                    val log = repository.getLogByUuid(editingLogUuid)
                    if (log != null) {
                        selectedMoodDinoId = log.moodDinoId
                        title = log.title ?: ""
                        content = log.content
                        ownThoughts = log.ownThoughts ?: ""

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
                } catch (e: Exception) {
                    Log.e("LogCreateViewModel", "Load attachments failed", e)
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
