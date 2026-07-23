package com.example.dinoroar.ui.diary

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.theme.LocalAppColors
import java.io.File

/**
 * 媒体附件区域带光晕效果的功能按钮组件，支持按下动画及禁用态样式。
 */
@Composable
fun GlassMediaButton(
    icon: String,
    text: String,
    glowColor: Color,
    textColor: Color,
    secondaryColor: Color,
    isLight: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val resolvedTextColor = if (isLight && !appColors.isDark && textColor == Color.White) {
        val r = (glowColor.red * 0.55f).coerceIn(0f, 1f)
        val g = (glowColor.green * 0.55f).coerceIn(0f, 1f)
        val b = (glowColor.blue * 0.55f).coerceIn(0f, 1f)
        Color(r, g, b, 1f)
    } else {
        textColor
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isLight) {
                    if (enabled) glowColor.copy(alpha = 0.22f) else glowColor.copy(alpha = 0.08f)
                } else {
                    if (enabled) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.02f)
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (enabled) {
                        listOf(glowColor.copy(alpha = 0.8f), glowColor.copy(alpha = 0.35f), glowColor.copy(alpha = 0.6f))
                    } else {
                        listOf(Color.Gray.copy(alpha = 0.35f), Color.Gray.copy(alpha = 0.12f))
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                onClick()
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (enabled) resolvedTextColor else secondaryColor.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * 格式化字节数为人类可读字符串（B / KB / MB）。
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

/**
 * 从 URI 同步解码并缩放 Bitmap 为 ImageBitmap，用于图片附件预览。
 */
@Composable
fun rememberUriImage(uri: Uri, context: Context, maxW: Int? = 300): ImageBitmap? {
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                Log.e("UriImage", "Failed to decode bitmap", e)
            }
        }
    }
    return bitmap
}

/**
 * 从视频 URI 提取第一帧作为缩略图，用于视频附件预览。
 */
@Composable
fun rememberVideoThumbnail(uri: Uri, context: Context): ImageBitmap? {
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                }
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
                Log.e("VideoThumbnail", "Failed to extract thumbnail for uri: $uri", e)
            } finally {
                try { retriever?.release() } catch (e: Exception) {}
            }
        }
    }
    return bitmap
}

/**
 * 通过 API 下载附件文件到本地目标路径。
 */
suspend fun downloadAttachmentFile(
    apiService: com.example.dinoroar.network.DinoApiService,
    uuid: String,
    destFile: File
): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val responseBody = apiService.downloadAttachment(uuid)
        destFile.parentFile?.mkdirs()
        responseBody.byteStream().use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        true
    } catch (e: Exception) {
        Log.e("DownloadAttachment", "Failed to download $uuid", e)
        false
    }
}

/**
 * 尝试自动定位用户手机上录音文件的初始浏览路径（适配主流国产录音 App 目录）。
 */
fun getInitialAudioUri(): Uri? {
    val extDir = Environment.getExternalStorageDirectory()
    val paths = listOf(
        "Recordings",
        "Sounds",
        "MIUI/sound_recorder",
        "Record"
    )
    for (path in paths) {
        val folder = File(extDir, path)
        if (folder.exists() && folder.isDirectory) {
            val docId = "primary:$path"
            try {
                return DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    docId
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    return try {
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Recordings"
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * 智能解析恐龙与心情组合标题（如 "😊 开心 快乐三角龙" 或 "快乐三角龙 😊 开心"），
 * 拆分为 (心情描述, 恐龙名称) 二元组，以便在 UI 中进行双色高亮渲染。
 */
fun parseMoodAndName(parts: List<String>, fullTitle: String): Pair<String, String> {
    val moodKeywords = listOf("开心", "兴奋", "得意", "期待", "惊讶", "一般", "紧张", "遗憾", "后悔", "伤心", "愤怒")
    fun isMoodPart(s: String): Boolean {
        return s.any {
            Character.getType(it) == Character.SURROGATE.toInt() ||
            Character.getType(it) == Character.OTHER_SYMBOL.toInt()
        } || moodKeywords.any { s.contains(it) }
    }

    if (parts.size >= 3) {
        val moodIndices = parts.indices.filter { isMoodPart(parts[it]) }
        if (moodIndices.isNotEmpty()) {
            val moodStr = moodIndices.map { parts[it] }.joinToString(" ")
            val nameStr = parts.indices.filterNot { moodIndices.contains(it) }.map { parts[it] }.joinToString(" ")
            return Pair(moodStr, nameStr)
        }
    } else if (parts.size == 2) {
        if (isMoodPart(parts[0])) {
            return Pair(parts[0], parts[1])
        } else if (isMoodPart(parts[1])) {
            return Pair(parts[1], parts[0])
        }
    }
    return Pair(fullTitle, "")
}

