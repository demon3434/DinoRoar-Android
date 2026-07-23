package com.example.dinoroar.ui.lock.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.ui.lock.CamouflageGameViewModel

@Composable
fun GameStartPanel(
    viewModel: CamouflageGameViewModel,
    onTriggerUnlock: () -> Unit,
    pulseScale: Float,
    textColor: Color,
    dinoColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 游戏标题
        Text(
            text = "森林恐龙大冒险",
            color = textColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.onTitleTapped(onTriggerUnlock)
                }
        )

        // 居中核心选色器和开始按钮
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // “开始游戏”按钮
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(44.dp)
                    .graphicsLayer(
                        scaleX = pulseScale,
                        scaleY = pulseScale
                    )
                    .background(dinoColor, RoundedCornerShape(22.dp))
                    .clickable {
                        viewModel.restartGame()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "开始游戏",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "点击小球更换小恐龙肤色：",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 水平换色圈
            val colors = listOf(
                Color(0xFF2E5B37) to "墨绿",
                Color(0xFF8B0000) to "暗红",
                Color(0xFFD2B48C) to "土黄",
                Color(0xFF795548) to "棕色",
                Color(0xFF1565C0) to "深蓝",
                Color(0xFFD84315) to "橙红",
                Color(0xFF6A1B9A) to "紫色",
                Color(0xFF37474F) to "碳黑"
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { (color, name) ->
                    val isSelected = viewModel.selectedDinoColor == color
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(color, RoundedCornerShape(15.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) textColor else Color.Transparent,
                                shape = RoundedCornerShape(15.dp)
                            )
                            .clickable {
                                viewModel.changeDinoColor(color)
                            }
                    )
                }
            }
        }
    }
}
