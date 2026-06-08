package com.example.yyplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.yyplayer.R
import com.example.yyplayer.data.lyrics.AlbumArtCache
import com.example.yyplayer.data.lyrics.AlbumArtFetcher
import com.example.yyplayer.data.lyrics.LyricsCache
import com.example.yyplayer.data.lyrics.LyricsFetcher
import com.example.yyplayer.data.lyrics.AlbumArtWriter
import com.example.yyplayer.data.lyrics.SourceOrderManager
import com.example.yyplayer.data.model.LyricsResult
import com.example.yyplayer.data.model.PlayMode
import com.example.yyplayer.data.model.PlayerTheme
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.model.resolveAlbumArtUri
import com.example.yyplayer.data.repository.LyricsOffsetManager
import com.example.yyplayer.data.repository.FavoritesManager
import com.example.yyplayer.data.repository.LyricsCoverSettings
import com.example.yyplayer.player.PlayerController
import com.example.yyplayer.player.MusicService
import com.example.yyplayer.ui.screens.components.LyricsDisplay
import com.example.yyplayer.ui.screens.components.formatDuration

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NowPlayingScreen(
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playMode: PlayMode,
    playerTheme: PlayerTheme,
    isFullScreen: Boolean,
    lyricsFontScale: Float = 1f,
    playbackSpeed: Float = 1f,
    isPortraitMode: Boolean = false,
    currentIndex: Int = 0,
    playlistSize: Int = 0,
    coverSizePercent: Float = 0.5f,
    onSpeedChange: (Float) -> Unit = {},
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayMode: () -> Unit,
    onFullScreenChange: (Boolean) -> Unit,
    onBackToLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lyricsFetcher = remember { LyricsFetcher() }
    val albumArtFetcher = remember { AlbumArtFetcher() }
    val albumArtWriter = remember { AlbumArtWriter(context) }
    val lyricsCache = remember { LyricsCache(context) }
    val albumArtCache = remember { AlbumArtCache(context) }
    val sourceOrderManager = remember { SourceOrderManager(context) }
    val lyricsOffsetManager = remember { LyricsOffsetManager(context) }
    val lyricsCoverSettings = remember { LyricsCoverSettings(context) }
    val scope = rememberCoroutineScope()
    var showLyricsOverlay by remember { mutableStateOf(false) }
    var lyricsResult by remember { mutableStateOf(LyricsResult(emptyList(), "")) }
    var networkAlbumArtUrl by remember { mutableStateOf<String?>(null) }
    var isAlbumArtLoading by remember { mutableStateOf(false) }
    var searchTrigger by remember { mutableIntStateOf(0) }
    var lyricsTimeOffset by remember { mutableLongStateOf(0L) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var lastSongId by remember { mutableLongStateOf(-1L) }
    val favoritesManager = remember { FavoritesManager(context) }
    var favoriteIds by remember { mutableStateOf(favoritesManager.getIds()) }
    var showLyricsSourcePicker by remember { mutableStateOf(false) }
    var refetchSource by remember { mutableStateOf<String?>(null) }
    val stateManager = remember { com.example.yyplayer.data.repository.PlayerStateManager(context) }
    var showLockButton by remember { mutableStateOf(stateManager.isShowLockButton()) }
    var slideDirection by remember { mutableIntStateOf(0) }

    // 歌词专用的细粒度播放位置追踪（100ms间隔，不影响其他UI）
    val player = remember { PlayerController.getInstance(context) }
    var finePosition by remember { mutableLongStateOf(0L) }
    var seekPending by remember { mutableStateOf(false) }
    LaunchedEffect(song?.id) {  // 只在切歌时重启，seek 时 isPlaying 短暂变 false 不会中断协程
        finePosition = 0L  // 切歌时立即重置，防止上一首进度残留
        seekPending = false
        while (true) {
            if (player.isPlaying) {  // 用 ExoPlayer 实际状态，而非捕获的快照
                val pos = player.currentPosition.coerceAtLeast(0L)
                if (!seekPending) {
                    finePosition = pos
                } else if (pos > 0L && pos >= finePosition - 500L) {
                    // seek 完成后恢复自动追踪（pos 追上目标附近）
                    seekPending = false
                    finePosition = pos
                }
            }
            delay(100)
        }
    }

    // 切歌后将 slideDirection 重置为 0（自动下一曲时默认方向=1，不受上次按钮影响）
    LaunchedEffect(song?.id) {
        slideDirection = 0
    }

    // 监听通知栏可能修改的收藏状态，双向同步
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ids") {
                favoriteIds = favoritesManager.getIds()
            }
        }
        favoritesManager.registerOnChangeListener(listener)
        onDispose { favoritesManager.unregisterOnChangeListener(listener) }
    }

    // 包裹 onSeek：暂停时拖动进度条也能即时更新 finePosition
    val wrappedOnSeek: (Long) -> Unit = { pos ->
        seekPending = true
        finePosition = pos
        onSeek(pos)
    }

    val onNextWithDirection: () -> Unit = {
        android.util.Log.i("AnimDir", "onNextWithDirection: 设置 slideDirection=1")
        slideDirection = 1; onNext()
    }
    val onPreviousWithDirection: () -> Unit = {
        android.util.Log.i("AnimDir", "onPreviousWithDirection: 设置 slideDirection=-1")
        slideDirection = -1; onPrevious()
    }

    val onSearchLyrics: () -> Unit = {
        searchTrigger++
        lyricsResult = LyricsResult(emptyList(), "", isLoading = true)
    }

    // 切换歌曲时恢复偏移量并自动获取歌词和封面
    LaunchedEffect(song?.id, searchTrigger) {
        val logTag = "NowPlaying_Load"
        val tStart = System.currentTimeMillis()

        // 快速跳过检查——如果歌词已加载且非手动搜索
        val skipReason = when {
            song == null -> "song==null"
            lyricsResult.lines.isNotEmpty() && searchTrigger == 0 && song.id == lastSongId -> "歌词已加载，跳过重复加载"
            else -> null
        }
        if (skipReason != null) {
            if (song == null) lyricsResult = LyricsResult(emptyList(), "")
            android.util.Log.i(logTag, "[${song?.id}] LaunchedEffect 跳过: $skipReason, searchTrigger=$searchTrigger")
            return@LaunchedEffect
        }

        // song 保证非null
        val s = song ?: return@LaunchedEffect

        // === 切歌：立即清空旧歌词，防止显示上一首歌的歌词 ===
        if (s.id != lastSongId) {
            lyricsResult = LyricsResult(emptyList(), "", isLoading = true)
        }

        val isFromSearch = searchTrigger > 0
        android.util.Log.i(logTag, "[${s.id}] ─── 开始加载歌词和封面: ${s.title}, isNewSong=${s.id != lastSongId}, searchTrigger=$searchTrigger ───")
        networkAlbumArtUrl = null

        // 判断是否为新歌曲（切歌），手动搜索时绕过缓存
        val isNewSong = s.id != lastSongId
        lastSongId = s.id

        // 恢复该歌曲的歌词偏移??
        val offsetKey = LyricsOffsetManager.getKey(s.title, s.artist)
        lyricsTimeOffset = lyricsOffsetManager.getOffset(offsetKey)

        // 尝试读缓存（切歌或手动搜索均先读缓存??
        val cacheKey = "${s.title}|${s.artist}|${s.filePath}"
        val cached = lyricsCache.get(cacheKey)
        val sourceOrder = sourceOrderManager.getEnabledSources()
        val writeLyricsToFile = lyricsCoverSettings.isWriteLyricsToFile()
        val loadEmbeddedLyrics = lyricsCoverSettings.isLoadEmbeddedLyrics()

        // 标记是否发起了网络请??
        var usedNetworkLyrics = false
        var usedNetworkCover = false

        // ====== 第一步：伴生 .lrc 文件（最高优先级） ======
        val companionLrcFile = if (s.filePath.isNotEmpty()) {
            lyricsFetcher.findCompanionLrcFile(s.filePath, s.title, s.artist)
        } else null

        if (companionLrcFile != null) {
            try {
                val rawLrc = companionLrcFile.readText()
                val lines = lyricsFetcher.parseLrc(rawLrc)
                if (lines.isNotEmpty()) {
                    lyricsResult = LyricsResult(lines, "伴生文件", rawLrc = rawLrc)
                    lyricsCache.put(cacheKey, rawLrc, "伴生文件")
                    android.util.Log.i(logTag, "[${s.id}] 歌词来自伴生文件(最高优先级): ${companionLrcFile.name}")
                }
            } catch (_: Exception) { }
        }

        // ====== 第二步：缓存（仅当伴生 .lrc 未命中时） ======
        if (lyricsResult.lines.isEmpty()) {
            if (cached != null && cached.lines.isNotEmpty()) {
                lyricsResult = cached
                android.util.Log.i(logTag, "[${s.id}] 歌词命中缓存, 来源=${cached.source}")
            } else if (cached != null && !isFromSearch) {
                lyricsResult = cached
                android.util.Log.i(logTag, "[${s.id}] 歌词缓存为空（之前获取失败），跳过网络获取")
            }
        }

        // ====== 第三步：网络获取 ======
        if (lyricsResult.lines.isEmpty() && !(cached != null && !isFromSearch)) {
            lyricsResult = LyricsResult(emptyList(), "", isLoading = true)
            lyricsResult = lyricsFetcher.fetchLyrics(
                title = s.title,
                artist = s.artist,
                filePath = if (loadEmbeddedLyrics) s.filePath else "",
                sourceOrder = sourceOrder,
                onSourceProgress = { sourceName ->
                    if (lyricsResult.source != sourceName) {
                        lyricsResult = LyricsResult(emptyList(), sourceName, isLoading = true)
                    }
                }
            )
            usedNetworkLyrics = lyricsResult.source.isNotEmpty() && lyricsResult.source != "伴生文件"
            // 缓存获取到的歌词（不论有无结果，避免重复请求）
            lyricsCache.put(cacheKey, lyricsResult.rawLrc, lyricsResult.source)

            // 如果启用「歌词写入文件」且有歌词结果，写入伴生 .lrc 文件
            if (writeLyricsToFile && lyricsResult.lines.isNotEmpty() && s.filePath.isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val audioFile = java.io.File(s.filePath)
                        val parent = audioFile.parentFile
                        if (parent != null) {
                            val lrcFile = java.io.File(parent, "${audioFile.nameWithoutExtension}.lrc")
                            lrcFile.writeText(lyricsResult.rawLrc)
                        }
                    } catch (_: Exception) { }
                }
            }
        }
        val lyricsElapsed = System.currentTimeMillis() - tStart
        android.util.Log.i(logTag, "[${s.id}] 歌词加载完成: ${lyricsResult.lines.size}?? 来源=${lyricsResult.source}, 耗时=${lyricsElapsed}ms")

        // 专辑图解析：MediaStore/侧边图片文件(.jpg)
        var hasLocalArt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                s.resolveAlbumArtUri(context) != null
            } catch (_: Throwable) { false }
        }

        if (hasLocalArt) {
            android.util.Log.i(logTag, "[${s.id}] 本地封面已存(MediaStore/侧边.jpg), 跳过网络获取")
        }

        if (!hasLocalArt) {
            android.util.Log.i("AlbumArt", "[${s.id}] 本地封面无效，进入网络搜索")
            isAlbumArtLoading = true
            val artKey = "${s.title}|${s.artist}|${s.filePath}"
            val cachedArt = albumArtCache.get(artKey)
            if (cachedArt != null) {
                networkAlbumArtUrl = cachedArt
                // 缓存命中时也下载写入侧边 .jpg，确保下次 resolveAlbumArtUri 命中
                albumArtWriter.writeToSongFile(s.filePath, cachedArt)
            } else {
                val result = albumArtFetcher.fetchAlbumArtUrl(s.title, s.artist, sourceOrder)
                val url = result?.second
                if (url != null) {
                    networkAlbumArtUrl = url
                    albumArtCache.put(artKey, url)
                    // 始终写入侧边 .jpg 文件
                    albumArtWriter.writeToSongFile(s.filePath, url)
                }
            }
            isAlbumArtLoading = false
        } else {
            isAlbumArtLoading = false
        }
        val totalElapsed = System.currentTimeMillis() - tStart
        android.util.Log.i(logTag, "[${s.id}] ─── 全部加载完成, 耗时=${totalElapsed}ms, hasLocalArt=${hasLocalArt}, networkUrl=${networkAlbumArtUrl != null} ───")
    }

    // 从指定歌词源重新获取歌词
    LaunchedEffect(refetchSource) {
        val source = refetchSource ?: return@LaunchedEffect
        val s = song ?: return@LaunchedEffect
        val logTag = "NowPlaying_Refetch"
        val cacheKey = "${s.title}|${s.artist}|${s.filePath}"

        // 先清空当前的歌词显示和缓存
        lyricsResult = LyricsResult(emptyList(), source, isLoading = true)
        lyricsCache.remove(cacheKey)

        // 删除伴生 .lrc 文件，确保从网络重新获取而非复用本地文件
        if (s.filePath.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val audioFile = java.io.File(s.filePath)
                    val parent = audioFile.parentFile
                    if (parent != null) {
                        val lrcFile = java.io.File(parent, "${audioFile.nameWithoutExtension}.lrc")
                        if (lrcFile.exists()) {
                            lrcFile.delete()
                            android.util.Log.i(logTag, "[${s.id}] 已删除伴生 .lrc: ${lrcFile.name}")
                        }
                    }
                } catch (_: Exception) { }
            }
        }
        android.util.Log.i(logTag, "[${s.id}] 重新获取歌词: ${s.title}, 指定??$source")

        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            lyricsFetcher.fetchLyrics(
                title = s.title,
                artist = s.artist,
                filePath = s.filePath,
                sourceOrder = listOf(source)
            )
        }

        lyricsResult = result
        if (result.rawLrc.isNotEmpty()) {
            lyricsCache.put(cacheKey, result.rawLrc, result.source)
        }
        android.util.Log.i(logTag, "[${s.id}] 重取完成: ${result.lines.size}?? 来源=${result.source}")
        refetchSource = null
    }

    // 将偏移量直接应用到歌词时间戳（最可靠，避免了 timeOffset 传递链路的同步问题）
    val adjustedLyricsResult = remember(lyricsResult, lyricsTimeOffset) {
        if (lyricsTimeOffset != 0L && lyricsResult.lines.isNotEmpty()) {
            lyricsResult.copy(
                lines = lyricsResult.lines.map { it.copy(time = (it.time + lyricsTimeOffset).coerceAtLeast(0L)) }
            )
        } else {
            lyricsResult
        }
    }

   // ===== 全屏/普通模式切换动画：缩放过渡 =====
    AnimatedContent(
        targetState = isFullScreen,
        transitionSpec = {
            if (targetState) {
                // 进入全屏：fadeIn稍快，fadeOut稍慢，平滑过渡
                fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)))
                    .using(SizeTransform(clip = true))
            } else {
                // 退出全屏：fadeIn稍慢，让新画面缓缓显现，避免闪烁
                fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)))
                    .using(SizeTransform(clip = true))
            }
        },
        label = "fullscreen_transition"
    ) { fullScreen ->
        if (fullScreen) {
            BackHandler { onFullScreenChange(false) }
            FullScreenMode(
                song = song,
                showLockButton = showLockButton,
                slideDirection = slideDirection,
                isPlaying = isPlaying,
                currentPosition = finePosition,
                duration = duration,
                playMode = playMode,
                playerTheme = playerTheme,
                lyricsResult = adjustedLyricsResult,
                networkAlbumArtUrl = networkAlbumArtUrl,
                isAlbumArtLoading = isAlbumArtLoading,
                showLyricsOverlay = showLyricsOverlay,
                timeOffset = 0L,
                onRequestOffsetDialog = { showOffsetDialog = true },
                onToggleLyrics = { showLyricsOverlay = !showLyricsOverlay },
                onSearchLyrics = onSearchLyrics,
                onFullScreenChange = onFullScreenChange,
                onPlayPause = onPlayPause,
                onNext = onNextWithDirection,
                onPrevious = onPreviousWithDirection,
                onSeek = wrappedOnSeek,
                onTogglePlayMode = onTogglePlayMode,
                onBackToLibrary = onBackToLibrary,
                favoriteIds = favoriteIds,
                onToggleFavorite = { id ->
                    favoritesManager.toggle(id)
                    favoriteIds = favoritesManager.getIds()
                    try { MusicService.requestRefresh(context) } catch (_: Exception) {}
                },
                lyricsFontScale = lyricsFontScale,
                playbackSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange
            )
        } else if (isPortraitMode) {
            PortraitMode(
                song = song,
                isPlaying = isPlaying,
                currentPosition = finePosition,
                duration = duration,
                playMode = playMode,
                playerTheme = playerTheme,
                lyricsResult = adjustedLyricsResult,
                networkAlbumArtUrl = networkAlbumArtUrl,
                isAlbumArtLoading = isAlbumArtLoading,
                currentIndex = currentIndex,
                playlistSize = playlistSize,
                onRequestOffsetDialog = { showOffsetDialog = true },
                onShowSourcePicker = { showLyricsSourcePicker = true },
                onSearchLyrics = onSearchLyrics,
                onEnterFullScreen = { onFullScreenChange(true) },
                onPlayPause = onPlayPause,
                onNext = onNextWithDirection,
                onPrevious = onPreviousWithDirection,
                onSeek = wrappedOnSeek,
                onTogglePlayMode = onTogglePlayMode,
                favoriteIds = favoriteIds,
                onToggleFavorite = { id ->
                    favoritesManager.toggle(id)
                    favoriteIds = favoritesManager.getIds()
                    try { MusicService.requestRefresh(context) } catch (_: Exception) {}
                },
                lyricsFontScale = lyricsFontScale,
                playbackSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange,
                modifier = modifier
            )
        } else {
            NormalMode(
                song = song,
                isPlaying = isPlaying,
                currentPosition = finePosition,
                duration = duration,
                playMode = playMode,
                playerTheme = playerTheme,
                lyricsResult = adjustedLyricsResult,
                networkAlbumArtUrl = networkAlbumArtUrl,
                isAlbumArtLoading = isAlbumArtLoading,
                slideDirection = slideDirection,
                timeOffset = 0L,
                onRequestOffsetDialog = { showOffsetDialog = true },
                onShowSourcePicker = { showLyricsSourcePicker = true },
                onSearchLyrics = onSearchLyrics,
                onEnterFullScreen = { onFullScreenChange(true) },
                onPlayPause = onPlayPause,
                onNext = onNextWithDirection,
                onPrevious = onPreviousWithDirection,
                onSeek = wrappedOnSeek,
                onTogglePlayMode = onTogglePlayMode,
                favoriteIds = favoriteIds,
                onToggleFavorite = { id ->
                    favoritesManager.toggle(id)
                    favoriteIds = favoritesManager.getIds()
                    try { MusicService.requestRefresh(context) } catch (_: Exception) {}
                },
                lyricsFontScale = lyricsFontScale,
                playbackSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange,
                coverSizePercent = coverSizePercent,
                modifier = modifier
            )
        }
    }

    // 歌词偏移弹窗
    if (showOffsetDialog && song != null) {
        LyricsOffsetDialog(
            song = song,
            lyricsTimeOffset = lyricsTimeOffset,
            lyricsResult = lyricsResult,
            scope = scope,
            showDialog = true,
            lyricsOffsetManager = lyricsOffsetManager,
            onDismiss = { showOffsetDialog = false },
            onTimeOffsetChanged = { lyricsTimeOffset = it },
            onSaveComplete = { searchTrigger++ }
        )
    }

    // 歌词源选择弹窗（半透明，列出所有歌词来源）
    if (showLyricsSourcePicker && song != null) {
        LyricsSourcePickerDialog(
            song = song,
            showDialog = true,
            currentSource = lyricsResult.source,
            sourceOrderManager = sourceOrderManager,
            onSourceSelected = { refetchSource = it },
            onDismiss = { showLyricsSourcePicker = false }
        )
    }
}

