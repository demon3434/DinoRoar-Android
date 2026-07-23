package com.example.dinoroar.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState {
    START,
    PLAYING,
    GAME_OVER
}

enum class DinoState {
    RUNNING,
    JUMPING,
    DUCKING,
    CRASHED
}

/**
 * 恐龙小游戏物理及状态计算中心
 */
class CamouflageGameViewModel : ViewModel() {

    var gameState by mutableStateOf(GameState.START)
        private set

    var dinoState by mutableStateOf(DinoState.RUNNING)
        private set

    var score by mutableStateOf(0)
        private set

    var highScore by mutableStateOf(0)
        private set

    var lives by mutableStateOf(5)
        private set

    var invincibleFrames by mutableStateOf(0)
        private set

    var dinoY by mutableStateOf(0f)
        private set

    var dinoVelocityY by mutableStateOf(0f)
        private set

    var cactusX by mutableStateOf(1280f)
        private set

    var cactusSpeed by mutableStateOf(15f)
        private set

    var cactusType by mutableStateOf(0)
        private set

    // 翼龙特定状态
    var pteroHeightType by mutableStateOf(0)
        private set
    var pteroFrame by mutableStateOf(0)
        private set

    var hasHeartItem by mutableStateOf(false)
        private set

    var heartItemX by mutableStateOf(0f)
        private set

    var bgOffset by mutableStateOf(0f)
        private set

    var dinoLegFrame by mutableStateOf(0)
        private set

    // 恐龙毛色状态 (默认棕色 0xFF795548)
    var selectedDinoColor by mutableStateOf(Color(0xFF795548))
        private set

    // 翼龙毛色状态 (默认棕色 0xFF795548)
    var selectedPteroColor by mutableStateOf(Color(0xFF795548))
        private set

    // 解锁点击计数
    var avatarTapCount by mutableStateOf(0)
        private set
    var titleTapCount by mutableStateOf(0)
        private set

    private var gameJob: Job? = null
    private val gravity = 2.5f
    private var isDuckActive = false

    fun changeDinoColor(color: Color) {
        selectedDinoColor = color
    }

    fun startGame(screenWidth: Float) {
        gameJob?.cancel()
        gameState = GameState.PLAYING
        dinoState = DinoState.RUNNING
        dinoY = 0f
        dinoVelocityY = 0f
        isDuckActive = false
        val trackMargin = screenWidth * 0.15f
        cactusX = screenWidth - trackMargin
        cactusSpeed = 19f
        score = 0
        lives = 5
        invincibleFrames = 0
        hasHeartItem = false
        avatarTapCount = 0
        titleTapCount = 0
    }

