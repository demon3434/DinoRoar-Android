package com.example.dinoroar.ui.diary

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.ui.main.StickerInfo
import com.example.dinoroar.ui.main.getDinoResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.detectTransformGestures

/** 手账贴纸最大数量约束（全局硬性上限：不得超过 6 张）*/
const val STICKER_MAX_COUNT = 6

/** 贴纸画布逻辑参考宽度（dp）—— 与屏幕实际宽度无关，用于统一双端坐标系 */
const val STICKER_CANVAS_LOGICAL_W = 360f

/** 贴纸画布逻辑参考高度（dp）—— 与 Canvas height(180.dp) 保持一致 */
const val STICKER_CANVAS_LOGICAL_H = 180f

/** 贴纸图标尺寸（dp），用于计算可拖拽范围上限 */
const val STICKER_SIZE_DP = 56f

/**
 * 自定义手势防滚动拦截器。
 * 只要用户在贴纸或控制柄上按下手指，就直接请求父 View 不要拦截此事件流。
 */
fun Modifier.disallowParentScroll(view: android.view.View, onDown: () -> Unit = {}): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            view.parent?.requestDisallowInterceptTouchEvent(true)
            onDown()
        }
    }
}

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
    selectedSticker: StickerInfo?,
    onSelectedStickerChange: (StickerInfo?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onSelectedStickerChange(null)
            }
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
            var offsetX by remember(sticker.id) { mutableStateOf(sticker.x) }
            var offsetY by remember(sticker.id) { mutableStateOf(sticker.y) }

            var parentCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
            var scaleHandleCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
            var rotateHandleCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }



            val isSelected = selectedSticker?.id == sticker.id

            val xMin = Math.max(0f, 28f * (sticker.scale - 1f))
            val xMax = STICKER_CANVAS_LOGICAL_W - 28f * (sticker.scale + 1f)
            val yMin = Math.max(0f, 28f * (sticker.scale - 1f))
            val yMax = logicalHeight - 28f * (sticker.scale + 1f)

            val cx = offsetX + 28f
            val cy = offsetY + 28f

            val safeX = offsetX.coerceIn(xMin, xMax)
            val safeY = offsetY.coerceIn(yMin, yMax)
            val finalLeftX = safeX - 28f * (sticker.scale - 1f)
            val finalTopY = safeY - 28f * (sticker.scale - 1f)
            val parentBoxSizeDp = stickerPhysicalSizeDp * sticker.scale + 24f

            Box(
                modifier = Modifier
                    .offset((finalLeftX * scaleX - 12).dp, (finalTopY * scaleY - 12).dp)
                    .size(parentBoxSizeDp.dp)
                    .disallowParentScroll(view) {
                        onSelectedStickerChange(sticker)
                    }
                    .pointerInput(sticker.id) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            val currentSticker = stickers[index]
                            // 1. 计算缩放并限制在 [1.0, 2.5]
                            val newScale = (currentSticker.scale * zoom).coerceIn(1.0f, 2.5f)
                            
                            // 2. 只有双指操作时才累加旋转（单指 tap/drag 时 zoom≈1.0，忽略微小抖动旋转）
                            val actualRotation = if (zoom != 1.0f) {
                                Math.toDegrees(rotation.toDouble()).toFloat()
                            } else {
                                0f
                            }
                            val newRotation = currentSticker.rotation + actualRotation
                            
                            // 3. 依据新缩放重新计算限位
                            val parts = canvasAspectRatio.split(":")
                            val wPart = parts.getOrNull(0)?.toFloatOrNull() ?: 2f
                            val hPart = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
                            val ratioVal = wPart / hPart
                            val currentLogicalHeight = STICKER_CANVAS_LOGICAL_W / ratioVal
                            
                            val newXMin = Math.max(0f, 28f * (newScale - 1f))
                            val newXMax = STICKER_CANVAS_LOGICAL_W - 28f * (newScale + 1f)
                            val newYMin = Math.max(0f, 28f * (newScale - 1f))
                            val newYMax = currentLogicalHeight - 28f * (newScale + 1f)
                            
                            // 4. 计算移动
                            val newX = (offsetX + pan.x / density / scaleX).coerceIn(newXMin, newXMax)
                            val newY = (offsetY + pan.y / density / scaleY).coerceIn(newYMin, newYMax)
                            
                            offsetX = newX
                            offsetY = newY
                            stickers[index] = currentSticker.copy(scale = newScale, rotation = newRotation, x = newX, y = newY)
                            onSelectedStickerChange(stickers[index])
                        }
                    }
                    .onGloballyPositioned { parentCoordinates = it }
                    .graphicsLayer(
                        scaleX = if (sticker.flipH) -1f else 1f,
                        scaleY = if (sticker.flipV) -1f else 1f,
                        rotationZ = sticker.rotation
                    )
            ) {
                // 1. 中央贴纸图片展示区
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onSelectedStickerChange(sticker)
                        }
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, neonBlue.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            } else Modifier
                        )
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
                }

                // 2. 悬浮操作控制柄 (仅在选中时呈现)
                if (isSelected) {
                    // ↖️ 左上角：垂直镜像翻转 (Flip V)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                val updated = sticker.copy(flipV = !sticker.flipV)
                                stickers[index] = updated
                                onSelectedStickerChange(updated)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f
                            
                            // 上半部分：空心直角三角形 (直角在右上角)
                            val topPath = Path().apply {
                                moveTo(0f, midY)
                                lineTo(w, midY)
                                lineTo(w, 0f)
                                close()
                            }
                            drawPath(
                                path = topPath,
                                color = Color.White,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            
                            // 下半部分：实心直角三角形 (直角在右下角)
                            val bottomPath = Path().apply {
                                moveTo(0f, midY)
                                lineTo(w, midY)
                                lineTo(w, h)
                                close()
                            }
                            drawPath(
                                path = bottomPath,
                                color = Color.White,
                                style = Fill
                            )
                        }
                    }

                    // ↗️ 右上角：一键删除贴纸 (Delete)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                stickers.remove(sticker)
                                onSelectedStickerChange(null)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val strokeW = 1.5.dp.toPx()
                            drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = strokeW)
                            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = strokeW)
                        }
                    }

                    // ↙️ 左下角：水平镜像翻转 (Flip H)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                val updated = sticker.copy(flipH = !sticker.flipH)
                                stickers[index] = updated
                                onSelectedStickerChange(updated)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            val w = size.width
                            val h = size.height
                            val midX = w / 2f
                            
                            // 左半部分：实心直角三角形 (直角在左下角)
                            val leftPath = Path().apply {
                                moveTo(midX, 0f)
                                lineTo(midX, h)
                                lineTo(0f, h)
                                close()
                            }
                            drawPath(
                                path = leftPath,
                                color = Color.White,
                                style = Fill
                            )
                            
                            // 右半部分：空心直角三角形 (直角在右下角)
                            val rightPath = Path().apply {
                                moveTo(midX, 0f)
                                lineTo(midX, h)
                                lineTo(w, h)
                                close()
                            }
                            drawPath(
                                path = rightPath,
                                color = Color.White,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    // ↘️ 右下角：等比缩放控制柄 (Scale 1.0 - 2.5)
                    // 采用窗口坐标系下的距离比例法：计算当前手指与贴纸中心的绝对像素距离相对于前一帧的比例，
                    // 进行等比缩放。该方法完全独立于容器本身的缩放/旋转/平移，彻底避免跳变与抖动。
                    var lastScaleWindowPos by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .disallowParentScroll(view)
                            .onGloballyPositioned { scaleHandleCoordinates = it }
                            .pointerInput(sticker.id) {
                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        scaleHandleCoordinates?.let { coords ->
                                            lastScaleWindowPos = coords.localToWindow(startPos)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val handleCoords = scaleHandleCoordinates
                                        val parentCoords = parentCoordinates
                                        if (handleCoords != null && parentCoords != null) {
                                            val currentWindowPos = handleCoords.localToWindow(change.position)
                                            val parentSize = parentCoords.size
                                            val centerWindowPos = parentCoords.localToWindow(
                                                Offset(parentSize.width / 2f, parentSize.height / 2f)
                                            )
                                            
                                            val currentDist = (currentWindowPos - centerWindowPos).getDistance()
                                            val previousDist = (lastScaleWindowPos - centerWindowPos).getDistance()
                                            
                                            if (previousDist > 1f) {
                                                val currentSticker = stickers[index]
                                                val scaleRatio = currentDist / previousDist
                                                val newScale = (currentSticker.scale * scaleRatio).coerceIn(1.0f, 2.5f)
                                                
                                                // 重新计算位置限位
                                                val parts = canvasAspectRatio.split(":")
                                                val wPart = parts.getOrNull(0)?.toFloatOrNull() ?: 2f
                                                val hPart = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
                                                val ratioVal = wPart / hPart
                                                val currentLogicalHeight = STICKER_CANVAS_LOGICAL_W / ratioVal
                                                
                                                val newXMin = Math.max(0f, 28f * (newScale - 1f))
                                                val newXMax = STICKER_CANVAS_LOGICAL_W - 28f * (newScale + 1f)
                                                val newYMin = Math.max(0f, 28f * (newScale - 1f))
                                                val newYMax = currentLogicalHeight - 28f * (newScale + 1f)
                                                
                                                val safeX = offsetX.coerceIn(newXMin, newXMax)
                                                val safeY = offsetY.coerceIn(newYMin, newYMax)
                                                
                                                offsetX = safeX
                                                offsetY = safeY
                                                stickers[index] = currentSticker.copy(scale = newScale, x = safeX, y = safeY)
                                                onSelectedStickerChange(stickers[index])
                                            }
                                            lastScaleWindowPos = currentWindowPos
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            val w = size.width
                            val h = size.height
                            val strokeWidth = 1.5.dp.toPx()
                            val padding = 2.dp.toPx()
                            
                            drawLine(
                                color = Color.White,
                                start = Offset(padding, padding),
                                end = Offset(w - padding, h - padding),
                                strokeWidth = strokeWidth
                            )
                            
                            val arrowLen = 4.dp.toPx()
                            // ↖ 左上角箭头
                            drawLine(
                                color = Color.White,
                                start = Offset(padding, padding),
                                end = Offset(padding + arrowLen, padding),
                                strokeWidth = strokeWidth
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(padding, padding),
                                end = Offset(padding, padding + arrowLen),
                                strokeWidth = strokeWidth
                            )
                            
                            // ↘ 右下角箭头
                            drawLine(
                                color = Color.White,
                                start = Offset(w - padding, h - padding),
                                end = Offset(w - padding - arrowLen, h - padding),
                                strokeWidth = strokeWidth
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(w - padding, h - padding),
                                end = Offset(w - padding, h - padding - arrowLen),
                                strokeWidth = strokeWidth
                            )
                        }
                    }

                    // ⬆️ 正上方：平面旋转控制柄 (Rotate)
                    // 采用窗口坐标系下的角度跟踪法：计算手指相对贴纸中心的绝对极坐标角度，并与前一帧相减，
                    // 得到真实物理旋转增量。该方法独立于容器自身的状态更新，彻底解决跳变与抖动。
                    var lastRotateWindowPos by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-24).dp)
                            .size(24.dp)
                            .disallowParentScroll(view)
                            .onGloballyPositioned { rotateHandleCoordinates = it }
                            .pointerInput(sticker.id) {
                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        rotateHandleCoordinates?.let { coords ->
                                            lastRotateWindowPos = coords.localToWindow(startPos)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val handleCoords = rotateHandleCoordinates
                                        val parentCoords = parentCoordinates
                                        if (handleCoords != null && parentCoords != null) {
                                            val currentSticker = stickers[index]
                                            val currentWindowPos = handleCoords.localToWindow(change.position)
                                            val parentSize = parentCoords.size
                                            val centerWindowPos = parentCoords.localToWindow(
                                                Offset(parentSize.width / 2f, parentSize.height / 2f)
                                            )
                                            
                                            val currentAngle = Math.toDegrees(
                                                Math.atan2(
                                                    (currentWindowPos.y - centerWindowPos.y).toDouble(),
                                                    (currentWindowPos.x - centerWindowPos.x).toDouble()
                                                )
                                            ).toFloat()
                                            
                                            val previousAngle = Math.toDegrees(
                                                Math.atan2(
                                                    (lastRotateWindowPos.y - centerWindowPos.y).toDouble(),
                                                    (lastRotateWindowPos.x - centerWindowPos.x).toDouble()
                                                )
                                            ).toFloat()
                                            
                                            var deltaAngle = currentAngle - previousAngle
                                            if (deltaAngle > 180f) deltaAngle -= 360f
                                            if (deltaAngle < -180f) deltaAngle += 360f
                                            
                                            val newRotation = currentSticker.rotation + deltaAngle
                                            stickers[index] = currentSticker.copy(rotation = newRotation)
                                            onSelectedStickerChange(stickers[index])
                                            
                                            lastRotateWindowPos = currentWindowPos
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val cx = w / 2f
                            val cy = h / 2f
                            
                            // 1. 绘制向下延伸的连接线 (连到贴纸虚线框顶部)
                            val strokeW = 1.dp.toPx()
                            drawLine(
                                color = Color.White.copy(alpha = 0.6f),
                                start = Offset(cx, cy),
                                end = Offset(cx, cy + 24.dp.toPx()),
                                strokeWidth = strokeW
                            )
                            
                            // 2. 绘制旋转球实心黑背景 + 白边框
                            val radius = 12.dp.toPx()
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.7f),
                                radius = radius,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            
                            // 3. 绘制顺时针旋转箭头 (半径 4.5dp)
                            val arcRadius = 4.5.dp.toPx()
                            drawArc(
                                color = Color.White,
                                startAngle = -180f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                                style = Stroke(width = 1.2f.dp.toPx())
                            )
                            // 终点在正下方 90 度 (cx, cy + arcRadius)。向左的箭头
                            val path = Path().apply {
                                moveTo(cx - 1.dp.toPx(), cy + arcRadius - 2.dp.toPx())
                                lineTo(cx - 4.dp.toPx(), cy + arcRadius)
                                lineTo(cx - 1.dp.toPx(), cy + arcRadius + 2.dp.toPx())
                                close()
                            }
                            drawPath(path, Color.White, style = Fill)
                        }
                    }

                    // ⬇️ 正下方：一键重置变换属性 (Reset)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 24.dp)
                            .size(24.dp)
                            .disallowParentScroll(view)
                            .clickable {
                                val updated = sticker.copy(scale = 1.0f, rotation = 0.0f, flipH = false, flipV = false)
                                stickers[index] = updated
                                onSelectedStickerChange(updated)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val cx = w / 2f
                            val cy = h / 2f
                            
                            // 1. 绘制向上延伸的连接线 (连到贴纸虚线框底部)
                            val strokeW = 1.dp.toPx()
                            drawLine(
                                color = Color.White.copy(alpha = 0.6f),
                                start = Offset(cx, cy),
                                end = Offset(cx, cy - 24.dp.toPx()),
                                strokeWidth = strokeW
                            )
                            
                            // 2. 绘制重置球实心黑背景 + 白边框
                            val radius = 12.dp.toPx()
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.7f),
                                radius = radius,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f),
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            
                            // 3. 绘制字母 "R" (高 8dp，宽约 5dp)
                            val rPath = Path().apply {
                                val left = cx - 2.5f.dp.toPx()
                                val top = cy - 4f.dp.toPx()
                                val right = cx + 2.5f.dp.toPx()
                                val bottom = cy + 4f.dp.toPx()
                                val midY = cy
                                
                                // 一竖
                                moveTo(left, top)
                                lineTo(left, bottom)
                                
                                // 上半圆环
                                moveTo(left, top)
                                lineTo(right - 1.dp.toPx(), top)
                                lineTo(right, top + 1.dp.toPx())
                                lineTo(right, midY - 1.dp.toPx())
                                lineTo(right - 1.dp.toPx(), midY)
                                lineTo(left, midY)
                                
                                // 右下一斜
                                moveTo(cx - 0.5f.dp.toPx(), midY)
                                lineTo(right, bottom)
                            }
                            drawPath(
                                path = rPath,
                                color = Color.White,
                                style = Stroke(width = 1.2f.dp.toPx())
                            )
                        }
                    }
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
