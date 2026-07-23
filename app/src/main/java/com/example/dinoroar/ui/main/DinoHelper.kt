package com.example.dinoroar.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

data class StickerInfo(
    val dinoId: String,
    val x: Float,
    val y: Float,
    val isNew: Boolean = false,
    val id: String = java.util.UUID.randomUUID().toString()
)

// HSL Premium Colors
val NeonBlue = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF66)
val NeonRed = Color(0xFFFF1744)
val NeonAmber = Color(0xFFFFB300)

fun getMoodScore(moodId: Int): Int {
    return when (moodId) {
        1 -> 10
        2 -> 9
        3 -> 8
        4 -> 7
        5 -> 6
        6 -> 5
        7 -> 4
        8 -> 3
        9 -> 2
        10 -> 1
        11 -> 0
        else -> 5
    }
}

fun getFallbackStickerResource(): Int {
    return com.example.dinoroar.R.drawable.sticker_fallback_logo
}

fun getDinoResource(moodId: Int): Int {
    return when (moodId) {
        1 -> com.example.dinoroar.R.drawable.mood_triceratops
        2 -> com.example.dinoroar.R.drawable.mood_pterodactyl_happy
        3 -> com.example.dinoroar.R.drawable.mood_t_rex_proud
        4 -> com.example.dinoroar.R.drawable.mood_brachiosaurus
        5 -> com.example.dinoroar.R.drawable.mood_stegosaurus
        6 -> com.example.dinoroar.R.drawable.mood_velociraptor
        7 -> com.example.dinoroar.R.drawable.mood_ankylosaurus_scared
        8 -> com.example.dinoroar.R.drawable.mood_pachycephalosaurus
        9 -> com.example.dinoroar.R.drawable.mood_parasaurolophus_regret
        10 -> com.example.dinoroar.R.drawable.mood_spinosaurus
        11 -> com.example.dinoroar.R.drawable.mood_dilophosaurus
        else -> com.example.dinoroar.R.drawable.mood_triceratops
    }
}

fun getDinoName(moodId: Int): String {
    return when (moodId) {
        1 -> "快乐三角龙"
        2 -> "冲天翼手龙"
        3 -> "挺胸霸王龙"
        4 -> "大眼睛雷龙"
        5 -> "呆呆剑龙"
        6 -> "佛系迅猛龙"
        7 -> "缩壳甲龙"
        8 -> "叹气肿头龙"
        9 -> "耷拉角副栉龙"
        10 -> "细雨棘龙"
        11 -> "怒火双脊龙"
        else -> "秘密小恐龙"
    }
}

fun getDinoMoodLabel(moodId: Int): String {
    return when (moodId) {
        1 -> "😊 开心"
        2 -> "🤩 兴奋"
        3 -> "😎 得意"
        4 -> "🌟 期待"
        5 -> "😮 惊讶"
        6 -> "😐 一般"
        7 -> "😰 紧张"
        8 -> "🍃 遗憾"
        9 -> "😣 后悔"
        10 -> "😭 伤心"
        11 -> "😡 愤怒"
        else -> "🦕 神秘"
    }
}

@Composable
fun rememberImageFromFile(file: File): ImageBitmap? {
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        if (file.exists()) {
            try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                if (raw != null) {
                    bitmap = raw.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return bitmap
}

fun getStickerLocalResource(imageUrl: String): Int? {
    val clean = imageUrl.trim().substringAfterLast('/')
    return when (clean) {
        "sticker_3d_triceratops.png", "sticker_3d_triceratops.webp" -> com.example.dinoroar.R.drawable.sticker_3d_triceratops
        "sticker_3d_pterodactyl.png", "sticker_3d_pterodactyl.webp" -> com.example.dinoroar.R.drawable.sticker_3d_pterodactyl
        "sticker_3d_t_rex.png", "sticker_3d_t_rex.webp" -> com.example.dinoroar.R.drawable.sticker_3d_t_rex
        "sticker_3d_brachiosaurus.png", "sticker_3d_brachiosaurus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_brachiosaurus
        "sticker_3d_stegosaurus.png", "sticker_3d_stegosaurus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_stegosaurus
        "sticker_3d_velociraptor.png", "sticker_3d_velociraptor.webp" -> com.example.dinoroar.R.drawable.sticker_3d_velociraptor
        "sticker_3d_ankylosaurus.png", "sticker_3d_ankylosaurus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_ankylosaurus
        "sticker_3d_pachycephalosaurus.png", "sticker_3d_pachycephalosaurus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_pachycephalosaurus
        "sticker_3d_parasaurolophus.png", "sticker_3d_parasaurolophus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_parasaurolophus
        "sticker_3d_spinosaurus.png", "sticker_3d_spinosaurus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_spinosaurus
        "sticker_3d_dilophosaurus.png", "sticker_3d_dilophosaurus.webp" -> com.example.dinoroar.R.drawable.sticker_3d_dilophosaurus
        else -> null
    }
}

fun getDinoMoodTip(moodId: Int): String {
    return when (moodId) {
        1 -> "快乐是会传染的，今天也要开心哦！"
        2 -> "把快乐写进日记，让它飞得更高吧！"
        3 -> "你太棒了！今天也是值得自豪的一天！"
        4 -> "未来闪闪发光，让我们一起期待明天吧！"
        5 -> "哇，今天发生了意想不到的奇妙事情呢！"
        6 -> "平静的一天也很美好，休息一下吧！"
        7 -> "别怕，缩进壳里也是保护自己的好办法，你很安全！"
        8 -> "没关系，每一次小小的遗憾都是成长的足迹。"
        9 -> "别太自责，过去的事就让它过去，下次会更好！"
        10 -> "伤心的时候可以哭出来，雨过天晴总会放晴的。"
        11 -> "深呼吸，把怒火倾诉给恐龙，它会默默倾听你的委屈。"
        else -> ""
    }
}
