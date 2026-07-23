package com.example.dinoroar.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.media.DiagnosticLogger
import com.example.dinoroar.theme.ThemeList
import com.example.dinoroar.network.NsdHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DinoLockSettingsSection(
    securePrefs: SecurePrefs,
    textPrimary: Color,
    textSecondary: Color,
    neonBlue: Color,
    cardBg: Color,
    onNavigateToEditPattern: () -> Unit
) {
    Text(
        text = "🔒 恐龙解锁配置列表",
        color = textPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    val currentPatternStr = securePrefs.lockPattern
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🦕 当前解锁恐龙序列 (只读):",
                color = textSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = currentPatternStr.split(",").joinToString(" ➔ ") { id ->
                    when (id) {
                        "1" -> "霸王龙"
                        "2" -> "三角龙"
                        "3" -> "剑龙"
                        "4" -> "翼手龙"
                        "5" -> "腕龙"
                        else -> "Dino-$id"
                    }
                },
                color = neonBlue,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToEditPattern() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "✏️ 绘制修改恐龙解锁手势",
                color = textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary
            )
        }
    }
}

@Composable
fun ThemeSelectSettingsSection(
    securePrefs: SecurePrefs,
    textPrimary: Color,
    neonBlue: Color,
    neonGreen: Color,
    neonAmber: Color,
    cardBg: Color,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    Text(
        text = "🎨 可选主题色列表 (点击应用)",
        color = textPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    ThemeList.forEach { theme ->
        val isSelected = theme.id == securePrefs.currentThemeId
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) neonBlue else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    securePrefs.currentThemeId = theme.id
                    onThemeChanged(theme.id)
                    Toast.makeText(context, "已切换主题: ${theme.name}", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(theme.darkBg)
                            .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(theme.neonBlue))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = theme.name,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.neonBlue))
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.neonGreen))
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(theme.neonAmber))
                }
            }
        }
    }
}