// ==================== 竖屏播放模式 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitMode(
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playMode: PlayMode,
    playerTheme: PlayerTheme,
    lyricsResult: LyricsResult,
    networkAlbumArtUrl: String?,
    isAlbumArtLoading: Boolean = false,
    currentIndex: Int = 0,
    playlistSize: Int = 0,
    onRequestOffsetDialog: (() -> Unit)? = null,
    onShowSourcePicker: () -> Unit = {},
    onSearchLyrics: () -> Unit = {},
    onEnterFullScreen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayMode: () -> Unit,
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    lyricsFontScale: Float = 1f,
    playbackSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var currentUri by remember { mutableStateOf<Uri?>(null) }

    // 解析专辑封面 URI
    LaunchedEffect(song?.id, song?.albumArtUri, networkAlbumArtUrl) {
        try {
            val localUri = withContext(Dispatchers.IO) { song?.resolveAlbumArtUri(context) }
            currentUri = networkAlbumArtUrl?.let { Uri.parse(it) } ?: localUri
        } catch (_: Exception) { }
    }

    val thumbInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(playerTheme.backgroundColor)
            .drawBehind {
                // Walkman 主题金属质感层叠
                playerTheme.metallicHighlightBrush(size)?.let { drawRect(brush = it, size = size) }
                playerTheme.metallicSheenBrush()?.let { drawRect(brush = it, size = size) }
                playerTheme.metallicVignetteBrush(size)?.let { drawRect(brush = it, size = size) }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .navigationBarsPadding()
    ) {
        // ---- 顶部：左上角 (N/N)，居中歌名 + 换行歌手，右侧歌词按钮 ----
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "(${currentIndex + 1}/${playlistSize})",
                style = MaterialTheme.typography.labelMedium,
                color = playerTheme.textColor.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = song?.title ?: "未选择歌曲",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = playerTheme.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                song?.let {
                    Text(
                        text = it.artist.ifEmpty { "未知" },
                        style = MaterialTheme.typography.bodySmall,
                        color = playerTheme.textColor.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 右上角：歌词偏移/重新获取按钮
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.End
            ) {
                if (onRequestOffsetDialog != null) {
                    IconButton(onClick = onRequestOffsetDialog, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "歌词延迟",
                            tint = playerTheme.textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onShowSourcePicker, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新获取歌词",
                            tint = playerTheme.textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ---- 专辑封面（正方形，可点击进入全屏）----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(playerTheme.primaryColor.copy(alpha = 0.15f))
                    .clickable(onClick = onEnterFullScreen),
                contentAlignment = Alignment.Center
            ) {
                AlbumCover(
                    displayUri = currentUri,
                    isPlaying = isPlaying,
                    isAlbumArtLoading = isAlbumArtLoading,
                    networkAlbumArtUrl = networkAlbumArtUrl
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ---- 歌词区域 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LyricsDisplay(
                lyrics = lyricsResult,
                currentPosition = currentPosition,
                onRequestOffsetDialog = null,
                onSearchLyrics = onSearchLyrics,
                fontScale = lyricsFontScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ---- 进度条 ----
        Slider(
            value = if (duration > 0) (currentPosition.toFloat() / duration) else 0f,
            onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = playerTheme.accentColor,
                activeTrackColor = playerTheme.accentColor
            ),
            thumb = {
                SliderDefaults.Thumb(
                    modifier = Modifier.size(14.dp),
                    interactionSource = thumbInteractionSource
                )
            },
            track = { sliderState ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    ) {
                        val trackY = size.height / 2
                        val activeEnd = size.width * sliderState.value
                        drawLine(
                            color = playerTheme.textColor.copy(alpha = 0.15f),
                            start = Offset(0f, trackY),
                            end = Offset(size.width, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = playerTheme.accentColor.copy(alpha = 0.45f),
                            start = Offset(0f, trackY),
                            end = Offset(activeEnd, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
        )

        // ---- 时间 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = playerTheme.textColor.copy(alpha = 0.5f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = playerTheme.textColor.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ---- 控制栏（复用 NormalModeButtonBar）----
        NormalModeButtonBar(
            playerTheme = playerTheme,
            playMode = playMode,
            isPlaying = isPlaying,
            song = song,
            favoriteIds = favoriteIds,
            onTogglePlayMode = onTogglePlayMode,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onToggleFavorite = onToggleFavorite,
            onEnterFullScreen = onEnterFullScreen,
            playbackSpeed = playbackSpeed,
            onSpeedChange = onSpeedChange
        )
    }
}

// ==================== 正常播放模式 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalMode(
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playMode: PlayMode,
    playerTheme: PlayerTheme,
    lyricsResult: LyricsResult,
    networkAlbumArtUrl: String?,
    isAlbumArtLoading: Boolean = false,
    slideDirection: Int = 0,
    timeOffset: Long = 0L,
    onRequestOffsetDialog: (() -> Unit)? = null,
    onShowSourcePicker: () -> Unit = {},
    onSearchLyrics: () -> Unit = {},
    onEnterFullScreen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayMode: () -> Unit,
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    lyricsFontScale: Float = 1f,
    playbackSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
    coverSizePercent: Float = 0.5f
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(playerTheme.backgroundColor)
            .drawBehind {
                // Walkman 主题金属质感?? 层叠??
                playerTheme.metallicHighlightBrush(size)?.let { drawRect(brush = it, size = size) }
                playerTheme.metallicSheenBrush()?.let { drawRect(brush = it, size = size) }
                playerTheme.metallicVignetteBrush(size)?.let { drawRect(brush = it, size = size) }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        .navigationBarsPadding()
    ) {
        val thumbInteractionSource = remember { MutableInteractionSource() }

        NormalModeAlbumInfoRow(
            modifier = Modifier.weight(1f),
            song = song,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            playerTheme = playerTheme,
            lyricsResult = lyricsResult,
            networkAlbumArtUrl = networkAlbumArtUrl,
            isAlbumArtLoading = isAlbumArtLoading,
            slideDirection = slideDirection,
            timeOffset = timeOffset,
            onRequestOffsetDialog = onRequestOffsetDialog,
            onShowSourcePicker = onShowSourcePicker,
            onSearchLyrics = onSearchLyrics,
            onEnterFullScreen = onEnterFullScreen,
            lyricsFontScale = lyricsFontScale,
            coverSizePercent = coverSizePercent
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 进度条（小圆点指示器）
        Slider(
            value = if (duration > 0) (currentPosition.toFloat() / duration) else 0f,
            onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = playerTheme.accentColor,
                activeTrackColor = playerTheme.accentColor
            ),
            thumb = {
                SliderDefaults.Thumb(
                    modifier = Modifier.size(14.dp),
                    interactionSource = thumbInteractionSource
                )
            },
            track = { sliderState ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    ) {
                        val trackY = size.height / 2
                        val activeEnd = size.width * sliderState.value
                        drawLine(
                            color = playerTheme.textColor.copy(alpha = 0.15f),
                            start = Offset(0f, trackY),
                            end = Offset(size.width, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = playerTheme.accentColor.copy(alpha = 0.45f),
                            start = Offset(0f, trackY),
                            end = Offset(activeEnd, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
        )

        // 时间
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = playerTheme.textColor.copy(alpha = 0.5f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = playerTheme.textColor.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        NormalModeButtonBar(
            playerTheme = playerTheme,
            playMode = playMode,
            isPlaying = isPlaying,
            song = song,
            favoriteIds = favoriteIds,
            onTogglePlayMode = onTogglePlayMode,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onToggleFavorite = onToggleFavorite,
            onEnterFullScreen = onEnterFullScreen,
            playbackSpeed = playbackSpeed,
            onSpeedChange = onSpeedChange
        )
    }
}

// ==================== 全屏播放模式 ====================

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FullScreenMode(
    song: Song?,
    showLockButton: Boolean = true,
    slideDirection: Int = 0,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playMode: PlayMode,
    playerTheme: PlayerTheme,
    lyricsResult: LyricsResult,
    networkAlbumArtUrl: String?,
    isAlbumArtLoading: Boolean = false,
    showLyricsOverlay: Boolean,
    timeOffset: Long = 0L,
    onRequestOffsetDialog: (() -> Unit)? = null,
    onToggleLyrics: () -> Unit,
    onSearchLyrics: () -> Unit = {},
    onFullScreenChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayMode: () -> Unit,
    onBackToLibrary: () -> Unit,
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Long) -> Unit = {},
    lyricsFontScale: Float = 1f,
    playbackSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {}
) {
    val thumbInteractionSource = remember { MutableInteractionSource() }
    val fsContext = LocalContext.current
    val fsSpeedPlayer = remember { PlayerController.getInstance(fsContext) }
    var fsShowSpeedDialog by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }

    // 全屏专辑封面：动画处理（放在分支外，歌词层 bgUri 也需要 fsCurrentUri）
    var fsCurrentUri by remember { mutableStateOf<Uri?>(null) }
    var fsPrevUri by remember { mutableStateOf<Uri?>(null) }
    val fsAnimProgress = remember { Animatable(1f) }
    val fsCapturedDirection = remember { mutableIntStateOf(1) }
    // 标记是否首次进入全屏，首次不播切歌动画
    var fsHasPreviousSong by remember { mutableStateOf(false) }
    // URI 是否至少解析过一次，防止第一次显示黑胶
    var fsUriInitialized by remember { mutableStateOf(false) }

    // 合并 URI 解析和动画：先保存旧 URI → 解析新 URI → 再播动画
    LaunchedEffect(song?.id, song?.albumArtUri) {
        try {
            fsPrevUri = fsCurrentUri
            val localUri = withContext(Dispatchers.IO) { song?.resolveAlbumArtUri(fsContext) }
            fsCurrentUri = networkAlbumArtUrl?.let { android.net.Uri.parse(it) } ?: localUri
            fsUriInitialized = true

            if (song?.id != null && fsHasPreviousSong) {
                fsCapturedDirection.intValue = if (slideDirection != 0) slideDirection else 1
                fsAnimProgress.snapTo(0f)
                fsAnimProgress.animateTo(1f, animationSpec = tween(600))
            }
            fsHasPreviousSong = true
        } catch (_: Exception) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerTheme.backgroundColor)
            .drawBehind {
                // Walkman 主题金属质感?? 层叠??
                playerTheme.metallicHighlightBrush(size)?.let { drawRect(brush = it, size = size) }
                playerTheme.metallicSheenBrush()?.let { drawRect(brush = it, size = size) }
                playerTheme.metallicVignetteBrush(size)?.let { drawRect(brush = it, size = size) }
            }
        ) {
        // 背景层（上划进入歌词/右划退出全屏）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onToggleLyrics, onFullScreenChange) {
                    var totalY = 0f
                    var totalX = 0f
                    detectDragGestures(
                        onDragStart = { totalY = 0f; totalX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalY += dragAmount.y
                            totalX += dragAmount.x
                        },
                        onDragEnd = {
                            when {
                                totalY < -150f -> {  // 上划进入歌词模式
                                    onToggleLyrics()
                                }
                                totalX > 150f -> {  // 右划：歌词模式→全屏封面，全屏封面→普通页
                                    if (showLyricsOverlay) {
                                        onToggleLyrics()
                                    } else {
                                        onFullScreenChange(false)
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            if (showLyricsOverlay) {
                // 歌词模式：有封面则模糊显示，无封面则使用主题??
                val bgUri = networkAlbumArtUrl?.let { android.net.Uri.parse(it) }
                    ?: fsCurrentUri

                if (bgUri != null) {
                    val bgPainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(fsContext)
                            .data(bgUri)
                            .size(1024)
                            .crossfade(true)
                            .build()
                    )
                    if (bgPainter.state is coil.compose.AsyncImagePainter.State.Success) {
                        Image(
                            painter = bgPainter,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.40f),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // 加载??失败 ??主题色背??
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(playerTheme.primaryColor.copy(alpha = 0.15f))
                        )
                    }
                } else {
                    // 无封????主题色背??
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(playerTheme.primaryColor.copy(alpha = 0.15f))
                    )
                }
                // 极淡遮罩，保证歌词可读性同时可看清专辑??
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.05f))
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 旧封面（淡出）
                    if (fsAnimProgress.value < 1f && fsUriInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - fsAnimProgress.value }
                        ) {
                            AlbumCover(
                                displayUri = fsPrevUri,
                                isPlaying = isPlaying,
                                isAlbumArtLoading = isAlbumArtLoading,
                                networkAlbumArtUrl = networkAlbumArtUrl,
                                requestSize = 1024,
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    // 新封面（滑入）
                    if (fsUriInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (fsAnimProgress.value < 1f) {
                                        val slidePx = (1f - fsAnimProgress.value) * size.width
                                        translationX = if (fsCapturedDirection.intValue == 1) slidePx else -slidePx
                                    }
                                }
                        ) {
                            AlbumCover(
                                displayUri = fsCurrentUri,
                                isPlaying = isPlaying,
                                isAlbumArtLoading = isAlbumArtLoading,
                                networkAlbumArtUrl = networkAlbumArtUrl,
                                requestSize = 1024,
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        // 歌词层（右划返回专辑全屏）
        if (showLyricsOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 180.dp)
                    .pointerInput(onToggleLyrics) {
                        var totalX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                if (dragAmount > 0f) {
                                    change.consume()
                                    totalX += dragAmount
                                }
                            },
                            onDragEnd = {
                                if (totalX > 150f) {  // 右划返回专辑全屏
                                    onToggleLyrics()
                                }
                            }
                        )
                    }
            ) {
                LyricsDisplay(
                    lyrics = lyricsResult,
                    currentPosition = currentPosition + timeOffset,
                    isFullScreen = true,
                    onRequestOffsetDialog = onRequestOffsetDialog,
                    onSearchLyrics = onSearchLyrics,
                    accentColor = playerTheme.primaryColor,
                    fontScale = lyricsFontScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 底部暗色渐变（在歌词层之上，保证底部控件可读性）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // 底部半透明控件区域（在最上层，不被任何点击拦截）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 歌曲信息
            val currentLineIndex = lyricsResult.lines.indexOfLast { it.time <= currentPosition }
            if (!showLyricsOverlay && lyricsResult.lines.isNotEmpty() && currentLineIndex >= 0) {
                // 专辑封面模式 + 有歌词：显示当前行 + 下一行，确保至少两行内容
                val nextLineIndex = currentLineIndex + 1
                val displayText = buildString {
                    append(lyricsResult.lines[currentLineIndex].text)
                    if (nextLineIndex < lyricsResult.lines.size) {
                        append("\n")
                        append(lyricsResult.lines[nextLineIndex].text)
                    }
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = song?.title ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (song != null) {
                Text(
                    text = "${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 进度??
            Slider(
                value = if (duration > 0) (currentPosition.toFloat() / duration) else 0f,
                onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Red.copy(alpha = 0.7f),
                    activeTrackColor = Color.White.copy(alpha = 0.6f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                thumb = {
                    SliderDefaults.Thumb(
                        modifier = Modifier.size(14.dp),
                        interactionSource = thumbInteractionSource
                    )
                },
                track = { sliderState ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    ) {
                        val trackY = size.height / 2
                        val activeEnd = size.width * sliderState.value
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = Offset(0f, trackY),
                            end = Offset(size.width, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(0f, trackY),
                            end = Offset(activeEnd, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            )

            // 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 控制按钮（半透明??
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 倍速按钮（左侧??
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = { fsShowSpeedDialog = true }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${"%.2f".format(playbackSpeed).trimEnd('0').trimEnd('.')}x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (playbackSpeed != 1.0f) Color.Red else Color.White.copy(alpha = 0.65f)
                    )

                    // 倍速选择弹窗（按钮上方弹出）
                    if (fsShowSpeedDialog) {
                        Popup(
                            alignment = Alignment.BottomCenter,
                            onDismissRequest = { fsShowSpeedDialog = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                    val isSelected = speed == playbackSpeed
                                    TextButton(onClick = {
                                        fsSpeedPlayer.setPlaybackSpeed(speed)
                                        onSpeedChange(speed)
                                        fsShowSpeedDialog = false
                                    }) {
                                        Text(
                                            text = "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x",
                                            fontSize = if (isSelected) 15.sp else 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.Red else Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 播放模式
                IconButton(onClick = onTogglePlayMode) {
                    Icon(
                        painter = when (playMode) {
                            PlayMode.SHUFFLE -> painterResource(R.drawable.ic_play_mode_shuffle)
                            PlayMode.REPEAT_ONE -> painterResource(R.drawable.ic_play_mode_repeat_one)
                            PlayMode.REPEAT_FOLDER -> painterResource(R.drawable.ic_play_mode_repeat)
                            PlayMode.NORMAL -> painterResource(R.drawable.ic_play_mode_order)
                        },
                        contentDescription = playMode.label,
                        tint = Color.White.copy(alpha = if (playMode != PlayMode.NORMAL) 0.9f else 0.4f)
                    )
                }

                // 上一??
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }

                // 播放/暂停
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7C4DFF).copy(alpha = 0.8f))
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                // 下一首
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }

                // 红心按钮
                val isFav = song?.id?.let { it in favoriteIds } ?: false
                IconButton(
                    onClick = { song?.id?.let { onToggleFavorite(it) } },
                    enabled = song != null
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFav) "取消收藏" else "收藏",
                        tint = Color.Red.copy(alpha = if (isFav) 1f else 0.5f)
                    )
                }

                // 退出???全屏/歌词模式
                IconButton(
                    onClick = {
                        if (showLyricsOverlay) {
                            onToggleLyrics()  // 歌词模式→返回全屏封面
                        } else {
                            onFullScreenChange(false)  // 全屏封面→返回普通页
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "退出全屏",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ===== 锁定遮罩（锁定后拦截所有触控，在底部控件之上、锁按钮之下）=====
        if (isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            // 持续消费所有触控事件，阻止传递到底层组件
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                            }
                        }
                    }
            )
        }

        // ===== 屏幕锁按钮（右侧中间）- 可由设置页控制显示 =====
        if (showLockButton) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { isScreenLocked = !isScreenLocked },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (isScreenLocked) "解锁屏幕" else "锁定屏幕",
                tint = Color.White.copy(alpha = if (isScreenLocked) 0.9f else 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
        }
    }
}

/** 无封面时显示的旋转黑胶唱??*/
/** 专辑封面内容（支持不同尺寸和裁切模式） */
@Composable
private fun AlbumCover(
    displayUri: Uri?,
    isPlaying: Boolean,
    isAlbumArtLoading: Boolean,
    networkAlbumArtUrl: String?,
    requestSize: Int = 300,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    // 缓存最后成功加载的封面，切歌时用旧封面过渡，避免闪黑胶
    var lastCover by remember { mutableStateOf<Painter?>(null) }
    if (displayUri != null) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(displayUri)
                .size(requestSize)
                .crossfade(true)
                .build()
        )
        if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
            lastCover = painter
            Image(
                painter = painter,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else if (painter.state is coil.compose.AsyncImagePainter.State.Loading) {
            val prevCover = lastCover
            if (prevCover != null) {
                // 新封面还在加载，先显示旧封面过渡
                Image(
                    painter = prevCover,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            } else {
                RotatingVinyl(
                    isPlaying = isPlaying,
                    isLoading = isAlbumArtLoading && networkAlbumArtUrl == null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Error / Empty：加载失败且无缓存回退时显示黑胶
            RotatingVinyl(
                isPlaying = isPlaying,
                isLoading = isAlbumArtLoading && networkAlbumArtUrl == null,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        RotatingVinyl(
            isPlaying = isPlaying,
            isLoading = isAlbumArtLoading && networkAlbumArtUrl == null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RotatingVinyl(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var rotation by remember { mutableFloatStateOf(0f) }

    // 播放时持续旋转，暂停/停止时冻结角??
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation = (rotation + 1f) % 360f
                kotlinx.coroutines.delay(50)
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.disk),
            contentDescription = "默认唱片",
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation),
            contentScale = ContentScale.Fit
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * 调整 LRC 歌词文本的所有时间戳，应用毫秒级偏移
 * @param rawLrc 原始 LRC 文本
 * @param offsetMs 偏移量（毫秒），正数=延后，负??提前
 * @return 调整后的 LRC 文本
 */
private fun adjustLrcTimestamps(rawLrc: String, offsetMs: Long): String {
    if (offsetMs == 0L) return rawLrc
    val offsetSec = offsetMs / 1000.0
    val regex = Regex("""\[(\d+):(\d+(?:[.:]\d+)?)]""")
    return rawLrc.lines().joinToString("\n") { line ->
        regex.replace(line) { match ->
            val minutes = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val secStr = match.groupValues[2].replace(':', '.')
            val totalSec = minutes * 60.0 + (secStr.toDoubleOrNull() ?: return@replace match.value)
            val adjusted = (totalSec + offsetSec).coerceAtLeast(0.0)
            val newMinutes = (adjusted / 60).toInt()
            val newSeconds = adjusted % 60
            "[${String.format("%02d:%05.2f", newMinutes, newSeconds)}]"
        }
    }
}

// ==================== NormalMode 子组件 ====================

@Composable
private fun NormalModeAlbumInfoRow(
    modifier: Modifier = Modifier,
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    playerTheme: PlayerTheme,
    lyricsResult: LyricsResult,
    networkAlbumArtUrl: String?,
    isAlbumArtLoading: Boolean = false,
    slideDirection: Int = 0,
    timeOffset: Long = 0L,
    onRequestOffsetDialog: (() -> Unit)? = null,
    onShowSourcePicker: () -> Unit = {},
    onSearchLyrics: () -> Unit = {},
    onEnterFullScreen: () -> Unit,
    lyricsFontScale: Float = 1f,
    coverSizePercent: Float = 0.5f
) {
    val configuration = LocalConfiguration.current
    // 横屏下短边 = screenHeightDp，封面大小 = 短边 × coverSizePercent
    val coverSizeDp = (configuration.screenHeightDp * coverSizePercent).dp

    // 上半部分：左侧大专辑 + 右侧歌曲信息+歌词
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // 左侧：专辑封面
        Box(
            modifier = Modifier
                .width(coverSizeDp)
                .aspectRatio(1f)
        ) {
            // 专辑封面（点击进入全屏）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(playerTheme.primaryColor.copy(alpha = 0.15f))
                    .clickable(onClick = onEnterFullScreen),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                var currentUri by remember { mutableStateOf<Uri?>(null) }
                var prevUri by remember { mutableStateOf<Uri?>(null) }
                val animProgress = remember { Animatable(1f) }
                val capturedDirection = remember { mutableIntStateOf(1) }
                // 标记是否首次进入该页面，首次不播切歌动画
                var hasPreviousSong by remember { mutableStateOf(false) }
                // URI 是否至少解析过一次（即使结果为 null），防止 URI 未加载时显示黑胶
                var uriInitialized by remember { mutableStateOf(false) }

                // 合并 URI 解析和动画：先保存旧 URI → 解析新 URI → 再播动画
                LaunchedEffect(song?.id, song?.albumArtUri) {
                    try {
                        prevUri = currentUri
                        val localUri = withContext(Dispatchers.IO) { song?.resolveAlbumArtUri(context) }
                        currentUri = networkAlbumArtUrl?.let { android.net.Uri.parse(it) } ?: localUri
                        uriInitialized = true

                        if (song?.id != null && hasPreviousSong) {
                            capturedDirection.intValue = if (slideDirection != 0) slideDirection else 1
                            animProgress.snapTo(0f)
                            animProgress.animateTo(1f, animationSpec = tween(600))
                        }
                        hasPreviousSong = true
                    } catch (_: Exception) { }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 旧封面（淡出）
                    if (animProgress.value < 1f && uriInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - animProgress.value }
                        ) {
                            AlbumCover(
                                displayUri = prevUri,
                                isPlaying = isPlaying,
                                isAlbumArtLoading = isAlbumArtLoading,
                                networkAlbumArtUrl = networkAlbumArtUrl
                            )
                        }
                    }
                    // 新封面（滑入）
                    if (uriInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (animProgress.value < 1f) {
                                        val slidePx = (1f - animProgress.value) * size.width
                                        translationX = if (capturedDirection.intValue == 1) slidePx else -slidePx
                                    }
                                }
                        ) {
                            AlbumCover(
                                displayUri = currentUri,
                                isPlaying = isPlaying,
                                isAlbumArtLoading = isAlbumArtLoading,
                                networkAlbumArtUrl = networkAlbumArtUrl
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧：歌曲信息居中，歌词占据剩余空间
        Column(modifier = Modifier.weight(0.45f)) {
            // 上半部：顶部对齐的歌名和信息（与左侧封面顶部对齐，仅占实际高度）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 歌名居中，偏移/搜索按钮靠右对齐
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 居中显示的歌名
                        Text(
                            text = song?.title ?: "未选择歌曲",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = playerTheme.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                        // 靠右的偏移/搜索按钮
                        if (onRequestOffsetDialog != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                IconButton(onClick = onRequestOffsetDialog, modifier = Modifier.size(26.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "歌词延迟",
                                        tint = playerTheme.textColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(onClick = onShowSourcePicker, modifier = Modifier.size(26.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "重新获取歌词",
                                        tint = playerTheme.textColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    song?.let {
                        Text(
                            text = "${it.artist} - ${it.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = playerTheme.textColor.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 下半部：歌词区域
            Box(modifier = Modifier.weight(1f)) {
                LyricsDisplay(
                    lyrics = lyricsResult,
                    currentPosition = currentPosition + timeOffset,
                    onRequestOffsetDialog = null,
                    onSearchLyrics = onSearchLyrics,
                    fontScale = lyricsFontScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun NormalModeButtonBar(
    playerTheme: PlayerTheme,
    playMode: PlayMode,
    isPlaying: Boolean,
    song: Song?,
    favoriteIds: Set<Long>,
    onTogglePlayMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onEnterFullScreen: () -> Unit,
    playbackSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {}
) {
    var showSpeedDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speedPlayer = remember { PlayerController.getInstance(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
       // 倍速按钮（左侧）
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${"%.2f".format(playbackSpeed).trimEnd('0').trimEnd('.')}x",
                color = if (playbackSpeed != 1.0f) playerTheme.accentColor else playerTheme.textColor.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = { showSpeedDialog = true })
                    .padding(horizontal = 5.dp, vertical = 6.dp)
            )

            // 倍速选择弹窗（按钮上方弹出）
            if (showSpeedDialog) {
                Popup(
                    alignment = Alignment.BottomCenter,
                    onDismissRequest = { showSpeedDialog = false }
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                            val isSelected = speed == playbackSpeed
                            TextButton(onClick = {
                                speedPlayer.setPlaybackSpeed(speed)
                                onSpeedChange(speed)
                                showSpeedDialog = false
                            }) {
                                Text(
                                    text = "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x",
                                    fontSize = if (isSelected) 15.sp else 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) playerTheme.accentColor else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 播放模式
        IconButton(onClick = onTogglePlayMode) {
            Icon(
                painter = when (playMode) {
                    PlayMode.SHUFFLE -> painterResource(R.drawable.ic_play_mode_shuffle)
                    PlayMode.REPEAT_ONE -> painterResource(R.drawable.ic_play_mode_repeat_one)
                    PlayMode.REPEAT_FOLDER -> painterResource(R.drawable.ic_play_mode_repeat)
                    PlayMode.NORMAL -> painterResource(R.drawable.ic_play_mode_order)
                },
                contentDescription = playMode.label,
                tint = if (playMode != PlayMode.NORMAL)
                    playerTheme.accentColor
                else
                    playerTheme.textColor.copy(alpha = 0.4f)
            )
        }

        // 上一首
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                tint = playerTheme.textColor
            )
        }

        // 播放/暂停
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(playerTheme.accentColor)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }

        // 下一首
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                tint = playerTheme.textColor
            )
        }

        // 红心按钮
        val isFav = song?.id?.let { it in favoriteIds } ?: false
        IconButton(
            onClick = { song?.id?.let { onToggleFavorite(it) } },
            enabled = song != null
        ) {
            Icon(
                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFav) "取消收藏" else "收藏",
                tint = if (isFav) Color.Red else playerTheme.textColor.copy(alpha = 0.5f)
            )
        }

        // 全屏按钮（最右侧）
        IconButton(onClick = onEnterFullScreen) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "全屏",
                tint = playerTheme.textColor.copy(alpha = 0.5f)
            )
        }
    }
}

// ==================== 歌词偏移弹窗（已拆分，降低主函数 JIT 编译量） ====================

@Composable
private fun LyricsOffsetDialog(
    song: Song,
    lyricsTimeOffset: Long,
    lyricsResult: LyricsResult,
    scope: kotlinx.coroutines.CoroutineScope,
    showDialog: Boolean,
    lyricsOffsetManager: LyricsOffsetManager,
    onDismiss: () -> Unit,
    onTimeOffsetChanged: (Long) -> Unit,
    onSaveComplete: () -> Unit = {}
) {
    if (!showDialog) return
    val offsetKey = LyricsOffsetManager.getKey(song.title, song.artist)
    val previousOffset = remember { lyricsTimeOffset }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    Color(0xDD333333),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { /*  */ }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (previousOffset != 0L) {
                Text(
                    text = "上次调整：${String.format("%+.1f", previousOffset / 1000.0)}s",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
            TextButton(onClick = {
                val newOffset = (lyricsTimeOffset - 500).coerceAtLeast(-30000L)
                lyricsOffsetManager.setOffset(offsetKey, newOffset)
                onTimeOffsetChanged(newOffset)
            }) {
                Text("\u2212", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "${"%.1f".format(lyricsTimeOffset / 1000.0)}s",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            TextButton(onClick = {
                val newOffset = (lyricsTimeOffset + 500).coerceAtMost(30000L)
                lyricsOffsetManager.setOffset(offsetKey, newOffset)
                onTimeOffsetChanged(newOffset)
            }) {
                Text("+", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = {
                // 先写文件，再重置偏移并重载歌词，确保当前播放立即生效
                scope.launch {
                    if (song.filePath.isNotEmpty() && lyricsResult.rawLrc.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val audioFile = java.io.File(song.filePath)
                                val parent = audioFile.parentFile
                                if (parent != null) {
                                    val lrcFile = java.io.File(parent, "${audioFile.nameWithoutExtension}.lrc")
                                    val adjustedLrc = adjustLrcTimestamps(lyricsResult.rawLrc, lyricsTimeOffset)
                                    lrcFile.writeText(adjustedLrc)
                                }
                            } catch (_: Exception) { }
                        }
                    }
                    // 文件写入完成后，重置偏移并触发重载（使修正立即生效）
                    lyricsOffsetManager.setOffset(offsetKey, 0L)
                    onTimeOffsetChanged(0L)
                    onSaveComplete()
                    onDismiss()
                }
            }) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        }
        }
    }
}

// ==================== 歌词源选择弹窗（已拆分，降低主函数 JIT 编译量） ====================

@Composable
private fun LyricsSourcePickerDialog(
    song: Song,
    showDialog: Boolean,
    currentSource: String,
    sourceOrderManager: SourceOrderManager,
    onSourceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xDD2A2A2A), shape = RoundedCornerShape(16.dp))
                .clickable { /* 消费点击 */ }
                .padding(20.dp)
                .width(260.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "选择歌词来源",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "重新获取「${song.title}」的歌词",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))
            SourceOrderManager.DEFAULT_ORDER.forEachIndexed { index, source ->
                val displayName = when (source) {
                    "酷狗音乐" -> "酷狗音乐"
                    "QQ音乐" -> "QQ音乐"
                    "酷我音乐" -> "酷我音乐"
                    "网易云音乐" -> "网易云音乐"
                    "LRCLIB" -> "LRCLIB"
                    else -> source
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (currentSource == source) Color.White.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable {
                            onDismiss()
                            onSourceSelected(source)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (currentSource == source) {
                        Text(
                            text = "当前",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (index < SourceOrderManager.DEFAULT_ORDER.size - 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}
