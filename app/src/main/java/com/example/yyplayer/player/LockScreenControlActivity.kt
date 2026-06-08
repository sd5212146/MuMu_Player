package com.example.yyplayer.player

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import com.example.yyplayer.R
import com.example.yyplayer.data.lyrics.LyricsCache
import com.example.yyplayer.data.model.PlayerTheme
import com.example.yyplayer.data.repository.ThemeRepository
import com.example.yyplayer.data.lyrics.LyricsFetcher
import com.example.yyplayer.data.lyrics.SourceOrderManager
import com.example.yyplayer.data.repository.PlayerStateManager
import com.example.yyplayer.data.model.LyricsResult
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.model.resolveAlbumArtUri
import com.example.yyplayer.ui.screens.components.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 锁屏控制界面
 * 在锁屏之上显示，包含专辑封面、歌词、进度条、控制按钮和时钟
 * 屏幕关闭时自动退出，屏幕点亮且正在播放时由 MusicService 自动启动
 */
class LockScreenControlActivity : ComponentActivity() {

    private var screenOffReceiver: BroadcastReceiver? = null
    private var userPresentReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 隐藏系统状态栏（全屏模式），保持屏幕常亮确保触控正常
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
        )

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val player = PlayerController.getInstance(this)
        if (player.mediaItemCount == 0) {
            finish()
            return
        }

        // 屏幕关闭时自动退出锁屏界面
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    finish()
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        // 用户解锁时自动退出锁屏界面，同时启动主界面防止程序最小化
        userPresentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!isFinishing) {
                    finishAndGoToApp()
                }
            }
        }
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        setContent {
            LockScreenControlUI(onDismiss = { finishAndGoToApp() })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            screenOffReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        try {
            userPresentReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
    }

    /**
     * 拦截主页手势（上划/Home 键），锁屏控件不最小化，直接回到主界面
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishing) {
            launchMainAndFinish()
        }
    }

    /**
     * 退出锁屏控件并启动主界面，同时解除系统锁屏（非安全锁屏下直接跳过）
     */
    private fun finishAndGoToApp() {
        // 尝试解除系统锁屏，让锁屏控件退出后直接进入应用
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    launchMainAndFinish()
                }

                override fun onDismissError() {
                    // 解除失败时直接进入主界面
                    launchMainAndFinish()
                }

                override fun onDismissCancelled() {
                    // 用户取消时也直接进入主界面
                    launchMainAndFinish()
                }
            })
        } else {
            @Suppress("DEPRECATION")
            km.newKeyguardLock("LockScreenControl").disableKeyguard()
            launchMainAndFinish()
        }
    }

    private fun launchMainAndFinish() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            // 不创建新任务，把现有主界面任务带到前台
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }
        finish()
    }

    /** 拦截游戏手柄按键（L1/R1 双击切歌，Select 暂停/播放等） */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val player = PlayerController.getInstance(this)
        if (player.mediaItemCount == 0) return super.dispatchKeyEvent(event)

        // 用户关闭了游戏手柄控制时跳过按键处理
        val stateManager = PlayerStateManager(this)
        if (!stateManager.isGamepadEnabled()) return super.dispatchKeyEvent(event)

        // L1/R1 双击切歌 + L+R 暂停
        if (GamepadHelper.handleLRKeyEvent(
                event,
                onPrevious = { player.seekToPreviousMediaItem() },
                onNext = { player.seekToNextMediaItem() },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                }
            )
        ) {
            return true
        }

        // Select → 暂停/播放, X → 音量+, Y → 音量-
        if (GamepadHelper.handleGlobalKeyEvent(
                event,
                context = this,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                }
            )
        ) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LockScreenControlActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
    }
}

