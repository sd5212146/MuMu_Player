package com.example.yyplayer.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yyplayer.data.model.FolderGroup
import com.example.yyplayer.data.model.Song

@Composable
fun FolderList(
    folderGroups: List<FolderGroup>,
    currentSongId: Long?,
    onSongClick: (Song, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 记录每个文件夹的展开/收起状态
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(modifier = modifier) {
        items(folderGroups, key = { it.folderPath }) { group ->
            val isExpanded = expandedFolders[group.folderPath] ?: true // 默认展开

            FolderItem(
                folderGroup = group,
                isExpanded = isExpanded,
                currentSongId = currentSongId,
                onToggle = {
                    expandedFolders[group.folderPath] = !isExpanded
                },
                onSongClick = { song ->
                    // 用 id 搜索，避免 data class 引用不一致
                    val globalIndex = folderGroups
                        .flatMap { it.songs }
                        .indexOfFirst { it.id == song.id }
                    if (globalIndex >= 0) {
                        onSongClick(song, globalIndex)
                    }
                }
            )
        }
    }
}

@Composable
private fun FolderItem(
    folderGroup: FolderGroup,
    isExpanded: Boolean,
    currentSongId: Long?,
    onToggle: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // 文件夹标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderGroup.folderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folderGroup.songs.size} 首歌曲",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isExpanded)
                    Icons.Default.KeyboardArrowDown
                else
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 文件夹下的歌曲列表
        AnimatedVisibility(visible = isExpanded) {
            Column {
                folderGroup.songs.forEach { song ->
                    SongItem(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        onClick = { onSongClick(song) }
                    )
                }
                // 文件夹底部间距
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // 分隔线
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}