@Composable
fun ServerAddressSettingsSection(
    securePrefs: SecurePrefs,
    nsdHelper: NsdHelper,
    textPrimary: Color,
    textSecondary: Color,
    neonBlue: Color,
    neonGreen: Color,
    neonAmber: Color,
    cardBg: Color,
    onSaveComplete: () -> Unit
) {
    val context = LocalContext.current
    var internalUrl by remember { mutableStateOf(securePrefs.intranetUrl ?: "") }
    var externalUrl by remember { mutableStateOf(securePrefs.extranetUrl ?: "") }

    var isScanningForServer by remember { mutableStateOf(false) }
    val discoveredService by nsdHelper.discoveredService.collectAsState()

    LaunchedEffect(discoveredService, isScanningForServer) {
        if (isScanningForServer) {
            discoveredService?.let { service ->
                val hostIp = service.hostAddress
                val port = service.port
                internalUrl = "http://$hostIp:$port"
                isScanningForServer = false
                nsdHelper.stopDiscovery()
                Toast.makeText(context, "已成功自动发现内网服务器！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            nsdHelper.stopDiscovery()
        }
    }

    Text(
        text = "🌐 填写秘密同步服务器地址",
        color = textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🏠 内网同步服务器地址 (WiFi私有网络):",
                color = textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = internalUrl,
                onValueChange = { internalUrl = it },
                placeholder = { Text("例如: http://192.168.1.100:8080", color = textSecondary, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = neonBlue,
                    unfocusedBorderColor = textSecondary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "提示: 局域网基舱调试，同步媒体速度极快",
                color = textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    nsdHelper.startDiscovery()
                    isScanningForServer = true
                    Toast.makeText(context, "正在寻找局域网内秘密服务器...", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isScanningForServer) "正在扫描内网服务..." else "🔍 自动发现内网服务",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🌍 外网同步服务器地址 (互联网云端端点):",
                color = textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = externalUrl,
                onValueChange = { externalUrl = it },
                placeholder = { Text("例如: http://your-cloud-server.com:8080", color = textSecondary, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = neonBlue,
                    unfocusedBorderColor = textSecondary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "提示: 户外移动网络下与家人跨区域同步",
                color = textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    Button(
        onClick = {
            nsdHelper.stopDiscovery()
            securePrefs.intranetUrl = internalUrl.trim()
            securePrefs.extranetUrl = externalUrl.trim()

            val finalUrl = if (internalUrl.isNotBlank()) internalUrl.trim() else externalUrl.trim()
            if (finalUrl.isNotBlank()) {
                securePrefs.serverUrl = finalUrl
            }
            Toast.makeText(context, "同步端点服务器地址已保存！", Toast.LENGTH_SHORT).show()
            onSaveComplete()
        },
        colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text("保存配置地址", color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MediaSettingsSection(
    securePrefs: SecurePrefs,
    repository: DataRepository,
    coroutineScope: CoroutineScope,
    textPrimary: Color,
    textSecondary: Color,
    neonAmber: Color,
    cardBg: Color,
    onNavigateToVideoQuality: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = "📁 多媒体数据传输与压缩设置",
        color = textPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    var isWifiOnlyEnabled by remember { mutableStateOf(securePrefs.isWifiOnlyEnabled) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📶 仅限 WiFi 同步媒体",
                    color = textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "开启后，录音和视频附件只会在连接 WiFi时上传",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Switch(
                checked = isWifiOnlyEnabled,
                onCheckedChange = {
                    isWifiOnlyEnabled = it
                    securePrefs.isWifiOnlyEnabled = it
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = neonAmber,
                    checkedTrackColor = neonAmber.copy(alpha = 0.5f)
                )
            )
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToVideoQuality() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "🎬 视频压缩品质 (全局设定)",
                    color = textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                val qualityLabel = when (securePrefs.globalVideoQuality) {
                    "360p" -> "极小 (360P)"
                    "480p" -> "标清 (480P)"
                    "720p" -> "高清 (720P)"
                    "1080p" -> "超清 (1080P)"
                    else -> "高清 (720P)"
                }
                Text(
                    text = "当前压缩质量: $qualityLabel",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🧹 优化手机存储空间",
                color = textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "物理清理本地已同步到秘密基地的图片与视频留念文件。清理后，大文件将从设备移出，但在详情页中点击仍能随时从基地实时拉取展示。",
                color = textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val synced = repository.getAllSyncedAttachments()
                            var cleanedCount = 0
                            var cleanedSize = 0L
                            synced.forEach { att ->
                                val path = att.localFilePath
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists()) {
                                        cleanedSize += file.length()
                                        file.delete()
                                    }
                                    repository.insertAttachment(att.copy(localFilePath = null))
                                    cleanedCount++
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val mb = "%.2f".format(cleanedSize / (1024f * 1024f))
                                Toast.makeText(context, "优化空间成功！已清理 $cleanedCount 个大文件，释放约 ${mb}MB 空间！", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "优化空间失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("立即优化手机存储空间", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VideoQualitySettingsSection(
    securePrefs: SecurePrefs,
    textPrimary: Color,
    neonBlue: Color,
    cardBg: Color,
    onQualitySelected: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = "🎬 视频录像压缩品质 (三级单选)",
        color = textPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    val qualities = listOf(
        Pair("360p", "极小体积 (360P) [日记留影最省空间]"),
        Pair("480p", "标清流畅 (480P) [适合家庭小空间调试]"),
        Pair("720p", "高清标准 (720P) [清晰省流最佳比例]"),
        Pair("1080p", "超清画质 (1080P) [细节饱满画幅清晰]")
    )

    qualities.forEach { (id, label) ->
        val isSelected = securePrefs.globalVideoQuality == id
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) neonBlue else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    securePrefs.globalVideoQuality = id
                    Toast.makeText(context, "视频压缩品质已保存！", Toast.LENGTH_SHORT).show()
                    onQualitySelected()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    color = if (isSelected) neonBlue else textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (isSelected) {
                    Text(text = "✔", color = neonBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogSettingsSection(
    textPrimary: Color,
    textSecondary: Color,
    neonBlue: Color,
    neonRed: Color,
    neonAmber: Color,
    cardBg: Color
) {
    val context = LocalContext.current
    var logsText by remember { mutableStateOf(DiagnosticLogger.getLogs()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "📋 压缩诊断日志",
            color = textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    logsText = DiagnosticLogger.getLogs()
                    Toast.makeText(context, "日志已刷新", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("刷新", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    DiagnosticLogger.clear()
                    logsText = ""
                    Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonRed),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("清空", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (logsText.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("当前暂无诊断日志，请录制上传视频后再试", color = textSecondary, fontSize = 13.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .border(1.dp, textSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logsText,
                        color = textPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("DinoRoar Logs", logsText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "日志已复制到剪贴板，请粘贴发给我！", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                modifier = Modifier.fillMaxWidth(),
                enabled = logsText.isNotEmpty()
            ) {
                Text("一键复制全部诊断日志", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
