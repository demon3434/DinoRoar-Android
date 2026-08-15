package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dinoroar.network.PromotionSummaryDto
import com.example.dinoroar.theme.LocalAppColors

/**
 * 首页看板 - 促销活动详情卡片弹窗 (PromotionDetailDialog)
 * 遵循《DinoRoar 开发者与 AI 协同约定》及 OpenSpec 设计方案。
 * 高质感毛玻璃微光弹窗，展示活动名称、时间区间、折扣力度、适用范围与直达手账商城按钮。
 */
@Composable
fun PromotionDetailDialog(
    promo: PromotionSummaryDto,
    onDismiss: () -> Unit,
    onGoToShop: () -> Unit
) {
    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary
    val neonAmber = appColors.neonAmber

    val purpleGradient = Brush.horizontalGradient(
        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .border(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部关闭按钮与标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(purpleGradient)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🎉 限时特惠",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 活动大标题
                Text(
                    text = promo.name,
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                if (!promo.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = promo.description,
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 优惠力度与活动信息卡片
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🏷️ 优惠力度与特惠细则：",
                        color = Color(0xFFD8B4FE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (promo.rules_summary.isNotEmpty()) {
                        promo.rules_summary.forEach { rule ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text("• ", color = Color(0xFFD8B4FE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = rule,
                                    color = textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "• 商城商品享受专属限时折扣特惠！",
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 适用范围
                    Text(
                        text = "🎯 适用范围：手账贴纸、背景画布商城",
                        color = textSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // 活动起止时间 (拆分为两行展示)
                    val startClean = promo.start_time.replace("T", " ").let { if (it.length >= 16) it.substring(0, 16) else it }
                    val endClean = promo.end_time.replace("T", " ").let { if (it.length >= 16) it.substring(0, 16) else it }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "⏰ 起始时间：$startClean",
                            color = textSecondary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "⏰ 截止时间：$endClean",
                            color = textSecondary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                }



                Spacer(modifier = Modifier.height(20.dp))

                // 底部行动按钮
                Button(
                    onClick = {
                        onDismiss()
                        onGoToShop()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(purpleGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✨ 立即前往手账商城逛逛",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
