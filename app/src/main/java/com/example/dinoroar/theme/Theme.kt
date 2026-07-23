package com.example.dinoroar.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun DinoRoarTheme(
    themeId: Int = 8,
    content: @Composable () -> Unit,
) {
    val appColors = ThemeList.find { it.id == themeId } ?: DefaultThemeColors

    val colorScheme = if (appColors.isDark) {
        darkColorScheme(
            primary = appColors.neonBlue,
            background = appColors.darkBg,
            surface = appColors.cardBg,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = appColors.neonBlue,
            background = appColors.darkBg,
            surface = appColors.cardBg,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
