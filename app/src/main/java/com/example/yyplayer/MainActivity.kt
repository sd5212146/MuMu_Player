package com.example.yyplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yyplayer.data.model.PlayerTheme
import com.example.yyplayer.data.repository.ThemeRepository
import com.example.yyplayer.ui.screens.LibraryScreen
import com.example.yyplayer.ui.screens.NowPlayingScreen
import com.example.yyplayer.ui.screens.SettingsScreen
import com.example.yyplayer.ui.screens.SplashScreen
import com.example.yyplayer.ui.theme.LocalWalkmanTheme
import com.example.yyplayer.ui.theme.YYplayerTheme
import com.example.yyplayer.data.repository.PlayerStateManager
import com.example.yyplayer.ui.viewmodel.EqualizerViewModel
import com.example.yyplayer.ui.viewmodel.LibraryViewModel
import com.example.yyplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var playerViewModel: PlayerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i("Startup_Timing", "┌── Activity.onCreate START")
        val createT0 = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        // 启用边缘到边缘显示
        enableEdgeToEdge()

        // 在 setContent 之前同步读取已保存的主题，让启动页与用户所选主题风格统一
        val initialTheme = kotlinx.coroutines.runBlocking {
            try {
                ThemeRepository(this@MainActivity).getCurrentTheme()
            } catch (_: Exception) { PlayerTheme.getById("walkman_blackgold") }
        }

        setContent {
            android.util.Log.i("Startup_Timing", "setContent 执行, afterCreate=" + (System.currentTimeMillis() - createT0) + "ms")
            val savedTheme = remember { initialTheme }

            var showSplash by remember { mutableStateOf(true) }

            // 3 秒后自动关闭闪屏
            LaunchedEffect(Unit) {
                delay(3000)
                android.util.Log.i("Startup_Timing", "闪屏关闭, afterCreate=" + (System.currentTimeMillis() - createT0) + "ms")
                showSplash = false
            }

            // ===== 闪屏覆盖层：splash 在顶层淡出，主界面在底层稳定存在 =====
            Box(modifier = Modifier.fillMaxSize()) {
                // 底层：主界面（仅一次，永不重建）
                if (!showSplash) {
                    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                    var permissionGranted by remember { mutableStateOf(
                        ContextCompat.checkSelfPermission(this@MainActivity, requiredPermission) == PackageManager.PERMISSION_GRANTED
                    ) }

                    if (!permissionGranted) {
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            permissionGranted = granted
                        }

                        PermissionScreen(
                            onRequestPermission = { permissionLauncher.launch(requiredPermission) }
                        )
                    } else {
                        // ------ ViewModel 初始化 ------
                        val pvm: PlayerViewModel = viewModel()
                        playerViewModel = pvm
                        val libraryViewModel: LibraryViewModel = viewModel()
                        val eqViewModel: EqualizerViewModel = viewModel()
                        val musicRepository = libraryViewModel.getRepository()
                        val walkmanTheme by pvm.themeRepository.currentTheme.collectAsState(initial = savedTheme)

                        pvm.setMusicRepository(musicRepository)

                        val mainContentT0 = System.currentTimeMillis()
                        android.util.Log.i("Startup_Timing", "开始渲染主界面, afterCreate=" + (System.currentTimeMillis() - createT0) + "ms")

                        YYplayerTheme(walkmanTheme = walkmanTheme) {
                            androidx.compose.runtime.key(Unit) {
                                MainContent(
                                    playerViewModel = pvm,
                                    libraryViewModel = libraryViewModel,
                                    equalizerViewModel = eqViewModel
                                )
                            }
                        }
                    }
                }

                // 顶层：splash 退出动画覆盖层
                if (showSplash) {
                    // === 闪屏期间提前创建 ViewModel，让歌曲数据/播放状态提前准备 ===
                    val preloadPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            Manifest.permission.READ_MEDIA_AUDIO
                        else
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                    if (preloadPermission == PackageManager.PERMISSION_GRANTED) {
                        // viewModel() 从 Activity 的 ViewModelStore 获取/创建实例，
                        // 闪屏结束后主界面再次调用 viewModel() 会返回同一实例（数据已就绪）
                        val prePvm: PlayerViewModel = viewModel()
                        playerViewModel = prePvm
                        val preLibraryVm: LibraryViewModel = viewModel()
                        val preEqVm: EqualizerViewModel = viewModel()
                        prePvm.setMusicRepository(preLibraryVm.getRepository())
                    }

                    val walkmanTheme = remember { savedTheme }
                    CompositionLocalProvider(com.example.yyplayer.ui.theme.LocalWalkmanTheme provides walkmanTheme) {
                        MaterialTheme(
                            colorScheme = com.example.yyplayer.ui.theme.createWalkmanColorScheme(walkmanTheme)
                        ) {
                            SplashScreen()
                        }
                    }

                    android.util.Log.i("Startup_Timing", "闪屏显示, afterCreate=" + (System.currentTimeMillis() - createT0) + "ms")
                }
            }
        }
    }

    /** 拦截游戏手柄按键 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vm = playerViewModel ?: return super.dispatchKeyEvent(event)

        // 方向键 + A/B 让 Compose 层处理（LibraryScreen 导航+确认/返回）
        if (com.example.yyplayer.player.GamepadHelper.isDpadKey(event.keyCode) ||
            com.example.yyplayer.player.GamepadHelper.isABButton(event.keyCode)
        ) {
            return super.dispatchKeyEvent(event)
        }

        // 用户关闭了游戏手柄控制时跳过按键处理
        val stateManager = PlayerStateManager(this)
        if (!stateManager.isGamepadEnabled()) return super.dispatchKeyEvent(event)

        // L1/R1 双击 + L+R 暂停
        if (com.example.yyplayer.player.GamepadHelper.handleLRKeyEvent(
                event,
                onPrevious = { vm.playPrevious() },
                onNext = { vm.playNext() },
                onPlayPause = { vm.togglePlayPause() }
            )
        ) {
            return true
        }

        // Select → 暂停/播放, X → 音量+, Y → 音量-
        if (com.example.yyplayer.player.GamepadHelper.handleGlobalKeyEvent(
                event,
                context = this,
                onPlayPause = { vm.togglePlayPause() }
            )
        ) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 应用被清理后台时关闭播放
        try {
            val intent = Intent(this, com.example.yyplayer.player.MusicService::class.java).apply {
                action = "com.example.yyplayer.action.STOP"
            }
            startService(intent)
        } catch (_: Exception) {}
    }
}

/** 权限申请界面 - 首次启动时显示 */
@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    val walkmanTheme = com.example.yyplayer.ui.theme.LocalWalkmanTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val appName = context.getString(com.example.yyplayer.R.string.app_name)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(walkmanTheme.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = walkmanTheme.textColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "需要音乐读取权限",
                style = MaterialTheme.typography.titleLarge,
                color = walkmanTheme.textColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$appName 需要访问您设备上的音乐文件才能构建音乐库。\n\n请点击下方按钮授予权限，或前往系统设置 → 应用 → $appName → 权限中开启。",
                style = MaterialTheme.typography.bodyMedium,
                color = walkmanTheme.textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = walkmanTheme.primaryColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("授予权限", color = Color.White)
            }
        }
    }
}

