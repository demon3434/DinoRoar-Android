package com.example.dinoroar.ui.lock

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dinoroar.ui.lock.components.GameControls
import com.example.dinoroar.ui.lock.components.GameOverPanel
import com.example.dinoroar.ui.lock.components.GameStartPanel
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay

@Composable
fun CamouflageGameScreen(
    onTriggerUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CamouflageGameViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 强行拦截手势与物理返回事件，处于伪装游戏按返回时一律销毁 Activity 退出应用，绝不暴露日记界面
    BackHandler(enabled = true) {
        activity?.finish()
    }

    // 动态控制屏幕横竖屏旋转：进入小游戏横屏，退出/解锁后恢复默认竖屏
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // 护眼暖绿纸张配色
    val bgColor = Color(0xFFF1F6F1)
    val gridColor = Color(0xFFE2EBE2)
    val panelBg = Color(0xFFE8EFE8)
    val dinoColor = Color(0xFF795548) // 默认棕色
    val cactusColor = Color(0xFF388E3C) // 松树障碍物改为绿色
    val dangerColor = Color(0xFFC62828) // 警告深红
    val heartColor = Color(0xFFEC407A) // 道具粉红
    val textColor = Color(0xFF263238) // 深灰蓝

    val gameState = viewModel.gameState
    val dinoState = viewModel.dinoState
    val score = viewModel.score
    val highScore = viewModel.highScore
    val lives = viewModel.lives
    val invincibleFrames = viewModel.invincibleFrames
    val dinoY = viewModel.dinoY
    val cactusX = viewModel.cactusX
    val cactusType = viewModel.cactusType
    val pteroHeightType = viewModel.pteroHeightType
    val pteroFrame = viewModel.pteroFrame
    val hasHeartItem = viewModel.hasHeartItem
    val heartItemX = viewModel.heartItemX
    val bgOffset = viewModel.bgOffset
    val dinoLegFrame = viewModel.dinoLegFrame

    // 屏幕宽度与高度自适应状态
    var screenWidth by remember { mutableStateOf(1280f) }
    var screenHeight by remember { mutableStateOf(720f) }

    // 动态恐龙大小自适应：固定为当前屏幕像素高度的 18%
    val dinoSize = remember(screenHeight) { (screenHeight * 0.18f).coerceIn(60f, 150f) }
    val heartItemY = remember(dinoSize) { dinoSize * 1.4f }

    // 游戏主物理循环更新传递
    LaunchedEffect(gameState, screenWidth, screenHeight) {
        if (gameState == GameState.PLAYING) {
            viewModel.startGame(screenWidth)
            var frameCount = 0
            while (viewModel.gameState == GameState.PLAYING) {
                delay(16)
                frameCount++
                viewModel.tickGameFrame(screenWidth, screenHeight, dinoSize, frameCount)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 顶部状态栏：头像、心心血条、得分
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：游戏小恐龙图标头像 + 心心血条
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .border(1.5.dp, Color(0xFFB8CBB8), RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.onAvatarTapped(onTriggerUnlock)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = com.example.dinoroar.R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 5个心心血条
                    Row {
                        for (i in 0 until 5) {
                            val hasLife = i < lives
                            Text(
                                text = if (hasLife) "❤️" else "🖤",
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 1.dp)
                            )
                        }
                    }
                }

                // 右侧：积分面板
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "最高得分  %05d".format(highScore),
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "当前得分  %05d".format(score),
                        color = textColor,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. 游戏核心 Canvas 画布区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 背景装饰网格
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSpacing = 80f
                    for (x in 0 until (size.width / gridSpacing).toInt() + 2) {
                        drawLine(
                            color = gridColor.copy(alpha = 0.5f),
                            start = Offset(x * gridSpacing, 0f),
                            end = Offset(x * gridSpacing, size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0 until (size.height / gridSpacing).toInt() + 2) {
                        drawLine(
                            color = gridColor.copy(alpha = 0.5f),
                            start = Offset(0f, y * gridSpacing),
                            end = Offset(size.width, y * gridSpacing),
                            strokeWidth = 1f
                        )
                    }
                }

                // 核心动画和绘制 Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (screenWidth != size.width || screenHeight != size.height) {
                        screenWidth = size.width
                        screenHeight = size.height
                    }

                    val width = size.width
                    val height = size.height
                    val groundY = height * 0.8f

                    // 左右跑道向内收窄安全边距
                    val trackMargin = width * 0.15f

                    // 绘制地平线 (仅在可见安全带内)
                    drawLine(
                        color = dinoColor.copy(alpha = 0.2f),
                        start = Offset(trackMargin, groundY),
                        end = Offset(width - trackMargin, groundY),
                        strokeWidth = 6f
                    )
                    drawLine(
                        color = dinoColor,
                        start = Offset(trackMargin, groundY),
                        end = Offset(width - trackMargin, groundY),
                        strokeWidth = 2f
                    )

                    // 裁剪并绘制滚动的地平线虚线与障碍物，避免突出大拇指遮挡区域
                    clipRect(left = trackMargin, right = width - trackMargin) {
                        // 绘制滚动虚线段
                        val dashWidth = 30f
                        val gapWidth = 90f
                        val cycleWidth = dashWidth + gapWidth
                        var currentX = trackMargin + bgOffset
                        while (currentX < width - trackMargin) {
                            if (currentX + dashWidth > trackMargin) {
                                drawLine(
                                    color = dinoColor.copy(alpha = 0.4f),
                                    start = Offset(currentX, groundY + 8f),
                                    end = Offset(currentX + dashWidth, groundY + 8f),
                                    strokeWidth = 3f
                                )
                            }
                            currentX += cycleWidth
                        }

                        // 绘制加血道具 (像素红心)
                        if (hasHeartItem && (gameState == GameState.PLAYING || gameState == GameState.GAME_OVER)) {
                            val hX = heartItemX
                            val hWidth = dinoSize * 0.4f
                            val hHeight = hWidth * (12f / 15f)
                            val hY = groundY - heartItemY - hHeight
                            val cellSize = hWidth / 15f
                            val pGrid = DinoCanvasSprites.FRAME_HEART
                            
                            for (r in 0 until 12) {
                                for (c in 0 until 15) {
                                    if (pGrid[r][c] == 'x') {
                                        drawRect(
                                            color = heartColor,
                                            topLeft = Offset(hX + c * cellSize, hY + r * cellSize),
                                            size = Size(cellSize + 0.3f, cellSize + 0.3f)
                                        )
                                    }
                                }
                            }
                        }

                        // 绘制障碍物
                        if (gameState == GameState.PLAYING || gameState == GameState.GAME_OVER) {
                            if (cactusType == 3) {
                                // 绘制翼龙 (随机颜色)
                                val pteroWidth = dinoSize * 0.8f
                                val pteroHeight = dinoSize * 0.6f
                                val pteroYVal = when (pteroHeightType) {
                                    0 -> dinoSize * 0.15f
                                    1 -> dinoSize * 0.65f
                                    else -> dinoSize * 1.15f
                                }
                                val cX = cactusX
                                val cY = groundY - pteroYVal - pteroHeight
                                val pGrid = if (pteroFrame == 0) DinoCanvasSprites.FRAME_PTERO1 else DinoCanvasSprites.FRAME_PTERO2
                                val cellW = pteroWidth / 46f
                                val cellH = pteroHeight / 40f
                                
                                for (r in 0 until 40) {
                                    for (c in 0 until 46) {
                                        if (pGrid[r][c] == 'x') {
                                            drawRect(
                                                color = viewModel.selectedPteroColor,
                                                topLeft = Offset(cX + c * cellW, cY + r * cellH),
                                                size = Size(cellW + 0.3f, cellH + 0.3f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // 陆地障碍物计算宽高度
                                val cacW = when (cactusType) {
                                    2 -> dinoSize * 0.75f
                                    else -> dinoSize * 0.45f
                                }
                                val cacH = when (cactusType) {
                                    1 -> dinoSize * 0.95f
                                    else -> dinoSize * 0.7f
                                }
                                val cX = cactusX
                                val cY = groundY - cacH

                                if (cactusType == 2) {
                                    // 绘制三层层叠松树
                                    val trunkW = cacW * 0.2f
                                    val trunkH = cacH * 0.2f
                                    drawRect(
                                        color = cactusColor.copy(alpha = 0.85f),
                                        topLeft = Offset(cX + cacW * 0.4f, cY + cacH * 0.8f),
                                        size = Size(trunkW, trunkH)
                                    )

                                    val pinePath = Path().apply {
                                        moveTo(cX + cacW * 0.5f, cY)
                                        lineTo(cX + cacW * 0.35f, cY + cacH * 0.3f)
                                        lineTo(cX + cacW * 0.65f, cY + cacH * 0.3f)
                                        close()

                                        moveTo(cX + cacW * 0.5f, cY + cacH * 0.2f)
                                        lineTo(cX + cacW * 0.2f, cY + cacH * 0.55f)
                                        lineTo(cX + cacW * 0.8f, cY + cacH * 0.55f)
                                        close()

                                        moveTo(cX + cacW * 0.5f, cY + cacH * 0.4f)
                                        lineTo(cX, cY + cacH * 0.8f)
                                        lineTo(cX + cacW, cY + cacH * 0.8f)
                                        close()
                                    }
                                    drawPath(path = pinePath, color = cactusColor)
                                } else {
                                    // 绘制经典仙人掌 (cactusType == 0 或 1)
                                    val cGrid = if (cactusType == 0) DinoCanvasSprites.FRAME_CACTUS_SMALL else DinoCanvasSprites.FRAME_CACTUS_LARGE
                                    val cols = if (cactusType == 0) 17 else 25
                                    val rows = if (cactusType == 0) 35 else 50
                                    val cellW = cacW / cols.toFloat()
                                    val cellH = cacH / rows.toFloat()

                                    for (r in 0 until rows) {
                                        for (c in 0 until cols) {
                                            if (cGrid[r][c] == 'x') {
                                                drawRect(
                                                    color = cactusColor,
                                                    topLeft = Offset(cX + c * cellW, cY + r * cellH),
                                                    size = Size(cellW + 0.3f, cellH + 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 绘制霸王龙 (只使用 Canvas 自绘制分支，高精像素定位)
                        val dX = trackMargin + 40f
                        val alphaVal = if (invincibleFrames > 0 && (invincibleFrames / 5) % 2 == 0) 0.3f else 1.0f

                        if (dinoState == DinoState.DUCKING) {
                            // 下蹲点阵绘制：尺寸 59x30
                            val dY = groundY - (dinoSize * 0.45f)
                            val cellSize = (dinoSize * 0.45f) / 30f
                            val currentGrid = if (dinoLegFrame == 0 && gameState == GameState.PLAYING) {
                                DinoCanvasSprites.FRAME_DUCK1
                            } else {
                                DinoCanvasSprites.FRAME_DUCK2
                            }

                            for (r in 0 until 30) {
                                for (c in 0 until 59) {
                                    if (currentGrid[r][c] == 'x') {
                                        drawRect(
                                            color = viewModel.selectedDinoColor,
                                            topLeft = Offset(dX + c * cellSize, dY + r * cellSize),
                                            size = Size(cellSize + 0.3f, cellSize + 0.3f),
                                            alpha = alphaVal
                                        )
                                    }
                                }
                            }
                        } else {
                            // 站立或跳跃点阵绘制：尺寸 40x43
                            val dY = groundY - dinoY - (dinoSize * 0.75f)
                            val cellSize = (dinoSize * 0.75f) / 43f
                            val currentGrid = when {
                                dinoY > 0f -> DinoCanvasSprites.FRAME_IDLE
                                dinoLegFrame == 0 && gameState == GameState.PLAYING -> DinoCanvasSprites.FRAME_RUN1
                                dinoLegFrame == 1 && gameState == GameState.PLAYING -> DinoCanvasSprites.FRAME_RUN2
                                else -> DinoCanvasSprites.FRAME_IDLE
                            }

                            for (r in 0 until 43) {
                                for (c in 0 until 40) {
                                    if (currentGrid[r][c] == 'x') {
                                        drawRect(
                                            color = viewModel.selectedDinoColor,
                                            topLeft = Offset(dX + c * cellSize, dY + r * cellSize),
                                            size = Size(cellSize + 0.3f, cellSize + 0.3f),
                                            alpha = alphaVal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 游戏控制层与不同状态的界面遮罩
        if (gameState == GameState.PLAYING) {
            // 双手触控按键
            GameControls(
                viewModel = viewModel,
                dinoSize = dinoSize
            )
        }

        // START 面板组件
        if (gameState == GameState.START) {
            GameStartPanel(
                viewModel = viewModel,
                onTriggerUnlock = onTriggerUnlock,
                pulseScale = pulseScale,
                textColor = textColor,
                dinoColor = dinoColor
            )
        }

        // GAME_OVER 面板组件
        if (gameState == GameState.GAME_OVER) {
            GameOverPanel(
                viewModel = viewModel,
                score = score,
                panelBg = panelBg,
                dangerColor = dangerColor,
                textColor = textColor,
                dinoColor = dinoColor
            )
        }
    }
}
