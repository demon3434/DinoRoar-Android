package com.example.dinoroar.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.UserUpdateLock
import kotlinx.coroutines.launch
import com.example.dinoroar.theme.LocalAppColors


private data class EditDinoInfo(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val bgColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEditPatternScreen(
    securePrefs: SecurePrefs,
    apiService: DinoApiService,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonBlue = appColors.neonBlue
    val neonAmber = appColors.neonAmber
    val neonRed = appColors.neonRed
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    // 对齐Web端背景马卡龙柔和配色的 5 只恐龙
    val dinos = remember {
        listOf(
            EditDinoInfo("1", "霸王龙", com.example.dinoroar.R.drawable.t_rex, Color(0xFFC8E6C9)), // 浅绿
            EditDinoInfo("2", "三角龙", com.example.dinoroar.R.drawable.triceratops, Color(0xFFFFE0B2)), // 浅橙
            EditDinoInfo("3", "剑龙", com.example.dinoroar.R.drawable.stegosaurus, Color(0xFFFFF9C4)), // 浅黄
            EditDinoInfo("4", "翼手龙", com.example.dinoroar.R.drawable.pterodactyl, Color(0xFFD1C4E9)), // 浅紫
            EditDinoInfo("5", "腕龙", com.example.dinoroar.R.drawable.brachiosaurus, Color(0xFFB2EBF2)) // 浅青
        )
    }

    // 状态管理 (最多只能选择 3 步)
    var tempPattern by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "重置解锁序列",
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = textPrimary
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
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. 副标题说明文案
            Text(
                text = "请点击选择 3 个恐龙，为孩子设置手势解锁序列。孩子端仅提示恐龙顺序，数字不对外显示。",
                color = textSecondary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Start,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 2. 可选恐龙卡片 (改排成两排以在手机端完美完整呈现)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 第一排: 霸王龙(1)、三角龙(2)、剑龙(3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dinos.take(3).forEach { dino ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .background(cardBg, RoundedCornerShape(12.dp))
                                .border(1.dp, textSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (tempPattern.size >= 3) {
                                        Toast.makeText(context, "解锁序列仅前 3 次有效，无需继续多选哦！", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    tempPattern = tempPattern + dino.id
                                }
                                .padding(8.dp)
                        ) {
                            Image(
                                painter = painterResource(dino.drawableRes),
                                contentDescription = dino.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(dino.bgColor, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${dino.name}(${dino.id})",
                                color = textPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 第二排: 翼手龙(4)、腕龙(5)
                Row(
                    modifier = Modifier.fillMaxWidth(0.68f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dinos.drop(3).forEach { dino ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .background(cardBg, RoundedCornerShape(12.dp))
                                .border(1.dp, textSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (tempPattern.size >= 3) {
                                        Toast.makeText(context, "解锁序列仅前 3 次有效，无需继续多选哦！", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    tempPattern = tempPattern + dino.id
                                }
                                .padding(8.dp)
                        ) {
                            Image(
                                painter = painterResource(dino.drawableRes),
                                contentDescription = dino.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(dino.bgColor, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${dino.name}(${dino.id})",
                                color = textPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 3. 已选解锁序列预览栏 (刚好 3 个虚线/图片框)
            Text(
                text = "已选解锁序列 (前 3 次有效)",
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 3) {
                    val dinoId = tempPattern.getOrNull(i)
                    val dino = dinoId?.let { id -> dinos.find { it.id == id } }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (dino != null) dino.bgColor else Color.Transparent)
                            .then(
                                if (dino == null) {
                                    Modifier.drawBehind {
                                        val stroke = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                        )
                                        drawRoundRect(
                                            color = textSecondary.copy(alpha = 0.5f),
                                            style = stroke,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                                        )
                                    }
                                } else {
                                    Modifier.border(1.dp, textSecondary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                }
                            )
                            .clickable(enabled = dino != null) {
                                // 点击虚线里的恐龙可以直接撤销它
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                val newList = tempPattern.toMutableList()
                                newList.removeAt(i)
                                tempPattern = newList
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (dino != null) {
                            Image(
                                painter = painterResource(dino.drawableRes),
                                contentDescription = dino.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. 数字序列文本展示
            val patternStr = if (tempPattern.isEmpty()) "-" else tempPattern.joinToString(", ")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "数字序列: ",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = patternStr,
                    color = neonBlue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 5. 控制与确认按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        tempPattern = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "清空",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "取消",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (tempPattern.size != 3) {
                            Toast.makeText(context, "必须恰好选择 3 个恐龙步骤设定解锁序列！", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            isSaving = true
                            val saveStr = tempPattern.joinToString(",")
                            try {
                                apiService.updateLockPattern(UserUpdateLock(lock_pattern = saveStr))
                                securePrefs.lockPattern = saveStr
                                Toast.makeText(context, "重置解锁序列成功并已同步！", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "同步云端失败，请检查网络！\n错误: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)), // Web端重置蓝
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isSaving,
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "确定重置",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
