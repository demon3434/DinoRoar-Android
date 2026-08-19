package com.example.dinoroar.ui.energy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 列表吸顶月度小计条
 */
@Composable
fun EnergyMonthlySectionHeader(
    monthGroup: String,
    monthIncome: Int = 0,
    monthExpense: Int = 0,
    modifier: Modifier = Modifier
) {
    // 格式化 monthGroup (如 2026-08 -> 2026年 8月)
    val displayMonth = try {
        val parts = monthGroup.split("-")
        if (parts.size == 2) {
            "${parts[0]}年 ${parts[1].toInt()}月"
        } else {
            monthGroup
        }
    } catch (e: Exception) {
        monthGroup
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.96f))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayMonth,
            color = Color(0xFFF1F5F9),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (monthIncome > 0) {
                Text(
                    text = "入 +$monthIncome",
                    color = Color(0xFF34D399),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (monthExpense > 0) {
                Text(
                    text = "支 -$monthExpense",
                    color = Color(0xFFFB923C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
