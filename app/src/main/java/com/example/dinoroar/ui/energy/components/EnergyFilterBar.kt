package com.example.dinoroar.ui.energy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.theme.LocalAppColors

enum class LedgerFilterType(val code: String, val label: String) {
    ALL("all", "全部"),
    INCOME("income", "📈 获得"),
    EXPENSE("expense", "📉 消耗")
}

enum class LedgerTimeRange(val code: String, val label: String) {
    ALL("all", "全部"),
    TODAY("today", "今日"),
    WEEK("week", "本周"),
    MONTH("month", "本月"),
    LAST_MONTH("last_month", "上月"),
    YEAR("year", "本年")
}

/**
 * 快捷收支分段选择与状态过滤栏 (全面适配 App 主题)
 */
@Composable
fun EnergyFilterBar(
    currentFilter: LedgerFilterType,
    onFilterSelected: (LedgerFilterType) -> Unit,
    currentTimeRange: LedgerTimeRange = LedgerTimeRange.ALL,
    onClearTimeRange: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerFilterType.values().forEach { filter ->
                val isSelected = filter == currentFilter
                val activeBg = when (filter) {
                    LedgerFilterType.EXPENSE -> Color(0xFFEA580C)
                    LedgerFilterType.INCOME -> appColors.neonGreen
                    LedgerFilterType.ALL -> Color(0xFFF59E0B)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) activeBg else appColors.cardBg)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) activeBg else borderCol,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onFilterSelected(filter) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.label,
                        color = if (isSelected) Color.White else appColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 清除时间筛选胶囊
        if (currentTimeRange != LedgerTimeRange.ALL) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                    .border(width = 1.dp, color = Color(0xFFF59E0B), shape = RoundedCornerShape(12.dp))
                    .clickable { onClearTimeRange() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "✖ ${currentTimeRange.label}",
                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
