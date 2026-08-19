package com.example.dinoroar.ui.main.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.getDinoMoodLabel
import com.example.dinoroar.ui.main.getDinoName
import com.example.dinoroar.ui.main.getDinoResource
import com.example.dinoroar.data.local.SecurePrefs
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryLogCard(
    logWithConfig: com.example.dinoroar.data.local.LogWithConfig,
    attachments: List<AttachmentEntity>,
    associatedPersons: List<PersonEntity>,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCardClick: () -> Unit,
    repository: DataRepository,
    syncManager: SyncManager
) {
    val log = logWithConfig.log
    val dinoConfig = logWithConfig.dinoConfig
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showConflictResolveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securePrefs = remember { SecurePrefs(context) }

    val configMap = remember(securePrefs.stickerConfigCache) {
        val map = mutableMapOf<String, String>()
        securePrefs.stickerConfigCache.split(",").filter { it.isNotBlank() }.forEach { item ->
            val parts = item.split(":")
            if (parts.size >= 2) {
                val id = parts[0].trim()
                val url = parts.subList(1, parts.size).joinToString(":")
                map[id] = url
            }
        }
        map
    }
    val serverBaseUrl = remember { securePrefs.serverUrl?.removeSuffix("/") ?: "" }

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
    val dinoMood = dinoConfig?.moodLabel ?: getDinoMoodLabel(log.moodDinoId)

    val stickers = remember(log.content) {
        val list = mutableListOf<String>()
        val pattern = java.util.regex.Pattern.compile("\\[sticker:([^:]+):[^\\]]+\\]")
        val matcher = pattern.matcher(log.content)
        while (matcher.find()) {
            val dinoId = matcher.group(1)
            if (dinoId != null) {
                list.add(dinoId)
            }
        }
        list
    }

    val cleanContent = remember(log.content) {
        log.content.replace(Regex("\\[sticker:[^\\]]+\\]"), "").trim()
    }

    val appColors = LocalAppColors.current
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonAmber = appColors.neonAmber
    val neonRed = appColors.neonRed
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.cardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        when {
                            log.isConflict -> {
                                Text(
                                    text = "☁️❗️ 基地有新记忆，需要整理 🔍",
                                    color = neonRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            log.isSynced -> {
                                Text(
                                    text = "☁️✨ 已安全存入基地 🦕",
                                    color = neonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            !log.isLocalOnly -> {
                                Text(
                                    text = "☁️⏳ 正在保存至基地...",
                                    color = neonBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            else -> {
                                Text(
                                    text = "☁️❌ 已暂存在树洞",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onEdit() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑日记",
                                tint = neonBlue.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除日记",
                                tint = neonRed.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val incidentDateFormatted = try {
                    val clean = log.incidentDate.replace("T", " ").replace("Z", "")
                    if (clean.length >= 19) clean.substring(0, 19) else clean
                } catch (e: Exception) { log.incidentDate }

                Text(
                    text = "写于：$incidentDateFormatted",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("确认删除日记？", fontWeight = FontWeight.Bold, color = textPrimary) },
                    text = { Text("删除后，该条日记将会从本地和秘密基地中彻底从你和家人的设备中抹除，无法恢复。", color = textSecondary) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                onDelete()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("删除", color = Color.White)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showDeleteConfirmDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("取消", color = Color.White)
                        }
                    },
                    containerColor = appColors.cardBg
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = dinoResId),
                        contentDescription = dinoName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(
                            text = dinoName,
                            color = neonAmber,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dinoMood,
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (stickers.isNotEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(max = 160.dp)
                    ) {
                        items(stickers) { stickerDinoId ->
                            val finalStickerId = stickerDinoId.trim()
                            val fallbackResId = com.example.dinoroar.R.drawable.sticker_fallback_logo
                            val imgUrl = configMap[finalStickerId]

                            val modelData: Any? = if (imgUrl != null) {
                                val localRes = com.example.dinoroar.ui.main.getStickerLocalResource(imgUrl)
                                if (localRes != null) localRes else {
                                    if (imgUrl.startsWith("/static/")) serverBaseUrl + imgUrl else imgUrl
                                }
                            } else null

                            if (modelData != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(modelData)
                                        .crossfade(true)
                                        .error(fallbackResId)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = fallbackResId),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (associatedPersons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                .background(colorPair.bg, RoundedCornerShape(12.dp))
                                .border(1.dp, colorPair.text.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            val label = if (!person.relationship.isNullOrEmpty()) {
                                "${person.name} (${person.relationship})"
                            } else {
                                person.name
                            }
                            Text(
                                text = label,
                                color = colorPair.text,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 标题与正文摘要展示
            val displayTitle = if (log.title.isNullOrBlank()) {
                "无标题"
            } else {
                if (log.title.length > 10) log.title.substring(0, 10) + "..." else log.title
            }
            Text(
                text = displayTitle,
                color = neonAmber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cleanContent,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            if (!log.ownThoughts.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🤫 我的悄悄话：${log.ownThoughts.trim()}",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // 附件数量统计徽章
            if (attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imagesCount = attachments.count { it.mimeType.startsWith("image/") }
                    val videosCount = attachments.count { it.mimeType.startsWith("video/") }
                    val audiosCount = attachments.count { it.mimeType.startsWith("audio/") }

                    if (imagesCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📸", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$imagesCount", color = textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (videosCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📹", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$videosCount", color = textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (audiosCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔊", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$audiosCount", color = textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            if (log.isConflict) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showConflictResolveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("🔍 整理与秘密基地的冲突记忆", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 冲突解决整理 Dialog（已解耦至独立组件 ConflictResolutionDialog.kt）
            if (log.isConflict && showConflictResolveDialog) {
                ConflictResolutionDialog(
                    log = log,
                    coroutineScope = coroutineScope,
                    repository = repository,
                    syncManager = syncManager,
                    onDismiss = { showConflictResolveDialog = false }
                )
            }
        }
    }
}
