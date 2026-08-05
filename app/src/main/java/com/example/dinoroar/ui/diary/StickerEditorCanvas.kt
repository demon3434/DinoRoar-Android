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
                return if (source == NestedScrollSource.UserInput) available
                else androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // 用 onSizeChanged 捕获画布实际渲染像素尺寸，转换为 dp 后计算缩放比
    // 比 BoxWithConstraints 更可靠（BoxWithConstraints 在 verticalScroll 内 maxHeight=Infinity）
    var canvasSizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg.copy(alpha = 0.4f))
            .border(1.dp, neonBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .nestedScroll(stickerDragNestedScrollConnection)
            .onSizeChanged { canvasSizePx = it }  // 第一次 layout 后即可得到真实像素宽高
    ) {
        // 将像素宽高转换为 dp，用于计算缩放比（首帧前兜底为 LOGICAL 值，等同 scaleX=1）
        val canvasW = if (canvasSizePx.width > 0) canvasSizePx.width / density else STICKER_CANVAS_LOGICAL_W
        val canvasH = if (canvasSizePx.height > 0) canvasSizePx.height / density else STICKER_CANVAS_LOGICAL_H
        // 逻辑坐标 → 实际 dp 的缩放比
        val scaleX = canvasW / STICKER_CANVAS_LOGICAL_W
        val scaleY = canvasH / STICKER_CANVAS_LOGICAL_H
        // 贴纸图标是固定的 56 屏幕dp，需要除以 scaleX 转换为逻辑坐标再计算边界
        // 这样无论 canvas 多宽，贴纸右/下边缘都精确贴到画布边缘
        val maxLogicX = STICKER_CANVAS_LOGICAL_W - STICKER_SIZE_DP / scaleX
        val maxLogicY = STICKER_CANVAS_LOGICAL_H - STICKER_SIZE_DP / scaleY

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
            // offsetX/Y 保存的是逻辑坐标（与屏幕尺寸无关）
            var offsetX by remember(sticker.id) { mutableStateOf(sticker.x) }
            var offsetY by remember(sticker.id) { mutableStateOf(sticker.y) }

            Box(
                modifier = Modifier
                    // 渲染时将逻辑坐标乘以缩放比，还原为当前设备上的实际 dp offset
                    .offset((offsetX * scaleX).dp, (offsetY * scaleY).dp)
                    .size(STICKER_SIZE_DP.dp)
                    .pointerInput(sticker.id) {
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
