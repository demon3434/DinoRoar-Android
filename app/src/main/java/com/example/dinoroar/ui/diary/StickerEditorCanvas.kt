package com.example.dinoroar.ui.diary

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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

/** 贴纸画布逻辑参考宽度（dp）—— 与屏幕实际宽度无关，用于统一双端坐标系 */
const val STICKER_CANVAS_LOGICAL_W = 360f

/** 贴纸画布逻辑参考高度（dp）—— 与 Canvas height(180.dp) 保持一致 */
const val STICKER_CANVAS_LOGICAL_H = 180f

/** 贴纸图标尺寸（dp），用于计算可拖拽范围上限 */
const val STICKER_SIZE_DP = 56f

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
    canvasInstanceId: Int?,
    canvasAspectRatio: String,
    canvasImageUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    val parts = canvasAspectRatio.split(":")
    val wPart = parts.getOrNull(0)?.toFloatOrNull() ?: 2f
    val hPart = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
    val aspectFloat = wPart / hPart
    val logicalHeight = STICKER_CANVAS_LOGICAL_W / aspectFloat



    // 拦截父级 verticalScroll 在拖拽贴纸时抢夺触控焦点
    val stickerDragNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                return if (source == NestedScrollSource.UserInput) available
                else androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectFloat)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, neonBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .nestedScroll(stickerDragNestedScrollConnection)
            .onSizeChanged { canvasSizePx = it }
    ) {
        // 渲染画布底图背景
        if (canvasInstanceId != null && canvasInstanceId != -1) {
            if (!canvasImageUrl.isNullOrBlank()) {
                val fullUrl = if (canvasImageUrl.startsWith("/static/")) serverBaseUrl + canvasImageUrl else canvasImageUrl
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(fullUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "背景画布",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 根据比例选择专属兜底图
                val fallbackResId = when (canvasAspectRatio) {
                    "16:9" -> com.example.dinoroar.R.drawable.canvas_fallback_16_9
                    "4:3" -> com.example.dinoroar.R.drawable.canvas_fallback_4_3
                    "1:1" -> com.example.dinoroar.R.drawable.canvas_fallback_1_1
                    "2:1" -> com.example.dinoroar.R.drawable.canvas_fallback_2_1
                    else -> com.example.dinoroar.R.drawable.canvas_fallback_2_1
                }
                Image(
                    painter = painterResource(id = fallbackResId),
                    contentDescription = "默认画布兜底",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cardBg.copy(alpha = 0.4f))
                )
            }
        } else {
            // 没有背景画布，一片空白，仅展示纯色底色背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardBg.copy(alpha = 0.4f))
            )
        }

        val canvasW = if (canvasSizePx.width > 0) canvasSizePx.width / density else STICKER_CANVAS_LOGICAL_W
        val canvasH = if (canvasSizePx.height > 0) canvasSizePx.height / density else logicalHeight
        val scaleX = canvasW / STICKER_CANVAS_LOGICAL_W
        val scaleY = canvasH / logicalHeight
        val maxLogicX = STICKER_CANVAS_LOGICAL_W - STICKER_SIZE_DP
        val maxLogicY = logicalHeight - STICKER_SIZE_DP
        val stickerPhysicalSizeDp = STICKER_SIZE_DP * scaleX

        if (stickers.isEmpty() && (canvasInstanceId == null || canvasInstanceId == -1)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "请在此铺设画布、装饰贴纸",
                    color = textSecondary.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        stickers.forEachIndexed { index, sticker ->
            // offsetX/Y 保存的是逻辑坐标（与屏幕尺寸无关）
            var offsetX by remember(sticker.id, sticker.x) { mutableStateOf(sticker.x) }
            var offsetY by remember(sticker.id, sticker.y) { mutableStateOf(sticker.y) }

            Box(
                modifier = Modifier
                    // 渲染时将逻辑坐标乘以缩放比，还原为当前设备上的实际 dp offset
                    .offset((offsetX * scaleX).dp, (offsetY * scaleY).dp)
                    .size(stickerPhysicalSizeDp.dp)
                    .pointerInput(sticker.id, maxLogicX, maxLogicY, scaleX, scaleY) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = { },
                            onDragCancel = { },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // 将物理像素位移转换为逻辑坐标增量：先除 density（→dp），再除 scale（→逻辑dp）
                                val newX = (offsetX + dragAmount.x / density / scaleX).coerceIn(0f, maxLogicX)
                                val newY = (offsetY + dragAmount.y / density / scaleY).coerceIn(0f, maxLogicY)
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
