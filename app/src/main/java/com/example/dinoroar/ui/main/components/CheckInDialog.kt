package com.example.dinoroar.ui.main.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dinoroar.network.CheckInResultResponse
import com.example.dinoroar.network.CheckInStatusResponse
import com.example.dinoroar.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CheckInDialog(
    status: CheckInStatusResponse?,
    isSubmitting: Boolean,
    onPerformCheckIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isCracked by remember { mutableStateOf(status?.has_checked_in_today == true) }
    var resultData by remember { mutableStateOf<CheckInResultResponse?>(null) }
    val isAlreadyChecked = status?.has_checked_in_today == true

    // 晃动与爆裂动效
    val wobbleRotation = remember { Animatable(0f) }
    val eggScale = remember { Animatable(1f) }
    val particleAlpha = remember { Animatable(0f) }

    LaunchedEffect(status?.has_checked_in_today) {
        if (status?.has_checked_in_today == true) {
            isCracked = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            appColors.cardBg.copy(alpha = 0.96f),
                            appColors.darkBg.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            colors = if (resultData?.is_crit == true) {
                                listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFFBBF24))
                            } else {
                                listOf(appColors.neonGreen.copy(alpha = 0.8f), appColors.neonBlue.copy(alpha = 0.5f))
                            }
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = appColors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题
                Text(
                    text = if (isCracked) "✨ 每日签到领奖" else "🥚 每日敲蛋打卡",
                    color = if (resultData?.is_crit == true) Color(0xFFF59E0B) else appColors.neonAmber,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                val streak = status?.streak_days ?: 1
                Text(
                    text = "已连续打卡 $streak 天 · 坚持写日记和打卡更有惊喜哦",
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 中间恐龙蛋互动区域
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (resultData?.is_crit == true) Color(0xFFF59E0B).copy(alpha = 0.25f)
                                    else appColors.neonGreen.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .clickable(
                            enabled = !isCracked && !isSubmitting && !isAlreadyChecked,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                // 连续晃动
                                wobbleRotation.animateTo(12f, tween(60))
                                wobbleRotation.animateTo(-12f, tween(60))
                                wobbleRotation.animateTo(8f, tween(60))
                                wobbleRotation.animateTo(-8f, tween(60))
                                wobbleRotation.animateTo(0f, tween(60))
                                eggScale.animateTo(1.15f, tween(100))
                                eggScale.animateTo(1.0f, tween(120))
                                onPerformCheckIn()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCracked) {
                        // 破壳状态
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {

                            Text(
                                text = if (resultData?.is_crit == true) "🦖💥" else "🐣✨",
                                fontSize = 54.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val rewardText = resultData?.let { "+${it.total_reward}" } ?: (status?.today_record?.let { "+${it.energy_reward + it.streak_bonus}" } ?: "已领取")
                            Text(
                                text = "$rewardText 蛋能量",
                                color = if (resultData?.is_crit == true) Color(0xFFF59E0B) else appColors.neonGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        // 完整恐龙蛋待敲状态
                        Box(
                            modifier = Modifier
                                .rotate(wobbleRotation.value)
                                .scale(eggScale.value),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🥚",
                                fontSize = 72.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 暴击或连签提示文案
                if (resultData?.is_crit == true) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "🔥 欧皇大暴击！触发了超高额蛋能量加成！",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (resultData != null && resultData!!.streak_bonus > 0) {
                    Surface(
                        color = appColors.neonGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, appColors.neonGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "👣 连续签到加成 +${resultData!!.streak_bonus} 蛋能量！",
                            color = appColors.neonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 7 天打卡连续脚印条
                Text(
                    text = "👣 本周打卡足迹",
                    color = appColors.textSecondary.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val weeklyHistory = status?.weekly_history ?: emptyList()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                    for (i in 0 until 7) {
                        val dayItem = weeklyHistory.getOrNull(i)
                        val isChecked = dayItem?.checked_in == true
                        val isToday = dayItem?.date == status?.today_date

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isChecked -> appColors.neonGreen.copy(alpha = 0.2f)
                                isToday -> appColors.neonAmber.copy(alpha = 0.12f)
                                else -> Color.White.copy(alpha = 0.04f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isChecked -> appColors.neonGreen.copy(alpha = 0.6f)
                                    isToday -> appColors.neonAmber.copy(alpha = 0.8f)
                                    else -> Color.White.copy(alpha = 0.08f)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (dayItem != null) "周${dayNames.getOrElse(dayItem.day_of_week - 1) { "日" }}" else "周${dayNames[i]}",
                                    fontSize = 10.sp,
                                    color = if (isToday) appColors.neonAmber else appColors.textSecondary,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isChecked) "✔️" else if (isToday) "🥚" else "🐾",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 操作按钮
                if (isCracked || isAlreadyChecked) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.neonGreen
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = "🎉 太棒了，收入囊中！",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPerformCheckIn()
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.neonAmber
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "🔨 敲碎恐龙蛋签到",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
