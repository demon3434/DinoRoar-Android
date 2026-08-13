package com.example.dinoroar.ui.diary.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.data.local.CanvasInstanceEntity
import kotlinx.coroutines.launch

/**
 * 全屏大图预览 Dialog组件
 * 1. 左右滑动 (HorizontalPager) 切换同一画布套系下不同长宽比大图
 * 2. 支持双指捏合缩放、双击放大/还原以及平移拖拽查看图片细节
 */
@Composable
fun CanvasPreviewDialog(
    instance: CanvasInstanceEntity,
    allInstancesInSet: List<CanvasInstanceEntity>,
    serverBaseUrl: String,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val initialPage = remember(instance.id, allInstancesInSet) {
        allInstancesInSet.indexOfFirst { it.id == instance.id }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { allInstancesInSet.size }
    val currentInstance = allInstancesInSet.getOrNull(pagerState.currentPage) ?: instance

    // 当应用进入 ON_STOP (切后台或锁屏) 时自动关闭全屏预览 Dialog，防止浮动 Window 遮挡锁屏界面
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                onDismiss()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 当前页面的缩放与平移状态
    var scale by remember(pagerState.currentPage) { mutableFloatStateOf(1f) }
    var offset by remember(pagerState.currentPage) { mutableStateOf(Offset.Zero) }

    val view = LocalView.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (view.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.let { w ->
                w.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                w.statusBarColor = android.graphics.Color.TRANSPARENT
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                val controller = androidx.core.view.WindowCompat.getInsetsController(w, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔍 预览画布 (${currentInstance.aspectRatio}) [${pagerState.currentPage + 1}/${allInstancesInSet.size}]",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "✕ 关闭",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                // 2. 中部 HorizontalPager 大图展示
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = scale <= 1.05f,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) { page ->
                    val pageInstance = allInstancesInSet.getOrNull(page) ?: currentInstance
                    val parts = pageInstance.aspectRatio.split(":")
                    val w = parts.getOrNull(0)?.toFloatOrNull() ?: 2f
                    val h = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
                    val aspect = w / h

                    val fullUrl = if (pageInstance.imageUrl.startsWith("/static/")) {
                        serverBaseUrl + pageInstance.imageUrl
                    } else {
                        pageInstance.imageUrl
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .aspectRatio(aspect)
                                .pointerInput(page) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.05f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 2.5f
                                                offset = Offset.Zero
                                            }
                                        }
                                    )
                                }
                                .pointerInput(page, scale) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val pointerCount = event.changes.size

                                            // 仅在已放大状态或双指捏合时拦截手势进行缩放平移，未放大时的单指单向滑动交由 Pager 处理
                                            if (scale > 1.01f || pointerCount > 1 || zoomChange != 1f) {
                                                val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                                if (newScale > 1.01f) {
                                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                                    val newOffset = offset + panChange
                                                    offset = Offset(
                                                        x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                                        y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                                    )
                                                } else {
                                                    offset = Offset.Zero
                                                }
                                                scale = newScale
                                                event.changes.forEach { it.consume() }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(fullUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "全屏预览图",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // 3. 底部长宽比选择栏 (点击可跳转指定 Pager Page)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "左右滑动或点击下方切换长宽比预览:",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allInstancesInSet.forEachIndexed { index, inst ->
                            val isSelected = index == pagerState.currentPage
                            val instParts = inst.aspectRatio.split(":")
                            val instW = instParts.getOrNull(0)?.toFloatOrNull() ?: 2f
                            val instH = instParts.getOrNull(1)?.toFloatOrNull() ?: 1f
                            val instAspect = instW / instH

                            Box(
                                modifier = Modifier
                                    .width(58.dp)
                                    .aspectRatio(instAspect)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                val instUrl = if (inst.imageUrl.startsWith("/static/")) {
                                    serverBaseUrl + inst.imageUrl
                                } else {
                                    inst.imageUrl
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(instUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(topEnd = 6.dp),
                                    modifier = Modifier.align(Alignment.BottomStart)
                                ) {
                                    Text(
                                        text = inst.aspectRatio,
                                        color = if (isSelected) Color(0xFFFFB300) else Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
