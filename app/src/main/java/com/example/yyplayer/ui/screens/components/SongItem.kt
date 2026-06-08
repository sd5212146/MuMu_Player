package com.example.yyplayer.ui.screens.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.yyplayer.R
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.model.resolveAlbumArtUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    index: Int = 0,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    isPlaying -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    else -> Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排序序号
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(24.dp)
        )

        // 专辑封面（异步解析 URI，避免阻塞主线程）
        val context = LocalContext.current
        val artUri = remember { mutableStateOf<Uri?>(null) }
        LaunchedEffect(song.id, song.albumArtUri) {
            artUri.value = withContext(Dispatchers.IO) {
                song.resolveAlbumArtUri(context)
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (artUri.value != null) {
                AsyncImage(
                    model = artUri.value,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 时长 + 正在播放标记
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPlaying) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "正在播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    currentSongId: Long? = null,
    onSongClick: (Song, Int) -> Unit,
    onSongLongClick: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(songs, key = { it.id }) { song ->
            SongItem(
                song = song,
                isPlaying = song.id == currentSongId,
                onClick = {
                    val index = songs.indexOf(song)
                    if (index >= 0) onSongClick(song, index)
                },
                onLongClick = if (onSongLongClick != null) {
                    { onSongLongClick(song) }
                } else null
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
fun SongInfoDialog(
    song: Song,
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {}
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
                .padding(20.dp)
        ) {
            Column {
                // 标题行
                Text(
                    text = "歌曲属性",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                // ---- 文件信息 ----
                val fileName = File(song.filePath).name
                val folderPath = File(song.filePath).parentFile?.absolutePath ?: ""

                InfoRow(label = "文件名", value = fileName)
                Spacer(Modifier.height(8.dp))
                InfoRow(label = "存储位置", value = folderPath)
                Spacer(Modifier.height(16.dp))

                // ---- Tag 信息 ----
                Text(
                    text = "Tag 信息",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))

                InfoRow(label = "标题", value = song.title.ifEmpty { "未知" })
                Spacer(Modifier.height(6.dp))
                InfoRow(label = "艺术家", value = song.artist.ifEmpty { "未知" })
                Spacer(Modifier.height(6.dp))
                InfoRow(label = "专辑", value = song.album.ifEmpty { "未知" })
                Spacer(Modifier.height(6.dp))
                InfoRow(label = "时长", value = formatDuration(song.duration))
                Spacer(Modifier.height(6.dp))
                InfoRow(label = "大小", value = formatFileSize(song.size))

                Spacer(Modifier.height(20.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 删除按钮
                    TextButton(onClick = { showConfirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    // 关闭按钮
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showConfirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${song.title}」吗？\n此操作不可撤销，文件将被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
