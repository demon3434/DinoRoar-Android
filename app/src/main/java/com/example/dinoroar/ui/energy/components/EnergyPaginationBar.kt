package com.example.dinoroar.ui.energy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.theme.LocalAppColors

/**
 * 蛋能量流水账本后端分页控制条 (对齐 Web 端标准)
 * [◀ 上一页]  第 1 / 2 页 (共 28 笔)  [ 20 条/页 ▼ ]  [下一页 ▶]
 */
@Composable
fun EnergyPaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalCount: Int,
    pageSize: Int,
    isLoading: Boolean,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    var isPageSizeMenuExpanded by remember { mutableStateOf(false) }

    val hasPrev = currentPage > 1
    val hasNext = currentPage < totalPages

    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = appColors.cardBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 上一页按钮
            Box(
                modifier = Modifier
                    .widthIn(min = 68.dp)
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasPrev && !isLoading) appColors.neonGreen.copy(alpha = 0.15f)
                        else Color.Gray.copy(alpha = 0.08f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (hasPrev && !isLoading) appColors.neonGreen.copy(alpha = 0.4f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = hasPrev && !isLoading) {
                        onPageChange(currentPage - 1)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◀ 上一页",
                    color = if (hasPrev && !isLoading) appColors.neonGreen else appColors.textSecondary.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 2. 中间页码与条数信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$currentPage/${maxOf(1, totalPages)}页",
                    color = appColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )

                Text(
                    text = "(${totalCount}笔)",
                    color = appColors.textSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )

                // 每页条数下拉选择胶囊
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                            .border(0.5.dp, borderCol, RoundedCornerShape(6.dp))
                            .clickable { isPageSizeMenuExpanded = true }
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$pageSize 条 ▼",
                            color = appColors.neonAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    DropdownMenu(
                        expanded = isPageSizeMenuExpanded,
                        onDismissRequest = { isPageSizeMenuExpanded = false },
                        modifier = Modifier
                            .background(appColors.cardBg)
                            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                    ) {
                        listOf(10, 20, 50).forEach { size ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$size 条/页",
                                        color = if (size == pageSize) appColors.neonAmber else appColors.textPrimary,
                                        fontWeight = if (size == pageSize) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    isPageSizeMenuExpanded = false
                                    onPageSizeChange(size)
                                }
                            )
                        }
                    }
                }
            }

            // 3. 下一页按钮
            Box(
                modifier = Modifier
                    .widthIn(min = 68.dp)
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasNext && !isLoading) appColors.neonGreen.copy(alpha = 0.15f)
                        else Color.Gray.copy(alpha = 0.08f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (hasNext && !isLoading) appColors.neonGreen.copy(alpha = 0.4f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = hasNext && !isLoading) {
                        onPageChange(currentPage + 1)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "下一页 ▶",
                    color = if (hasNext && !isLoading) appColors.neonGreen else appColors.textSecondary.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
