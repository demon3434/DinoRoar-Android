package com.example.dinoroar.ui.energy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.network.EnergyTransactionDto
import com.example.dinoroar.theme.LocalAppColors

/**
 * 银行流水级生动图鉴流水条目卡片 (全面适配 App 主题)
 * 遵循“业务类型主导 + 附属明细次级化 + 日记显标题”三层视觉规范
 */
@Composable
fun EnergyTransactionCard(
    tx: EnergyTransactionDto,
    serverBaseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isPositive = tx.change_amount > 0
    val isDark = appColors.isDark

    // 确定类型专属 Emoji 图标
    val typeEmoji = remember(tx.target_type_id, tx.event_type_id, isPositive) {
        when {
            tx.target_type_id == 3 || tx.event_type_id == 201 -> "📝"
            tx.target_type_id == 2 || tx.event_type_id == 101 -> "🥚"
            tx.target_type_id == 1 || tx.event_type_id == 301 -> "🛍️"
            tx.target_type_id == 4 || tx.event_type_id == 401 -> "🎁"
            isPositive -> "🥚"
            else -> "🛍️"
        }
    }

    val dirBadgeText = if (isPositive) "📈 获得" else "📉 消耗"
    val dirColor = if (isPositive) appColors.neonGreen else (if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C))
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = appColors.cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 左侧：类型专属 Logo 徽标（光晕背景）
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(dirColor.copy(alpha = 0.12f))
                    .border(1.dp, dirColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = typeEmoji,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 中间：三层内容区（大类主标题 + 附属详情 + 时间）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 第一行：大类主标题 + 方向徽标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.asset_display.title.ifBlank { tx.event_name },
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(dirColor.copy(alpha = 0.15f))
                            .border(0.5.dp, dirColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = dirBadgeText,
                            color = dirColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // 第二行：附属详情（日记真实标题 / 兑换商品详情）
                if (tx.asset_display.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tx.asset_display.subtitle,
                        color = appColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 第三行：交易时间 (格式规范单行展示)
                Text(
                    text = "🕒 ${tx.created_at}",
                    color = appColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 实物缩略图 (贴纸/画布/心情恐龙，36x36dp带微圆角)
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

            if (fullImgUrl != null) {
                Spacer(modifier = Modifier.width(8.dp))
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(fullImgUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = tx.asset_display.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFF1F5F9))
                        .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 3. 变动金额与结余 (紧凑宽度，去除末尾符号)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(min = 52.dp)
            ) {
                Text(
                    text = if (isPositive) "+${tx.change_amount}" else "${tx.change_amount}",
                    color = dirColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "结余 ${tx.balance_after}",
                    color = appColors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
