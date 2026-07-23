package com.example.dinoroar.ui.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import com.example.dinoroar.theme.LocalAppColors

/**
 * 守护恐龙心情选择全屏界面。
 * 展示所有已激活恐龙列表供用户点选，点击后回调 [onMoodSelected] 并可通过 [onNavigateBack] 返回。
 */
@Composable
fun LogMoodSelectView(
    currentMoodId: Int,
    dinos: List<Triple<Int, Int, String>>,
    onMoodSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonBlue = appColors.neonBlue
    val neonAmber = appColors.neonAmber
    val neonGreen = appColors.neonGreen
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = neonAmber
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "选择你的守护心情",
                color = neonAmber,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // 恐龙列表
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            dinos.forEach { (id, resId, desc) ->
                val isSelected = currentMoodId == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) neonBlue.copy(alpha = 0.15f) else cardBg)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) neonBlue else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            onMoodSelected(id)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = id.toString(),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        val fullTitle = desc.substringBefore("\n")
                        val subtitle = desc.substringAfter("\n", "")
                        val parts = fullTitle.split(" ").filter { it.isNotBlank() }
                        val (moodPart, namePart) = parseMoodAndName(parts, fullTitle)

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = neonGreen, fontWeight = FontWeight.Bold)) {
                                    append(moodPart)
                                }
                                if (namePart.isNotEmpty()) {
                                    append(" ")
                                    withStyle(SpanStyle(color = neonAmber, fontWeight = FontWeight.Bold)) {
                                        append(namePart)
                                    }
                                }
                            },
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        if (subtitle.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                color = textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
