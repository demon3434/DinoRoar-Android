package com.example.dinoroar.ui.main.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinoroar.theme.LocalAppColors
import com.example.dinoroar.data.local.ActivityStateTracker
import com.example.dinoroar.ui.diary.CanvasExchangeActivity
import com.example.dinoroar.ui.sticker.StickerExchangeActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandcraftShopMenuScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val darkBg = appColors.darkBg
    val cardBg = appColors.cardBg
    val neonAmber = appColors.neonAmber
    val neonBlue = appColors.neonBlue
    val textPrimary = appColors.textPrimary
    val textSecondary = appColors.textSecondary

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🛍️ 欢迎光临手账商城",
            color = neonAmber,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "积攒写日记奖励的蛋能量，在这里兑换各种装扮吧",
            color = textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // 二级菜单卡片 1：贴纸商城
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clickable {
                    ActivityStateTracker.isExternalActivityActive = true
                    val intent = Intent(context, StickerExchangeActivity::class.java)
                    context.startActivity(intent)
                }
                .border(
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(neonAmber.copy(alpha = 0.3f), Color.Transparent))),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "贴纸商城 🦕🛒",
                        color = neonAmber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "解锁超萌的各类恐龙情绪贴纸\n丰富你日记里的心境表达！",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    color = neonAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🦖", fontSize = 28.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 二级菜单卡片 2：画布商城
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clickable {
                    ActivityStateTracker.isExternalActivityActive = true
                    val intent = Intent(context, CanvasExchangeActivity::class.java)
                    context.startActivity(intent)
                }
                .border(
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(neonBlue.copy(alpha = 0.3f), Color.Transparent))),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "画布商城 🖼️🛍️",
                        color = neonBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "解锁不同心境主题的手账背景底图\n完美适应 4 档宽高比自适应渲染！",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    color = neonBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎨", fontSize = 28.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}
