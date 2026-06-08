package com.example.yyplayer.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yyplayer.data.model.LyricsResult
import kotlinx.coroutines.delay

@Composable
fun LyricsDisplay(
    lyrics: LyricsResult,
    currentPosition: Long,
    isFullScreen: Boolean = false,
    onRequestOffsetDialog: (() -> Unit)? = null,
    onSearchLyrics: (() -> Unit)? = null,
    accentColor: Color? = null,
    fontScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (lyrics.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (lyrics.source.isNotEmpty())
                        "正在查询: ${lyrics.source}..."
                    else
                        "正在获取歌词...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFullScreen)
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    if (lyrics.lines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(enabled = lyrics.source.isEmpty()) { onSearchLyrics?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (lyrics.source.isEmpty())
                    "暂无歌词\n点击搜索"
                else
                    "未找到歌词\n来源: ${lyrics.source}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFullScreen)
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val textColor = if (isFullScreen)
        Color.White
    else
        MaterialTheme.colorScheme.onSurface

    // 当前行（不偏移，由外部传入 adjustedPosition 或直接用 currentPosition）
    // 如果没有任何一行时间 <= currentPosition，返回 -1（不高亮任何行，避免异常 LRC 造成显示卡死）
    val currentIndex = lyrics.lines.indexOfLast { it.time <= currentPosition }

    val density = LocalDensity.current
    val itemHeight = 48.dp

    // 全屏模式：将当前行滚动到屏幕居中位置
    // 参考标准做法：固定 item 高度计算偏移行数，animateScrollToItem 不传 offset 参数
    LaunchedEffect(currentIndex) {
        if (currentIndex < 0) return@LaunchedEffect  // 没有匹配的行，无需滚动
        if (isFullScreen) {
            if (currentIndex > 0) {
                delay(50) // 等待布局完成
                val viewStart = listState.layoutInfo.viewportStartOffset
                val viewEnd = listState.layoutInfo.viewportEndOffset
                val vh = viewEnd - viewStart
                if (vh > 0) {
                    val itemHeightPx = with(density) { itemHeight.toPx() }
                    val linesInHalfViewport = ((vh / itemHeightPx).toInt() / 2).coerceAtLeast(1)
                    val targetIndex = (currentIndex - linesInHalfViewport).coerceAtLeast(0)
                    listState.animateScrollToItem(targetIndex)
                }
            }
        } else {
            if (currentIndex > 0) {
                listState.animateScrollToItem(currentIndex)
            }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isCurrent = index == currentIndex
                Text(
                    text = line.text,
                    fontSize = (if (isCurrent) {
                        if (isFullScreen) 18.sp else 14.sp
                    } else {
                        if (isFullScreen) 12.sp else 10.sp
                    }) * fontScale,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Light,
                    color = when {
                        isCurrent && isFullScreen && accentColor != null -> accentColor
                        isCurrent && isFullScreen -> Color.White
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isFullScreen -> Color.White.copy(alpha = 0.75f)
                        else -> textColor.copy(alpha = 0.55f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = itemHeight)
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        // 歌词偏移调节按钮（右上角，点击弹出设置弹窗）
        if (onRequestOffsetDialog != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 8.dp, top = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = onRequestOffsetDialog,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isFullScreen) Color.Black.copy(alpha = 0.30f)
                            else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "歌词延迟",
                        tint = if (isFullScreen) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
