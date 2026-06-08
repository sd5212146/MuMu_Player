package com.example.yyplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yyplayer.data.model.FolderGroup
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.player.GamepadHelper
import com.example.yyplayer.ui.screens.components.SongItem
import com.example.yyplayer.ui.screens.components.SongList
import com.example.yyplayer.ui.screens.components.SongInfoDialog
import com.example.yyplayer.ui.viewmodel.LibraryViewModel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    currentSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (Song, List<Song>?) -> Unit,
    onBackToNowPlaying: () -> Unit = {},
    onSortChanged: (sortedSongs: List<Song>, currentIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val folderGroups by libraryViewModel.folderGroups.collectAsState()
    val isScanning by libraryViewModel.isScanning.collectAsState()
    val lastOpenedFolderPath by libraryViewModel.lastOpenedFolderPath.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<FolderGroup?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var folderToHide by remember { mutableStateOf<FolderGroup?>(null) }
    var songToShowInfo by remember { mutableStateOf<Song?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var gamepadIndex by remember { mutableIntStateOf(0) }
    var isGamepadActive by remember { mutableStateOf(false) }

    // 排序持久化存储
    val sortPrefs = remember { context.getSharedPreferences("library_sort", 0) }
    var sortField by remember { mutableStateOf(sortPrefs.getString("sort_field", "title") ?: "title") }
    var sortAscending by remember { mutableStateOf(sortPrefs.getBoolean("sort_asc", true)) }
    LaunchedEffect(sortField, sortAscending) {
        sortPrefs.edit().putString("sort_field", sortField).putBoolean("sort_asc", sortAscending).apply()
    }

    // 按当前排序方式排列文件夹歌曲（用于顶栏序号和列表展示）
    val folderSortedSongs = remember(selectedFolder, sortField, sortAscending) {
        val folder = selectedFolder
        if (folder != null) {
            val folderSongs = folder.songs
            val comparator: Comparator<Song> = when (sortField) {
                "artist" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
                "filename" -> compareBy(String.CASE_INSENSITIVE_ORDER) { java.io.File(it.filePath).name }
                else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }
            if (sortAscending) folderSongs.sortedWith(comparator)
            else folderSongs.sortedWith(comparator.reversed())
        } else {
            emptyList()
        }
    }

    // 计算当前歌曲在排序后列表中的位置，LazyColumn 直接用初始滚动位置，避免 scrollToItem 触发中间项创建
    val initialSongIndex = remember(selectedFolder?.folderPath, folderSortedSongs, currentSongId) {
        if (selectedFolder != null && currentSongId != null && folderSortedSongs.isNotEmpty()) {
            val idx = folderSortedSongs.indexOfFirst { it.id == currentSongId }
            if (idx >= 0) idx else 0
        } else 0
    }
    val folderListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialSongIndex
    )

    // 选中文件夹变化时滚动到当前歌曲位置（解决先创建 state 后自动导航导致的 initialIndex 不准确问题）
    LaunchedEffect(selectedFolder?.folderPath, currentSongId, folderSortedSongs) {
        if (selectedFolder != null && currentSongId != null && folderSortedSongs.isNotEmpty()) {
            val idx = folderSortedSongs.indexOfFirst { it.id == currentSongId }
            if (idx >= 0 && folderListState.firstVisibleItemIndex != idx) {
                folderListState.scrollToItem(idx)
            }
        }
    }

    // 排序变化时同步更新 ExoPlayer 的歌单顺序（仅当用户主动切换排序时触发，不因文件夹导航/歌曲切换触发，避免启动时冗余重排 100+ 首歌阻塞主线程）
    LaunchedEffect(sortField, sortAscending) {
        if (selectedFolder != null && currentSongId != null && folderSortedSongs.isNotEmpty()) {
            val idx = folderSortedSongs.indexOfFirst { it.id == currentSongId }
            if (idx >= 0) {
                onSortChanged(folderSortedSongs, idx)
            }
        }
    }

    // 当 folderGroups 或当前歌曲变化时：恢复上次打开的文件夹或自动定位到歌曲目录
    val autoNavTime = remember { System.currentTimeMillis() }
    LaunchedEffect(folderGroups, currentSongId) {
        android.util.Log.i("Startup_Timing", "auto-navigate检查: folderGroups=" + folderGroups.size + ", songId=" + currentSongId + ", 距composition=" + (System.currentTimeMillis() - autoNavTime) + "ms")
        // 优先恢复用户上次打开的文件夹（从红心收藏或其他目录进入播放，返回时回到同一位置）
        if (selectedFolder == null && lastOpenedFolderPath != null) {
            val savedFolder = folderGroups.find { it.folderPath == lastOpenedFolderPath }
            if (savedFolder != null) {
                selectedFolder = savedFolder
            }
        }
        // 没有保存记录且当前有歌曲，自动定位到歌曲所在文件夹
        if (selectedFolder == null && currentSongId != null) {
            val songFolder = folderGroups.firstOrNull { group ->
                group.songs.any { it.id == currentSongId }
            }
            if (songFolder != null) {
                selectedFolder = songFolder
            }
        }
        // 同步已有 selection（文件夹内容更新后保持选中状态）
        selectedFolder?.let { old ->
            selectedFolder = folderGroups.find { it.folderPath == old.folderPath }
        }
    }

    // 歌曲列表首次渲染后已自动停在当前歌曲位置（通过 initialSongIndex），无需额外滚动

    // 搜索时用 filteredSongs，否则看 selectedFolder

    // 系统返回手势：在文件夹内时返回上级目录，在家目录时跳转播放页
    BackHandler(enabled = selectedFolder != null) {
        selectedFolder = null
        libraryViewModel.setLastOpenedFolder(null)
        showSearch = false
        gamepadIndex = 0
    }
    BackHandler(enabled = selectedFolder == null) {
        onBackToNowPlaying()
    }

    Scaffold(
        topBar = {
            if (selectedFolder != null) {
                // 文件夹内部：显示文件夹名 + 返回按钮 + 搜索
                TopAppBar(
                    title = { Text(selectedFolder!!.folderName) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFolder = null; libraryViewModel.setLastOpenedFolder(null); showSearch = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        val total = if (selectedFolder != null) folderSortedSongs.size else 0
                        val currentIdx = if (currentSongId != null && total > 0) {
                            val idx = folderSortedSongs.indexOfFirst { it.id == currentSongId }
                            if (idx >= 0) idx + 1 else 1
                        } else 1
                        Text(
                            text = "$currentIdx/$total",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        }
                        // 排序按钮
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "排序",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (sortAscending) 0f else 180f)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                listOf(
                                    "title" to "歌曲名",
                                    "artist" to "歌手",
                                    "filename" to "文件名"
                                ).forEach { (field, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(label, modifier = Modifier.weight(1f))
                                                if (field == sortField) {
                                                    Text(
                                                        text = if (sortAscending) " ↑" else " ↓",
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (field == sortField) {
                                                sortAscending = !sortAscending
                                            } else {
                                                sortField = field
                                                sortAscending = true
                                            }
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            } else {
                TopAppBar(
                    title = { Text("音乐库") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        }
                        IconButton(onClick = { libraryViewModel.refreshMusic() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        },
        modifier = modifier
    ) { padding ->
        val keyHandler: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
            if (event.type == KeyEventType.KeyDown) {
                val nativeKeyCode = event.nativeKeyEvent?.keyCode
                when {
                    // A = 确认（进入文件夹 / 播放歌曲）
                    nativeKeyCode == android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                        isGamepadActive = true
                        handleConfirm(selectedFolder, folderGroups, gamepadIndex, onSelectFolder = { selectedFolder = it; gamepadIndex = 0 }, onPlaySong = onSongClick)
                        true
                    }
                    // B = 返回（从文件夹退出）
                    nativeKeyCode == android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                        if (selectedFolder != null) {
                            selectedFolder = null; libraryViewModel.setLastOpenedFolder(null); showSearch = false; gamepadIndex = 0
                        }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        isGamepadActive = true
                        gamepadIndex = (gamepadIndex - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        isGamepadActive = true
                        gamepadIndex = (gamepadIndex + 1).coerceAtMost(getMaxIndex(selectedFolder, folderGroups))
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        isGamepadActive = true
                        handleConfirm(selectedFolder, folderGroups, gamepadIndex, onSelectFolder = { selectedFolder = it; gamepadIndex = 0 }, onPlaySong = onSongClick)
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        if (selectedFolder != null) {
                            selectedFolder = null; libraryViewModel.setLastOpenedFolder(null); showSearch = false; gamepadIndex = 0
                        }
                        true
                    }
                    else -> false
                }
            } else false
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onPreviewKeyEvent(keyHandler)
        ) {
            // ===== 文件夹进入/退出动画 =====
            AnimatedContent(
                targetState = selectedFolder?.folderName ?: "__root__",
                transitionSpec = {
                    val entering = initialState == "__root__" && targetState != "__root__"
                    if (entering) {
                        // 进入文件夹：纯淡入淡出
                        fadeIn(animationSpec = tween(150)).togetherWith(fadeOut(animationSpec = tween(100)))
                    } else {
                        // 退出文件夹：纯淡入淡出
                        fadeIn(animationSpec = tween(150)).togetherWith(fadeOut(animationSpec = tween(100)))
                    }
                },
                label = "folder_transition"
            ) { _ ->
                if (selectedFolder == null) {
                    Column(Modifier.fillMaxSize()) {
                        if (showSearch) {
                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    libraryViewModel.search(it)
                                },
                                placeholder = { Text("搜索歌曲") },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            libraryViewModel.search("")
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "清除")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true
                            )
                        }

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (isScanning) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "正在扫描音乐...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (searchQuery.isNotBlank()) {
                                val searchResults by libraryViewModel.filteredSongs.collectAsState()
                                if (searchResults.isEmpty()) {
                                    EmptyLibraryHint()
                                } else {
                                    SongList(
                                        songs = searchResults,
                                        currentSongId = currentSongId,
                                        onSongClick = { song, _ ->
                                            onSongClick(song, null)
                                        },
                                        onSongLongClick = { song ->
                                            songToShowInfo = song
                                        }
                                    )
                                }
                            } else if (folderGroups.isEmpty()) {
                                EmptyLibraryHint()
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(folderGroups, key = { _, it -> it.folderPath }) { index, group ->
                                        FolderRow(
                                            folderGroup = group,
                                            currentSongId = currentSongId,
                                            isSelected = isGamepadActive && index == gamepadIndex,
                                            onClick = { selectedFolder = group; libraryViewModel.setLastOpenedFolder(group.folderPath); gamepadIndex = 0 },
                                            onLongClick = {
                                                folderToHide = group
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // 文件夹内搜索栏
                        if (showSearch) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("在${selectedFolder!!.folderName}中搜索") },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "清除")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true
                            )
                        }

                        // 过滤并排序后的歌曲（基于 folderSortedSongs 做搜索过滤）
                        val displayedSongs = remember(folderSortedSongs, searchQuery) {
                            if (searchQuery.isNotBlank()) {
                                folderSortedSongs.filter {
                                    it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.artist.contains(searchQuery, ignoreCase = true)
                                }
                            } else {
                                folderSortedSongs
                            }
                        }

                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (displayedSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "未找到匹配的歌曲"
                                               else if (selectedFolder?.folderPath == "__nas__") "请先扫描 NAS 文件\n\n在设置 > NAS 网络音乐中扫描"
                                               else "该文件夹没有歌曲",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), state = folderListState) {
                                    itemsIndexed(displayedSongs, key = { _, it -> it.id }) { index, song ->
                                        SongItem(
                                            song = song,
                                            index = index,
                                            isPlaying = song.id == currentSongId,
                                            isSelected = isGamepadActive && index == gamepadIndex,
                                            onClick = {
                                                onSongClick(song, displayedSongs)
                                            },
                                            onLongClick = {
                                                songToShowInfo = song
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 长按隐藏确认弹窗
    if (folderToHide != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { folderToHide = null },
            title = { Text("隐藏文件夹", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Text("确定隐藏「${folderToHide!!.folderName}」吗？\n\n隐藏后该目录将不再显示，重新扫描歌曲后可恢复。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val name = folderToHide!!.folderName
                    libraryViewModel.toggleHiddenFolder(folderToHide!!.folderPath)
                    folderToHide = null
                    // 隐藏后清空跳转目标，保持在音乐库主页
                    libraryViewModel.setLastOpenedFolder(null)
                    android.widget.Toast.makeText(context, "已隐藏: $name", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("隐藏", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { folderToHide = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 长按歌曲属性弹窗
    if (songToShowInfo != null) {
        SongInfoDialog(
            song = songToShowInfo!!,
            onDismiss = { songToShowInfo = null },
            onDelete = {
                val song = songToShowInfo ?: return@SongInfoDialog
                try {
                    val file = java.io.File(song.filePath)
                    if (file.exists()) file.delete()
                    val parent = file.parentFile
                    if (parent != null) {
                        val name = file.nameWithoutExtension
                        java.io.File(parent, "$name.jpg").delete()
                        java.io.File(parent, "$name.lrc").delete()
                    }
                } catch (_: Exception) {}
                libraryViewModel.getRepository().removeSong(song)
                android.widget.Toast.makeText(context, "已删除: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                songToShowInfo = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folderGroup: FolderGroup,
    currentSongId: Long? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isFavorites = folderGroup.folderPath == "__favorites__"
    val isNas = folderGroup.folderPath == "__nas__"
    val hasCurrentSong = currentSongId != null && folderGroup.songs.any { it.id == currentSongId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isFavorites || isNas) {{}} else onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件夹图标（红心收藏=红心，NAS网络=云，普通=文件夹）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isFavorites -> MaterialTheme.colorScheme.errorContainer
                            isNas -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isFavorites -> Icons.Default.Favorite
                        isNas -> Icons.Default.Cloud
                        else -> Icons.Default.Folder
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        isFavorites -> Color.Red
                        isNas -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文件夹信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderGroup.folderName,
                    style = MaterialTheme.typography.bodyMedium,
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

            // 右箭头/正在播放标记
            if (hasCurrentSong) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "正在播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "进入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
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

@Composable
private fun EmptyLibraryHint() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无音乐文件",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "将MP3等音频文件放入手机存储中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ====== 手柄导航辅助函数 ======

/** 获取当前列表最大索引 */
private fun getMaxIndex(selectedFolder: FolderGroup?, folderGroups: List<FolderGroup>): Int {
    if (selectedFolder != null) {
        return (selectedFolder.songs.size - 1).coerceAtLeast(0)
    }
    return (folderGroups.size - 1).coerceAtLeast(0)
}

/** 执行确认操作（方向键→） */
private fun handleConfirm(
    selectedFolder: FolderGroup?,
    folderGroups: List<FolderGroup>,
    index: Int,
    onSelectFolder: (FolderGroup) -> Unit,
    onPlaySong: (Song, List<Song>?) -> Unit
) {
    if (selectedFolder != null) {
        val songs = selectedFolder.songs
        if (index in songs.indices) {
            onPlaySong(songs[index], songs)
        }
    } else {
        if (index in folderGroups.indices) {
            onSelectFolder(folderGroups[index])
        }
    }
}


