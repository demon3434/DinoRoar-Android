package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.DashboardReviewSummary
import com.example.dinoroar.ui.main.PeriodReviewData

@Composable
fun NaturalPeriodReviewPanel(
    reviewSummary: DashboardReviewSummary,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val glassBg = appColors.cardBg.copy(alpha = 0.5f)
    val glassBorder = appColors.textSecondary.copy(alpha = 0.2f)

    var reviewTabState by remember { mutableIntStateOf(0) } // 0:周, 1:本月, 2:上月, 3:本年
    val activeReview = when (reviewTabState) {
        0 -> reviewSummary.weekReview
        1 -> reviewSummary.monthReview
        2 -> reviewSummary.lastMonthReview
        else -> reviewSummary.yearReview
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header with Switch Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 时光机",
                    color = appColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Toggle tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    TabItem(
                        text = "本周",
                        selected = reviewTabState == 0,
                        onClick = { reviewTabState = 0 }
                    )
                    TabItem(
                        text = "本月",
                        selected = reviewTabState == 1,
                        onClick = { reviewTabState = 1 }
                    )
                    TabItem(
                        text = "上月",
                        selected = reviewTabState == 2,
                        onClick = { reviewTabState = 2 }
                    )
                    TabItem(
                        text = "本年",
                        selected = reviewTabState == 3,
                        onClick = { reviewTabState = 3 }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // Date Range Label
            Text(
                text = "📅 周期范围: " + activeReview.dateRangeStr,
                color = appColors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Diary Count Stat
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📝 新增日记",
                    color = appColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${activeReview.count} 篇",
                        color = appColors.neonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    val diff = activeReview.diffFromLastPeriod
                    if (diff > 0) {
                        Text(
                            text = "(比上期 +$diff 📈)",
                            color = appColors.neonGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else if (diff < 0) {
                        Text(
                            text = "(比上期 $diff 📉)",
                            color = appColors.neonRed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = "(与上期持平)",
                            color = appColors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mood stacked progress bar
            Text(
                text = "🎨 情绪占比晴雨表",
                color = appColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            MoodStackedBar(
                highRatio = activeReview.moodPercentages.getOrElse(0) { 0f },
                midRatio = activeReview.moodPercentages.getOrElse(1) { 0f },
                lowRatio = activeReview.moodPercentages.getOrElse(2) { 0f }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Top partners
            Text(
                text = "👥 提到最多的小伙伴",
                color = appColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (activeReview.topPersons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "本周期还没有写过小伙伴呢 🦖",
                        color = appColors.textSecondary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    activeReview.topPersons.forEachIndexed { index, pair ->
                        TopPersonCard(
                            rank = index + 1,
                            person = pair.first,
                            count = pair.second,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining empty weight cards to align grid
                    val emptyCards = 3 - activeReview.topPersons.size
                    for (i in 0 until emptyCards) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) appColors.neonAmber else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) appColors.darkBg else appColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MoodStackedBar(
    highRatio: Float,
    midRatio: Float,
    lowRatio: Float
) {
    val appColors = LocalAppColors.current
    val total = highRatio + midRatio + lowRatio
    val drawHigh = if (total == 0f) 0f else highRatio / total
    val drawMid = if (total == 0f) 0f else midRatio / total
    val drawLow = if (total == 0f) 0f else lowRatio / total

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            if (drawHigh > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(drawHigh)
                        .background(appColors.neonGreen)
                )
            }
            if (drawMid > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(drawMid)
                        .background(appColors.neonBlue)
                )
            }
            if (drawLow > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(drawLow)
                        .background(appColors.neonRed)
                )
            }
            if (total == 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.15f))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(label = "开心", percent = (highRatio * 100).toInt(), color = appColors.neonGreen)
            LegendItem(label = "平静", percent = (midRatio * 100).toInt(), color = appColors.neonBlue)
            LegendItem(label = "难过", percent = (lowRatio * 100).toInt(), color = appColors.neonRed)
        }
    }
}

@Composable
private fun LegendItem(label: String, percent: Int, color: Color) {
    val appColors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label $percent%",
            color = appColors.textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TopPersonCard(
    rank: Int,
    person: PersonEntity,
    count: Int,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val colors = listOf(appColors.neonAmber, appColors.neonBlue, appColors.neonGreen)
    val rankColor = colors.getOrElse(rank - 1) { appColors.textSecondary }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rank circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(rankColor.copy(alpha = 0.1f))
        ) {
            Text(
                text = "$rank",
                color = rankColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Initials avatar
        val initial = person.name.firstOrNull()?.toString() ?: "👤"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(appColors.neonBlue.copy(alpha = 0.1f))
                .border(1.dp, appColors.neonBlue.copy(alpha = 0.3f), CircleShape)
        ) {
            Text(
                text = initial,
                fontSize = 13.sp,
                color = appColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = person.name,
            color = appColors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "提及 $count 次",
            color = appColors.textSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
