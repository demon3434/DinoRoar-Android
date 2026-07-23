package com.example.dinoroar.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppThemeColors(
    val id: Int,
    val name: String,
    val isDark: Boolean,
    val darkBg: Color,          // 页面背景底色
    val cardBg: Color,          // 卡片背景色
    val textPrimary: Color,     // 主文字颜色
    val textSecondary: Color,   // 副文字颜色
    val neonBlue: Color,        // 高亮青色
    val neonGreen: Color,       // 成功/高亮绿色
    val neonRed: Color,         // 警示/高亮红色
    val neonAmber: Color        // 强调/高亮橙色
)

val ThemeList = listOf(
    // 12套亮色 (id 0..7, 10..13)
    AppThemeColors(
        id = 0,
        name = "马卡龙粉 (亮)",
        isDark = false,
        darkBg = Color(0xFFFFF0F5),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF4A2E35),
        textSecondary = Color(0xFF8B5F65),
        neonBlue = Color(0xFFFFA07A),
        neonGreen = Color(0xFF87CEFA),
        neonRed = Color(0xFFFF69B4),
        neonAmber = Color(0xFFFFD700)
    ),
    AppThemeColors(
        id = 1,
        name = "草莓奶昔 (亮)",
        isDark = false,
        darkBg = Color(0xFFFFECEF),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF6B454A),
        textSecondary = Color(0xFF9E7075),
        neonBlue = Color(0xFFFF7597),
        neonGreen = Color(0xFFFFB7B2),
        neonRed = Color(0xFFFF4D6D),
        neonAmber = Color(0xFFFF9E00)
    ),
    AppThemeColors(
        id = 2,
        name = "清新抹茶 (亮)",
        isDark = false,
        darkBg = Color(0xFFF1F8E9),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF2E401A),
        textSecondary = Color(0xFF556B2F),
        neonBlue = Color(0xFF81C784),
        neonGreen = Color(0xFF388E3C),
        neonRed = Color(0xFFE57373),
        neonAmber = Color(0xFFFBC02D)
    ),
    AppThemeColors(
        id = 3,
        name = "薄荷苏打 (亮)",
        isDark = false,
        darkBg = Color(0xFFE0F2F1),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF004D40),
        textSecondary = Color(0xFF00796B),
        neonBlue = Color(0xFF4DB6AC),
        neonGreen = Color(0xFF009688),
        neonRed = Color(0xFFFF8A80),
        neonAmber = Color(0xFFFFD54F)
    ),
    AppThemeColors(
        id = 4,
        name = "元气柠檬 (亮)",
        isDark = false,
        darkBg = Color(0xFFFFFDE7),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF4E342E),
        textSecondary = Color(0xFF8D6E63),
        neonBlue = Color(0xFFFFF176),
        neonGreen = Color(0xFF9CCC65),
        neonRed = Color(0xFFFF8A65),
        neonAmber = Color(0xFFFFA726)
    ),
    AppThemeColors(
        id = 5,
        name = "紫罗兰奶盖 (亮)",
        isDark = false,
        darkBg = Color(0xFFF3E5F5),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF4A148C),
        textSecondary = Color(0xFF7B1FA2),
        neonBlue = Color(0xFFBA68C8),
        neonGreen = Color(0xFFAB47BC),
        neonRed = Color(0xFFEF9A9A),
        neonAmber = Color(0xFFFFB74D)
    ),
    AppThemeColors(
        id = 6,
        name = "蔚蓝天空 (亮)",
        isDark = false,
        darkBg = Color(0xFFE3F2FD),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF0D47A1),
        textSecondary = Color(0xFF1976D2),
        neonBlue = Color(0xFF64B5F6),
        neonGreen = Color(0xFF29B6F6),
        neonRed = Color(0xFFFF8A80),
        neonAmber = Color(0xFFFFD54F)
    ),
    AppThemeColors(
        id = 7,
        name = "奶油橘子 (亮)",
        isDark = false,
        darkBg = Color(0xFFFFF3E0),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFE65100),
        textSecondary = Color(0xFFF57C00),
        neonBlue = Color(0xFFFFB74D),
        neonGreen = Color(0xFFFFCC80),
        neonRed = Color(0xFFFF8A80),
        neonAmber = Color(0xFFFFB300)
    ),
    AppThemeColors(
        id = 10,
        name = "樱花风铃 (亮)",
        isDark = false,
        darkBg = Color(0xFFFCE4EC),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF880E4F),
        textSecondary = Color(0xFFAD1457),
        neonBlue = Color(0xFFF48FB1),
        neonGreen = Color(0xFFC5E1A5),
        neonRed = Color(0xFFEC407A),
        neonAmber = Color(0xFFFFB74D)
    ),
    AppThemeColors(
        id = 11,
        name = "暖阳燕麦 (亮)",
        isDark = false,
        darkBg = Color(0xFFFAF0E6),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF3E2723),
        textSecondary = Color(0xFF6D4C41),
        neonBlue = Color(0xFFD7CCC8),
        neonGreen = Color(0xFFAED581),
        neonRed = Color(0xFFFF8A65),
        neonAmber = Color(0xFFFFA726)
    ),
    AppThemeColors(
        id = 12,
        name = "薰衣草晚霞 (亮)",
        isDark = false,
        darkBg = Color(0xFFEDE7F6),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF311B92),
        textSecondary = Color(0xFF512DA8),
        neonBlue = Color(0xFF9FA8DA),
        neonGreen = Color(0xFF80CBC4),
        neonRed = Color(0xFFEF5350),
        neonAmber = Color(0xFFFFD54F)
    ),
    AppThemeColors(
        id = 13,
        name = "海盐青蓝 (亮)",
        isDark = false,
        darkBg = Color(0xFFE0F7FA),
        cardBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF006064),
        textSecondary = Color(0xFF00838F),
        neonBlue = Color(0xFF80DEEA),
        neonGreen = Color(0xFF4DD0E1),
        neonRed = Color(0xFFFF7043),
        neonAmber = Color(0xFFFFCA28)
    ),
    // 6套暗色 (id 8..9, 14..17)
    AppThemeColors(
        id = 8,
        name = "星空夜幕 (暗)",
        isDark = true,
        darkBg = Color(0xFF15151A),
        cardBg = Color(0xFF22222E),
        textPrimary = Color(0xFFE0E0E6),
        textSecondary = Color(0xFFB0B0BB),
        neonBlue = Color(0xFF00E5FF),
        neonGreen = Color(0xFF00FF66),
        neonRed = Color(0xFFFF1744),
        neonAmber = Color(0xFFFFB300)
    ),
    AppThemeColors(
        id = 9,
        name = "熔岩深邃 (暗)",
        isDark = true,
        darkBg = Color(0xFF1A1212),
        cardBg = Color(0xFF2B1C1C),
        textPrimary = Color(0xFFECE0E0),
        textSecondary = Color(0xFFBCAAA4),
        neonBlue = Color(0xFFFF7043),
        neonGreen = Color(0xFF81C784),
        neonRed = Color(0xFFFF5252),
        neonAmber = Color(0xFFFFB74D)
    ),
    AppThemeColors(
        id = 14,
        name = "赛博霓虹 (暗)",
        isDark = true,
        darkBg = Color(0xFF0D0B18),
        cardBg = Color(0xFF1B152B),
        textPrimary = Color(0xFFE1D9FF),
        textSecondary = Color(0xFFA594E0),
        neonBlue = Color(0xFF00F0FF),
        neonGreen = Color(0xFF39FF14),
        neonRed = Color(0xFFFF0055),
        neonAmber = Color(0xFFFF007F)
    ),
    AppThemeColors(
        id = 15,
        name = "翡翠幽谷 (暗)",
        isDark = true,
        darkBg = Color(0xFF0A1813),
        cardBg = Color(0xFF142921),
        textPrimary = Color(0xFFD0EBDD),
        textSecondary = Color(0xFF84B59D),
        neonBlue = Color(0xFF26A69A),
        neonGreen = Color(0xFF00E676),
        neonRed = Color(0xFFFF5252),
        neonAmber = Color(0xFFFFD54F)
    ),
    AppThemeColors(
        id = 16,
        name = "深海极光 (暗)",
        isDark = true,
        darkBg = Color(0xFF0A121D),
        cardBg = Color(0xFF142132),
        textPrimary = Color(0xFFD6E4F0),
        textSecondary = Color(0xFF8BA9C4),
        neonBlue = Color(0xFF00B0FF),
        neonGreen = Color(0xFF00E676),
        neonRed = Color(0xFFFF5252),
        neonAmber = Color(0xFFFFAB40)
    ),
    AppThemeColors(
        id = 17,
        name = "暗夜流金 (暗)",
        isDark = true,
        darkBg = Color(0xFF191612),
        cardBg = Color(0xFF28231D),
        textPrimary = Color(0xFFF5E6CC),
        textSecondary = Color(0xFFC7B299),
        neonBlue = Color(0xFF4FC3F7),
        neonGreen = Color(0xFF81C784),
        neonRed = Color(0xFFE57373),
        neonAmber = Color(0xFFFFD700)
    )
)

val DefaultThemeColors = ThemeList.first { it.id == 8 } // 默认星空夜幕暗色为最酷基准

val LocalAppColors = staticCompositionLocalOf { DefaultThemeColors }
