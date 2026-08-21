package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dinoroar.theme.LocalAppColors

data class CheckInBubbleState(
    val isVisible: Boolean = false,
    val icon: String = "🎉",
    val title: String = "",
    val message: String = "",
    val isCrit: Boolean = false,
    val isSuccess: Boolean = true
)

/**
 * 每日签到结果顶层反馈弹窗 (Topmost Check-in Result Dialog)
 * 采用全屏 Dialog 浮层，绝对不被侧边栏 Drawer 遮挡，且由用户主动点击关闭。
 */
@Composable
fun CheckInBubble(
    state: CheckInBubbleState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    val appColors = LocalAppColors.current

    val borderColor = if (state.isCrit) {
        Color(0xFFF59E0B)
    } else if (state.isSuccess) {
        appColors.neonGreen
    } else {
        appColors.neonRed
    }

    val themeColor = if (state.isCrit) Color(0xFFF59E0B) else if (state.isSuccess) appColors.neonGreen else appColors.neonRed

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = appColors.cardBg,
            border = BorderStroke(1.5.dp, borderColor),
            shadowElevation = 16.dp,
            modifier = modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部图标
                Text(
                    text = state.icon,
                    fontSize = 44.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 标题
                Text(
                    text = state.title,
                    color = themeColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (state.isCrit) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEA580C).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "💥 触发破壳暴击！",
                            color = Color(0xFFEA580C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 内容详情
                Text(
                    text = state.message,
                    color = appColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 底部确定按钮（由用户自己主动点击关闭）
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = if (state.isSuccess) "我知道啦 ✨" else "确定",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
