package com.example.yyplayer.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yyplayer.data.model.PlayerTheme
import com.example.yyplayer.ui.theme.LocalWalkmanTheme
import kotlinx.coroutines.delay

/** 开屏加载动画 */
@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // 音符图标呼吸脉动动画（scale 0.9 ↔ 1.1 循环）
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 轻微旋转摆动（-5° ↔ 5°）
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotate"
    )

    // 扫描文字状态（加载中...、加载中..、加载中.）
    val dotsCount by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dots"
    )
    val dotsText = remember(dotsCount) {
        val count = dotsCount.toInt().coerceIn(1, 3)
        ".".repeat(count)
    }

    val walkmanTheme = LocalWalkmanTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                if (walkmanTheme.isWalkmanTheme) {
                    val size = this.size
                    walkmanTheme.metallicHighlightBrush(size)?.let { brush ->
                        drawRect(brush = brush, size = size)
                    }
                    walkmanTheme.metallicSheenBrush()?.let { brush ->
                        drawRect(brush = brush, size = size)
                    }
                    walkmanTheme.metallicVignetteBrush(size)?.let { brush ->
                        drawRect(brush = brush, size = size)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 动画音符图标
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 应用名称
            Text(
                text = "MuMu Player",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 加载指示器
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 状态文字
            Text(
                text = "正在加载页面${dotsText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
