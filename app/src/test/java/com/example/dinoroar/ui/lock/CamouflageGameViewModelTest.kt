package com.example.dinoroar.ui.lock

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CamouflageGameViewModelTest {

    @Test
    fun testInitialState() {
        val viewModel = CamouflageGameViewModel()
        assertEquals(GameState.START, viewModel.gameState)
        assertEquals(0, viewModel.score)
        assertEquals(5, viewModel.lives)
        assertEquals(0f, viewModel.dinoY)
        assertEquals(Color(0xFF795548), viewModel.selectedDinoColor) // 默认棕色
    }

    @Test
    fun testStartGame() {
        val viewModel = CamouflageGameViewModel()
        viewModel.startGame(1280f)
        assertEquals(GameState.PLAYING, viewModel.gameState)
        assertEquals(0, viewModel.score)
        assertEquals(5, viewModel.lives)
        assertEquals(1280f - 1280f * 0.15f, viewModel.cactusX)
    }

    @Test
    fun testJumpOnlyOnGround() {
        val viewModel = CamouflageGameViewModel()
        viewModel.startGame(1280f)

        // 地面上起跳
        viewModel.jump(100f)
        assertTrue(viewModel.dinoVelocityY < 0f) // 初速度向上（负值）

        // 人为模拟在空中
        // tick 一帧让恐龙上天
        viewModel.tickGameFrame(1280f, 720f, 100f, 1)
        val airVelocity = viewModel.dinoVelocityY
        
        // 空中再次起跳不应该改变速度
        viewModel.jump(100f)
        assertEquals(airVelocity, viewModel.dinoVelocityY)
    }

    @Test
    fun testAvatarUnlockTapCount() {
        val viewModel = CamouflageGameViewModel()
        var unlocked = false
        
        // 连点 5 次触发解锁
        for (i in 1..5) {
            viewModel.onAvatarTapped {
                unlocked = true
            }
        }
        
        assertTrue(unlocked)
        assertEquals(0, viewModel.avatarTapCount) // 应该被重置为 0
    }

    @Test
    fun testDuckPressedAndReleased() {
        val viewModel = CamouflageGameViewModel()
        viewModel.startGame(1280f)
        assertEquals(DinoState.RUNNING, viewModel.dinoState)

        viewModel.onDuckPressed()
        viewModel.tickGameFrame(1280f, 720f, 100f, 1)
        assertEquals(DinoState.DUCKING, viewModel.dinoState)

        viewModel.onDuckReleased()
        viewModel.tickGameFrame(1280f, 720f, 100f, 2)
        assertEquals(DinoState.RUNNING, viewModel.dinoState)
    }

    @Test
    fun testAirDiveOnDuckPressed() {
        val viewModel = CamouflageGameViewModel()
        viewModel.startGame(1280f)
        
        // 起跳
        viewModel.onJumpPressed(100f)
        viewModel.tickGameFrame(1280f, 720f, 100f, 1)
        assertTrue(viewModel.dinoY > 0f)
        
        val normalVelocity = viewModel.dinoVelocityY
        
        // 空中按下下蹲，触发 Dive
        viewModel.onDuckPressed()
        viewModel.tickGameFrame(1280f, 720f, 100f, 2)
        
        // 应该具有比正常重力加速更大的向下速度
        assertTrue(viewModel.dinoVelocityY > normalVelocity + 2.5f)
    }

    @Test
    fun testChangeDinoColor() {
        val viewModel = CamouflageGameViewModel()
        val targetColor = Color(0xFF2E5B37) // 墨绿色
        viewModel.changeDinoColor(targetColor)
        assertEquals(targetColor, viewModel.selectedDinoColor)
    }

    @Test
    fun generateBase64() {
        val sprites = mapOf(
            "霸王龙待机帧 (FRAME_IDLE)" to DinoCanvasSprites.FRAME_IDLE,
            "霸王龙跑动帧1 (FRAME_RUN1)" to DinoCanvasSprites.FRAME_RUN1,
            "霸王龙跑动帧2 (FRAME_RUN2)" to DinoCanvasSprites.FRAME_RUN2,
            "霸王龙下蹲帧1 (FRAME_DUCK1)" to DinoCanvasSprites.FRAME_DUCK1,
            "霸王龙下蹲帧2 (FRAME_DUCK2)" to DinoCanvasSprites.FRAME_DUCK2,
            "天空翼龙帧1 (FRAME_PTERO1)" to DinoCanvasSprites.FRAME_PTERO1,
            "天空翼龙帧2 (FRAME_PTERO2)" to DinoCanvasSprites.FRAME_PTERO2,
            "加血道具红心帧 (FRAME_HEART)" to DinoCanvasSprites.FRAME_HEART
        )

        val md = StringBuilder()
        md.append("# 离线霸王龙小游戏像素图片 Base64 极速参考手册\n\n")
        md.append("本文件由单元测试自动生成。包含了目前游戏中使用的所有 8-bit 像素图片点阵结构、物理尺寸以及对应的超短 PNG Base64 预览编码。\n\n")
        md.append("## 像素图片一览表\n\n")
        md.append("| 序号 | 图片名称 | 尺寸 (宽x高) | 预览图 & Base64 编码 |\n")
        md.append("|:---|:---|:---|:---|\n")

        var index = 1
        for ((name, matrix) in sprites) {
            val h = matrix.size
            val w = matrix[0].length
            val scale = 1
            val img = java.awt.image.BufferedImage(w * scale, h * scale, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            // White transparent background
            g.color = java.awt.Color(0xF1, 0xF6, 0xF1)
            g.fillRect(0, 0, w * scale, h * scale)
            // Color selection
            if (name.contains("HEART")) {
                g.color = java.awt.Color(0xEC, 0x40, 0x7A) // Pink
            } else {
                g.color = java.awt.Color(0x79, 0x55, 0x48) // Dino Brown
            }

            for (r in 0 until h) {
                val line = matrix[r]
                for (c in 0 until w) {
                    if (c < line.length && line[c] == 'x') {
                        g.fillRect(c * scale, r * scale, scale, scale)
                    }
                }
            }
            g.dispose()
            val baos = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "png", baos)
            val base64 = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(baos.toByteArray())

            md.append("| $index | $name | ${w}x${h} | ![预览]($base64)<br><br>`$base64` |\n")
            index++
        }

        val outFile = java.io.File("docs/游戏图片Base64参考.md")
        outFile.parentFile.mkdirs()
        outFile.writeText(md.toString(), Charsets.UTF_8)
        println("Generated markdown successfully written to: ${outFile.absolutePath}")
    }
}
