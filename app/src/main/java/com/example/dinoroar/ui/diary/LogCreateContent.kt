package com.example.dinoroar.ui.diary

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.ui.main.StickerInfo
import java.io.File

/**
 * LogCreateScreenContent
 *
 * 日记编辑页面的纯 UI 渲染层（Box + Scaffold + 所有子 Section）。
 * 与业务状态和 ViewModel 完全解耦，只接收数据和回调参数。
 * 所有 Launcher、副作用、保存逻辑仍保留在 LogCreateScreen.kt 中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCreateScreenContent(
    // ── 主题色 ──
    darkBg: androidx.compose.ui.graphics.Color,
    cardBg: androidx.compose.ui.graphics.Color,
    neonBlue: androidx.compose.ui.graphics.Color,
    neonGreen: androidx.compose.ui.graphics.Color,
    neonRed: androidx.compose.ui.graphics.Color,
    neonAmber: androidx.compose.ui.graphics.Color,
    textPrimary: androidx.compose.ui.graphics.Color,
    textSecondary: androidx.compose.ui.graphics.Color,

    // ── 心情恐龙 ──
    selectedMoodDinoId: Int,
    dinos: List<Triple<Int, Int, String>>,
    showMoodSelectScreen: Boolean,
    onMoodClick: () -> Unit,
    onMoodSelected: (Int) -> Unit,
    onMoodSelectBack: () -> Unit,

    // ── 关系人物 ──
    selectedPersons: List<PersonEntity>,
    selectedPersonUuids: Set<String>,
    onNavigateToPersonSelect: () -> Unit,

    // ── 日记表单 ──
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    ownThoughts: String,
    onOwnThoughtsChange: (String) -> Unit,

    // ── 贴纸 ──
    stickers: SnapshotStateList<StickerInfo>,
    stickerCacheMap: Map<Int, String>,
    serverBaseUrl: String,
    canvasInstanceId: Int?,
    canvasAspectRatio: String,
    canvasImageUrl: String?,
    onSelectCanvasClick: () -> Unit,
    onSelectStickerClick: () -> Unit,

    // ── 多媒体 ──
    audioRecorder: com.example.dinoroar.media.AudioRecorder,
    selectedImageUris: SnapshotStateList<Uri>,
    selectedVideoUris: SnapshotStateList<Uri>,
    recordedFiles: SnapshotStateList<File>,
    isRecording: Boolean,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    onPickVideo: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onToggleRecording: () -> Unit,
    tempAttachments: SnapshotStateList<TempAttachment>,
    initialAttachments: List<AttachmentEntity>,
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    onPreviewImage: (Uri) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onRemoveVideo: (Uri) -> Unit,
    onRemoveAudio: (File) -> Unit,

    // ── 底部保存栏 ──
    onSaveToBase: () -> Unit,
    onSaveToTreehole: () -> Unit,

    // ── 对话框 ──
    isCompressing: Boolean,
    showExitConfirmDialog: Boolean,
    onExitConfirmDismiss: () -> Unit,
    onExitConfirmSaveToBase: () -> Unit,
    onExitConfirmSaveToTreehole: () -> Unit,
    onExitConfirmDiscard: () -> Unit,
    previewImageUri: Uri?,
    onPreviewImageDismiss: () -> Unit,

    // ── 顶栏返回 ──
    onBackClick: () -> Unit,

    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isSttHudActive by remember { mutableStateOf(false) }
    var isSttRecording by remember { mutableStateOf(false) }
    var isSttTranscribing by remember { mutableStateOf(false) }
    var isSttCancelHovered by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "🦖 编辑日记 🦕",
                            color = neonAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = neonAmber)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
                )
            },
            containerColor = darkBg,
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // 1. 守护恐龙心情
                LogCreateMoodSection(
                    selectedMoodDinoId = selectedMoodDinoId,
                    dinos = dinos,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    neonBlue = neonBlue,
                    neonAmber = neonAmber,
                    neonGreen = neonGreen,
                    onClick = onMoodClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 关联关系人物
                LogCreatePersonSection(
                    selectedPersons = selectedPersons,
                    selectedPersonUuids = selectedPersonUuids,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    neonBlue = neonBlue,
                    onNavigateToPersonSelect = onNavigateToPersonSelect
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 标题/正文/悄悄话 输入区
                LogCreateFormSection(
                    title = title,
                    onTitleChange = onTitleChange,
                    content = content,
                    onContentChange = onContentChange,
                    ownThoughts = ownThoughts,
                    onOwnThoughtsChange = onOwnThoughtsChange,
                    audioRecorder = audioRecorder,
                    apiService = apiService,
                    onHudStateChange = { active, recording, transcribing, cancel ->
                        isSttHudActive = active
                        isSttRecording = recording
                        isSttTranscribing = transcribing
                        isSttCancelHovered = cancel
                    },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    neonBlue = neonBlue
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. 贴纸画板与商城入口
                LogCreateStickerSection(
                    stickers = stickers,
                    stickerCacheMap = stickerCacheMap,
                    serverBaseUrl = serverBaseUrl,
                    cardBg = cardBg,
                    neonBlue = neonBlue,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    canvasInstanceId = canvasInstanceId,
                    canvasAspectRatio = canvasAspectRatio,
                    canvasImageUrl = canvasImageUrl,
                    onSelectCanvasClick = onSelectCanvasClick,
                    onSelectStickerClick = onSelectStickerClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. 多媒体工具栏（相册/拍照/视频/录音）
                LogCreateMediaSection(
                    selectedImageUrisCount = selectedImageUris.size,
                    selectedVideoUrisCount = selectedVideoUris.size,
                    recordedFilesCount = recordedFiles.size,
                    isRecording = isRecording,
                    onPickImage = onPickImage,
                    onCaptureImage = onCaptureImage,
                    onPickVideo = onPickVideo,
                    onCaptureVideo = onCaptureVideo,
                    onPickAudio = onPickAudio,
                    onToggleRecording = onToggleRecording,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    neonRed = neonRed
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 已选多媒体附件预览与命名管理区
                LogCreateMediaPreviewList(
                    selectedImageUris = selectedImageUris,
                    selectedVideoUris = selectedVideoUris,
                    recordedFiles = recordedFiles,
                    tempAttachments = tempAttachments,
                    initialAttachments = initialAttachments,
                    apiService = apiService,
                    securePrefs = securePrefs,
                    neonBlue = neonBlue,
                    neonGreen = neonGreen,
                    neonRed = neonRed,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    onPreviewImage = onPreviewImage,
                    onRemoveImage = onRemoveImage,
                    onRemoveVideo = onRemoveVideo,
                    onRemoveAudio = onRemoveAudio
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 底部双通道保存栏
                LogCreateBottomBar(
                    onSaveToBase = onSaveToBase,
                    onSaveToTreehole = onSaveToTreehole,
                    neonAmber = neonAmber
                )

                // 🔐 后台异步压缩加密遮罩
                LogCreateCompressingOverlay(
                    isCompressing = isCompressing,
                    cardBg = cardBg,
                    neonBlue = neonBlue,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                // 退出确认弹窗
                if (showExitConfirmDialog) {
                    LogCreateExitConfirmDialog(
                        onDismiss = onExitConfirmDismiss,
                        onSaveToBase = onExitConfirmSaveToBase,
                        onSaveToTreehole = onExitConfirmSaveToTreehole,
                        onDiscard = onExitConfirmDiscard,
                        cardBg = cardBg,
                        neonAmber = neonAmber,
                        neonBlue = neonBlue,
                        neonRed = neonRed,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }

                // 大图全屏预览弹窗
                if (previewImageUri != null) {
                    LogCreateImagePreviewDialog(
                        previewImageUri = previewImageUri,
                        onDismiss = onPreviewImageDismiss
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 心情选择器全屏滑入覆盖层
        AnimatedVisibility(
            visible = showMoodSelectScreen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            LogMoodSelectView(
                currentMoodId = selectedMoodDinoId,
                dinos = dinos,
                onMoodSelected = onMoodSelected,
                onNavigateBack = onMoodSelectBack,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 🎙️ STT 语音输入 Center Wave HUD 全屏悬浮蒙层
        AnimatedVisibility(
            visible = isSttHudActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CenterWaveHud(
                isRecording = isSttRecording,
                isTranscribing = isSttTranscribing,
                isCancelHovered = isSttCancelHovered
            )
        }
    }
}
