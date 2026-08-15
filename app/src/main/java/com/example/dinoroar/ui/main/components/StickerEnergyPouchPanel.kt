package com.example.dinoroar.ui.main.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.theme.LocalAppColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import com.example.dinoroar.ui.main.EnergyDeltaSummary
import com.example.dinoroar.ui.sticker.StickerExchangeActivity
import com.example.dinoroar.data.local.SecurePrefs
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun StickerEnergyPouchPanel(
    eggEnergy: Int,
    energyDelta: EnergyDeltaSummary,
    securePrefs: SecurePrefs,
    activePromotion: com.example.dinoroar.network.PromotionSummaryDto? = null,
    onNavigateToHandcraftShop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val glassBg = appColors.cardBg.copy(alpha = 0.5f)
    val glassBorder = appColors.textSecondary.copy(alpha = 0.2f)

    var showHelpDialog by remember { mutableStateOf(false) }
    var showPromotionDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        // 问号帮助按钮，定位在卡片右上角
        IconButton(
            onClick = { showHelpDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = "帮助",
                tint = appColors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }

        Column {
            // 第一行：左侧蛋能量与余额 + 特惠精简徽章，右侧购物袋图标
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 20.dp), // 留出空隙，防止右侧的购物袋按钮与右上角问号按钮发生视觉冲突
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：蛋能量、余额与精简特惠胶囊
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🥚 蛋能量",
                        color = appColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "余额：$eggEnergy",
                        color = appColors.neonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (activePromotion != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f)),
                            modifier = Modifier.clickable { showPromotionDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("🔥", fontSize = 10.5.sp)
                                Text(
                                    text = "特惠",
                                    color = Color(0xFFD946EF),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // 右侧的彩色购物袋按钮，尺寸与云朵按钮一致，点击进入手账商城菜单
                IconButton(
                    onClick = {
                        onNavigateToHandcraftShop()
                    }
                ) {
                    Text(
                        text = "🛍️",
                        fontSize = 20.sp
                    )
                }
            }

            // 促销活动详情弹窗
            if (showPromotionDialog && activePromotion != null) {
                PromotionDetailDialog(
                    promo = activePromotion,
                    onDismiss = { showPromotionDialog = false },
                    onGoToShop = {
                        showPromotionDialog = false
                        onNavigateToHandcraftShop()
                    }
                )
            }





            // Help Dialog
            if (showHelpDialog) {
                AlertDialog(
                    onDismissRequest = { showHelpDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showHelpDialog = false }) {
                            Text(
                                text = "我知道啦",
                                color = appColors.neonAmber,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "🥚 蛋能量说明书",
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "⭐ 什么是蛋能量？\n蛋能量是小朋友记录日记赚取的神奇积分奖励哦！",
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "⭐ 怎么获得蛋能量？\n• ✍️ 记一篇纯文字悄悄话，获得 10 蛋能量\n• 🖼️ 记一篇包含图片/视频/录音的悄悄话，获得 30 蛋能量！",
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "⭐ 怎么使用蛋能量？\n• 🛍️ 点击右侧的购物袋图标，可以去贴纸商店挑选并兑换精美的贴纸\n• 🎨 兑换后的贴纸可直接用于日记排版哦！",
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    containerColor = appColors.cardBg,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "📈 蛋能量增量记录：",
                color = appColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 2x2 Grid for deltas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DeltaItem(label = "今日新增", value = energyDelta.today, color = appColors.neonBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    DeltaItem(label = "上周新增", value = energyDelta.lastWeek, color = appColors.textSecondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    DeltaItem(label = "本周新增", value = energyDelta.thisWeek, color = appColors.neonAmber)
                    Spacer(modifier = Modifier.height(6.dp))
                    DeltaItem(label = "本月新增", value = energyDelta.thisMonth, color = appColors.neonGreen)
                }
            }
        }
    }
}

@Composable
private fun DeltaItem(
    label: String,
    value: Int,
    color: Color
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = appColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "+$value ⚡",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
