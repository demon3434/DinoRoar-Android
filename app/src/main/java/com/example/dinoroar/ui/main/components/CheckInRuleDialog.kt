package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.dinoroar.theme.LocalAppColors

/**
 * 每日敲蛋签到规则说明弹窗
 */
@Composable
fun CheckInRuleDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.neonBlue
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "我知道啦",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        },
        title = {
            Text(
                text = "📜 每日敲蛋签到规则",
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "⭐ 什么是敲蛋签到？\n每日可在应用内完成一次敲蛋打卡，赢取用于在【手账商城】兑换专属贴纸与特色画布的“蛋能量”。",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
                Text(
                    text = "🎁 每日基础奖励\n• 每日首次敲蛋打卡，可随机获得 10 ~ 25 点基础蛋能量。",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
                Text(
                    text = "💥 破壳暴击奖励\n• 每次敲蛋均有一定几率触发【破壳暴击】！\n• 触发暴击时，单次将直接获得 40 ~ 60 点超高额蛋能量奖励。",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
                Text(
                    text = "👣 连续签到加成\n• 保持每日连续打卡，可激活阶梯式连签奖励：\n  - 连续打卡 3 天：当日额外获赠 +5 蛋能量\n  - 连续打卡 7 天：当日额外获赠 +15 蛋能量\n  - 连续打卡 14 天及以上：当日额外获赠 +30 蛋能量\n• 若中途中断签到，连续天数将重置回第 1 天。",
                    color = appColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
                Text(
                    text = "💡 规则小贴士\n• 签到状态于每日自然日 00:00 自动刷新。\n• 所获蛋能量永久有效，可随时前往手账商城挑选心仪的恐龙贴纸与装饰道具。",
                    color = appColors.textSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        },
        containerColor = appColors.cardBg,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    )
}
