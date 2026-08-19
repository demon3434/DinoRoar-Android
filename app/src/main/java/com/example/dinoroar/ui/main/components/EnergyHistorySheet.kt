package com.example.dinoroar.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.data.local.SecurePrefs
import com.example.dinoroar.network.EnergyTransactionDto
import com.example.dinoroar.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyHistorySheet(
    transactions: List<EnergyTransactionDto>,
    isLoading: Boolean,
    eggEnergy: Int,
    securePrefs: SecurePrefs,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.darkBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = appColors.textSecondary.copy(alpha = 0.4f))
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📜 蛋能量明细账本",
                        color = appColors.neonAmber,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "当前总余额：$eggEnergy 蛋能量",
                        color = appColors.neonGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = appColors.textSecondary.copy(alpha = 0.15f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 列表与空状态
            if (isLoading && transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = appColors.neonGreen, modifier = Modifier.size(36.dp))
                }
            } else if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🥚", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无蛋能量变动记录",
                            color = appColors.textSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        EnergyTransactionCard(
                            tx = tx,
                            serverUrl = securePrefs.serverUrl ?: ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyTransactionCard(
    tx: EnergyTransactionDto,
    serverUrl: String,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isEarn = tx.change_amount > 0
    val display = tx.asset_display

    // 解析图片全路径
    val rawImgUrl = display.image_url
    val fullImgUrl = if (!rawImgUrl.isNullOrBlank()) {
        if (rawImgUrl.startsWith("http://") || rawImgUrl.startsWith("https://")) {
            rawImgUrl
        } else {
            val base = serverUrl.trimEnd('/')
            val path = if (rawImgUrl.startsWith('/')) rawImgUrl else "/$rawImgUrl"
            "$base$path"
        }
    } else null

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = appColors.cardBg.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, appColors.textSecondary.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧实体形象缩略图
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (fullImgUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fullImgUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = display.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = when (tx.target_type_id) {
                            1 -> "🛍️"
                            2 -> "🥚"
                            3 -> "📓"
                            else -> "🎁"
                        },
                        fontSize = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 中间标题与角标
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(android.graphics.Color.parseColor(display.theme_color.ifBlank { "#10B981" })).copy(alpha = 0.18f),
                        border = BorderStroke(
                            1.dp,
                            Color(android.graphics.Color.parseColor(display.theme_color.ifBlank { "#10B981" })).copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = display.badge_label,
                            color = Color(android.graphics.Color.parseColor(display.theme_color.ifBlank { "#10B981" })),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = display.title,
                        color = appColors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = tx.created_at,
                    color = appColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧变动金额与变动后余额
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (isEarn) "+${tx.change_amount}" else "${tx.change_amount}",
                    color = if (isEarn) appColors.neonGreen else Color(0xFFE879F9),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "余 ${tx.balance_after}",
                    color = appColors.textSecondary.copy(alpha = 0.6f),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
