package com.example.dinoroar.ui.diary

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.ui.main.StickerInfo
import com.example.dinoroar.ui.main.getDinoResource

/** 手账贴纸最大数量约束（全局硬性上限：不得超过 6 张）*/
const val STICKER_MAX_COUNT = 6

/**
 * 手账贴纸拖拽编辑 Canvas 组件。
 * 负责渲染、拖拽定位、以及删除贴纸；严格强制最大 [STICKER_MAX_COUNT] 张上限。
 */
@Composable
fun StickerEditorCanvas(
    stickers: MutableList<StickerInfo>,
    stickerCacheMap: Map<Int, String>,
    serverBaseUrl: String,
    cardBg: Color,
    neonBlue: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // 拦截父级 verticalScroll 在拖拽贴纸时抢夺触控焦点
    val stickerDragNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                return if (source == NestedScrollSource.Drag) available
                else androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg.copy(alpha = 0.4f))
            .border(1.dp, neonBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .nestedScroll(stickerDragNestedScrollConnection)
    ) {
        if (stickers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "这里是手账草地 🌴\n在下方选择恐龙贴纸，然后拖动它装饰你的日记吧！",
                    color = textSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        stickers.forEachIndexed { index, sticker ->
            var offsetX by remember(sticker.id) { mutableStateOf(sticker.x) }
            var offsetY by remember(sticker.id) { mutableStateOf(sticker.y) }

            Box(
                modifier = Modifier
                    .offset(offsetX.dp, offsetY.dp)
                    .size(56.dp)
                    .pointerInput(sticker.id) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = { },
                            onDragCancel = { },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newX = (offsetX + dragAmount.x / density).coerceIn(0f, 280f)
                                val newY = (offsetY + dragAmount.y / density).coerceIn(0f, 120f)
                                offsetX = newX
                                offsetY = newY
                                stickers[index] = stickers[index].copy(x = newX, y = newY)
                            }
                        )
                    }
            ) {
                val stickerId = sticker.dinoId.toIntOrNull() ?: 1
                val imgUrl = stickerCacheMap[stickerId]

                if (imgUrl != null) {
                    val localRes = com.example.dinoroar.ui.main.getStickerLocalResource(imgUrl)
                    val modelData: Any = if (localRes != null) {
                        localRes
                    } else if (imgUrl.startsWith("/static/") || imgUrl.startsWith("http")) {
                        if (imgUrl.startsWith("/static/")) serverBaseUrl + imgUrl else imgUrl
                    } else {
                        "$serverBaseUrl/static/images/dinosaurs/$imgUrl"
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(modelData)
                            .crossfade(true)
                            .error(getDinoResource(if (stickerId >= 1000) stickerId - 1000 else stickerId))
                            .build(),
                        contentDescription = sticker.dinoId,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val renderId = if (stickerId >= 1000) stickerId - 1000 else stickerId
                    Image(
                        painter = painterResource(id = getDinoResource(renderId)),
                        contentDescription = sticker.dinoId,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 删除按钮（× 角标）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .clickable {
                            stickers.remove(sticker)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除贴纸",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * 处理从 StickerPickerActivity 返回的贴纸选择结果，更新 stickers 列表。
 * 强制保证最终贴纸数不超过 [STICKER_MAX_COUNT] 张上限。
 *
 * @param selectedIds  从 picker 带回的贴纸 ID 列表
 * @param stickers     当前可变贴纸列表，直接原地更新
 * @param onLimitExceeded  若 picker 返回的总量超过上限时，通知调用方显示 Toast
 */
fun applyStickerPickerResult(
    selectedIds: List<String>,
    stickers: MutableList<StickerInfo>,
    onLimitExceeded: (() -> Unit)? = null
) {
    val newStickers = mutableListOf<StickerInfo>()
    val currentMap = stickers.groupBy { it.dinoId }.mapValues { it.value.toMutableList() }

    selectedIds.forEach { stickerId ->
        val existingList = currentMap[stickerId]
        if (existingList != null && existingList.isNotEmpty()) {
            newStickers.add(existingList.removeAt(0))
        } else {
            if (newStickers.size < STICKER_MAX_COUNT) {
                val idx = newStickers.size
                val col = idx % 3
                val row = idx / 3
                val newX = 10f + col * 90f
                val newY = 10f + row * 65f
                newStickers.add(StickerInfo(stickerId, newX, newY))
            } else {
                onLimitExceeded?.invoke()
            }
        }
    }

    stickers.clear()
    stickers.addAll(newStickers)
}
