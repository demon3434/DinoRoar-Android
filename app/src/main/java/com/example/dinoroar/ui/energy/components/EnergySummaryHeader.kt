package com.example.dinoroar.ui.energy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.network.EnergySummaryDto
import com.example.dinoroar.theme.LocalAppColors

/**
 * 紧凑型 5 维立体可点击时间筛选卡片 (今日 / 本周 / 本月 / 上月 / 本年)
 * 彻底移除顶层冗余大卡片，极简紧凑排布，最大化减少屏幕高度占用
 */
@Composable
fun EnergySummaryHeader(
    summary: EnergySummaryDto,
    currentTimeRange: LedgerTimeRange,
    onTimeRangeSelected: (LedgerTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimeFilterCard(
            label = "今日",
            income = summary.today_income,
            expense = summary.today_expense,
            isSelected = currentTimeRange == LedgerTimeRange.TODAY,
            onClick = {
                val next = if (currentTimeRange == LedgerTimeRange.TODAY) LedgerTimeRange.ALL else LedgerTimeRange.TODAY
                onTimeRangeSelected(next)
            }
        )
        TimeFilterCard(
            label = "本周",
            income = summary.week_income,
            expense = summary.week_expense,
            isSelected = currentTimeRange == LedgerTimeRange.WEEK,
            onClick = {
                val next = if (currentTimeRange == LedgerTimeRange.WEEK) LedgerTimeRange.ALL else LedgerTimeRange.WEEK
                onTimeRangeSelected(next)
            }
        )
        TimeFilterCard(
            label = "本月",
            income = summary.month_total_income,
            expense = summary.month_total_expense,
            isSelected = currentTimeRange == LedgerTimeRange.MONTH,
            onClick = {
                val next = if (currentTimeRange == LedgerTimeRange.MONTH) LedgerTimeRange.ALL else LedgerTimeRange.MONTH
                onTimeRangeSelected(next)
            }
        )
        TimeFilterCard(
            label = "上月",
            income = summary.last_month_income,
            expense = summary.last_month_expense,
            isSelected = currentTimeRange == LedgerTimeRange.LAST_MONTH,
            onClick = {
                val next = if (currentTimeRange == LedgerTimeRange.LAST_MONTH) LedgerTimeRange.ALL else LedgerTimeRange.LAST_MONTH
                onTimeRangeSelected(next)
            }
        )
        TimeFilterCard(
            label = "本年",
            income = summary.year_income,
            expense = summary.year_expense,
            isSelected = currentTimeRange == LedgerTimeRange.YEAR,
            onClick = {
                val next = if (currentTimeRange == LedgerTimeRange.YEAR) LedgerTimeRange.ALL else LedgerTimeRange.YEAR
                onTimeRangeSelected(next)
            }
        )
    }
}

@Composable
private fun TimeFilterCard(
    label: String,
    income: Int,
    expense: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    val borderColor = if (isSelected) {
        Color(0xFFF59E0B)
    } else {
        if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
    }

    val bgColor = if (isSelected) {
        if (isDark) Color(0xFFF59E0B).copy(alpha = 0.18f) else Color(0xFFFEF3C7)
    } else {
        appColors.cardBg
    }

    Box(
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) (if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)) else appColors.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = appColors.neonGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)) {
                        append("+$income\n")
                    }
                    withStyle(SpanStyle(color = if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)) {
                        append("-$expense")
                    }
                },
                textAlign = TextAlign.Center,
                lineHeight = 11.5.sp,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}
