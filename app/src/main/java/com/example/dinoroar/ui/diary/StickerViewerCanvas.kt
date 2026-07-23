package com.example.dinoroar.ui.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinoroar.ui.main.StickerInfo
import com.example.dinoroar.ui.main.getDinoResource
import com.example.dinoroar.ui.main.getStickerLocalResource

/**
 * 旧版贴纸 ID 名称到新数字 ID 的映射表（兼容历史数据）。
 * 在 [StickerViewerCanvas] 组件与 LogDetailScreen 中共享使用。
 */
val fallbackStickerResId = com.example.dinoroar.R.drawable.sticker_fallback_logo

/**
 * 从日记内容字符串解析贴纸列表（[sticker:id:x,y] 格式）。
 */
fun parseStickerList(rawContent: String): List<StickerInfo> {
    val pattern = java.util.regex.Pattern.compile("\\[sticker:([^:]+):([0-9.-]+),([0-9.-]+)\\]")
    val matcher = pattern.matcher(rawContent)
    val list = mutableListOf<StickerInfo>()
    while (matcher.find()) {
        val dinoId = matcher.group(1) ?: ""
        val x = matcher.group(2)?.toFloatOrNull() ?: 50f
        val y = matcher.group(3)?.toFloatOrNull() ?: 50f
        list.add(StickerInfo(dinoId, x, y))
    }
    return list
}

/**
 * 移除内容字符串中所有贴纸标签，返回纯正文。
 */
fun stripStickerTags(rawContent: String): String =
    rawContent.replace(Regex("\\[sticker:[^\\]]+\\]"), "").trim()

/**
 * 手账贴纸只读展示 Canvas——用于日记详情页，贴纸不可拖动/删除，仅按保存位置展示。
 *
 * @param stickers       已解析好的贴纸列表
 * @param stickersConfig 从服务器拉取的贴纸配置（含图片 URL）
 * @param serverBaseUrl  服务器基础 URL，用于拼接静态图片路径
 * @param cardBg         背景卡片色
 * @param neonBlue       边框强调色
 */
@Composable
fun StickerViewerCanvas(
    stickers: List<StickerInfo>,
    stickersConfig: List<com.example.dinoroar.network.StickerConfigDto>,
    serverBaseUrl: String,
    cardBg: Color,
    neonBlue: Color,
    modifier: Modifier = Modifier
) {
    if (stickers.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg.copy(alpha = 0.3f))
            .border(1.dp, neonBlue.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
    ) {
        stickers.forEach { sticker ->
            Box(
                modifier = Modifier
                    .offset(sticker.x.dp, sticker.y.dp)
                    .size(56.dp)
            ) {
                val finalStickerId = sticker.dinoId.trim()
                val configObj = stickersConfig.find { it.id.toString() == finalStickerId }
                val imgUrl = configObj?.image_url
                if (imgUrl != null) {
                    val localRes = getStickerLocalResource(imgUrl)
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
                            .error(fallbackStickerResId)
                            .build(),
                        contentDescription = finalStickerId,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = fallbackStickerResId),
                        contentDescription = finalStickerId,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
