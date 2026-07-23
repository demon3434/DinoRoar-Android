package com.example.dinoroar.ui.diary

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import java.io.File
import kotlinx.coroutines.launch

/**
 * 日记编辑/创建页面已选多媒体留念（图片、视频、录音）预览与重命名列表组件。
 * 从 LogCreateScreen 中抽取出来，实现 UI 模块化与代码瘦身。
 */
@Composable
fun LogCreateMediaPreviewList(
    selectedImageUris: MutableList<Uri>,
    selectedVideoUris: MutableList<Uri>,
    recordedFiles: MutableList<File>,
    tempAttachments: MutableList<TempAttachment>,
    initialAttachments: List<AttachmentEntity>,
    apiService: DinoApiService,
    securePrefs: SecurePrefs,
    neonBlue: Color,
    neonGreen: Color,
    neonRed: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    onPreviewImage: (Uri) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onRemoveVideo: (Uri) -> Unit,
    onRemoveAudio: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 语音播放临时状态（局部状态管理）
    var isPlayingVoiceFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. 已选图片现场留念
        if (selectedImageUris.isNotEmpty()) {
            Text(
                text = "📸 已选图片现场留念 (${selectedImageUris.size}/9):",
                color = textPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            val imageChunks = selectedImageUris.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imageChunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunk.forEach { uri ->
                            val bitmap = rememberUriImage(uri, context)
                            val tempAtt = tempAttachments.find {
                                it.sourceUri == uri || (uri.scheme == "file" && it.file?.absolutePath == uri.path)
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cardBg)
                                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                val isFileScheme = uri.scheme == "file"
                                                val targetFile = if (isFileScheme) File(uri.path ?: "") else null
                                                if (targetFile != null && !targetFile.exists()) {
                                                    Toast.makeText(context, "正在从秘密基地拉取图片留念...", Toast.LENGTH_SHORT).show()
                                                    val initialAtt = initialAttachments.find { it.localFilePath == targetFile.absolutePath }
                                                    val uuid = initialAtt?.uuid ?: targetFile.nameWithoutExtension
                                                    val ok = downloadAttachmentFile(apiService, uuid, targetFile)
                                                    if (!ok) {
                                                        Toast.makeText(context, "拉取图片失败，请检查网络连接！", Toast.LENGTH_SHORT).show()
                                                        return@launch
                                                    }
                                                }
                                                onPreviewImage(uri)
                                            }
                                        }
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Image preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }

                                    // 压缩中指示遮罩层
                                    if (tempAtt?.status == "compressing") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = neonBlue
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("整理中...", color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    // 物理大小标签
                                    val imageSize = when {
                                        tempAtt?.status == "ready" && tempAtt.file != null -> tempAtt.file.length()
                                        uri.scheme == "file" -> initialAttachments.find { it.localFilePath == uri.path }?.fileSize ?: 0L
                                        else -> 0L
                                    }
                                    if (imageSize > 0L) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatFileSize(imageSize),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // 右上角删除按钮
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                            .clickable {
                                                onRemoveImage(uri)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "删除图片",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 给留念命名文本框
                                var titleText by remember(tempAtt?.title) { mutableStateOf(tempAtt?.title ?: "") }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = titleText,
                                    onValueChange = { newTitle ->
                                        titleText = newTitle
                                        val idx = tempAttachments.indexOfFirst { it.uuid == tempAtt?.uuid }
                                        if (idx != -1) {
                                            tempAttachments[idx] = tempAttachments[idx].copy(title = newTitle)
                                        }
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = textPrimary,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                        .border(1.dp, textSecondary.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                        .padding(vertical = 4.dp),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                            if (titleText.isEmpty()) {
                                                Text("给留念起名...", color = textSecondary.copy(alpha = 0.5f), fontSize = 10.sp)
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
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

        // B. 已选视频现场留念
        if (selectedVideoUris.isNotEmpty()) {
            Text(
                text = "🎥 已选视频现场留念 (${selectedVideoUris.size}/3):",
                color = textPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            val videoChunks = selectedVideoUris.chunked(3)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                videoChunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunk.forEach { uri ->
                            val thumbnail = rememberVideoThumbnail(uri, context)
                            val tempAtt = tempAttachments.find {
                                it.sourceUri == uri || (uri.scheme == "file" && it.file?.absolutePath == uri.path)
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .border(1.dp, neonBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                val isFileScheme = uri.scheme == "file"
                                                val targetFile = if (isFileScheme) File(uri.path ?: "") else null
                                                if (targetFile != null && !targetFile.exists()) {
                                                    Toast.makeText(context, "正在从秘密基地拉取视频留念...", Toast.LENGTH_SHORT).show()
                                                    val initialAtt = initialAttachments.find { it.localFilePath == targetFile.absolutePath }
                                                    val uuid = initialAtt?.uuid ?: targetFile.nameWithoutExtension
                                                    val ok = downloadAttachmentFile(apiService, uuid, targetFile)
                                                    if (!ok) {
                                                        Toast.makeText(context, "拉取视频失败，请检查网络连接！", Toast.LENGTH_SHORT).show()
                                                        return@launch
                                                    }
                                                }
                                                try {
                                                    securePrefs.isExternalActivityActive = true
                                                    val playUri = if (uri.scheme == "file") {
                                                        val file = File(uri.path ?: "")
                                                        FileProvider.getUriForFile(context, "com.example.dinoroar.fileprovider", file)
                                                    } else {
                                                        uri
                                                    }
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(playUri, "video/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e("LogCreateScreen", "Play video failed", e)
                                                    Toast.makeText(context, "无法播放该视频", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                ) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail,
                                            contentDescription = "Video preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
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

                                    // 压缩中指示遮罩层
                                    if (tempAtt?.status == "compressing") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = neonBlue
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("整理中...", color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    // 物理大小标签
                                    val videoSize = when {
                                        tempAtt?.status == "ready" && tempAtt.file != null -> tempAtt.file.length()
                                        uri.scheme == "file" -> initialAttachments.find { it.localFilePath == uri.path }?.fileSize ?: 0L
                                        else -> 0L
                                    }
                                    if (videoSize > 0L) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatFileSize(videoSize),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // 右上角删除按钮
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                            .clickable {
                                                onRemoveVideo(uri)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "删除视频",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 给留念命名文本框
                                var titleText by remember(tempAtt?.title) { mutableStateOf(tempAtt?.title ?: "") }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = titleText,
                                    onValueChange = { newTitle ->
                                        titleText = newTitle
                                        val idx = tempAttachments.indexOfFirst { it.uuid == tempAtt?.uuid }
                                        if (idx != -1) {
                                            tempAttachments[idx] = tempAttachments[idx].copy(title = newTitle)
                                        }
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = textPrimary,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                        .border(1.dp, textSecondary.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                        .padding(vertical = 4.dp),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                            if (titleText.isEmpty()) {
                                                Text("给留念起名...", color = textSecondary.copy(alpha = 0.5f), fontSize = 10.sp)
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
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

        // C. 已选语音留言列表
        if (recordedFiles.isNotEmpty()) {
            Text(
                text = "🔊 已选语音留念 (${recordedFiles.size}/5):",
                color = textPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            recordedFiles.forEachIndexed { index, voiceFile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(cardBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isCurrentPlaying = isPlayingVoiceFile == voiceFile
                    val tempAtt = tempAttachments.find { it.file?.absolutePath == voiceFile.absolutePath }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isCurrentPlaying) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlayingVoiceFile = null
                                } else {
                                    coroutineScope.launch {
                                        if (!voiceFile.exists()) {
                                            Toast.makeText(context, "正在从秘密基地读取留言录音...", Toast.LENGTH_SHORT).show()
                                            val initialAtt = initialAttachments.find { it.localFilePath == voiceFile.absolutePath }
                                            val uuid = initialAtt?.uuid ?: voiceFile.nameWithoutExtension
                                            val ok = downloadAttachmentFile(apiService, uuid, voiceFile)
                                            if (!ok) {
                                                Toast.makeText(context, "读取录音失败，请检查网络连接！", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                        }
                                        try {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                            mediaPlayer = MediaPlayer().apply {
                                                setDataSource(voiceFile.absolutePath)
                                                prepare()
                                                start()
                                                setOnCompletionListener {
                                                    isPlayingVoiceFile = null
                                                }
                                            }
                                            isPlayingVoiceFile = voiceFile
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "播放失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = if (isCurrentPlaying) "Stop" else "Play",
                                tint = if (isCurrentPlaying) neonRed else neonBlue
                            )
                        }
                        var titleText by remember(tempAtt?.title) { mutableStateOf(tempAtt?.title ?: "") }
                        Column {
                            Text(
                                text = "语音留念段 #${index + 1}",
                                color = textSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = titleText,
                                onValueChange = { newTitle ->
                                    titleText = newTitle
                                    val idx = tempAttachments.indexOfFirst { it.uuid == tempAtt?.uuid }
                                    if (idx != -1) {
                                        tempAttachments[idx] = tempAttachments[idx].copy(title = newTitle)
                                    }
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = textPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier
                                    .width(120.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                decorationBox = { inner ->
                                    Box {
                                        if (titleText.isEmpty()) {
                                            Text("给声音起名...", color = textSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val audioSize = when {
                            tempAtt?.status == "ready" && tempAtt.file != null -> tempAtt.file.length()
                            voiceFile.exists() -> voiceFile.length()
                            else -> initialAttachments.find { it.localFilePath == voiceFile.absolutePath }?.fileSize ?: 0L
                        }
                        if (audioSize > 0L) {
                            Text(
                                text = formatFileSize(audioSize),
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isCurrentPlaying) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlayingVoiceFile = null
                                }
                                val isExisting = initialAttachments.any { it.localFilePath == voiceFile.absolutePath }
                                if (!isExisting) {
                                    voiceFile.delete()
                                }
                                onRemoveAudio(voiceFile)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = neonRed
                            )
                        }
                    }
                }
            }
        }
    }
}
