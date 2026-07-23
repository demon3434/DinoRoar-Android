package com.example.dinoroar.ui.main.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.sync.SyncManager
import com.example.dinoroar.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 冲突解决对话框组件——当云端与本地日记内容发生 LWW 冲突时弹出。
 * 提供三种解决方案：保留本地、载入云端、自动拼合。
 *
 * 此组件从 DiaryLogCard 中解耦，使日记卡片组件不包含大量冲突处理 UI 代码。
 */
@Composable
fun ConflictResolutionDialog(
    log: LogEntity,
    coroutineScope: CoroutineScope,
    repository: DataRepository,
    syncManager: SyncManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val neonBlue = appColors.neonBlue
    val neonGreen = appColors.neonGreen
    val neonAmber = appColors.neonAmber
    val neonRed = appColors.neonRed
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🦖 整理与云端基地的冲突记忆",
                fontWeight = FontWeight.Bold,
                color = neonRed,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "小恐龙在传送时发现这篇日记在秘密基地也被修改过哦！请选择一种方式来整理记忆：",
                    color = textPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 本地版本展示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.cardBg, RoundedCornerShape(8.dp))
                        .border(1.dp, neonAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "📱 存入树洞的记忆 (手机本地)",
                            color = neonAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val localTime = try { log.updatedAt.replace("T", " ") } catch (e: Exception) { log.updatedAt }
                        Text(
                            text = "修改时间: $localTime",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = log.content, color = textPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 云端版本展示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.cardBg, RoundedCornerShape(8.dp))
                        .border(1.dp, neonBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "☁️ 存入秘密基地的记忆 (云端同步)",
                            color = neonBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val serverTime = try { log.serverUpdatedAt?.replace("T", " ") ?: "" } catch (e: Exception) { log.serverUpdatedAt ?: "" }
                        Text(
                            text = "修改时间: $serverTime",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = log.serverContent ?: "", color = textPrimary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 保留本地
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val newVersion = (log.serverVersion ?: log.version) + 1
                            val resolvedLog = log.copy(
                                isConflict = false,
                                version = newVersion,
                                isSynced = false,
                                serverContent = null,
                                serverUpdatedAt = null,
                                serverVersion = 0
                            )
                            repository.insertLog(resolvedLog, emptyList())
                            onDismiss()
                            Toast.makeText(context, "已选择保留树洞记忆，正在传送...", Toast.LENGTH_SHORT).show()
                            syncManager.sync(isManual = true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保留存入树洞的记忆", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                // 载入云端
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val resolvedLog = log.copy(
                                content = log.serverContent ?: log.content,
                                updatedAt = log.serverUpdatedAt ?: log.updatedAt,
                                version = log.serverVersion,
                                isConflict = false,
                                isSynced = true,
                                serverContent = null,
                                serverUpdatedAt = null,
                                serverVersion = 0
                            )
                            repository.insertLog(resolvedLog, emptyList())
                            onDismiss()
                            Toast.makeText(context, "已选择载入秘密基地的记忆 🦕", Toast.LENGTH_SHORT).show()
                            syncManager.sync(isManual = true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("载入秘密基地的记忆", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // 自动拼合
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val mergedContent = "${log.content}\n\n==== 💡 拼合的秘密基地记忆 ====\n${log.serverContent ?: ""}"
                            val resolvedLog = log.copy(
                                content = mergedContent,
                                updatedAt = log.serverUpdatedAt ?: log.updatedAt,
                                version = maxOf(log.version, log.serverVersion),
                                isConflict = false,
                                isSynced = false,
                                serverContent = null,
                                serverUpdatedAt = null,
                                serverVersion = 0
                            )
                            repository.insertLog(resolvedLog, emptyList())
                            onDismiss()
                            Toast.makeText(context, "记忆已拼合，你可以点击编辑进行修剪～", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("在日记内自动拼合", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("稍后处理", color = Color.White)
            }
        },
        containerColor = appColors.cardBg
    )
}
