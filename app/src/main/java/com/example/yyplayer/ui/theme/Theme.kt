package com.example.yyplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.yyplayer.data.model.PlayerTheme

// Walkman主题的CompositionLocal
val LocalWalkmanTheme = staticCompositionLocalOf {
    PlayerTheme.PRESETS[0]
}

/** 根据 Walkman 主题创建统一的 MaterialTheme 配色方案 */
internal fun createWalkmanColorScheme(walkmanTheme: PlayerTheme) = lightColorScheme(
    primary = walkmanTheme.primaryColor,
    onPrimary = Color.White,
    primaryContainer = walkmanTheme.buttonColor,
    onPrimaryContainer = walkmanTheme.primaryColor,
    secondary = walkmanTheme.accentColor,
    onSecondary = Color.White,
    secondaryContainer = walkmanTheme.secondaryColor,
    onSecondaryContainer = walkmanTheme.primaryColor,
    tertiary = walkmanTheme.accentColor,
    onTertiary = Color.White,
    surface = walkmanTheme.backgroundColor,
    onSurface = walkmanTheme.textColor,
    surfaceVariant = walkmanTheme.buttonColor,
    onSurfaceVariant = walkmanTheme.textColor.copy(alpha = 0.6f),
    background = walkmanTheme.backgroundColor,
    onBackground = walkmanTheme.textColor,
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = walkmanTheme.textColor.copy(alpha = 0.2f),
    surfaceContainerHigh = walkmanTheme.backgroundColor,
    surfaceContainer = walkmanTheme.backgroundColor,
    surfaceContainerLow = walkmanTheme.backgroundColor,
    surfaceBright = walkmanTheme.backgroundColor,
    surfaceDim = walkmanTheme.backgroundColor
)

@Composable
fun YYplayerTheme(
    walkmanTheme: PlayerTheme = PlayerTheme.PRESETS[0],
    content: @Composable () -> Unit
) {
    // 始终使用 walkmanTheme 的配色，覆盖整个 MaterialTheme
    val colorScheme = createWalkmanColorScheme(walkmanTheme)

    CompositionLocalProvider(LocalWalkmanTheme provides walkmanTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}