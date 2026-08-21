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
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.PromotionSummaryDto
import com.example.dinoroar.ui.diary.CanvasExchangeActivity
import com.example.dinoroar.ui.sticker.StickerExchangeActivity
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandcraftShopMenuScreen(
    apiService: DinoApiService? = null,
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

    val activePromos by produceState<List<PromotionSummaryDto>>(initialValue = emptyList()) {
        value = try {
            apiService?.getActivePromotionsSummary() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        if (activePromos.isNotEmpty()) {
            val promo = activePromos.first()
            val rulesText = if (promo.rules_summary.isNotEmpty()) {
                "（${promo.rules_summary.joinToString("，")}）"
            } else if (!promo.description.isNullOrBlank()) {
                "（${promo.description}）"
            } else ""

            Surface(
                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎉", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "节日特惠活动进行中：${promo.name}$rulesText",
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                        if (!promo.description.isNullOrBlank() && promo.rules_summary.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "💬 ${promo.description}",
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }


        // 二级菜单卡片 1：贴纸商城
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clickable {
                    val intent = Intent(context, StickerExchangeActivity::class.java)
                    context.startActivity(intent)
                },
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
