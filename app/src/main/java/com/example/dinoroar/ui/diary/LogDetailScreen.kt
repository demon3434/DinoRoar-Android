@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.dinoroar.ui.diary

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.dinoroar.network.MediaCacheManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.dinoroar.ui.main.StickerInfo
import com.example.dinoroar.ui.main.getDinoResource
import com.example.dinoroar.ui.main.getDinoName

private val BackgroundDownloadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun LogDetailScreen(
    repository: DataRepository,
    syncManager: SyncManager,
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    logUuid: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val stickersConfig = remember {
        mutableStateListOf<com.example.dinoroar.network.StickerConfigDto>().apply {
            addAll(com.example.dinoroar.network.StickerConfigCache.get())
        }
    }
    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }
    LaunchedEffect(Unit) {
        try {
            val list = apiService.getStickersConfig()
            val flatList = list.flatMap { it.stickers }
            com.example.dinoroar.network.StickerConfigCache.set(flatList)
            stickersConfig.clear()
            stickersConfig.addAll(flatList)
        } catch (e: Exception) {
            android.util.Log.e("LogDetailScreen", "加载贴纸配置失败: ${e.message}")
        }
    }

    val allCategories by repository.allCategories.collectAsStateWithLifecycle(initialValue = emptyList())

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Promote Dialog States
    var showPromotePersonDialog by remember { mutableStateOf<PersonEntity?>(null) }
    var promotePersonName by remember { mutableStateOf("") }
    var promotePersonAbbrev by remember { mutableStateOf("") }
    var promotePersonRelation by remember { mutableStateOf("") }
    var promotePersonCategoryUuid by remember { mutableStateOf<String?>(null) }
    var promotePersonColor by remember { mutableStateOf("red") }

    var logWithConfig by remember { mutableStateOf<com.example.dinoroar.data.local.LogWithConfig?>(null) }
    var attachments by remember { mutableStateOf<List<AttachmentEntity>>(emptyList()) }
    val associatedPersons by repository.getPersonsForLogFlow(logUuid).collectAsStateWithLifecycle(initialValue = emptyList())

    val logCanvas by repository.getLogCanvasByUuidFlow(logUuid).collectAsStateWithLifecycle(initialValue = null)
    val canvasImageUrl by repository.getCanvasImageUrlFlow(logUuid).collectAsStateWithLifecycle(initialValue = null)

    val canvasInstanceId = logCanvas?.canvasInstanceId
    val canvasAspectRatio = logCanvas?.canvasAspectRatio ?: "2:1"

    LaunchedEffect(logUuid) {
        logWithConfig = repository.getLogWithConfigByUuid(logUuid)

        repository.getAttachmentsForLogFlow(logUuid).collect {
            attachments = it
        }
    }

    val item = logWithConfig ?: return
    val log = item.log
    val dinoConfig = item.dinoConfig

    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonAmber = appColors.neonAmber
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val neonRed = appColors.neonRed
    val cardBg = appColors.cardBg

    val dinoResId = remember(dinoConfig, log.moodDinoId) {
        if (dinoConfig != null) {
            val resourceName = "mood_" + dinoConfig.legacyKey.lowercase(java.util.Locale.US)
            val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            if (resId != 0) resId else getDinoResource(log.moodDinoId)
        } else {
            getDinoResource(log.moodDinoId)
        }
    }
    val dinoName = dinoConfig?.name ?: getDinoName(log.moodDinoId)
    val dinoMood = dinoConfig?.moodLabel ?: com.example.dinoroar.ui.main.getDinoMoodLabel(log.moodDinoId)
    val moodTip = dinoConfig?.moodTip ?: com.example.dinoroar.ui.main.getDinoMoodTip(log.moodDinoId)

    var isPlayingVoice by remember { mutableStateOf(false) }
    // 使用 ExoPlayer 替代 MediaPlayer，支持边下边播缓存
    var audioPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var previewImageAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }
    val isDownloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    var activePlayVideoAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }
    // 点击时锁定 URL，防止背景下载完成后 recomposition 导致 URL 切换引发双 ExoPlayer init
    var activePlayVideoUrl by remember { mutableStateOf<String?>(null) }
    val imageAttachments = remember(attachments) { attachments.filter { it.mimeType.startsWith("image/") } }
    val videoAttachments = remember(attachments) { attachments.filter { it.mimeType.startsWith("video/") } }
    val audioAttachments = remember(attachments) { attachments.filter { it.mimeType.startsWith("audio/") } }

    // 页面销毁时释放 ExoPlayer
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🦖 日记全文 🦕",
                        color = neonAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = neonAmber
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(logUuid) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = neonAmber
                        )
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = neonRed
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题展示
            val displayTitle = if (log.title.isNullOrBlank()) {
                "无标题"
            } else {
                if (log.title.length > 10) log.title.substring(0, 10) + "..." else log.title
            }

            Text(
                text = displayTitle,
                color = neonAmber,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // 时间展示
            val incidentDateFormatted = try {
                log.incidentDate.replace("T", " ").substring(0, 19)
            } catch (e: Exception) { log.incidentDate }
            val updatedAtFormatted = formatUtcToLocal(log.updatedAt)
            val isEdited = log.incidentDate != log.updatedAt && log.updatedAt.isNotBlank() && incidentDateFormatted != updatedAtFormatted

            Column {
                Text(
                    text = "写于：$incidentDateFormatted",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (isEdited) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "改于：$updatedAtFormatted",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 心情与恐龙
            Card(
                colors = CardDefaults.cardColors(containerColor = appColors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = dinoResId),
                        contentDescription = dinoName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = neonGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(dinoMood)
                                }
                                append(" ")
                                withStyle(
                                    SpanStyle(
                                        color = neonAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(dinoName)
                                }
                            },
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (!moodTip.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = moodTip,
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 人物关联
            if (associatedPersons.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    associatedPersons.forEach { person ->
                        val colorPair = remember(person.colorTag, person.isTemporary) {
                            if (person.isTemporary) {
                                com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag("gray")
                            } else {
                                com.example.dinoroar.ui.person.DinoColorPalette.getColorByTag(person.colorTag)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorPair.bg)
                                .border(1.dp, colorPair.text.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (person.isTemporary) {
                                        showPromotePersonDialog = person
                                        promotePersonName = person.name
                                        promotePersonAbbrev = com.example.dinoroar.ui.person.PinyinUtils.getAbbreviation(person.name)
                                        promotePersonRelation = person.relationship
                                        promotePersonCategoryUuid = if (allCategories.isNotEmpty()) allCategories[0].uuid else null
                                        promotePersonColor = "red"
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            val label = if (!person.relationship.isNullOrEmpty()) {
                                "${person.name} (${person.relationship})"
                            } else {
                                person.name
                            }
                            val suffix = if (person.isTemporary) " (路人转正➔)" else ""
                            Text(
                                text = "$label$suffix",
                                color = colorPair.text,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

            // Parse stickers from content（使用 StickerViewerCanvas.kt 中的工具函数）
            val rawContent = log.content
            val stickersList = remember(rawContent) { parseStickerList(rawContent) }
            val displayContent = remember(rawContent) { stripStickerTags(rawContent) }

            // 日记正文
            val paragraphs = displayContent.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = "\u3000\u3000$paragraph",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 22.sp
                    )
                }
            }

            // 贴纸手账展示（已解耦至 StickerViewerCanvas.kt 组件）
            StickerViewerCanvas(
                stickers = stickersList,
                stickersConfig = stickersConfig,
                serverBaseUrl = serverBaseUrl,
                cardBg = appColors.cardBg,
                neonBlue = neonBlue,
                canvasInstanceId = canvasInstanceId,
                canvasAspectRatio = canvasAspectRatio,
                canvasImageUrl = canvasImageUrl
            )

            // 悄悄话
            log.ownThoughts?.let { thoughts ->
                if (thoughts.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, neonGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "💡 我的悄悄话:",
                                color = neonGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val thoughtsParagraphs = thoughts.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                thoughtsParagraphs.forEach { tParagraph ->
                                    Text(
                                        text = "\u3000\u3000$tParagraph",
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 附件部分

            // A. 图片
            if (imageAttachments.isNotEmpty()) {
                Text(
                    text = "📸 现场图片 (${imageAttachments.size}):",
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                val imageChunks = imageAttachments.chunked(3)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imageChunks.forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { att ->
                                val file = if (att.localFilePath != null) {
                                    File(att.localFilePath)
                                } else {
                                    File(context.cacheDir, "${att.uuid}_image.png")
                                }
                                val fileUri = Uri.fromFile(file)
                                val isImgDownloading = isDownloadingMap[att.uuid] ?: false
                                val fileExists = remember(att.localFilePath, isImgDownloading) { file.exists() }
                                val bitmap = rememberUriImageForPreview(fileUri, fileExists, context)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(appColors.cardBg.copy(alpha = 0.2f))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isImgDownloading) {
                                            coroutineScope.launch {
                                                if (!file.exists()) {
                                                    isDownloadingMap[att.uuid] = true
                                                    try {
                                                        Toast.makeText(context, "正在从秘密基地拉取图片留念...", Toast.LENGTH_SHORT).show()
                                                        val ok = downloadAttachmentFile(apiService, att.uuid, file)
                                                        if (!ok) {
                                                            Toast.makeText(context, "拉取图片失败，请检查网络连接！", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            repository.insertAttachment(att.copy(localFilePath = file.absolutePath))
                                                        }
                                                    } finally {
                                                        isDownloadingMap.remove(att.uuid)
                                                    }
                                                }
                                                previewImageAttachment = att
                                            }
                                        }
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Image preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (isImgDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.align(Alignment.Center).size(24.dp),
                                            color = neonGreen,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    // 物理大小标签
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatFileSize(att.fileSize),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    // 备注标签
                                    if (!att.title.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.TopStart)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = att.title,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                fontFamily = FontFamily.Monospace,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            val remaining = 3 - chunk.size
                            repeat(remaining) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // B. 视频
            if (videoAttachments.isNotEmpty()) {
                Text(
                    text = "🎥 现场视频 (${videoAttachments.size}):",
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                val videoChunks = videoAttachments.chunked(3)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    videoChunks.forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { att ->
                                val file = if (att.localFilePath != null) {
                                    File(att.localFilePath)
                                } else {
                                    File(context.cacheDir, "${att.uuid}_video.mp4")
                                }
                                val fileUri = Uri.fromFile(file)
                                // 用轮询代替静态 remember，文件落盘后（最多 30 秒内）自动更新缩略图，无需切换页面
                                var fileExists by remember(att.localFilePath) { mutableStateOf(file.exists()) }
                                LaunchedEffect(att.localFilePath) {
                                    if (!fileExists) {
                                        repeat(30) {
                                            delay(1000L)
                                            if (file.exists()) {
                                                fileExists = true
                                                return@LaunchedEffect
                                            }
                                        }
                                    }
                                }
                                val thumbnail = rememberVideoThumbnailForPreview(fileUri, fileExists, context)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .border(1.dp, neonBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            // 点击时立即计算并锁定 URL，不依赖后续 recomposition 重算，防止背景下载后 URL 切换导致 ExoPlayer 被意外重建
                                            val baseUrl = securePrefs.serverUrl?.removeSuffix("/") ?: ""
                                            val tk = securePrefs.token ?: ""
                                            val localExists = file.exists()
                                            val url = if (localExists) {
                                                Uri.fromFile(file).toString()
                                            } else {
                                                "$baseUrl/api/attachments/download/${att.uuid}?token=$tk"
                                            }
                                            com.example.dinoroar.media.DiagnosticLogger.log("LogDetailScreen", "I", "Video thumbnail clicked: uuid=${att.uuid}, fileExists=$localExists, path=${file.absolutePath}")
                                            activePlayVideoUrl = url
                                            activePlayVideoAttachment = att
                                        }
                                ) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail,
                                            contentDescription = "Video preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(36.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = neonGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    // 物理大小标签
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatFileSize(att.fileSize),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    // 备注标签
                                    if (!att.title.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.TopStart)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = att.title,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                fontFamily = FontFamily.Monospace,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            val remaining = 3 - chunk.size
                            repeat(remaining) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // C. 录音（Task 2.3：使用 ExoPlayer + CacheDataSource 实现边下边播+永久缓存）
            audioAttachments.forEach { att ->
                val isAudioDownloading = isDownloadingMap[att.uuid] ?: false
                val sizeStr = remember(att.fileSize) { formatFileSize(att.fileSize) }
                val displayTitle = if (!att.title.isNullOrBlank()) att.title else att.fileName
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (isPlayingVoice) {
                            // 停止当前播放
                            audioPlayer?.stop()
                            audioPlayer?.release()
                            audioPlayer = null
                            isPlayingVoice = false
                        } else {
                            if (isAudioDownloading) return@Button // 防并发拦截
                            coroutineScope.launch {
                                val baseUrl = securePrefs.serverUrl?.removeSuffix("/") ?: ""
                                val tk = securePrefs.token
                                // 构建带 token 的流媒体 URL，直接走 ExoPlayer CacheDataSource 边下边播
                                val streamUrl = "$baseUrl/api/attachments/download/${att.uuid}?token=${tk ?: ""}"

                                // 若本地已缓存，优先使用本地文件路径
                                val localFile = if (att.localFilePath != null) File(att.localFilePath) else null
                                val playUri: Uri = if (localFile != null && localFile.exists()) {
                                    Uri.fromFile(localFile)
                                } else {
                                    Uri.parse(streamUrl)
                                }

                                try {
                                    val cacheFactory = MediaCacheManager.createCacheDataSourceFactory(context, tk)
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(playUri)
                                        .setMimeType(androidx.media3.common.MimeTypes.AUDIO_MP4)
                                        .build()
                                    val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
                                        .createMediaSource(mediaItem)

                                    val player = ExoPlayer.Builder(context).build().apply {
                                        setMediaSource(mediaSource)
                                        prepare()
                                        playWhenReady = true
                                        addListener(object : androidx.media3.common.Player.Listener {
                                            override fun onPlaybackStateChanged(state: Int) {
                                                if (state == androidx.media3.common.Player.STATE_ENDED) {
                                                    isPlayingVoice = false
                                                    audioPlayer?.release()
                                                    audioPlayer = null
                                                    // 播完后将落盘路径写入 Room（若还未写入）
                                                    if (localFile == null || !localFile.exists()) {
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            val destFile = File(context.cacheDir, "${att.uuid}.m4a")
                                                            val ok = downloadAttachmentFile(apiService, att.uuid, destFile)
                                                            if (ok) repository.insertAttachment(att.copy(localFilePath = destFile.absolutePath))
                                                        }
                                                    }
                                                }
                                            }
                                        })
                                    }
                                    // 释放上一个播放器
                                    audioPlayer?.release()
                                    audioPlayer = player
                                    isPlayingVoice = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "音频播放出错：${e.message}", Toast.LENGTH_SHORT).show()
                                    isPlayingVoice = false
                                }
                            }
                        }
                    },
                    enabled = !isAudioDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAudioDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = neonGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isAudioDownloading) {
                            "⏳ \"$displayTitle\" ($sizeStr)..."
                        } else if (isPlayingVoice) {
                            "⏸ \"$displayTitle\" ($sizeStr)"
                        } else {
                            "▶ \"$displayTitle\" ($sizeStr)"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Task 3.3：视频流媒体播放弹窗（ExoPlayer + CacheDataSource）
    // playUrl 在点击时已锁定至 activePlayVideoUrl，不随 recomposition 重算，防止双 ExoPlayer 问题
    activePlayVideoAttachment?.let { att ->
        val playUrl = activePlayVideoUrl ?: return@let
        VideoPlayerDialog(
            videoUrl = playUrl,
            token = securePrefs.token,
            title = att.title,
            onDismiss = {
                activePlayVideoAttachment = null
                activePlayVideoUrl = null
                // 播放结束或弹窗关闭后，静默下载并落盘，保证下次完全离线秒开且能生成缩略图，避免播放时带宽竞争抢网络
                BackgroundDownloadScope.launch {
                    val destFile = if (att.localFilePath != null) File(att.localFilePath) else File(context.cacheDir, "${att.uuid}_video.mp4")
                    if (!destFile.exists()) {
                        val ok = downloadAttachmentFile(apiService, att.uuid, destFile)
                        if (ok) {
                            repository.insertAttachment(att.copy(localFilePath = destFile.absolutePath))
                        }
                    }
                }
            }
        )
    }

    // 路人转正弹窗
    showPromotePersonDialog?.let { person ->
        AlertDialog(
            onDismissRequest = { showPromotePersonDialog = null },
            title = { Text("🌟 临时人物转正 🌟", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "💡 转正后，该人物将被归入选定的正式分类下，并展现于写日记和统计页面中！",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 16.sp
                        )
                    }

                    OutlinedTextField(
                        value = promotePersonName,
                        onValueChange = {
                            promotePersonName = it
                            promotePersonAbbrev = com.example.dinoroar.ui.person.PinyinUtils.getAbbreviation(it)
                        },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = promotePersonAbbrev,
                        onValueChange = { promotePersonAbbrev = it },
                        label = { Text("首字母缩写") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = promotePersonRelation,
                        onValueChange = { promotePersonRelation = it },
                        label = { Text("关系描述 (如爸爸, 同桌)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Suggestions for relations
                    Text("常用推荐:", fontSize = 11.sp, color = Color.Gray)
                    val suggestions = listOf("好朋友", "普通同学", "老师", "家长", "邻居")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.take(4).forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable { promotePersonRelation = suggestion }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(suggestion, fontSize = 11.sp)
                            }
                        }
                    }

                    // Category dropdown list
                    if (allCategories.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val currentCategoryName = allCategories.find { it.uuid == promotePersonCategoryUuid }?.name ?: "选择分类"
                        Text("所属分类:", fontSize = 11.sp, color = Color.Gray)
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(currentCategoryName)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                allCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            promotePersonCategoryUuid = category.uuid
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (promotePersonName.isBlank()) {
                            Toast.makeText(context, "姓名不能为空！", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (promotePersonCategoryUuid == null) {
                            Toast.makeText(context, "请选择人物分类！所有正式关系人必须指定分类。", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val updated = person.copy(
                                name = promotePersonName.trim(),
                                abbreviation = promotePersonAbbrev.uppercase(Locale.US),
                                relationship = promotePersonRelation.trim(),
                                categoryUuid = promotePersonCategoryUuid,
                                colorTag = promotePersonColor,
                                isTemporary = false, // 💡 转正！
                                isSynced = false
                            )
                            repository.insertPerson(updated)
                            showPromotePersonDialog = null
                            Toast.makeText(context, "人物已成功转正并归类！", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("转正并保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPromotePersonDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 图片放大预览弹窗
    previewImageAttachment?.let { att ->
        val file = if (att.localFilePath != null) {
            File(att.localFilePath)
        } else {
            File(context.cacheDir, "${att.uuid}_image.png")
        }
        val fileUri = Uri.fromFile(file)
        Dialog(
            onDismissRequest = { previewImageAttachment = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val fullBitmap = rememberUriImageForPreview(fileUri, true, context, maxW = null)
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { previewImageAttachment = null },
                contentAlignment = Alignment.Center
            ) {
                if (fullBitmap != null) {
                    Image(
                        bitmap = fullBitmap,
                        contentDescription = "Zoomable full preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offset += pan * scale
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
                
                if (!att.title.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = att.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "确认删除日记？",
                    color = neonAmber,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "删除此篇日记后，本地多媒体文件将被清理，下次同步时将上报删除指令。该操作不可撤销哦！",
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            repository.softDeleteLog(logUuid)
                            Toast.makeText(context, "日记已删除，将在下次同步时上报", Toast.LENGTH_SHORT).show()
                            syncManager.sync()
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonRed)
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = textSecondary)
                }
            },
            containerColor = cardBg,
            properties = DialogProperties(usePlatformDefaultWidth = true)
        )
    }
}





@Composable
private fun rememberUriImageForPreview(uri: Uri, fileExists: Boolean, context: android.content.Context, maxW: Int? = 300): ImageBitmap? {
    var bitmap by remember(uri, fileExists) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, fileExists) {
        if (!fileExists) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = android.graphics.BitmapFactory.decodeStream(stream)
                    if (raw != null) {
                        val scaled = if (maxW != null) {
                            val scale = raw.width.toFloat() / maxW
                            if (scale > 1f) {
                                android.graphics.Bitmap.createScaledBitmap(raw, maxW, (raw.height / scale).toInt(), true)
                            } else raw
                        } else raw
                        bitmap = scaled.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                Log.e("UriImagePreview", "Failed to decode bitmap", e)
            }
        }
    }
    return bitmap
}

@Composable
private fun rememberVideoThumbnailForPreview(uri: Uri, fileExists: Boolean, context: android.content.Context): ImageBitmap? {
    var bitmap by remember(uri, fileExists) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, fileExists) {
        if (!fileExists) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val raw = retriever.getFrameAtTime(-1)
                if (raw != null) {
                    val maxW = 300
                    val scale = raw.width.toFloat() / maxW
                    val scaled = if (scale > 1f) {
                        android.graphics.Bitmap.createScaledBitmap(raw, maxW, (raw.height / scale).toInt(), true)
                    } else raw
                    bitmap = scaled.asImageBitmap()
                }
            } catch (e: Exception) {
                Log.e("VideoThumbnailPreview", "Failed to extract thumbnail", e)
            } finally {
                try { retriever?.release() } catch (e: Exception) {}
            }
        }
    }
    return bitmap
}

private fun formatUtcToLocal(utcTime: String): String {
    if (utcTime.isBlank()) return ""
    val clean = utcTime.replace("T", " ")
    val formats = listOf(
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss"
    )
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(clean)
            if (date != null) {
                val localSdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                localSdf.timeZone = java.util.TimeZone.getDefault()
                return localSdf.format(date)
            }
        } catch (e: Exception) {}
    }
    return utcTime
}
