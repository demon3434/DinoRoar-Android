package com.example.dinoroar.ui.sticker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.network.StickerConfigDto

/**
 * 贴纸选择器的底部托盘，展示当前已选的贴纸列表，并支持滑轨查看、删除单张、清空及确定添加带回编辑页。
 */
@Composable
fun StickerPickerBottomTray(
    selectedStickers: MutableList<StickerConfigDto>,
    serverBaseUrl: String,
    cardBg: Color,
    darkBg: Color,
    neonAmber: Color,
    neonRed: Color,
    neonGreen: Color,
    textPrimary: Color,
    onDone: (List<String>) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = cardBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选贴纸 (${selectedStickers.size}/6)",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (selectedStickers.isNotEmpty()) {
                    Text(
                        text = "清空 🗑",
                        color = neonRed,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { selectedStickers.clear() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(selectedStickers) { sticker ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(darkBg)
                                .border(1.dp, neonAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            val localRes = com.example.dinoroar.ui.main.getStickerLocalResource(sticker.image_url)
                            val modelData: Any = if (localRes != null) localRes else {
                                if (sticker.image_url.startsWith("/static/")) serverBaseUrl + sticker.image_url else sticker.image_url
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(modelData)
                                    .crossfade(true)
                                    .error(com.example.dinoroar.R.drawable.mood_triceratops)
                                    .build(),
                                contentDescription = sticker.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    .clickable { selectedStickers.remove(sticker) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onDone(selectedStickers.map { it.id.toString() }) },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("确定添加", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