@Composable
private fun LockScreenControlUI(onDismiss: () -> Unit = {}) {
    val context = LocalContext.current
    val player = remember { PlayerController.getInstance(context) }

    // 从主题仓库获取当前主题，同步锁屏颜色与设置页一致
    val themeRepository = remember { ThemeRepository(context) }
    val currentTheme by themeRepository.currentTheme.collectAsState(initial = PlayerTheme.getById("walkman_blackgold"))

    // ---- 播放器状态 ----
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }
    var artworkUri by remember { mutableStateOf(player.mediaMetadata.artworkUri) }
    var title by remember { mutableStateOf(player.mediaMetadata.title?.toString() ?: "") }
    var artist by remember { mutableStateOf(player.mediaMetadata.artist?.toString() ?: "") }

    // ---- 歌词 ----
    val lyricsFetcher = remember { LyricsFetcher() }
    val lyricsCache = remember { LyricsCache(context) }
    val sourceOrderManager = remember { SourceOrderManager(context) }
    var lyricsResult by remember { mutableStateOf(LyricsResult(emptyList(), "")) }
    var lastLyricsKey by remember { mutableStateOf("") }

    // 备用：从 player MediaItem 自行解析封面（解决 MediaMetadata.artworkUri 为空的情况）
    var resolvedArtworkUri by remember { mutableStateOf<Uri?>(null) }
    // 电池电量（定时刷新）
    var batteryPct by remember { mutableStateOf(-1) }

    // 每 500ms 刷新播放状态，同时尝试解析封面
    // 暂停时改成 3 秒间隔，减少不必要的 CPU 唤醒
    LaunchedEffect(Unit) {
        while (true) {
            // 检测播放器是否已被服务空闲释放（暂停 30s 后），若是则退出锁屏
            if (!PlayerController.hasPlayer()) {
                (context as? LockScreenControlActivity)?.finish()
                return@LaunchedEffect
            }

            val currentPlaying = player.isPlaying
            isPlaying = currentPlaying
            currentPosition = player.currentPosition
            duration = player.duration
            artworkUri = player.mediaMetadata.artworkUri
            title = player.mediaMetadata.title?.toString() ?: ""
            artist = player.mediaMetadata.artist?.toString() ?: ""

            // 定时刷新电池电量
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryPct = level * 100 / scale
            }

            // 如果 MediaMetadata 无封面，自行解析
            if (artworkUri == null && resolvedArtworkUri == null) {
                val currentItem = player.currentMediaItem
                val filePath = currentItem?.localConfiguration?.uri?.toString() ?: ""
                val songId = currentItem?.mediaId?.toLongOrNull() ?: 0L
                if (filePath.isNotEmpty()) {
                    val resolved = withContext(Dispatchers.IO) {
                        Song(id = songId, title = title, filePath = filePath).resolveAlbumArtUri(context)
                    }
                    if (resolved != null) {
                        resolvedArtworkUri = resolved
                    }
                }
            }

            delay(if (currentPlaying) 500L else 3000L)
        }
    }

    // 优先用 MediaMetadata 的封面，否则用自行解析的
    val displayArtworkUri = artworkUri ?: resolvedArtworkUri

    // 歌曲变化时获取歌词（先缓存，后网络）
    LaunchedEffect(title, artist) {
        if (title.isEmpty()) return@LaunchedEffect
        val cacheKey = "$title|$artist"
        if (cacheKey != lastLyricsKey) {
            lastLyricsKey = cacheKey
            lyricsResult = LyricsResult(emptyList(), "")
            val cached = lyricsCache.get(cacheKey)
            if (cached != null) {
                lyricsResult = cached
            } else {
                val sourceOrder = sourceOrderManager.getEnabledSources()
                lyricsResult = lyricsFetcher.fetchLyrics(
                    title = title,
                    artist = artist,
                    sourceOrder = sourceOrder
                )
                if (lyricsResult.lines.isNotEmpty()) {
                    lyricsCache.put(cacheKey, lyricsResult.rawLrc, lyricsResult.source)
                }
            }
        }
    }

    // 当前歌词行
    val currentLineIndex = if (lyricsResult.lines.isNotEmpty()) {
        lyricsResult.lines.indexOfLast { it.time <= currentPosition }
    } else -1
    val currentLineText = if (currentLineIndex >= 0) {
        lyricsResult.lines[currentLineIndex].text
    } else ""

    // 拖拽进度（null=未拖拽，否则为 0f~1f），用于平滑拖动进度条
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    val displayProgress = draggingFraction ?: (if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f)
    val displayPosition = if (draggingFraction != null) (draggingFraction!! * duration).toLong() else currentPosition

    // 播放结束或无内容时自动退出
    LaunchedEffect(title) {
        if (title.isEmpty()) {
            delay(1000)
            (context as? LockScreenControlActivity)?.finish()
        }
    }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 背景半透明专辑图
        if (displayArtworkUri != null) {
            AsyncImage(
                model = displayArtworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.25f),
                contentScale = ContentScale.Crop
            )
        }

        // 暗色渐变（降低透明度，封面色调更明显）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.06f),
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.30f)
                        )
                    )
                )
        )

        // ===== 顶部信息栏：时钟 + 歌曲序号 + 电量&关闭 =====
        var clockText by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(1000)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：时钟（固定宽度，使标题居中）
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = clockText,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 中间：歌名（大号加粗）+ 歌手（小号，换行显示）
            Column(
                modifier = Modifier.weight(2f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MarqueeTitle(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (artist.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MarqueeTitle(
                        text = artist,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // 右侧：电量（隐藏 X 关闭按钮和 N/N）
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "${batteryPct}%",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ===== 专辑封面（独立居中，N/N 覆盖在顶部居中） =====
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.6f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f))
        ) {
            if (displayArtworkUri != null) {
                AsyncImage(
                    model = displayArtworkUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val diskPainter: Painter = painterResource(id = R.drawable.disk)
                Image(
                    painter = diskPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // ===== 底部：歌词 =====
        if (currentLineText.isNotEmpty()) {
            Text(
                text = currentLineText,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 50.dp) // 增大与封面的间距
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.35f)
                .width(width = 96.dp)
                .clickable { player.seekToPreviousMediaItem() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp)
            )
        }
        

        
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight(0.35f)
                .fillMaxWidth(0.3f)
                .clickable { if (player.isPlaying) player.pause() else player.play() },
            contentAlignment = Alignment.Center
        ) {
            if (!isPlaying) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.35f)
                .width(width = 96.dp)
                .clickable { player.seekToNextMediaItem() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp)
            )
        }

        // ===== 底部进度条 =====
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            if (duration > 0) {
                val density = LocalDensity.current

                // 进度条触控区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                player.seekTo((fraction * duration).toLong())
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    draggingFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    draggingFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    val f = draggingFraction ?: return@detectDragGestures
                                    val targetPos = (f * duration).toLong()
                                    player.seekTo(targetPos)
                                    currentPosition = targetPos
                                    draggingFraction = null
                                },
                                onDragCancel = {
                                    draggingFraction = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val trackY = size.height / 2
                        val trackLeft = 0f
                        val trackRight = size.width
                        val activeEnd = trackLeft + (trackRight - trackLeft) * displayProgress

                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(trackLeft, trackY),
                            end = Offset(trackRight, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = currentTheme.accentColor.copy(alpha = 0.45f),
                            start = Offset(trackLeft, trackY),
                            end = Offset(activeEnd, trackY),
                            strokeWidth = 2.dp.toPx()
                        )
                        val thumbX = activeEnd.coerceIn(
                            with(density) { 6.dp.toPx() },
                            size.width - with(density) { 6.dp.toPx() }
                        )
                        drawCircle(
                            color = currentTheme.accentColor.copy(alpha = 0.85f),
                            radius = with(density) { 5.dp.toPx() },
                            center = Offset(thumbX, trackY)
                        )
                    }
                }

                // 时间文字（进度条下方，左右分开）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(displayPosition),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(duration),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 滚动标题组件：文本超出容器宽度时自动平滑滚动（跑马灯效果）
 */
@Composable
private fun MarqueeTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    var containerWidth by remember { mutableStateOf(0f) }
    var textWidth by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val needsScroll = textWidth > containerWidth && containerWidth > 0f

    val offsetAnim = remember { Animatable(0f) }

    LaunchedEffect(needsScroll) {
        if (needsScroll) {
            val scrollDistance = textWidth - containerWidth
            while (true) {
                // 在起始位置暂停 2 秒
                delay(2000)
                // 平滑滚动到末端（速度约 0.8 px/ms）
                offsetAnim.animateTo(
                    targetValue = -scrollDistance,
                    animationSpec = tween(
                        durationMillis = (scrollDistance / 0.8f).toInt().coerceIn(3000, 12000),
                        easing = LinearEasing
                    )
                )
                // 在末端暂停 2 秒
                delay(2000)
                // 瞬间回到起始位置重新开始
                offsetAnim.snapTo(0f)
            }
        } else {
            offsetAnim.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer(clip = true)
            .onSizeChanged { containerWidth = it.width.toFloat() },
        contentAlignment = if (needsScroll) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { textWidth = it.width.toFloat() }
                .offset(x = with(density) { offsetAnim.value.toDp() })
        )
    }
}
