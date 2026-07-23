package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.ui.main.PersonMoodStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryPersonsPagerPanel(
    categories: List<PersonCategoryEntity>,
    categorySummaries: Map<String, List<PersonMoodStatus>>,
    onFilterPerson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val glassBg = appColors.cardBg.copy(alpha = 0.4f)
    val glassBorder = appColors.textSecondary.copy(alpha = 0.15f)

    // 过滤出未删除且有数据的分类，或者直接以所有未删除分类作为页数
    val activeCategories = remember(categories) { categories.filter { !it.isDeleted } }
    
    if (activeCategories.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(glassBg)
                .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🌴 还没有创建任何关系人分类哦",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { activeCategories.size })

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "👥 日记中的ta",
            color = appColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val category = activeCategories[page]
            val persons = categorySummaries[category.uuid] ?: emptyList()
            
            // 唯美渐变背景色定义
            val cardBrush = when (page % 4) {
                0 -> Brush.verticalGradient(listOf(Color(0x1B00E676), Color(0x0800E676))) // 🟢 绿
                1 -> Brush.verticalGradient(listOf(Color(0x1B2979FF), Color(0x082979FF))) // 🔵 蓝
                2 -> Brush.verticalGradient(listOf(Color(0x1BFF9100), Color(0x08FF9100))) // 🟡 橙
                else -> Brush.verticalGradient(listOf(Color(0x1BD500F9), Color(0x08D500F9))) // 🟣 紫
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .background(cardBrush)
                    .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header inside Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏷️ ${category.name} (${persons.size}人)",
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${page + 1} / ${activeCategories.size}",
                            color = appColors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (persons.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "该分类下暂无小伙伴，可在关系人管理中添加哦",
                                color = appColors.textSecondary.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        // Max 3 items display, if more we scroll inside card
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(persons) { status ->
                                PersonRowItem(
                                    status = status,
                                    onClick = { onFilterPerson(status.person.uuid) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRowItem(
    status: PersonMoodStatus,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val initial = status.person.name.firstOrNull()?.toString() ?: "👤"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(appColors.neonBlue.copy(alpha = 0.15f))
                .border(1.dp, appColors.neonBlue.copy(alpha = 0.3f), CircleShape)
        ) {
            Text(
                text = initial,
                fontSize = 12.sp,
                color = appColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Name & count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.person.name,
                color = appColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "📝 记录了 ${status.diaryCount} 篇日记",
                color = appColors.textSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Mood capsules (aggregated frequency count)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status.diaryCount == 0) {
                Text(
                    text = "暂无心情",
                    color = appColors.textSecondary.copy(alpha = 0.4f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                if (status.happyCount > 0) {
                    MoodCountBadge(emoji = "😊", count = status.happyCount, color = appColors.neonGreen)
                }
                if (status.calmCount > 0) {
                    MoodCountBadge(emoji = "😐", count = status.calmCount, color = appColors.neonBlue)
                }
                if (status.sadCount > 0) {
                    MoodCountBadge(emoji = "😢", count = status.sadCount, color = appColors.neonRed)
                }
            }
        }
    }
}

@Composable
private fun MoodCountBadge(
    emoji: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .width(40.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(top = 5.dp, bottom = 5.dp)
    ) {
        Text(text = emoji, fontSize = 13.sp, lineHeight = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$count",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
