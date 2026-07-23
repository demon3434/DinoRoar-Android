package com.example.dinoroar.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.Manifest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.ActivityStateTracker
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.provider.DocumentsContract
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import com.example.dinoroar.R
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.theme.LocalAppColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.media.AudioRecorder
import com.example.dinoroar.media.MediaCompressor
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.FileProvider
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.MediaMetadataRetriever
import androidx.compose.ui.geometry.Offset
import com.example.dinoroar.ui.main.StickerInfo
import com.example.dinoroar.ui.main.getDinoResource
import com.example.dinoroar.ui.main.getDinoName



enum class AttachmentType { IMAGE, VIDEO, AUDIO }

data class TempAttachment(
    val uuid: String,
    val sourceUri: Uri,
    val type: AttachmentType,
    val file: File? = null,
    val status: String = "compressing", // "compressing" | "ready" | "failed"
    val md5: String? = null,
    val title: String? = null
)

fun sanitizeMultilineText(input: String): String {
    if (input.isEmpty()) return ""
    return input.replace(Regex("(\\r?\\n)+"), "\n").trimEnd('\r', '\n')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCreateScreen(
    repository: DataRepository,
    syncManager: SyncManager,
    audioRecorder: AudioRecorder,
    mediaCompressor: MediaCompressor,
    apiService: com.example.dinoroar.network.DinoApiService,
    editingLogUuid: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToPersonSelect: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val securePrefs = remember { com.example.dinoroar.data.local.SecurePrefs(context) }
    val sessionUuid = remember { editingLogUuid ?: "TEMP_DRAFT_KEY" }

    val stickers = remember { mutableStateListOf<StickerInfo>() }

    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }
    var stickerCacheMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val cacheStr = securePrefs.stickerConfigCache ?: ""
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

        try {
            val list = apiService.getStickersConfig()
            val newMap = mutableMapOf<Int, String>()
            list.flatMap { it.stickers }.forEach { st ->
                newMap[st.id] = st.image_url
            }
            stickerCacheMap = newMap
            val newCacheStr = list.flatMap { it.stickers }.joinToString(",") { "${it.id}:${it.image_url}" }
            securePrefs.stickerConfigCache = newCacheStr
        } catch (e: Exception) {
            android.util.Log.e("LogCreateScreen", "Failed to refresh sticker configs: ${e.message}")
        }
    }

    var cleanContentHasBeenParsed by remember { mutableStateOf(false) }


    val initialStickerIds = remember { mutableStateListOf<String>() }


    


    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ActivityStateTracker.isExternalActivityActive = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selectedIds = result.data?.getStringArrayListExtra("selected_sticker_ids") ?: emptyList()
            applyStickerPickerResult(selectedIds, stickers) {
                android.widget.Toast.makeText(context, "手账贴纸最多只能添加 6 张哦！", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val tempStickerInventory = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(securePrefs.stickerInventory) {
        tempStickerInventory.clear()
        securePrefs.stickerInventory.split(",").filter { it.isNotBlank() }.forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val dino = parts[0].trim()
                val count = parts[1].trim().toIntOrNull() ?: 0
                tempStickerInventory[dino] = (tempStickerInventory[dino] ?: 0) + count
            }
        }
    }

    // 使用自定义 Factory 初始化 ViewModel
    val viewModel: LogCreateViewModel = viewModel(
        factory = remember(repository, syncManager, audioRecorder, mediaCompressor) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LogCreateViewModel(
                        repository = repository,
                        syncManager = syncManager,
                        audioRecorder = audioRecorder,
                        mediaCompressor = mediaCompressor
                    ) as T
                }
            }
        }
    )

    // 状态代理与引用代理
    var selectedMoodDinoId by viewModel::selectedMoodDinoId
    var title by viewModel::title
    var content by viewModel::content

    LaunchedEffect(content) {
        if (!cleanContentHasBeenParsed && content.isNotEmpty()) {
            val pattern = java.util.regex.Pattern.compile("\\[sticker:([^:]+):([0-9.-]+),([0-9.-]+)\\]")
            val matcher = pattern.matcher(content)
            val list = mutableListOf<StickerInfo>()
            val sb = StringBuffer()
            while (matcher.find()) {
                val dinoId = matcher.group(1) ?: ""
                val x = matcher.group(2)?.toFloatOrNull() ?: 50f
                val y = matcher.group(3)?.toFloatOrNull() ?: 50f
                list.add(StickerInfo(dinoId, x, y))
                matcher.appendReplacement(sb, "")
            }
            matcher.appendTail(sb)
            stickers.addAll(list)

            initialStickerIds.clear()
            initialStickerIds.addAll(list.map { it.dinoId })

            content = sanitizeMultilineText(sb.toString())

            cleanContentHasBeenParsed = true
        }
    }
    var ownThoughts by viewModel::ownThoughts
    var selectedPersonUuids by viewModel::selectedPersonUuids
    var isEditMode by viewModel::isEditMode
    var selectedVideoResolution by viewModel::selectedVideoResolution

    val selectedImageUris = viewModel.selectedImageUris
    val selectedVideoUris = viewModel.selectedVideoUris
    val recordedFiles = viewModel.recordedFiles
    val initialAttachments = viewModel.initialAttachments
    val tempAttachments = viewModel.tempAttachments

    // 纯 UI 交互状态（依然留在 remember 里）
    var showMoodSelectScreen by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCreatePersonDialog by remember { mutableStateOf(false) }

    val allPersons by repository.allPersons.collectAsStateWithLifecycle(initialValue = emptyList())
    val allDinoConfigs by repository.getAllActiveDinoConfigsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val hasChanges = content.isNotBlank() || ownThoughts.isNotBlank() ||
            selectedImageUris.isNotEmpty() || selectedVideoUris.isNotEmpty() || recordedFiles.isNotEmpty() ||
            selectedPersonUuids.isNotEmpty() || selectedMoodDinoId != 1

    // 临时拍摄用 Uri
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempVideoUri by remember { mutableStateOf<Uri?>(null) }

    // 单个录音临时状态
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingVoiceFile by remember { mutableStateOf<File?>(null) } // 当前播放的录音文件
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // 大图全屏预览弹窗状态
    var previewImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // 后台非阻塞压缩 Loading 状态
    var isCompressing by remember { mutableStateOf(false) }

    var selectedPersons by remember { mutableStateOf<List<PersonEntity>>(emptyList()) }
    LaunchedEffect(selectedPersonUuids, allPersons) {
        val list = mutableListOf<PersonEntity>()
        selectedPersonUuids.forEach { uuid ->
            val p = repository.getPersonByUuid(uuid)
            if (p != null) list.add(p)
        }
        selectedPersons = list
    }

    val performSaveDiary = { skipSync: Boolean ->
        if (title.trim().isBlank()) {
            Toast.makeText(context, "日记标题不能为空哦！请先填写一个日记标题～", Toast.LENGTH_SHORT).show()
        } else if (content.trim().isBlank()) {
            Toast.makeText(context, "事情经过不能为空哦！请倾诉你此刻的经过～", Toast.LENGTH_SHORT).show()
        } else {
            isCompressing = true // 拉起加载层

            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val logUuid = editingLogUuid ?: UUID.randomUUID().toString()
                    val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

                    val hasCompressing = tempAttachments.any { it.status == "compressing" }
                    if (hasCompressing) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "💡 记忆正在整理中，请等小恐龙打包完成哦～", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

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
                                    Log.e("LogCreateScreen", "Physical file cleanup failed for $path", e)
                                }
                            }
                        }
                    }
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

                     // 4. Save Diary Log
                     val currentLog = if (isEditMode) repository.getLogByUuid(logUuid) else null
                     val createdAtVal = currentLog?.createdAt ?: timeStamp
                     val updatedAtVal = timeStamp
                     val versionVal = if (isEditMode) (currentLog?.version ?: 1) + 1 else 1

                     // Clean multiline newlines and serialize stickers to the end of content
                     val cleanedContent = sanitizeMultilineText(content)
                     val cleanedOwnThoughts = sanitizeMultilineText(ownThoughts)
                     val stickersStr = stickers.joinToString("") { "[sticker:${it.dinoId}:${it.x.toInt()},${it.y.toInt()}]" }
                     val finalContent = cleanedContent + (if (stickersStr.isNotEmpty()) "\n" + stickersStr else "")

                     // Calculate and award egg energy
                     if (!isEditMode) {
                         val energyReward = when {
                             cleanedOwnThoughts.trim().length > 5 -> 20
                             (tempAttachments.size + recordedFiles.size) > 0 -> 10
                             else -> 5
                         }
                         val oldEnergy = securePrefs.eggEnergy
                         val newEnergy = oldEnergy + energyReward
                         securePrefs.eggEnergy = newEnergy
                         
                         // Every 100 energy awards a random sticker
                         if (newEnergy / 100 > oldEnergy / 100) {
                               val nextDinoId = (1..11).random()
                               val nextDinoStr = nextDinoId.toString()
                               tempStickerInventory[nextDinoStr] = (tempStickerInventory[nextDinoStr] ?: 0) + 1
                               kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                   Toast.makeText(context, "🎉 守护蛋能量爆发！恭喜获得 1 张【" + getDinoName(nextDinoId) + "】贴纸！已送入您的手账仓库～", Toast.LENGTH_LONG).show()
                               }
                          }
                      }

                      val logEntity = LogEntity(
                         uuid = logUuid,
                         title = title.trim(),
                         incidentDate = createdAtVal, moodDinoId = selectedMoodDinoId,
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

                       // Save sticker inventory transaction to Prefs & deduct used stickers
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

                     // 5. Sync to server if not skipped (Use background scheduler to avoid blocking UI)
                     if (!skipSync) {
                         syncManager.scheduleBackgroundSync()
                     }

                    viewModel.clearSession()

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isCompressing = false
                        Toast.makeText(
                            context,
                            if (skipSync) "🎈 秘密已悄悄藏进小树洞啦！\\n⚠️ 注意：这仅仅保存在手机临时缓存里，如果清理手机垃圾缓存，秘密会丢失哦，记得及时【存入秘密基地】妥善同步！" else "✨ 秘密正在传送至秘密基地，小恐龙会在后台帮你安全存盘哦～",
                            Toast.LENGTH_LONG
                        ).show()
                        onNavigateBack()
                    }
                } catch (e: Exception) {
                    Log.e("LogCreateScreen", "Save failed", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isCompressing = false
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 监听 sessionUuid 变化以初始化或恢复编辑状态
    LaunchedEffect(sessionUuid) {
        viewModel.setupSession(sessionUuid, editingLogUuid, context.cacheDir)
    }

    // 重置相机/外链状态的副作用，退出时重置
    DisposableEffect(Unit) {
        onDispose {
            ActivityStateTracker.reset()
        }
    }

    // 11档恐龙心情配对
    val dinos = remember(allDinoConfigs) {
        allDinoConfigs.map { config ->
            val resId = getDinoResource(config.id)
            val desc = "${config.moodLabel} ${config.name}\n${config.moodTip ?: ""}"
            Triple(config.id, resId, desc)
        }.ifEmpty {
            listOf(
                Triple(1, R.drawable.mood_triceratops, "快乐三角龙 😊 开心\n今天遇到了很棒的事，心里美滋滋的！"),
                Triple(2, R.drawable.mood_pterodactyl_happy, "冲天翼手龙 🤩 兴奋\n太棒啦！我要起飞咯，整个人都兴奋起来了！"),
                Triple(3, R.drawable.mood_t_rex_proud, "挺胸霸王龙 😎 得意\n哼哼，今天我超级棒，快给我竖个大拇指！"),
                Triple(4, R.drawable.mood_brachiosaurus, "大眼睛雷龙 🌟 期待\n好期待明天呀，真希望时间能过得快一点！"),
                Triple(5, R.drawable.mood_stegosaurus, "呆呆剑龙 😮 惊讶\n哇塞！真是太不可思议啦，惊掉下巴！"),
                Triple(6, R.drawable.mood_velociraptor, "佛系迅猛龙 😐 一般\n平静轻松的一天，小恐龙也在打哈欠~"),
                Triple(7, R.drawable.mood_ankylosaurus_scared, "缩壳甲龙 😰 紧张\n捏了一把汗，缩壳长出尖刺防卫一下"),
                Triple(8, R.drawable.mood_pachycephalosaurus, "叹气肿头龙 🍃 遗憾\n唉…又撞疼了大光头，真可惜"),
                Triple(9, R.drawable.mood_parasaurolophus_regret, "耷拉角副栉龙 😣 后悔\n如果当时我没有那么做，会不会更好呢…"),
                Triple(10, R.drawable.mood_spinosaurus, "细雨棘龙 😭 伤心\n小雨哗啦啦，棘龙的大背鳍也像哭泣的小伞"),
                Triple(11, R.drawable.mood_dilophosaurus, "怒火双脊龙 😡 愤怒\n吼！发怒的双脊龙褶伞张开，怒气冲天！")
            )
        }
    }

    // Launchers for media selection & capturing
    // --- 异步多媒体处理 Helper 方法 ---
    fun processAndAddImage(uri: Uri) {
        if (selectedImageUris.size >= 9) {
            Toast.makeText(context, "图片最多只能上传 9 张哦！", Toast.LENGTH_SHORT).show()
            return
        }
        selectedImageUris.add(uri)
        val attUuid = UUID.randomUUID().toString()
        val temp = TempAttachment(uuid = attUuid, sourceUri = uri, type = AttachmentType.IMAGE, status = "compressing")
        tempAttachments.add(temp)
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    fun processAndAddVideo(uri: Uri) {
        if (selectedVideoUris.size >= 3) {
            Toast.makeText(context, "视频最多只能上传 3 个哦！", Toast.LENGTH_SHORT).show()
            return
        }
        selectedVideoUris.add(uri)
        val attUuid = UUID.randomUUID().toString()
        val temp = TempAttachment(uuid = attUuid, sourceUri = uri, type = AttachmentType.VIDEO, status = "compressing")
        tempAttachments.add(temp)
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    fun processAndAddAudio(file: File) {
        if (recordedFiles.size >= 5) {
            Toast.makeText(context, "语音留言最多导入 5 段哦！", Toast.LENGTH_SHORT).show()
            return
        }
        recordedFiles.add(file)
        val attUuid = UUID.randomUUID().toString()
        val temp = TempAttachment(uuid = attUuid, sourceUri = Uri.fromFile(file), type = AttachmentType.AUDIO, status = "compressing")
        tempAttachments.add(temp)
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { processAndAddImage(it) }
    }

    val cameraImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        ActivityStateTracker.isCameraActive = false
        if (success) {
            tempPhotoUri?.let { processAndAddImage(it) }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { processAndAddVideo(it) }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val intentData = result.data
            val uris = mutableListOf<Uri>()
            if (intentData != null) {
                val clipData = intentData.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                } else {
                    intentData.data?.let { uris.add(it) }
                }
            }
            uris.forEach { uri ->
                if (recordedFiles.size < 5) {
                    coroutineScope.launch {
                        val tempFile = File(context.cacheDir, "imported_${java.util.UUID.randomUUID()}.m4a")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            processAndAddAudio(tempFile)
                        } catch (e: Exception) {
                            Toast.makeText(context, "导入音频失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "语音留言最多导入 5 段哦！", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        ActivityStateTracker.isCameraActive = false
        if (success) {
            tempVideoUri?.let { processAndAddVideo(it) }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val voiceFile = audioRecorder.startRecording()
            if (voiceFile != null) {
                isRecording = true
            }
        } else {
            Toast.makeText(context, "需要麦克风录音权限！", Toast.LENGTH_SHORT).show()
        }
    }

    // Colors
    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonRed = appColors.neonRed
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary


    BackHandler(enabled = showMoodSelectScreen) {
        showMoodSelectScreen = false
    }

    BackHandler(enabled = !showMoodSelectScreen && hasChanges) {
        showExitConfirmDialog = true
    }

    BackHandler(enabled = !showMoodSelectScreen && !hasChanges) {
        viewModel.clearSession()
        onNavigateBack()
    }

    LogCreateScreenContent(
        darkBg = darkBg,
        cardBg = cardBg,
        neonBlue = neonBlue,
        neonGreen = neonGreen,
        neonRed = neonRed,
        neonAmber = neonAmber,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        selectedMoodDinoId = selectedMoodDinoId,
        dinos = dinos,
        showMoodSelectScreen = showMoodSelectScreen,
        onMoodClick = { showMoodSelectScreen = true },
        onMoodSelected = { selectedMoodDinoId = it; showMoodSelectScreen = false },
        onMoodSelectBack = { showMoodSelectScreen = false },
        selectedPersons = selectedPersons,
        selectedPersonUuids = selectedPersonUuids,
        onNavigateToPersonSelect = {
            com.example.dinoroar.PersonSelectorState.onPersonsSelected = { uuids ->
                selectedPersonUuids = uuids.toSet()
            }
            onNavigateToPersonSelect(selectedPersonUuids.toList())
        },
        title = title,
        onTitleChange = { title = it },
        content = content,
        onContentChange = { content = it },
        ownThoughts = ownThoughts,
        onOwnThoughtsChange = { ownThoughts = it },
        stickers = stickers,
        stickerCacheMap = stickerCacheMap,
        serverBaseUrl = serverBaseUrl,
        onSelectStickerClick = {
            val currentIds = stickers.map { it.dinoId }
            val intent = Intent(context, com.example.dinoroar.ui.sticker.StickerPickerActivity::class.java).apply {
                putStringArrayListExtra("already_used_sticker_ids", ArrayList(currentIds))
            }
            ActivityStateTracker.isExternalActivityActive = true
            pickerLauncher.launch(intent)
        },
        audioRecorder = audioRecorder,
        selectedImageUris = selectedImageUris,
        selectedVideoUris = selectedVideoUris,
        recordedFiles = recordedFiles,
        isRecording = isRecording,
        onPickImage = {
            ActivityStateTracker.isExternalActivityActive = true
            imagePickerLauncher.launch("image/*")
        },
        onCaptureImage = {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val photoFile = File(context.cacheDir, "CAP_$timeStamp.jpg")
            val uri = FileProvider.getUriForFile(context, "com.example.dinoroar.fileprovider", photoFile)
            tempPhotoUri = uri
            ActivityStateTracker.isCameraActive = true
            ActivityStateTracker.isExternalActivityActive = true
            cameraImageLauncher.launch(uri)
        },
        onPickVideo = {
            ActivityStateTracker.isExternalActivityActive = true
            videoPickerLauncher.launch("video/*")
        },
        onCaptureVideo = {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val videoFile = File(context.cacheDir, "CAP_$timeStamp.mp4")
            val uri = FileProvider.getUriForFile(context, "com.example.dinoroar.fileprovider", videoFile)
            tempVideoUri = uri
            ActivityStateTracker.isCameraActive = true
            ActivityStateTracker.isExternalActivityActive = true
            cameraVideoLauncher.launch(uri)
        },
        onPickAudio = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val initialUri = getInitialAudioUri()
                    if (initialUri != null) {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                    }
                }
            }
            securePrefs.isExternalActivityActive = true
            audioPickerLauncher.launch(intent)
        },
        onToggleRecording = {
            if (isRecording) {
                val voiceFile = audioRecorder.stopRecording()
                isRecording = false
                if (voiceFile != null) {
                    if (recordedFiles.size < 5) {
                        processAndAddAudio(voiceFile)
                    } else {
                        Toast.makeText(context, "留言最多录制 5 段哦！", Toast.LENGTH_SHORT).show()
                        voiceFile.delete()
                    }
                }
            } else {
                if (recordedFiles.size >= 5) {
                    Toast.makeText(context, "留言最多录制 5 段哦！", Toast.LENGTH_SHORT).show()
                } else {
                    val permissionCheck = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        val tempFile = audioRecorder.startRecording()
                        isRecording = tempFile != null
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        },
        tempAttachments = tempAttachments,
        initialAttachments = initialAttachments,
        apiService = apiService,
        securePrefs = securePrefs,
        onPreviewImage = { uri -> previewImageUri = uri },
        onRemoveImage = { uri -> removeImage(uri) },
        onRemoveVideo = { uri -> removeVideo(uri) },
        onRemoveAudio = { file -> removeAudio(file) },
        onSaveToBase = { performSaveDiary(false) },
        onSaveToTreehole = { performSaveDiary(true) },
        isCompressing = isCompressing,
        showExitConfirmDialog = showExitConfirmDialog,
        onExitConfirmDismiss = { showExitConfirmDialog = false },
        onExitConfirmSaveToBase = { showExitConfirmDialog = false; performSaveDiary(false) },
        onExitConfirmSaveToTreehole = { showExitConfirmDialog = false; performSaveDiary(true) },
        onExitConfirmDiscard = { viewModel.clearSession(); showExitConfirmDialog = false; onNavigateBack() },
        previewImageUri = previewImageUri,
        onPreviewImageDismiss = { previewImageUri = null },
        onBackClick = {
            if (hasChanges) {
                showExitConfirmDialog = true
            } else {
                viewModel.clearSession()
                onNavigateBack()
            }
        },
        modifier = modifier
    )

    if (showCreatePersonDialog) {
        LogCreatePersonDialog(
            onDismiss = { showCreatePersonDialog = false },
            neonAmber = neonAmber,
            onCreatePerson = { name, abbrev, relation ->
                coroutineScope.launch {
                    val pUuid = "p-" + UUID.randomUUID().toString()
                    val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                    val newPerson = PersonEntity(
                        uuid = pUuid,
                        name = name,
                        abbreviation = abbrev.uppercase(Locale.US),
                        relationship = relation,
                        createdAt = timeStamp
                    )
                    repository.insertPerson(newPerson)
                    selectedPersonUuids = selectedPersonUuids + pUuid
                    showCreatePersonDialog = false
                }
            }
        )
    }
}