    /**
     * 每隔约 16ms 驱动一次物理帧刷新
     */
    fun tickGameFrame(
        screenWidth: Float,
        screenHeight: Float,
        dinoSize: Float,
        frameCount: Int
    ) {
        if (gameState != GameState.PLAYING) return

        // 1. 无敌时间扣减
        if (invincibleFrames > 0) {
            invincibleFrames--
        }

        // 2. 恐龙跳跃起落计算与状态切换
        if (dinoY > 0f || dinoVelocityY != 0f) {
            // 在空中时若按了下蹲键，则加速向下砸
            val curGravity = if (isDuckActive && dinoVelocityY > 0f) gravity * 4.5f else gravity
            dinoVelocityY += curGravity
            dinoY -= dinoVelocityY
            if (dinoY <= 0f) {
                dinoY = 0f
                dinoVelocityY = 0f
                dinoState = if (isDuckActive) DinoState.DUCKING else DinoState.RUNNING
            }
        } else {
            dinoState = if (isDuckActive) DinoState.DUCKING else DinoState.RUNNING
        }

        // 3. 跑步动效换腿
        if (dinoY == 0f && frameCount % 5 == 0) {
            dinoLegFrame = 1 - dinoLegFrame
        }

        // 翼龙翅膀摆动换帧
        if (cactusType == 3 && frameCount % 12 == 0) {
            pteroFrame = 1 - pteroFrame
        }

        // 4. 移动障碍物与背景滚动
        cactusX -= cactusSpeed
        if (hasHeartItem) {
            heartItemX -= cactusSpeed
            if (heartItemX < 0f) {
                hasHeartItem = false
            }
        }
        bgOffset = (bgOffset - cactusSpeed) % 120f
        if (bgOffset > 0f) bgOffset -= 120f

        // 每 5 秒提速
        if (frameCount % 300 == 0) {
            cactusSpeed = (cactusSpeed + 2.0f).coerceAtMost(36f)
        }

        // 5. 障碍物出屏重置与计分 (在可视安全带左侧边缘 trackMargin 之外算作出屏)
        val trackMargin = screenWidth * 0.15f
        val minX = trackMargin - dinoSize
        if (cactusX < minX) {
            // 重置到右侧安全可视带以外
            cactusX = screenWidth - trackMargin + Random.nextFloat() * 300f + 100f
            
            // 如果得分达到 3 分，35% 概率生成翼龙
            if (score >= 3 && Random.nextFloat() < 0.35f) {
                cactusType = 3
                pteroHeightType = Random.nextInt(3) // 0=低空, 1=中空, 2=高空
                val pteroColors = listOf(
                    Color(0xFF795548), // 棕色
                    Color(0xFF37474F), // 碳黑
                    Color(0xFF8B0000), // 暗红
                    Color(0xFF1565C0), // 深蓝
                    Color(0xFF6A1B9A), // 紫色
                    Color(0xFF2E5B37)  // 墨绿
                )
                selectedPteroColor = pteroColors[Random.nextInt(pteroColors.size)]
            } else {
                cactusType = Random.nextInt(3)
            }
            
            score++
            if (score > highScore) {
                highScore = score
            }

            // 15% 概率在障碍物后方生成空中血包
            if (!hasHeartItem && Random.nextFloat() < 0.15f) {
                hasHeartItem = true
                heartItemX = cactusX + 750f + Random.nextFloat() * 400f
            }
        }

        // 6. 碰撞检测 (物理碰撞盒紧密包裹设计)
        val groundY = screenHeight * 0.8f
        val dinoLeftX = trackMargin + 40f

        // 物理包围框：根据下蹲状态自适应调整宽高，与绘制点阵完美贴合
        val dinoLeft = dinoLeftX + dinoSize * 0.05f
        val dinoRight = if (dinoState == DinoState.DUCKING) {
            dinoLeftX + dinoSize * 0.88f
        } else {
            dinoLeftX + dinoSize * 0.70f
        }
        val dinoBottom = groundY - dinoY
        val dinoTop = if (dinoState == DinoState.DUCKING) {
            groundY - dinoY - dinoSize * 0.45f
        } else {
            groundY - dinoY - dinoSize * 0.75f
        }

        // 障碍物碰撞盒计算
        val isCollidingCactus = if (cactusType == 3) {
            // 翼龙碰撞盒
            val pteroWidth = dinoSize * 0.8f
            val pteroHeight = dinoSize * 0.6f
            val pteroYVal = when (pteroHeightType) {
                0 -> dinoSize * 0.15f // 低空，需跳过，下蹲也撞
                1 -> dinoSize * 0.65f // 中空，可下蹲或起跳，站立被撞
                else -> dinoSize * 1.15f // 高空，站立和下蹲都安全
            }
            val cacLeft = cactusX
            val cacRight = cacLeft + pteroWidth
            val cacBottom = groundY - pteroYVal
            val cacTop = cacBottom - pteroHeight

            dinoRight > cacLeft && dinoLeft < cacRight &&
                    dinoBottom > cacTop && dinoTop < cacBottom
        } else {
            // 仙人掌碰撞盒
            val cacWidth = when (cactusType) {
                2 -> dinoSize * 0.75f
                else -> dinoSize * 0.45f
            }
            val cacHeight = when (cactusType) {
                1 -> dinoSize * 0.95f
                else -> dinoSize * 0.7f
            }
            val cacLeft = cactusX
            val cacRight = cacLeft + cacWidth
            val cacBottom = groundY
            val cacTop = cacBottom - cacHeight

            dinoRight > cacLeft && dinoLeft < cacRight &&
                    dinoBottom > cacTop && dinoTop < cacBottom
        }

        if (isCollidingCactus && invincibleFrames <= 0) {
            lives--
            if (lives <= 0) {
                gameState = GameState.GAME_OVER
                dinoState = DinoState.CRASHED
                gameJob?.cancel()
            } else {
                invincibleFrames = 90
                cactusX = screenWidth - trackMargin + 100f
                if (score >= 3 && Random.nextFloat() < 0.35f) {
                    cactusType = 3
                    pteroHeightType = Random.nextInt(3)
                } else {
                    cactusType = Random.nextInt(3)
                }
            }
        }

        // 吃到空中血包检测
        if (hasHeartItem) {
            val heartItemY = dinoSize * 1.4f
            val heartWidth = dinoSize * 0.5f
            val heartHeight = dinoSize * 0.5f
            val heartLeft = heartItemX
            val heartRight = heartLeft + heartWidth
            val heartBottom = groundY - heartItemY
            val heartTop = heartBottom - heartHeight

            val isCollidingHeart = dinoRight > heartLeft && dinoLeft < heartRight &&
                    dinoBottom > heartTop && dinoTop < heartBottom

            if (isCollidingHeart) {
                hasHeartItem = false
                lives = (lives + 1).coerceAtMost(5)
            }
        }
    }

    fun jump(dinoSize: Float) {
        if (gameState == GameState.PLAYING && dinoY == 0f) {
            dinoState = DinoState.JUMPING
            val maxJumpHeight = dinoSize * 1.8f
            dinoVelocityY = -sqrt(2f * gravity * maxJumpHeight)
        }
    }

    fun onJumpPressed(dinoSize: Float) {
        jump(dinoSize)
    }

    fun onJumpReleased() {
        // 可实现微小的起跳高度微调，当前原版保持即可
    }

    fun onDuckPressed() {
        if (gameState == GameState.PLAYING) {
            isDuckActive = true
            if (dinoState == DinoState.RUNNING) {
                dinoState = DinoState.DUCKING
            } else if (dinoState == DinoState.JUMPING) {
                // 如果在空中，重置向上速度，强制向下俯冲
                if (dinoVelocityY < 0f) {
                    dinoVelocityY = 0f
                }
                dinoVelocityY += gravity * 8f
            }
        }
    }

    fun onDuckReleased() {
        isDuckActive = false
        if (gameState == GameState.PLAYING && dinoState == DinoState.DUCKING) {
            dinoState = DinoState.RUNNING
        }
    }

    fun onAvatarTapped(onTriggerUnlock: () -> Unit) {
        avatarTapCount++
        if (avatarTapCount >= 5) {
            avatarTapCount = 0
            onTriggerUnlock()
        }
    }

    fun onTitleTapped(onTriggerUnlock: () -> Unit) {
        titleTapCount++
        if (titleTapCount >= 5) {
            titleTapCount = 0
            onTriggerUnlock()
        }
    }

    fun restartGame() {
        gameState = GameState.PLAYING
    }

    fun backToMenu() {
        gameState = GameState.START
        gameJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        gameJob?.cancel()
    }
}
