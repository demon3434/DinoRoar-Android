package com.example.dinoroar.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * 极简优雅的高级状态过滤胶囊组件
 * 彻底杜绝文字变更引起的宽度突变与抖动，通过呼吸指示点与微光色彩平滑渐变传达激活状态
 */
@Composable
fun ElegantFilterChip(
    text: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color? = null
) {
    val appColors = LocalAppColors.current
    val highlightColor = activeColor ?: appColors.neonGreen

    val animatedBgColor by animateColorAsState(
        targetValue = if (selected) highlightColor.copy(alpha = 0.16f) else appColors.cardBg.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "chipBg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (selected) highlightColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(220),
        label = "chipBorder"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (selected) highlightColor else appColors.textSecondary,
        animationSpec = tween(220),
        label = "chipText"
    )
    val animatedDotColor by animateColorAsState(
        targetValue = if (selected) highlightColor else appColors.textSecondary.copy(alpha = 0.35f),
        animationSpec = tween(220),
        label = "chipDot"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(50),
        color = animatedBgColor,
        border = BorderStroke(1.dp, animatedBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 状态指示点
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(animatedDotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = animatedTextColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
