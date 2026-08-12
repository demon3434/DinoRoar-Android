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
import androidx.core.content.FileProvider



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
    val securePrefs = remember { com.example.dinoroar.data.local.SecurePrefs(context) }
    val sessionUuid = remember { editingLogUuid ?: "TEMP_DRAFT_KEY" }

    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }
    // 使用自定义 Factory 初始化 ViewModel，加入 securePrefs 与 apiService 依赖
    val viewModel: LogCreateViewModel = viewModel(
        factory = remember(repository, syncManager, audioRecorder, mediaCompressor, securePrefs, apiService) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LogCreateViewModel(
                        repository = repository,
                        syncManager = syncManager,
                        audioRecorder = audioRecorder,
                        mediaCompressor = mediaCompressor,
                        securePrefs = securePrefs,
                        apiService = apiService
                    ) as T
                }
            }
        }
    )

    // 状态代理与引用代理
    var selectedMoodDinoId by viewModel::selectedMoodDinoId
    var title by viewModel::title
    var content by viewModel::content
    var ownThoughts by viewModel::ownThoughts
    var selectedPersonUuids by viewModel::selectedPersonUuids
    var isEditMode by viewModel::isEditMode
    var selectedVideoResolution by viewModel::selectedVideoResolution

    // 纯 UI 交互状态（依然留在 remember 里）
    var showMoodSelectScreen by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCreatePersonDialog by remember { mutableStateOf(false) }

    val allPersons by repository.allPersons.collectAsStateWithLifecycle(initialValue = emptyList())
    val allDinoConfigs by repository.getAllActiveDinoConfigsFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val hasChanges = content.isNotBlank() || ownThoughts.isNotBlank() ||
            viewModel.selectedImageUris.isNotEmpty() || viewModel.selectedVideoUris.isNotEmpty() || viewModel.recordedFiles.isNotEmpty() ||
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
        viewModel.saveDiary(context, skipSync) { success, msg ->
            if (!success && msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else if (success) {
                Toast.makeText(
                    context,
                    if (skipSync) "🎈 秘密已悄悄藏进小树洞啦！\n⚠️ 注意：这仅仅保存在手机临时缓存里，如果清理手机垃圾缓存，秘密会丢失哦，记得及时【存入秘密基地】妥善同步！" else "✨ 秘密正在传送至秘密基地，小恐龙会在后台帮你安全存盘哦～",
                    Toast.LENGTH_LONG
                ).show()
                onNavigateBack()
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
    val dinos = remember(allDinoConfigs) { getDinoMoodList(allDinoConfigs) }

    // Launchers for media selection & capturing
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { viewModel.processAndAddImage(context, it) }
    }

    val cameraImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        ActivityStateTracker.isCameraActive = false
        if (success) {
            tempPhotoUri?.let { viewModel.processAndAddImage(context, it) }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { viewModel.processAndAddVideo(context, it) }
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
            uris.forEach { viewModel.importAudio(context, it) }
        }
    }

    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        ActivityStateTracker.isCameraActive = false
        if (success) {
            tempVideoUri?.let { viewModel.processAndAddVideo(context, it) }
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

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ActivityStateTracker.isExternalActivityActive = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selectedIds = result.data?.getStringArrayListExtra("selected_sticker_ids") ?: emptyList()
            applyStickerPickerResult(selectedIds, viewModel.stickers) {
                android.widget.Toast.makeText(context, "手账贴纸最多只能添加 6 张哦！", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val canvasPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ActivityStateTracker.isExternalActivityActive = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val returnedInstanceId = result.data?.getIntExtra("selected_canvas_instance_id", -1) ?: -1
            val returnedRatio = result.data?.getStringExtra("selected_canvas_aspect_ratio") ?: "2:1"
            val returnedImageUrl = result.data?.getStringExtra("selected_canvas_image_url")

            viewModel.updateCanvas(
                instanceId = if (returnedInstanceId == -1) null else returnedInstanceId,
                ratio = returnedRatio,
                imageUrl = returnedImageUrl
            )
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
        stickers = viewModel.stickers,
        stickerCacheMap = viewModel.stickerCacheMap,
        serverBaseUrl = serverBaseUrl,
        canvasInstanceId = viewModel.canvasInstanceId,
        canvasAspectRatio = viewModel.canvasAspectRatio,
        canvasImageUrl = viewModel.canvasImageUrl,
        onSelectCanvasClick = {
            val intent = Intent(context, com.example.dinoroar.ui.diary.CanvasPickerActivity::class.java).apply {
                putExtra("current_canvas_instance_id", viewModel.canvasInstanceId ?: -1)
                putExtra("current_canvas_aspect_ratio", viewModel.canvasAspectRatio)
            }
            ActivityStateTracker.isExternalActivityActive = true
            canvasPickerLauncher.launch(intent)
        },
        onSelectStickerClick = {
            val currentIds = viewModel.stickers.map { it.dinoId }
            val intent = Intent(context, com.example.dinoroar.ui.sticker.StickerPickerActivity::class.java).apply {
                putStringArrayListExtra("already_used_sticker_ids", ArrayList(currentIds))
            }
            ActivityStateTracker.isExternalActivityActive = true
            pickerLauncher.launch(intent)
        },
        audioRecorder = audioRecorder,
        selectedImageUris = viewModel.selectedImageUris,
        selectedVideoUris = viewModel.selectedVideoUris,
        recordedFiles = viewModel.recordedFiles,
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
                    if (viewModel.recordedFiles.size < 5) {
                        viewModel.processAndAddAudio(context, voiceFile)
                    } else {
                        Toast.makeText(context, "留言最多录制 5 段哦！", Toast.LENGTH_SHORT).show()
                        voiceFile.delete()
                    }
                }
            } else {
                if (viewModel.recordedFiles.size >= 5) {
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
        tempAttachments = viewModel.tempAttachments,
        initialAttachments = viewModel.initialAttachments,
        apiService = apiService,
        securePrefs = securePrefs,
        onPreviewImage = { uri -> previewImageUri = uri },
        onRemoveImage = { uri -> viewModel.removeImage(uri) },
        onRemoveVideo = { uri -> viewModel.removeVideo(uri) },
        onRemoveAudio = { file -> viewModel.removeAudio(file) },
        onSaveToBase = { performSaveDiary(false) },
        onSaveToTreehole = { performSaveDiary(true) },
        isCompressing = viewModel.isCompressing,
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
                viewModel.createPerson(name, abbrev, relation) {
                    showCreatePersonDialog = false
                }
            }
        )
    }
}
