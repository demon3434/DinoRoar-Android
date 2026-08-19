package com.example.dinoroar.ui.energy.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.network.EnergyTransactionDto
import com.example.dinoroar.theme.LocalAppColors
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 拟物化银行级蛋能量电子凭证回单弹窗 (全面适配 App 主题)
 */
@Composable
fun EnergyReceiptDialog(
    tx: EnergyTransactionDto,
    serverBaseUrl: String,
    onDismiss: () -> Unit,
    onNavigateToDiary: ((String) -> Unit)? = null,
    onNavigateToShop: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isPositive = tx.change_amount > 0

    val rawImgUrl = tx.asset_display.image_url
    val fullImgUrl = remember(rawImgUrl, serverBaseUrl) {
        if (rawImgUrl.isNullOrBlank()) null
        else if (rawImgUrl.startsWith("http://") || rawImgUrl.startsWith("https://")) rawImgUrl
        else {
            val base = serverBaseUrl.trimEnd('/')
            val path = if (rawImgUrl.startsWith('/')) rawImgUrl else "/$rawImgUrl"
            "$base$path"
        }
    }

    val themeColor = remember(tx.asset_display.theme_color, isPositive) {
        try {
            Color(android.graphics.Color.parseColor(tx.asset_display.theme_color))
        } catch (e: Exception) {
            if (isPositive) appColors.neonGreen else appColors.neonAmber
        }
    }

    val diaryUuid = remember(tx.asset_display.detail_info) {
        try {
            tx.asset_display.detail_info?.get("diary_uuid")?.jsonPrimitive?.contentOrNull
                ?: tx.asset_display.detail_info?.get("diary_uuid")?.toString()?.trim('"')
        } catch (e: Exception) {
            null
        }
    }

    val containerBg = appColors.cardBg
    val innerCardBg = if (appColors.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val cardBorder = if (appColors.isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerBg),
            border = BorderStroke(1.dp, cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部回单状态与关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧾 蛋能量电子凭证",
                        color = appColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "✕",
                        color = appColors.textSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 凭证核心卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(innerCardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "交易成功 · 权威已记账",
                            color = appColors.neonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 金额变动大字
                        Text(
                            text = if (isPositive) "+${tx.change_amount}" else "${tx.change_amount}",
                            color = if (isPositive) appColors.neonGreen else appColors.neonAmber,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "蛋能量变动",
                            color = appColors.textSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 分割线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(cardBorder)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 键值详情列表
                        ReceiptInfoRow(
                            label = "业务类型",
                            value = tx.asset_display.title.ifBlank { tx.event_name },
                            valueColor = appColors.textPrimary,
                            labelColor = appColors.textSecondary,
                            isBold = true
                        )

                        if (tx.asset_display.subtitle.isNotBlank()) {
                            ReceiptInfoRow(
                                label = "业务详情",
                                value = tx.asset_display.subtitle,
                                valueColor = themeColor,
                                labelColor = appColors.textSecondary
                            )
                        }

                        ReceiptInfoRow(
                            label = "交易时间",
                            value = tx.created_at,
                            valueColor = appColors.textPrimary,
                            labelColor = appColors.textSecondary,
                            isMonospace = true
                        )

                        val prevBalance = tx.balance_after - tx.change_amount
                        ReceiptInfoRow(
                            label = "变动前余额",
                            value = "$prevBalance 🥚",
                            valueColor = appColors.textPrimary,
                            labelColor = appColors.textSecondary
                        )

                        ReceiptInfoRow(
                            label = "变动后结余",
                            value = "${tx.balance_after} 🥚",
                            valueColor = Color(0xFFF59E0B),
                            labelColor = appColors.textSecondary,
                            isBold = true
                        )

                        if (!tx.request_uuid.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "流水单号",
                                    color = appColors.textSecondary,
                                    fontSize = 11.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tx.request_uuid.take(12) + "...",
                                        color = appColors.textPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "复制",
                                        color = appColors.neonBlue,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(appColors.neonBlue.copy(alpha = 0.12f))
                                            .clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("UUID", tx.request_uuid))
                                                Toast.makeText(context, "单号已复制", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 实体缩略图预览卡片
                if (fullImgUrl != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerCardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fullImgUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = tx.asset_display.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.asset_display.subtitle.ifBlank { tx.asset_display.title },
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                            Text(
                                text = "关联 ${tx.asset_display.badge_label} 实体档案",
                                color = appColors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 动作按钮区域：直达日记手账
                if (!diaryUuid.isNullOrBlank() && onNavigateToDiary != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onNavigateToDiary(diaryUuid)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.neonBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "📖 查看对应手账详情", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "🦕 DINOROAR 秘密基地能量银行系统 · 电子验讫",
                    color = appColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ReceiptInfoRow(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    isBold: Boolean = false,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
