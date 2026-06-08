package com.example.yyplayer.ui.screens

import android.media.audiofx.AudioEffect
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yyplayer.data.lyrics.AlbumArtFetcher
import com.example.yyplayer.data.lyrics.AlbumArtSourceManager
import com.example.yyplayer.data.lyrics.AlbumArtWriter
import com.example.yyplayer.data.lyrics.SourceOrderManager
import com.example.yyplayer.data.repository.LyricsFontSizeSettings
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.model.PlayerTheme
import com.example.yyplayer.data.model.ScreenRatio
import com.example.yyplayer.data.model.resolveAlbumArtUri
import com.example.yyplayer.data.repository.PlayerStateManager
import com.example.yyplayer.player.MusicService
import com.example.yyplayer.ui.viewmodel.LibraryViewModel
import com.example.yyplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.yyplayer.R
import java.util.HashMap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

private const val SETTINGS_TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    audioSessionId: Int = 0,
    onBackToNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentThemeId by playerViewModel.themeRepository.currentThemeId.collectAsState(initial = "classic_black")
    val songs by libraryViewModel.songs.collectAsState()
    val isScanning by libraryViewModel.isScanning.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sourceOrderManager = remember { SourceOrderManager(context) }
    var showThemePage by remember { mutableStateOf(false) }
    var showCustomThemePage by remember { mutableStateOf(false) }
    var showGuidePage by remember { mutableStateOf(false) }
    var showAboutPage by remember { mutableStateOf(false) }
    var showLyricsPage by remember { mutableStateOf(false) }
    var showLyricsCoverPage by remember { mutableStateOf(false) }
    var showNasPage by remember { mutableStateOf(false) }
    var showRatioPage by remember { mutableStateOf(false) }
    var showBatchCoverPage by remember { mutableStateOf(false) }
    var showManualScanPage by remember { mutableStateOf(false) }
    var showAlbumArtPage by remember { mutableStateOf(false) }
    var showLyricsFontSizePage by remember { mutableStateOf(false) }
    val mainScrollState = rememberScrollState()
    var refreshToggle by remember { mutableStateOf(false) }

    // 系统返回手势：在二级页面时返回设置首页，在设置首页时跳转播放页
    val isInSubPage = showThemePage || showCustomThemePage || showGuidePage || showAboutPage ||
            showLyricsPage || showLyricsCoverPage || showNasPage || showRatioPage ||
            showBatchCoverPage || showManualScanPage || showAlbumArtPage || showLyricsFontSizePage
    BackHandler(enabled = isInSubPage) {
        showThemePage = false
        showCustomThemePage = false
        showGuidePage = false
        showAboutPage = false
        showLyricsPage = false
        showLyricsCoverPage = false
        showNasPage = false
        showRatioPage = false
        showBatchCoverPage = false
        showManualScanPage = false
        showAlbumArtPage = false
        showLyricsFontSizePage = false
    }
    BackHandler(enabled = !isInSubPage) {
        onBackToNowPlaying()
    }
    val enabledSources = remember(sourceOrderManager, refreshToggle) {
        sourceOrderManager.getEnabledSources()
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                showThemePage = showThemePage,
                showCustomThemePage = showCustomThemePage,
                showGuidePage = showGuidePage,
                showAboutPage = showAboutPage,
                showLyricsPage = showLyricsPage,
                showLyricsCoverPage = showLyricsCoverPage,
                showNasPage = showNasPage,
                showRatioPage = showRatioPage,
                showBatchCoverPage = showBatchCoverPage,
                showManualScanPage = showManualScanPage,
                showAlbumArtPage = showAlbumArtPage,
                showLyricsFontSizePage = showLyricsFontSizePage,
                onBack = {
                    showThemePage = false
                    showCustomThemePage = false
                    showGuidePage = false
                    showAboutPage = false
                    showLyricsPage = false
                    showLyricsCoverPage = false
                    showNasPage = false
                    showRatioPage = false
                    showBatchCoverPage = false
                    showManualScanPage = false
                    showAlbumArtPage = false
                    showLyricsFontSizePage = false
                }
            )
        },
        modifier = modifier
    ) { padding ->
        SettingsPageContent(
            padding = padding,
            showThemePage = showThemePage,
            showCustomThemePage = showCustomThemePage,
            showGuidePage = showGuidePage,
            showAboutPage = showAboutPage,
            showLyricsPage = showLyricsPage,
            showLyricsCoverPage = showLyricsCoverPage,
            showNasPage = showNasPage,
            showRatioPage = showRatioPage,
            showBatchCoverPage = showBatchCoverPage,
            showManualScanPage = showManualScanPage,
            currentThemeId = currentThemeId,
            showAlbumArtPage = showAlbumArtPage,
            showLyricsFontSizePage = showLyricsFontSizePage,
            mainScrollState = mainScrollState,
            songs = songs,
            isScanning = isScanning,
            scope = scope,
            context = context,
            sourceOrderManager = sourceOrderManager,
            refreshToggle = refreshToggle,
            enabledSources = enabledSources,
            audioSessionId = audioSessionId,
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            onShowNasPage = { showNasPage = true },
            onShowThemePage = { showThemePage = true },
            onShowCustomThemePage = { showCustomThemePage = true },
            onShowGuidePage = { showGuidePage = true },
            onShowAboutPage = { showAboutPage = true },
            onShowLyricsPage = { showLyricsPage = true },
            onShowLyricsCoverPage = { showLyricsCoverPage = true },
            onShowRatioPage = { showRatioPage = true },
            onShowBatchCoverPage = { showBatchCoverPage = true },
            onShowManualScanPage = { showManualScanPage = true },
            onShowAlbumArtPage = { showAlbumArtPage = true },
            onShowLyricsFontSizePage = { showLyricsFontSizePage = true }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopAppBar(
    showThemePage: Boolean,
    showCustomThemePage: Boolean,
    showGuidePage: Boolean,
    showAboutPage: Boolean,
    showLyricsPage: Boolean,
    showLyricsCoverPage: Boolean,
    showNasPage: Boolean,
    showRatioPage: Boolean,
    showBatchCoverPage: Boolean,
    showManualScanPage: Boolean,
    showAlbumArtPage: Boolean,
    showLyricsFontSizePage: Boolean,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                when {
                    showCustomThemePage -> "自定义主题颜色"
                    showThemePage -> "主题设置"
                    showGuidePage -> "使用说明"
                    showAboutPage -> "关于"
                    showLyricsPage -> "歌词获取源"
                    showLyricsCoverPage -> "歌词/封面管理"
                    showNasPage -> "NAS 网络音乐"
                    showRatioPage -> "屏幕比例"
                    showBatchCoverPage -> "批量获取封面"
                    showManualScanPage -> "手动扫描"
                    showAlbumArtPage -> "封面获取源"
                    showLyricsFontSizePage -> "歌词字体大小"
                    else -> "设置"
                }
            )
        },
        navigationIcon = {
            if (showThemePage || showCustomThemePage || showGuidePage || showAboutPage || showLyricsPage || showLyricsCoverPage || showNasPage || showRatioPage || showBatchCoverPage || showManualScanPage || showAlbumArtPage || showLyricsFontSizePage) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
private fun SettingsPageContent(
    padding: PaddingValues,
    showThemePage: Boolean,
    showCustomThemePage: Boolean,
    showGuidePage: Boolean,
    showAboutPage: Boolean,
    showLyricsPage: Boolean,
    showLyricsCoverPage: Boolean,
    showNasPage: Boolean,
    showRatioPage: Boolean,
    showBatchCoverPage: Boolean,
    showManualScanPage: Boolean,
    showAlbumArtPage: Boolean,
    showLyricsFontSizePage: Boolean,
    mainScrollState: androidx.compose.foundation.ScrollState,
    currentThemeId: String,
    songs: List<Song>,
    isScanning: Boolean,
    scope: CoroutineScope,
    context: android.content.Context,
    sourceOrderManager: SourceOrderManager,
    refreshToggle: Boolean,
    enabledSources: List<String>,
    audioSessionId: Int,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onShowNasPage: () -> Unit,
    onShowThemePage: () -> Unit,
    onShowCustomThemePage: () -> Unit,
    onShowGuidePage: () -> Unit,
    onShowAboutPage: () -> Unit,
    onShowLyricsPage: () -> Unit,
    onShowLyricsCoverPage: () -> Unit,
    onShowRatioPage: () -> Unit,
    onShowBatchCoverPage: () -> Unit,
    onShowManualScanPage: () -> Unit,
    onShowAlbumArtPage: () -> Unit,
    onShowLyricsFontSizePage: () -> Unit
) {
    // 计算当前页面状态，用于二级页面进出动画
    val currentSettingsPage = when {
        showCustomThemePage -> "custom_theme"
        showThemePage -> "theme"
        showGuidePage -> "guide"
        showAboutPage -> "about"
        showLyricsPage -> "lyrics_source"
        showLyricsCoverPage -> "lyrics_cover"
        showNasPage -> "nas"
        showRatioPage -> "ratio"
        showBatchCoverPage -> "batch_cover"
        showManualScanPage -> "manual_scan"
        showAlbumArtPage -> "album_art"
        showLyricsFontSizePage -> "lyrics_font_size"
        else -> "main"
    }
    AnimatedContent(
        targetState = currentSettingsPage,
        transitionSpec = {
            val entering = initialState == "main" && targetState != "main"
            if (entering) {
                // 进入二级页：纯淡入淡出
                fadeIn(animationSpec = tween(150)).togetherWith(fadeOut(animationSpec = tween(100)))
            } else {
                // 返回主页：纯淡入淡出
                fadeIn(animationSpec = tween(150)).togetherWith(fadeOut(animationSpec = tween(100)))
            }
        },
        label = "settings_transition"
    ) { _ ->
        when {
            showCustomThemePage -> CustomThemePage(padding = padding, scope = scope, playerViewModel = playerViewModel)
            showThemePage -> ThemePage(padding = padding, currentThemeId = currentThemeId, scope = scope, playerViewModel = playerViewModel, onShowCustomTheme = onShowCustomThemePage)
            showGuidePage -> GuidePage(padding = padding)
            showAboutPage -> AboutPage(padding = padding, context = context)
            showLyricsPage -> LyricsSourcePage(padding = padding, sourceOrderManager = sourceOrderManager, refreshToggle = refreshToggle)
            showLyricsCoverPage -> LyricsCoverManagePage(padding = padding)
            showNasPage -> NasPage(padding = padding, libraryViewModel = libraryViewModel)
            showRatioPage -> RatioPage(padding = padding, playerViewModel = playerViewModel)
            showBatchCoverPage -> BatchCoverPage(padding = padding, context = context, libraryViewModel = libraryViewModel, scope = scope)
            showManualScanPage -> com.example.yyplayer.ui.screens.components.ManualScanPage(padding = padding, libraryViewModel = libraryViewModel, context = context, scope = scope)
            showAlbumArtPage -> AlbumArtSourcePage(padding = padding, context = context)
            showLyricsFontSizePage -> LyricsFontSizePage(padding = padding, playerViewModel = playerViewModel)
            else -> MainSettingsPage(
                padding = padding,
                mainScrollState = mainScrollState,
                songs = songs,
                isScanning = isScanning,
                currentThemeId = currentThemeId,
                enabledSources = enabledSources,
                audioSessionId = audioSessionId,
                context = context,
                scope = scope,
                playerViewModel = playerViewModel,
                libraryViewModel = libraryViewModel,
                onShowNasPage = onShowNasPage,
                onShowThemePage = onShowThemePage,
                onShowGuidePage = onShowGuidePage,
                onShowAboutPage = onShowAboutPage,
                onShowLyricsPage = onShowLyricsPage,
                onShowLyricsCoverPage = onShowLyricsCoverPage,
                onShowRatioPage = onShowRatioPage,
                onShowBatchCoverPage = onShowBatchCoverPage,
                onShowManualScanPage = onShowManualScanPage,
                onShowAlbumArtPage = onShowAlbumArtPage,
                onShowLyricsFontSizePage = onShowLyricsFontSizePage
            )
        }
    }
}

@Composable
private fun BatchCoverPage(
    padding: PaddingValues,
    context: android.content.Context,
    libraryViewModel: LibraryViewModel,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val fetcher = remember { AlbumArtFetcher() }
    val writer = remember { AlbumArtWriter(context) }
    val albumArtSourceManager = remember { AlbumArtSourceManager(context) }
    var scanState by remember { mutableStateOf("idle") } // idle | scanning | fetching | done
    var totalSongs by remember { mutableIntStateOf(0) }
    var checkedCount by remember { mutableIntStateOf(0) }
    var coverlessSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var successCount by remember { mutableIntStateOf(0) }
    var failCount by remember { mutableIntStateOf(0) }
    var fetchProgress by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ---- 说明框 ----
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 20.dp)
        ) {
            Text(
                text = "扫描所有歌曲中的无封面曲目，然后逐个从网络获取封面并保存到歌曲同目录下（如 song.mp3.jpg）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- 开始扫描 ----
        SectionTitle("执行")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
                .clickable(enabled = scanState == "idle") {
                    scanState = "scanning"
                    totalSongs = 0
                    checkedCount = 0
                    coverlessSongs = emptyList()
                    successCount = 0
                    failCount = 0
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val TAG = "BatchCover"
                            val globalT0 = System.currentTimeMillis()
                            val allSongs = libraryViewModel.songs.value
                            totalSongs = allSongs.size
                            Log.i(TAG, "=== 开始批量检查 ${allSongs.size} 首歌曲 ===")

                            val noCover = mutableListOf<Song>()
                            var lastUiUpdate = 0L
                            val uiThrottleMs = 120L

                            for ((index, song) in allSongs.withIndex()) {
                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdate > uiThrottleMs) {
                                    checkedCount = index + 1
                                    lastUiUpdate = now
                                }
                                if (!checkSongHasAlbumArt(song, context)) {
                                    noCover.add(song)
                                }
                            }
                            checkedCount = allSongs.size
                            coverlessSongs = noCover

                            Log.i(TAG, "第一阶段完成: 共 ${allSongs.size} 首, 无封面 ${noCover.size} 首, 耗时 ${System.currentTimeMillis() - globalT0}ms")

                            // 进入第二阶段
                            scanState = "fetching"
                            fetchProgress = 0

                            // 第二阶段：逐个获取封面（下载失败自动换源重试）
                            val order = albumArtSourceManager.getEnabledSources()
                            for ((i, song) in noCover.withIndex()) {
                                fetchProgress = i + 1
                                try {
                                    Log.d(TAG, "[$i/${noCover.size}] 获取封面: ${song.title} - ${song.artist}")
                                    var gotCover = false
                                    var skipSources = emptySet<String>()
                                    var lastException: Exception? = null
                                    while (!gotCover) {
                                        val pair = try {
                                            fetcher.fetchAlbumArtUrl(song.title, song.artist, order, skipSources = skipSources)
                                        } catch (e: Exception) {
                                            lastException = e
                                            null
                                        }
                                        if (pair == null) break
                                        val (sourceName, foundUrl) = pair
                                        val ok = writer.writeToSongFile(song.filePath, foundUrl)
                                        if (ok) {
                                            gotCover = true
                                            successCount++
                                            Log.i(TAG, "[$i/${noCover.size}] 成功($sourceName): ${song.title}")
                                        } else {
                                            skipSources = skipSources + sourceName
                                            Log.w(TAG, "[$i/${noCover.size}] 源«$sourceName»下载失败, 尝试下一个源...")
                                        }
                                    }
                                    if (!gotCover) {
                                        failCount++
                                        val reason = if (lastException != null) "${lastException::class.simpleName}: ${lastException.message}" else "所有源均无结果或下载失败"
                                        Log.w(TAG, "[$i/${noCover.size}] 获取失败: ${song.title} - $reason")
                                    }
                                } catch (e: Exception) {
                                    failCount++
                                    Log.e(TAG, "[$i/${noCover.size}] 获取失败: ${song.title}: ${e::class.simpleName}: ${e.message}")
                                }
                                val now2 = System.currentTimeMillis()
                                if (now2 - lastUiUpdate > uiThrottleMs) {
                                    // 更新进度
                                    lastUiUpdate = now2
                                }
                            }

                            Log.i(TAG, "批量获取完成: 成功 $successCount, 失败 $failCount, 总计耗时 ${System.currentTimeMillis() - globalT0}ms")
                        }
                        scanState = "done"
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (scanState) {
                            "scanning" -> "正在检查..."
                            "fetching" -> "正在获取封面..."
                            else -> "开始扫描"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (scanState == "idle") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = when (scanState) {
                            "scanning" -> "已检查 $checkedCount / $totalSongs"
                            "fetching" -> "$successCount 成功 · $failCount 失败（${fetchProgress} / ${coverlessSongs.size}）"
                            "done" -> "成功 $successCount，失败 $failCount（共 ${coverlessSongs.size} 首无封面）"
                            else -> "扫描所有歌曲，找出无封面曲目"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (scanState == "scanning" || scanState == "fetching") {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

        // ---- 进度条 ----
        if (scanState == "scanning" || scanState == "fetching") {
            Spacer(Modifier.height(12.dp))
            val progress = when (scanState) {
                "scanning" -> if (totalSongs > 0) checkedCount.toFloat() / totalSongs else 0f
                "fetching" -> if (coverlessSongs.size > 0) fetchProgress.toFloat() / coverlessSongs.size else 0f
                else -> 0f
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // ---- 执行结果摘要 ----
        if (scanState == "done") {
            val allNoCover = coverlessSongs.size
            val resultText = if (allNoCover == 0) {
                "无需获取，所有歌曲都已有封面"
            } else {
                "共 $allNoCover 首无封面，成功获取 $successCount 首" +
                    if (failCount > 0) ", 失败 $failCount 首" else ""
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (failCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                    .padding(horizontal = 12.dp, vertical = 20.dp)
            ) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failCount > 0) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- 扫描结果 ----
        if (scanState == "done") {
            if (coverlessSongs.isNotEmpty()) {
                SectionTitle("无封面的歌曲 (${coverlessSongs.size})")
                coverlessSongs.forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = song.artist.ifEmpty { "未知" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            } else if (totalSongs > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 20.dp)
                ) {
                Text(
                    text = "所有 $totalSongs 首歌曲都已有封面！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** 设置主页面分组标题 */
@Composable
private fun SettingsGroupHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 使用说明分组标题 */
@Composable
private fun GuideSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** 使用说明卡片组件 */
@Composable
private fun GuideSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            items.forEachIndexed { index, item ->
                Text(
                    text = "•$item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                if (index < items.size - 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

/** 使用说明分割线 */
@Composable
private fun GuideDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (!enabled) Modifier.alpha(0.45f) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = if (enabled) onCheckedChange else { _ -> },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

/** 尝试打开系统均衡器面板 */
private fun openSystemEqualizer(context: android.content.Context, audioSessionId: Int) {
    Log.d("Equalizer", "openSystemEqualizer: audioSessionId=$audioSessionId")

    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        // 传入有效的音频会话ID（>0），否则不传
        if (audioSessionId > 0) {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        }
    }

    try {
        if (context is android.app.Activity) {
            // 官方文档要求使用 startActivityForResult
            context.startActivityForResult(intent, 100)
        } else {
            context.startActivity(intent)
        }
        Log.d("Equalizer", "startActivity 成功")
    } catch (e: Exception) {
        Log.e("Equalizer", "startActivity 失败: ${e::class.simpleName}: ${e.message}")
        android.widget.Toast.makeText(context, "设备无系统音效面板", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 获取来源的显示名称 */
private fun sourceName(source: String): String = when (source) {
    "酷狗音乐" -> "酷狗音乐"
    "QQ音乐" -> "QQ音乐"
    "酷我音乐" -> "酷我音乐"
    "网易云音乐" -> "网易云音乐"
    "LRCLIB" -> "LRCLIB"
    else -> source
}

/** 获取来源的描述文本 */
private fun sourceDesc(source: String): String = when (source) {
    "酷狗音乐" -> "歌词"
    "QQ音乐" -> "歌词"
    "酷我音乐" -> "歌词"
    "网易云音乐" -> "歌词"
    "LRCLIB" -> "国际"
    else -> ""
}

/** 检查歌曲是否有专辑封面（优先 MediaStore，兜底侧边 .jpg 文件） */
private fun checkSongHasAlbumArt(song: Song, context: android.content.Context): Boolean {
    return song.resolveAlbumArtUri(context) != null
}

@Composable
private fun ThemePage(
    padding: PaddingValues,
    currentThemeId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    playerViewModel: PlayerViewModel,
    onShowCustomTheme: () -> Unit = {}
) {
    val isCustomSelected = currentThemeId == com.example.yyplayer.data.repository.ThemeRepository.CUSTOM_THEME_ID
    val customTheme = remember { kotlinx.coroutines.runBlocking { playerViewModel.themeRepository.getCustomColors() } }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("适用于 1:1 模式下的复古 Walkman 播放器", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        PlayerTheme.PRESETS.forEach { theme ->
            val isSelected = currentThemeId == theme.id
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { scope.launch { playerViewModel.themeRepository.setTheme(theme.id) } }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(theme.primaryColor))
                    Box(Modifier.size(24.dp).clip(CircleShape).background(theme.accentColor))
                    Box(Modifier.size(24.dp).clip(CircleShape).background(theme.buttonColor))
                }
                Spacer(Modifier.width(16.dp))
                Text(theme.name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                if (isSelected) Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
        }
        // ---- 自定义主题入口 ----
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
        Text("自定义", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (isCustomSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onShowCustomTheme() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(customTheme.primaryColor))
                Box(Modifier.size(24.dp).clip(CircleShape).background(customTheme.accentColor))
                Box(Modifier.size(24.dp).clip(CircleShape).background(customTheme.buttonColor))
            }
            Spacer(Modifier.width(16.dp))
            Text("自定义颜色", style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (isCustomSelected) Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            else Text("配置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ====== 自定义主题颜色页 ======

private val COLOR_PRESETS = listOf(
    // 红/粉系列
    Color(0xFFB71C1C) to "深红", Color(0xFFD32F2F) to "暗红", Color(0xFFF44336) to "红",
    Color(0xFFE91E63) to "玫红", Color(0xFFAD1457) to "深粉", Color(0xFFF06292) to "浅粉",
    // 紫系列
    Color(0xFF4A148C) to "深紫", Color(0xFF9C27B0) to "紫",   Color(0xFF7C4DFF) to "亮紫",
    Color(0xFF673AB7) to "暗紫", Color(0xFF9370DB) to "中紫",
    // 靛/蓝系列
    Color(0xFF1A237E) to "藏青", Color(0xFF3F51B5) to "靛",   Color(0xFF536DFE) to "亮靛",
    Color(0xFF1565C0) to "深蓝", Color(0xFF2196F3) to "蓝",   Color(0xFF03A9F4) to "浅蓝",
    Color(0xFF0277BD) to "钴蓝", Color(0xFF87CEEB) to "天蓝", Color(0xFF4682B4) to "钢蓝",
    // 青/绿系列
    Color(0xFF00BCD4) to "青",   Color(0xFF26C6DA) to "亮青", Color(0xFF00838F) to "暗青",
    Color(0xFF009688) to "青绿", Color(0xFF26A69A) to "亮绿", Color(0xFF00695C) to "墨绿",
    Color(0xFF1B5E20) to "深绿", Color(0xFF2E7D32) to "暗绿", Color(0xFF4CAF50) to "绿",
    Color(0xFF66BB6A) to "浅绿", Color(0xFF8BC34A) to "草绿", Color(0xFF689F38) to "橄榄绿",
    Color(0xFF00BFA5) to "薄荷", Color(0xFF2E8B57) to "海绿",
    // 黄/琥珀系列
    Color(0xFFFDD835) to "黄",   Color(0xFFFFEB3B) to "亮黄", Color(0xFFF9A825) to "暗黄",
    Color(0xFFFFC107) to "琥珀", Color(0xFFFFCA28) to "亮琥珀", Color(0xFFFFA000) to "深琥珀",
    // 橙系列
    Color(0xFFFF9800) to "橙",   Color(0xFFFB8C00) to "暗橙", Color(0xFFEF6C00) to "深橙",
    Color(0xFFFF5722) to "橘红", Color(0xFFE65100) to "赤橙", Color(0xFFBF360C) to "焦橙",
    Color(0xFFFF6F00) to "亮橙", Color(0xFFFF6347) to "番茄", Color(0xFFFA8072) to "三文鱼",
    // 棕/褐色系列
    Color(0xFF795548) to "棕",   Color(0xFF6D4C41) to "暗棕", Color(0xFF4E342E) to "深棕",
    Color(0xFF3E2723) to "咖啡", Color(0xFF8D6E63) to "褐",   Color(0xFFD2691E) to "巧克力",
    Color(0xFFBC8F8F) to "玫瑰棕",
    // 灰/蓝灰系列
    Color(0xFF607D8B) to "蓝灰", Color(0xFF546E7A) to "暗蓝灰", Color(0xFF455A64) to "深蓝灰",
    Color(0xFF9E9E9E) to "浅灰", Color(0xFFBDBDBD) to "灰",   Color(0xFF757575) to "中灰",
    Color(0xFF424242) to "暗灰", Color(0xFF333333) to "深灰", Color(0xFF212121) to "碳灰",
    // 黑白系列
    Color(0xFFFFFFFF) to "白",   Color(0xFFF5F5F5) to "烟白", Color(0xFFE0E0E0) to "银灰",
    Color(0xFFC0C0C0) to "银",   Color(0xFF000000) to "黑",
    // 金色
    Color(0xFFC9A84C) to "金",   Color(0xFFD4AF37) to "亮金", Color(0xFFB8860B) to "暗金",
    // 特殊色
    Color(0xFF6495ED) to "矢车菊", Color(0xFFFF6B6B) to "珊瑚", Color(0xFFCD5C5C) to "印度红",
    Color(0xFF6A5ACD) to "石板蓝", Color(0xFF001F3F) to "海军蓝", Color(0xFF722F37) to "酒红",
    Color(0xFF40E0D0) to "绿松", Color(0xFFE6E6FA) to "薰衣草", Color(0xFF800000) to "栗色",
    Color(0xFF808000) to "橄榄", Color(0xFFBDB76B) to "暗卡其"
)

private data class ColorRole(val label: String, val get: (PlayerTheme) -> Color, val set: (PlayerTheme, Color) -> PlayerTheme)

private val COLOR_ROLES = listOf(
    ColorRole("主色", { it.primaryColor }, { t, c -> t.copy(primaryColor = c) }),
    ColorRole("辅色", { it.accentColor }, { t, c -> t.copy(accentColor = c) }),
    ColorRole("背景色", { it.backgroundColor }, { t, c -> t.copy(backgroundColor = c) }),
    ColorRole("文字色", { it.textColor }, { t, c -> t.copy(textColor = c) }),
    ColorRole("按钮色", { it.buttonColor }, { t, c -> t.copy(buttonColor = c) }),
    ColorRole("次要色", { it.secondaryColor }, { t, c -> t.copy(secondaryColor = c) })
)

@Composable
private fun CustomThemePage(
    padding: PaddingValues,
    scope: kotlinx.coroutines.CoroutineScope,
    playerViewModel: PlayerViewModel
) {
    var currentCustomTheme by remember { mutableStateOf(kotlinx.coroutines.runBlocking { playerViewModel.themeRepository.getCustomColors() }) }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)
        ) {
            Text("选择各颜色角色对应的颜色，自定义专属主题配色。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        // ---- 预览 ----
        SectionTitle("预览")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(currentCustomTheme.backgroundColor).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(currentCustomTheme.primaryColor))
                Box(Modifier.size(28.dp).clip(CircleShape).background(currentCustomTheme.accentColor))
                Box(Modifier.size(28.dp).clip(CircleShape).background(currentCustomTheme.buttonColor))
            }
            Spacer(Modifier.width(16.dp))
            Text("预览文本", style = MaterialTheme.typography.bodyLarge,
                color = currentCustomTheme.textColor, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(currentCustomTheme.primaryColor))
        }
        Spacer(Modifier.height(20.dp))
        // ---- 各颜色角色 ----
        COLOR_ROLES.forEach { role ->
            val currentColor = role.get(currentCustomTheme)
            SectionTitle(role.label)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                COLOR_PRESETS.forEach { (color, _) ->
                    val isPicked = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isPicked) 3.dp else 0.dp,
                                color = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                            .clickable {
                                currentCustomTheme = role.set(currentCustomTheme, color)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPicked) {
                            Text("✓", color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        // ---- 保存按钮 ----
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    scope.launch {
                        playerViewModel.themeRepository.saveCustomColors(currentCustomTheme)
                        playerViewModel.themeRepository.setTheme(com.example.yyplayer.data.repository.ThemeRepository.CUSTOM_THEME_ID)
                    }
                }.padding(14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("保存并应用", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** 计算颜色亮度 (0~1)，用于判断应使用黑色还是白色文字 */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

@Composable
private fun GuidePage(padding: PaddingValues) {
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(R.drawable.mumu), contentDescription = null,
                    modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(8.dp))
                Text("MuMu Player 使用说明", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("方屏安卓设备音乐播放器，支持本地音乐播放、歌词显示、专辑封面获取、锁屏控制等功能。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.LibraryMusic, title = "音乐")
        GuideSectionCard(icon = Icons.Default.LibraryMusic, title = "音乐库", items = listOf(
            "歌曲按文件夹分组显示，点击文件夹进入查看内部歌曲", "顶栏搜索图标可按歌曲名/歌手搜索",
            "长按文件夹可将其隐藏，重新扫描可恢复", "自动记住最后打开的文件夹，回到音乐库时自动定位到当前播放歌曲",
            "红心收藏文件夹始终置顶", "文件夹中歌曲显示排序序号，当前播放的歌曲和文件夹有播放图标标记"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.LibraryMusic, title = "支持的文件类型", items = listOf(
            "MP3、FLAC、WAV、AAC、OGG、M4A、WMA、APE 等常见音频格式", "系统自动扫描设备中被 MediaStore 索引的音频文件"))
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.PlayArrow, title = "播放与歌词")
       GuideSectionCard(icon = Icons.Default.PlayArrow, title = "播放页面", items = listOf(
            "点击歌曲自动进入播放页，底部进度条可拖拽拖动", "右上角按钮进入全屏模式，点击屏幕中央切换封面与歌词", "全屏模式下右侧有锁定按钮，防止误触"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Subtitles, title = "歌词显示", items = listOf(
            "歌词页面右侧时钟图标按钮可打开偏移调节面板",
            "\"＋\"号表示歌词整体滞后播放（歌词显示慢于声音），每按一次延后 0.5 秒",
            "\"－\"号表示歌词整体提前播放（歌词显示快于声音），每按一次提前 0.5 秒",
            "数值例如 +2.0s 表示歌词滞后 2 秒（需加偏移使歌词提前显示）",
            "数值例如 -1.0s 表示歌词提前 1 秒（需减偏移使歌词延后显示）",
            "调节后点击「保存」将偏移量写入伴生 .lrc 文件，永久生效",
            "若在设置中开启了「歌词写入文件」，保存时也会更新音频元数据",
            "设置页「歌词字体大小」可单独调整歌词显示字号（5 档可选）",
            "设置页「歌词与封面管理」可开关歌词写入文件、加载内嵌歌词"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.MusicNote, title = "歌词获取", items = listOf(
            "自动联网搜索歌词（LRCLIB → QQ → 网易云 → 酷我 → 酷狗）", "获取后自动缓存，设置页可勾选启用的歌词来源，至少保留一个", "支持加载歌曲文件内嵌的歌词（ID3 USLT / FLAC 内嵌歌词）"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Album, title = "专辑封面", items = listOf(
            "自动从 iTunes、Deezer、MusicBrainz、网易云、QQ、酷狗 获取封面", "获取到的封面自动写入歌曲元数据，同时保存为侧边 .jpg 文件",
            "设置页可批量扫描无封面歌曲，逐个从网络获取并写入封面", "设置页「封面来源管理」可勾选启用的封面获取源"))
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.Repeat, title = "播放控制")
        GuideSectionCard(icon = Icons.Default.Repeat, title = "播放模式", items = listOf("顺序播放 → 文件夹循环 → 单曲循环 → 随机播放"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.FastForward, title = "倍速播放", items = listOf(
            "正常/全屏播放页面左侧均有倍速按钮", "可选速度：0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 1.75x / 2.0x", "退出应用后自动恢复为 1.0x"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Favorite, title = "红心收藏", items = listOf(
            "每首歌曲右侧有红心按钮，点击后歌曲出现在音乐库红心收藏文件夹", "从红心收藏文件夹播放时，歌曲在该列表内循环"))
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.Lock, title = "锁屏与通知")
        GuideSectionCard(icon = Icons.Default.Lock, title = "锁屏控制", items = listOf(
            "设置页开启「锁屏控件」后，屏幕亮起时自动弹出锁屏控制界面", "支持上一首/下一首/暂停播放/进度拖动",
            "锁屏页面底部显示当前播放歌词", "在设置页可单独关闭「显示锁屏按钮」"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Notifications, title = "通知栏", items = listOf(
            "播放时显示通知栏控制面板，包含进度条、歌曲信息及播放控制按钮",
            "暂停时通知栏保留，显示暂停状态，可继续通过通知栏控制",
            "通知栏右侧有红心收藏按钮，支持开关切换"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Lock, title = "全屏屏幕锁", items = listOf(
            "全屏播放页和全屏歌词页右侧中间有半透明锁定按钮", "点击后锁定屏幕触控，避免误触进度条、播放/暂停等控件", "再次点击锁定按钮即可解锁屏幕"))
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.Gamepad, title = "手柄操作")
        GuideSectionCard(icon = Icons.Default.Gamepad, title = "手柄操作", items = listOf(
            "方向键 ↑/↓ = 选择文件夹/歌曲", "方向键 → 或 A 键 = 确认（进入文件夹 / 播放歌曲）",
            "方向键 ← 或 B 键 = 返回上级", "Select = 暂停/播放", "X = 音量+，Y = 音量-",
            "双击 L1 = 上一首，双击 R1 = 下一首", "L + R 同时按下 = 暂停/播放", "锁屏时仍可使用手柄操作"))
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.PlayArrow, title = "启动")
        GuideSectionCard(icon = Icons.Default.PlayArrow, title = "启动", items = listOf(
            "启动时显示加载页面，提示「正在加载页面」", "设置页开启「启动后自动播放」后，歌单不为空时自动播放第一首"))
        Spacer(Modifier.height(24.dp))
        GuideSectionHeader(icon = Icons.Default.Settings, title = "设置")
        GuideSectionCard(icon = Icons.Default.Settings, title = "设置", items = listOf(
            "音乐设置：重新扫描歌曲、手动扫描目录、NAS 网络音乐、自动扫描开关、跳过歌曲过少目录",
            "系统设置：锁屏控件、锁屏按钮、通知栏控件、音量归零暂停、启动后自动播放、系统均衡器、主题切换（含自定义颜色）、屏幕比例（自适应/1:1/4:3/16:9/8:7）、清空缓存",
            "歌词与封面：歌词来源管理、封面来源管理、歌词写入与内嵌歌词开关、歌词字体大小（5 档）、批量获取封面",
            "关于：使用说明、关于应用"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.ColorLens, title = "主题设置", items = listOf(
            "在 1:1 模式下可选多种复古 Walkman 播放器配色", "设置页切换即时生效", "支持自定义主题颜色（主色/辅色/背景色/文字色/按钮色/次要色）"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Equalizer, title = "均衡器", items = listOf("在设置页点击「打开系统均衡器」进入设备原生音效调节面板"))
        Spacer(Modifier.height(8.dp))
        GuideSectionCard(icon = Icons.Default.Cloud, title = "NAS 网络音乐", items = listOf(
            "在设置页开启后输入服务器地址即可扫描远程音乐文件", "扫描后的歌曲出现在音乐库的「NAS 网络」文件夹中"))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutPage(padding: PaddingValues, context: android.content.Context) {
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Image(painter = painterResource(R.drawable.mumu), contentDescription = null,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("MuMu Player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        val appVersion = remember {
            try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知" }
            catch (_: Exception) { "未知" }
        }
        Text("v$appVersion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        listOf("作者" to "沐木SD (Hua Miller)", "歌词来源" to "LRCLIB / QQ / 网易云 / 酷我 / 酷狗", "封面来源" to "iTunes / Deezer / MusicBrainz / 网易云 / QQ / 酷狗").forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.7f))
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
        // 鸣谢
        Text("特别鸣谢", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
        ) {
            Text("感谢协助测试的网友们：", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text("1. 眼明手快身体好", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text("2. 熊发飙", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text("3. 网忙忙", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("以及没提到姓名的网友们", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Text("感谢使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun LyricsSourcePage(padding: PaddingValues, sourceOrderManager: SourceOrderManager, refreshToggle: Boolean) {
    val currentEnabled = remember { mutableStateOf(sourceOrderManager.getEnabledSources()) }
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("勾选启用的歌词来源，搜索顺序固定如下。如果某个来源获取失败，会自动切换到下一个。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        SourceOrderManager.DEFAULT_ORDER.forEachIndexed { index, source ->
            val isEnabled = source in currentEnabled.value
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { sourceOrderManager.setEnabled(source, !isEnabled); currentEnabled.value = sourceOrderManager.getEnabledSources() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(4.dp))
                Spacer(Modifier.width(12.dp))
                Text(sourceName(source), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(sourceDesc(source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                Checkbox(checked = isEnabled, onCheckedChange = { sourceOrderManager.setEnabled(source, it); currentEnabled.value = sourceOrderManager.getEnabledSources() })
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
            Text("💡 LRCLIB 是国际歌词服务，覆盖中英文歌曲的带时间轴歌词，作为兜底来源推荐开启。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumArtSourcePage(padding: PaddingValues, context: android.content.Context) {
    val albumArtSourceManager = remember { AlbumArtSourceManager(context) }
    val enabledSources = remember { mutableStateOf(albumArtSourceManager.getEnabledSources()) }
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("勾选启用的封面获取源，搜索顺序固定如下。如果某个来源获取失败，会自动切换到下一个。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        AlbumArtSourceManager.DEFAULT_ORDER.forEachIndexed { index, source ->
            val isEnabled = source in enabledSources.value
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { albumArtSourceManager.setEnabled(source, !isEnabled); enabledSources.value = albumArtSourceManager.getEnabledSources() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(4.dp))
                Spacer(Modifier.width(12.dp))
                Text(sourceName(source), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Checkbox(checked = isEnabled, onCheckedChange = { albumArtSourceManager.setEnabled(source, it); enabledSources.value = albumArtSourceManager.getEnabledSources() })
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
            Text("封面来源包括 iTunes、Deezer、MusicBrainz、网易云音乐、QQ音乐、酷狗音乐。" +
                    "\nDeezer 和 iTunes 对英文歌曲覆盖较好，网易云和 QQ 对中文歌曲覆盖较好。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LyricsCoverManagePage(padding: PaddingValues) {
    val context = LocalContext.current
    val lyricsCoverSettings = remember { com.example.yyplayer.data.repository.LyricsCoverSettings(context) }
    var writeLyrics by remember { mutableStateOf(lyricsCoverSettings.isWriteLyricsToFile()) }
    var loadEmbeddedLyrics by remember { mutableStateOf(lyricsCoverSettings.isLoadEmbeddedLyrics()) }
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle("歌词")
        SettingsToggleRow(title = "歌词写入文件", subtitle = "开启后将歌词写入歌曲所在目录（伴生 .lrc 文件）\n关闭后仅保存在应用缓存中",
            isChecked = writeLyrics, onCheckedChange = { writeLyrics = it; lyricsCoverSettings.setWriteLyricsToFile(it) })
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(title = "加载歌曲本身的歌词", subtitle = "开启后尝试读取歌曲文件内嵌的歌词标签\n（如 ID3 USLT / FLAC 内嵌歌词）",
            isChecked = loadEmbeddedLyrics, onCheckedChange = { loadEmbeddedLyrics = it; lyricsCoverSettings.setLoadEmbeddedLyrics(it) })
       Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
            Text("封面获取后自动保存为侧边 .jpg 文件（如 song.mp3.jpg），无需额外设置。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ====== 音乐设置子项 ======
@Composable
private fun MusicSettingsSection(
    songs: List<Song>,
    isScanning: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    libraryViewModel: LibraryViewModel,
    onShowNasPage: () -> Unit,
    onShowManualScanPage: () -> Unit
) {
    var autoScanEnabled by remember { mutableStateOf(com.example.yyplayer.ui.viewmodel.LibraryViewModel.isAutoScanEnabled(context)) }
    var minSongs by remember { mutableIntStateOf(com.example.yyplayer.ui.viewmodel.LibraryViewModel.getMinSongsThreshold(context)) }

    SettingsGroupHeader(icon = Icons.Default.LibraryMusic, title = "音乐设置")

    SectionTitle("重新扫描")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(enabled = !isScanning) {
            Log.d(SETTINGS_TAG, "点击: 重新扫描歌曲")
            scope.launch { libraryViewModel.getRepository().forceRefresh(); libraryViewModel.refreshMusic() }
            com.example.yyplayer.ui.viewmodel.LibraryViewModel.setAutoScanEnabled(context, true)
        }
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Refresh, null, Modifier.size(24.dp),
            tint = if (isScanning) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isScanning) "正在扫描..." else "重新扫描歌曲", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                color = if (isScanning) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
            Text("共 ${songs.size} 首歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("NAS")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: NAS 网络音乐")
            onShowNasPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Cloud, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("网络音乐", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("连接 NAS / 局域网服务器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("手动扫描")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 手动扫描")
            onShowManualScanPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Refresh, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("手动扫描", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("清空结果、选择扫描位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("自动扫描")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Refresh, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("进入音乐库时自动扫描", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(if (autoScanEnabled) "打开音乐库时将检查并扫描新歌曲" else "已关闭，需手动触发扫描",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = autoScanEnabled, onCheckedChange = {
            autoScanEnabled = it
            com.example.yyplayer.ui.viewmodel.LibraryViewModel.setAutoScanEnabled(context, it)
        },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("扫描限制")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Column {
            Text(
                text = "跳过歌曲过少的目录",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (minSongs <= 0) "扫描所有目录（不限）"
                       else "跳过歌曲数量少于 $minSongs 首的目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 2, 5, 10, 20, 50).forEach { value ->
                    val isSelected = minSongs == value
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            minSongs = value
                            com.example.yyplayer.ui.viewmodel.LibraryViewModel.setMinSongsThreshold(context, value)
                            libraryViewModel.recomputeFolderGroups()
                        },
                        label = { Text(text = if (value == 0) "不限" else "$value 首", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }
    }
}

// ====== 系统设置子项 ======
@Composable
private fun SystemSettingsSection(
    currentThemeId: String,
    audioSessionId: Int,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    playerViewModel: PlayerViewModel,
    onShowThemePage: () -> Unit,
    onShowRatioPage: () -> Unit
) {
    val stateManager = remember { PlayerStateManager(context) }
    var lockScreenEnabled by remember { mutableStateOf(stateManager.isLockScreenEnabled()) }
    var showLockButton by remember { mutableStateOf(stateManager.isShowLockButton()) }
    var notifEnabled by remember { mutableStateOf(stateManager.isNotificationEnabled()) }
    var gamepadEnabled by remember { mutableStateOf(stateManager.isGamepadEnabled()) }

    SettingsGroupHeader(icon = Icons.Default.Settings, title = "系统设置")

    SectionTitle("锁屏")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("锁屏控制", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(if (lockScreenEnabled) "锁屏时显示播放控制" else "已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = lockScreenEnabled, onCheckedChange = { lockScreenEnabled = it; stateManager.setLockScreenEnabled(it) },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(4.dp))

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("显示锁屏按钮", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("全屏播放时右侧是否显示锁定屏幕按钮", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = showLockButton, onCheckedChange = { showLockButton = it; stateManager.setShowLockButton(it) },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(4.dp))

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("通知栏控件", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("显示播放控制面板（含封面、进度和按钮）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = notifEnabled, onCheckedChange = {
            notifEnabled = it
            stateManager.setNotificationEnabled(it)
            MusicService.requestRefresh(context)
        },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(4.dp))

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("音量归零暂停", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("系统音量降到0时自动暂停播放", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var pauseOnVolumeZero by remember { mutableStateOf(stateManager.isPauseOnVolumeZero()) }
        Switch(checked = pauseOnVolumeZero, onCheckedChange = { pauseOnVolumeZero = it; stateManager.setPauseOnVolumeZero(it); playerViewModel.setPauseOnVolumeZero(it) },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(4.dp))

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("启动后自动播放", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("启动应用后自动继续播放上次的歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var autoPlay by remember { mutableStateOf(stateManager.isAutoPlayEnabled()) }
       Switch(checked = autoPlay, onCheckedChange = { autoPlay = it; stateManager.setAutoPlayEnabled(it) },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(4.dp))

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("游戏手柄切歌", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(if (gamepadEnabled) "L1/R1 双击切歌，Select 暂停/播放" else "已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = gamepadEnabled, onCheckedChange = { gamepadEnabled = it; stateManager.setGamepadEnabled(it) },
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("均衡器")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 打开系统均衡器")
            openSystemEqualizer(context, audioSessionId)
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Equalizer, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("打开系统均衡器", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("使用设备自带的均衡器调节音效", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("主题")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 主题设置")
            onShowThemePage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ColorLens, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("主题设置", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("当前: ${if (currentThemeId == com.example.yyplayer.data.repository.ThemeRepository.CUSTOM_THEME_ID) "自定义" else PlayerTheme.getById(currentThemeId).name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("屏幕比例")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 屏幕比例")
            onShowRatioPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Settings, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("屏幕比例", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("自适应 / 1:1 / 4:3 / 16:9 / 8:7", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("设置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("播放器竖屏模式")
    val portraitMode by playerViewModel.portraitMode.collectAsState()
    val autoRotate by playerViewModel.autoRotate.collectAsState()
    SettingsToggleRow(
        title = "启用竖屏模式",
        subtitle = "竖屏设备上的播放器将使用纵向排布布局\n关闭后恢复横屏排布",
        isChecked = portraitMode,
        enabled = !autoRotate,
        onCheckedChange = { playerViewModel.setPortraitMode(it) }
    )
    Spacer(Modifier.height(4.dp))
    SettingsToggleRow(
        title = "自动识别方向",
        subtitle = "开启后根据屏幕方向自动切换横竖屏布局\n启用后将关闭上方竖屏模式开关",
        isChecked = autoRotate,
        onCheckedChange = { playerViewModel.setAutoRotate(it) }
    )

    Spacer(Modifier.height(12.dp))
    SectionTitle("缓存")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 清空缓存")
            scope.launch(Dispatchers.IO) {
                try {
                    context.cacheDir.listFiles()?.forEach { file ->
                        if (file.isDirectory) file.deleteRecursively()
                        else file.delete()
                    }
                } catch (_: Exception) { }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "缓存已清空", Toast.LENGTH_SHORT).show()
                }
            }
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Delete, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("清空缓存", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("清除所有歌词缓存、封面缓存和应用缓存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("清理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

// ====== 歌词与封面子项 ======
@Composable
private fun LyricsCoverSection(
    enabledSources: List<String>,
    onShowLyricsPage: () -> Unit,
    onShowAlbumArtPage: () -> Unit,
    onShowLyricsCoverPage: () -> Unit,
    onShowBatchCoverPage: () -> Unit,
    onShowLyricsFontSizePage: () -> Unit
) {
    SettingsGroupHeader(icon = Icons.Default.Subtitles, title = "歌词与封面")

    SectionTitle("歌词来源")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 歌词来源")
            onShowLyricsPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("歌词来源", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("已启用: ${enabledSources.size} 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("封面来源")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 封面来源")
            onShowAlbumArtPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Album, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("封面来源", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("管理封面获取源的启用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("歌词管理")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 歌词/封面管理")
            onShowLyricsCoverPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("歌词/封面管理", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("写入方式、内嵌歌词等", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("获取封面")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 批量获取封面")
            onShowBatchCoverPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Album, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("批量获取封面", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("检查所有歌曲，为无封面歌曲逐个获取并写入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("开始", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("歌词字体")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 歌词字体")
            onShowLyricsFontSizePage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Subtitles, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("歌词字体大小", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("自定义歌词显示的大小", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("设置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

// ====== 关于子项 ======
@Composable
private fun AboutSection(
    onShowGuidePage: () -> Unit,
    onShowAboutPage: () -> Unit
) {
    SettingsGroupHeader(icon = Icons.Default.Info, title = "关于")

    SectionTitle("说明")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 操作指南")
            onShowGuidePage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("使用说明", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("查看软件功能及操作方法", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("关于应用")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            Log.d(SETTINGS_TAG, "点击: 关于应用")
            onShowAboutPage()
        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("关于应用", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("版本信息、作者及版权", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}


@Composable
private fun NasPage(padding: PaddingValues, libraryViewModel: LibraryViewModel) {
    val context = LocalContext.current
    val nasSettings = remember { com.example.yyplayer.data.repository.NasSettings(context) }
    var nasEnabled by remember { mutableStateOf(nasSettings.isEnabled()) }
    var serverUrl by remember { mutableStateOf(nasSettings.getServerUrl()) }
    var username by remember { mutableStateOf(nasSettings.getUsername()) }
    var password by remember { mutableStateOf(nasSettings.getPassword()) }
    val isNasScanning by libraryViewModel.isNasScanning.collectAsState()
    val nasError by libraryViewModel.nasError.collectAsState()
    val nasSongs by libraryViewModel.nasSongs.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
        SettingsToggleRow(title = "启用 NAS 网络音乐", subtitle = "开启后在音乐库「红心收藏」下方显示 NAS 文件夹\n通过 HTTP 协议扫描局域网内的音乐文件",
            isChecked = nasEnabled, onCheckedChange = { nasEnabled = it; nasSettings.setEnabled(it) })
        Spacer(Modifier.height(20.dp))
        SectionTitle("服务器地址")
        OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it; nasSettings.setServerUrl(it) },
            label = { Text("例如: http://192.168.1.100/music") }, placeholder = { Text("http://...") },
            enabled = nasEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(16.dp))
        SectionTitle("认证信息（可选）")
        OutlinedTextField(value = username, onValueChange = { username = it; nasSettings.setUsername(it) },
            label = { Text("用户名") }, enabled = nasEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; nasSettings.setPassword(it) },
            label = { Text("密码") }, enabled = nasEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Spacer(Modifier.height(20.dp))
        SectionTitle("操作")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = nasEnabled && !isNasScanning && serverUrl.isNotBlank()) { libraryViewModel.scanNas() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (isNasScanning) CircularProgressIndicator(Modifier.size(24.dp))
            else Icon(Icons.Default.Refresh, null, Modifier.size(24.dp),
                tint = if (nasEnabled && serverUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isNasScanning) "正在扫描 NAS..." else "扫描 NAS 服务器", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    color = if (nasEnabled && serverUrl.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text(if (nasSongs.isNotEmpty()) "已发现 ${nasSongs.size} 首歌曲" else "点击从服务器扫描音乐文件",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (nasError != null) { Spacer(Modifier.height(8.dp)); Text(nasError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        if (nasSongs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { libraryViewModel.clearNas() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Text("清空 NAS 缓存", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
            Text("使用说明：\n\n1. 将音乐文件放在 NAS 的 HTTP 服务目录中\n   （如 Apache/Nginx/简单的文件服务器）\n\n2. 在上方输入完整的 HTTP 地址\n   例如: http://192.168.1.100/music/\n\n3. 如果服务器需要认证，填写用户名和密码\n\n4. 点击「扫描 NAS 服务器」开始扫描\n\n5. 在音乐库的「NAS 网络」文件夹中访问\n\n支持的格式：MP3 / FLAC / WAV / AAC / OGG / M4A / WMA / APE\n\n💡 文件名格式为「艺术家 - 标题.扩展名」时，会自动识别艺术家和标题",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatioPage(padding: PaddingValues, playerViewModel: PlayerViewModel) {
    val currentRatio by playerViewModel.screenRatio.collectAsState()
    val adaptiveEnabled by playerViewModel.adaptiveEnabled.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
        SettingsToggleRow(title = "自适应", subtitle = "开启后内容自动填满屏幕，关闭后按选定比例居中显示",
            isChecked = adaptiveEnabled, onCheckedChange = { playerViewModel.setAdaptiveEnabled(it) })
        Spacer(Modifier.height(20.dp))
        SectionTitle("屏幕比例")
        if (adaptiveEnabled) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp)) {
                Text("自适应开启时，内容填满整个屏幕。关闭「自适应」后即可选择显示比例。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ScreenRatio.entries.forEach { ratio ->
                val isSelected = currentRatio.id == ratio.id
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { playerViewModel.setScreenRatio(ratio) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center) {
                        Text(ratio.id, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(ratio.displayName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (isSelected) Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
            }
            // 封面大小占比滑块
            Spacer(Modifier.height(12.dp))
            SectionTitle("封面大小占比")
            val ratioId = currentRatio.id
            val currentCoverPercent = remember(ratioId) { mutableFloatStateOf(playerViewModel.getCoverSizePercent(ratioId)) }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${currentRatio.id} 短边 × ${(currentCoverPercent.floatValue * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = currentCoverPercent.floatValue,
                        onValueChange = { currentCoverPercent.floatValue = (it * 20).toInt() / 20f },
                        onValueChangeFinished = { playerViewModel.setCoverSizePercent(ratioId, currentCoverPercent.floatValue) },
                        valueRange = 0.15f..0.85f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("较小", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
                }
            }
        }
    }
}

@Composable
private fun MainSettingsPage(
    padding: PaddingValues,
    mainScrollState: androidx.compose.foundation.ScrollState,
    songs: List<Song>,
    isScanning: Boolean,
    currentThemeId: String,
    enabledSources: List<String>,
    audioSessionId: Int,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onShowNasPage: () -> Unit,
    onShowThemePage: () -> Unit,
    onShowGuidePage: () -> Unit,
    onShowAboutPage: () -> Unit,
    onShowLyricsPage: () -> Unit,
    onShowLyricsCoverPage: () -> Unit,
    onShowRatioPage: () -> Unit,
    onShowBatchCoverPage: () -> Unit,
    onShowManualScanPage: () -> Unit,
    onShowAlbumArtPage: () -> Unit,
    onShowLyricsFontSizePage: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(mainScrollState).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        // ===== 音乐设置 =====
        MusicSettingsSection(
            songs = songs,
            isScanning = isScanning,
            context = context,
            scope = scope,
            libraryViewModel = libraryViewModel,
            onShowNasPage = onShowNasPage,
            onShowManualScanPage = onShowManualScanPage
        )

        // ===== 系统设置 =====
        SystemSettingsSection(
            currentThemeId = currentThemeId,
            audioSessionId = audioSessionId,
            context = context,
            scope = scope,
            playerViewModel = playerViewModel,
            onShowThemePage = onShowThemePage,
            onShowRatioPage = onShowRatioPage
        )

        // ===== 歌词与封面 =====
        LyricsCoverSection(
            enabledSources = enabledSources,
            onShowLyricsPage = onShowLyricsPage,
            onShowAlbumArtPage = onShowAlbumArtPage,
            onShowLyricsCoverPage = onShowLyricsCoverPage,
            onShowBatchCoverPage = onShowBatchCoverPage,
            onShowLyricsFontSizePage = onShowLyricsFontSizePage
        )

        // ===== 关于 =====
        AboutSection(
            onShowGuidePage = onShowGuidePage,
            onShowAboutPage = onShowAboutPage
        )
    }
}

@Composable
private fun LyricsFontSizePage(
    padding: PaddingValues,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val lyricsFontSizeSettings = remember { LyricsFontSizeSettings(context) }
    val currentScale by playerViewModel.lyricsFontScale.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "调整歌词在播放页面和全屏模式下的显示大小。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SectionTitle("当前: ${LyricsFontSizeSettings.scaleDisplayName(currentScale)}")

        LyricsFontSizeSettings.SCALE_OPTIONS.forEach { scale ->
            val isSelected = currentScale == scale
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { playerViewModel.setLyricsFontScale(scale) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 预览文本
                Text(
                    text = LyricsFontSizeSettings.scaleDisplayName(scale),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = (16 * scale).sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Text(
                        text = "使用中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // 说明框
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            Text(
                text = "💡 调整后的字体大小会立即生效，你可以返回播放页面查看效果。" +
                        "\n小：适合屏幕较小或歌词信息密集的场景" +
                        "\n默认：标准显示大小" +
                        "\n大/特大/超大：适合大屏幕或视力需要辅助的场景",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