@Composable
fun MainContent(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    equalizerViewModel: EqualizerViewModel
) {
    android.util.Log.i("Startup_Timing", "┌── MainContent 首次 composition")
    var selectedTab by remember { mutableIntStateOf(if (playerViewModel.hasSavedPlayState()) 1 else 0) }
    var restored by remember { mutableStateOf(false) }
    var nowPlayingFirstRender by remember { mutableStateOf(true) }

    // 监听 selectedTab 和 restored 变化，用于调试重建问题
    val compositionTime = remember { System.currentTimeMillis() }
    var settledLogged by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab, restored) {
        android.util.Log.i("MainContent_State", "selectedTab=$selectedTab, restored=$restored")
        if (restored && !settledLogged) {
            settledLogged = true
            android.util.Log.i("Startup_Timing", "└── 初始状态稳定, 距composition=" + (System.currentTimeMillis() - compositionTime) + "ms")
        }
    }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val playMode by playerViewModel.playMode.collectAsState()
    val walkmanTheme = LocalWalkmanTheme.current

    // 根据 Walkman 主题自动调整系统状态栏/导航栏图标颜色
    // 深色背景（如黑金）使用浅色图标（白色），浅色背景使用深色图标（黑色）
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val bg = walkmanTheme.backgroundColor
        val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
        val useLightIcons = luminance < 0.5f  // 背景深色 → 需要浅色图标
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !useLightIcons
        controller.isAppearanceLightNavigationBars = !useLightIcons
    }

    // 定时更新进度（1秒一次，降低功耗）
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            playerViewModel.updateProgress()
            delay(1000)
        }
    }

    // 歌曲加载完成后尝试恢复上次播放状态
    // 延迟一帧切换 tab，让首帧的 LibraryScreen 先完成渲染和 JIT 编译
    // 避免 NowPlayingScreen 的 JIT 编译（1200+行）阻塞首帧导致 6s+ 卡顿
    val songs by libraryViewModel.songs.collectAsState()
    val songsReadyTime = remember { System.currentTimeMillis() }
    LaunchedEffect(songs) {
        if (songs.isNotEmpty() && !restored) {
            android.util.Log.i("Startup_Timing", "songs就绪, 数量=" + songs.size + ", 距composition=" + (System.currentTimeMillis() - songsReadyTime) + "ms")
            restored = true
            val repo = libraryViewModel.getRepository()
            if (playerViewModel.tryRestore(repo)) {
                android.util.Log.i("Startup_Timing", "播放状态恢复成功, tab→1")
                delay(1)
                android.util.Log.i("Startup_Timing", "延迟结束, 切换至播放页 tab")
                selectedTab = 1
            } else {
                android.util.Log.i("Startup_Timing", "无播放状态需要恢复")
            }
        }
    }

    // 检测播放器是否已被空闲释放（服务停止后播放器为空），自动静默恢复
    // 场景：息屏暂停→30s空闲释放→解锁→Activity恢复→自动恢复播放器
    val autoRestorePlaylist by playerViewModel.playlist.collectAsState()
    LaunchedEffect(restored) {
        if (restored && autoRestorePlaylist.isNotEmpty()) {
            playerViewModel.restorePlayerIfNeeded()
        }
    }

    var isFullScreen by remember { mutableStateOf(false) }
    val currentRatio by playerViewModel.screenRatio.collectAsState()
    val adaptiveEnabled by playerViewModel.adaptiveEnabled.collectAsState()
    val lyricsFontScale by playerViewModel.lyricsFontScale.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val portraitMode by playerViewModel.portraitMode.collectAsState()
    val autoRotate by playerViewModel.autoRotate.collectAsState()
    val playlist by playerViewModel.playlist.collectAsState()
    val currentIndex = playlist.indexOfFirst { it.id == currentSong?.id }.coerceAtLeast(0)
    val playlistSize = playlist.size
    val coverSizePercent by playerViewModel.coverSizePercent.collectAsState()
    val configuration = LocalConfiguration.current
    // 自动识别方向时根据屏幕宽高比决定，否则使用手动设置
    val effectivePortraitMode = if (autoRotate) {
        configuration.screenWidthDp < configuration.screenHeightDp
    } else {
        portraitMode
    }

    val navItems = remember {
        listOf(
            Icons.Default.LibraryMusic to "音乐库",
            Icons.Default.MusicNote to "播放",
            Icons.Default.Settings to "设置"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (adaptiveEnabled) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().aspectRatio(currentRatio.ratio)
            }
        ) {
            Scaffold(
                bottomBar = {
                    AnimatedVisibility(
                        visible = selectedTab != 1 || !isFullScreen,
                        enter = fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                    ) {
                        NavigationBar(
                            containerColor = walkmanTheme.backgroundColor,
                            contentColor = walkmanTheme.textColor,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.height(52.dp)
                        ) {
                            navItems.forEachIndexed { index, (icon, label) ->
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = null,
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = walkmanTheme.primaryColor,
                                        unselectedIconColor = walkmanTheme.textColor.copy(alpha = 0.55f),
                                        indicatorColor = walkmanTheme.primaryColor.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier.height(52.dp)
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing))
                                .togetherWith(fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
                                .using(SizeTransform(clip = true))
                        },
                        label = "tab_transition"
                    ) { tab ->
                        when (tab) {
                            0 -> {
                                android.util.Log.i("Startup_Timing", "渲染 LibraryScreen tab")
                                LibraryScreen(
                                    libraryViewModel = libraryViewModel,
                                    currentSongId = currentSong?.id,
                                    isPlaying = isPlaying,
                                    onSongClick = { song, folderSongs ->
                                        val clickT0 = System.currentTimeMillis()
                                        android.util.Log.i("MainClick", "[${song.id}] 点击歌曲: ${song.title}")
                                        // 清空搜索查询，确保返回音乐库时显示所有文件夹
                                        libraryViewModel.search("")
                                        val playlist = if (!folderSongs.isNullOrEmpty()) {
                                            folderSongs
                                        } else {
                                            libraryViewModel.songs.value
                                        }
                                        val realIndex = playlist.indexOfFirst { it.id == song.id }
                                        if (realIndex < 0) return@LibraryScreen
                                        playerViewModel.setPlaylist(playlist, realIndex)
                                        playerViewModel.equalizerController?.let {
                                            equalizerViewModel.loadFromController(it)
                                        }
                                        selectedTab = 1
                                        val switchElapsed = System.currentTimeMillis() - clickT0
                                        android.util.Log.i("MainClick", "[${song.id}] 切换到播放页, 耗时=${switchElapsed}ms")
                                    },
                                    onSortChanged = { sortedSongs, currentIndex ->
                                        playerViewModel.reorderPlaylist(sortedSongs, currentIndex)
                                    },
                                    onBackToNowPlaying = { selectedTab = 1 },
                                    modifier = Modifier.padding(padding)
                                )
                            }
                            1 -> {
                                if (nowPlayingFirstRender) {
                                    nowPlayingFirstRender = false
                                    android.util.Log.i("Startup_Timing", "┌── 首次渲染 NowPlayingScreen")
                                }
                                kotlin.run {
                                    NowPlayingScreen(
                                        song = currentSong,
                                        isPlaying = isPlaying,
                                        currentPosition = currentPosition,
                                        duration = duration,
                                        playMode = playMode,
                                        playerTheme = walkmanTheme,
                                        isFullScreen = isFullScreen,
                                        lyricsFontScale = lyricsFontScale,
                                        playbackSpeed = playbackSpeed,
                                        isPortraitMode = effectivePortraitMode,
                                        currentIndex = currentIndex,
                                        playlistSize = playlistSize,
                                        coverSizePercent = coverSizePercent,
                                        onSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
                                        onPlayPause = { playerViewModel.togglePlayPause() },
                                        onNext = { playerViewModel.playNext() },
                                        onPrevious = { playerViewModel.playPrevious() },
                                        onSeek = { playerViewModel.seekTo(it) },
                                        onTogglePlayMode = { playerViewModel.togglePlayMode() },
                                        onFullScreenChange = { isFullScreen = it },
                                        onBackToLibrary = { selectedTab = 0 },
                                        modifier = Modifier.padding(padding)
                                    )
                                }
                            }
                            2 -> {
                                SettingsScreen(
                                    playerViewModel = playerViewModel,
                                    libraryViewModel = libraryViewModel,
                                    audioSessionId = playerViewModel.player.audioSessionId,
                                    onBackToNowPlaying = { selectedTab = 1 },
                                    modifier = Modifier.padding(padding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}