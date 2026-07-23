package com.example.dinoroar.ui.lock

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

data class DinoInfo(
    val id: Int,
    val name: String,
    val drawableRes: Int
)

@Composable
fun NineGridLockScreen(
    correctPattern: String, // e.g. "2,2,4"
    onUnlockSuccess: () -> Unit,
    onBackToGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val securePrefs = remember { com.example.dinoroar.data.local.SecurePrefs(context) }

    // 强行拦截手势与物理返回事件，彻底禁止物理返回退栈绕过密码保护
    BackHandler(enabled = true) {
        if (securePrefs.isCamouflageEnabled) {
            onBackToGame()
        } else {
            activity?.finish()
        }
    }

    val hapticFeedback = LocalHapticFeedback.current

    // 暗黑霓虹科幻配色
    val bgColor = Color(0xFF121214) // 纯粹夜空黑
    val cardBg = Color(0xFF1E1E22) // 卡片哑光背景
    val cardBorderDefault = Color(0xFF2E3035) // 卡片默认边框
    val neonBlue = Color(0xFF00E5FF) // 霓虹天蓝
    val neonRed = Color(0xFFFF1744) // 警告深红
    val neonAmber = Color(0xFFFFB300) // 霓虹琥珀金

    // 服务端定义的 5 种形态卡通恐龙与数字 1~5 一一映射
    val dinoList = remember {
        listOf(
            DinoInfo(1, "霸王龙", com.example.dinoroar.R.drawable.t_rex),
            DinoInfo(2, "三角龙", com.example.dinoroar.R.drawable.triceratops),
            DinoInfo(3, "剑龙", com.example.dinoroar.R.drawable.stegosaurus),
            DinoInfo(4, "翼手龙", com.example.dinoroar.R.drawable.pterodactyl),
            DinoInfo(5, "腕龙", com.example.dinoroar.R.drawable.brachiosaurus)
        )
    }

    // 存储当前 3x3 按钮中的恐龙 ID (1 ~ 5)
    val shuffledGrid = remember { mutableStateListOf<Int>() }

    // 九宫格生成洗牌算法：先随机生成 4 个恐龙，再与 5 个必选恐龙合并，最后随机彻底打乱
    fun shuffleGrid() {
        shuffledGrid.clear()
        // 1. 先随机生成 4 个恐龙 (1~5 范围)
        val random4 = List(4) { kotlin.random.Random.nextInt(1, 6) }
        // 2. 准备 5 个必选恐龙
        val required5 = listOf(1, 2, 3, 4, 5)
        // 3. 合并并随机打乱
        val combined = (random4 + required5).shuffled()
        shuffledGrid.addAll(combined)
    }

    // 首次进入时执行洗牌
    LaunchedEffect(Unit) {
        shuffleGrid()
    }

    // 分割解析云端下发的密码序列
    val correctList = remember(correctPattern) {
        correctPattern.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    // 记录用户已点击成功匹配的按键序列
    val inputSequence = remember { mutableStateListOf<Int>() }
    val displaySequence = remember { mutableStateListOf<Int>() }
    var isError by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("依次点击正确的恐龙序列解锁基舱") }

    // 错误重置逻辑
    LaunchedEffect(isError) {
        if (isError) {
            delay(1000)
            inputSequence.clear()
            displaySequence.clear()
            isError = false
            statusText = "解密失败，请再次输入恐龙序列"
            // 重新在后台进行洗牌打乱位置，防止偷窥
            shuffleGrid()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. 顶部终端信息面板
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = "🦕 恐龙认证终端 🦖",
                color = neonAmber,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                color = if (isError) neonRed else Color.LightGray,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }

        // 2. 密码反馈卡片指示栏 (槽数与密码总位数相等，展示已通过的恐龙卡通微缩图)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until correctList.size) {
                val hasInput = i < displaySequence.size
                val activeId = if (hasInput) displaySequence[i] else null
                val activeDino = dinoList.firstOrNull { it.id == activeId }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(48.dp)
                        .background(
                            color = if (isError) neonRed.copy(alpha = 0.1f) else cardBg,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = when {
                                isError -> neonRed
                                hasInput -> neonBlue
                                else -> cardBorderDefault
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeDino != null) {
                        Image(
                            painter = painterResource(id = activeDino.drawableRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    } else {
                        // 未填槽时显示微小的占位点
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // 3. 3x3 动态洗牌正方形恐龙卡片网格
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            for (row in 0 until 3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        if (index < shuffledGrid.size) {
                            val dinoId = shuffledGrid[index]
                            val dino = dinoList.first { it.id == dinoId }
                            
                            DinoImageCard(
                                drawableRes = dino.drawableRes,
                                isSelected = false, // 禁用点击高亮色框，始终保持哑光边框防窥
                                isError = isError,
                                neonBlue = neonBlue,
                                neonRed = neonRed,
                                cardBg = cardBg,
                                cardBorderDefault = cardBorderDefault,
                                onClick = {
                                    if (!isError && inputSequence.size < correctList.size) {
                                        // 仅仅将所点击的 ID 加入输入队列，提供点击震动反馈
                                        inputSequence.add(dinoId)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                        // 生成一个随机恐龙 ID 作为掩护进度展示
                                        val randomDinoId = kotlin.random.Random.nextInt(1, 6)
                                        displaySequence.add(randomDinoId)

                                        // 碰撞避免：输满 3 位时如果随机掩护序列恰好等于正确密码序列，强行把最后一位换为非正确值
                                        if (displaySequence.size == correctList.size) {
                                            val isFullyCorrect = displaySequence.zip(correctList).all { it.first == it.second }
                                            if (isFullyCorrect) {
                                                val lastCorrect = correctList.last()
                                                val alternatives = (1..5).filter { it != lastCorrect }
                                                displaySequence[displaySequence.lastIndex] = alternatives.random()
                                            }
                                        }
                                        
                                        // 只在输满 3 个时，才执行统一校验
                                        if (inputSequence.size == correctList.size) {
                                            val isMatched = inputSequence.zip(correctList).all { it.first == it.second }
                                            if (isMatched) {
                                                statusText = "认证通过！"
                                                onUnlockSuccess()
                                            } else {
                                                // 输满 3 个才判定失败，防止侧信道猜测密码
                                                isError = true
                                                statusText = "解锁序列不正确，请重新输入"
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3.5 恐龙守护防丢温馨提示卡
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(neonAmber.copy(alpha = 0.4f), Color.Transparent, neonBlue.copy(alpha = 0.4f))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                append("💡 ")
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = neonAmber)) {
                    append("恐龙防丢守护卡")
                }
                append("：只保存在手机里的记忆（")
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = neonAmber)) {
                    append("树洞")
                }
                append("）在删除软件或清理手机时可能会永久丢失哦 😭。只有存入")
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = neonBlue)) {
                    append("秘密基地")
                }
                append("，小恐龙才能帮你永久存盘，换手机也能找回！")
            }
            Text(
                text = annotatedString,
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
                fontFamily = FontFamily.Monospace
            )
        }

        // 4. 底部返回游戏按钮
        Box(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .clickable { onBackToGame() }
        ) {
            Text(
                text = "返回小游戏",
                color = Color.Gray,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DinoImageCard(
    drawableRes: Int,
    isSelected: Boolean,
    isError: Boolean,
    neonBlue: Color,
    neonRed: Color,
    cardBg: Color,
    cardBorderDefault: Color,
    onClick: () -> Unit
) {
    // 按压缩放回弹
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(120),
        label = "scale"
    )

    // 变色边框
    val borderColor by animateColorAsState(
        targetValue = when {
            isError && isSelected -> neonRed
            isSelected -> neonBlue
            else -> cardBorderDefault
        },
        animationSpec = tween(200),
        label = "border"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
            .background(cardBg, RoundedCornerShape(14.dp))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )
    }

    // 回弹定时器
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }
}
