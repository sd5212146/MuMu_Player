package com.example.yyplayer.player

import android.app.Notification
import android.app.PendingIntent
import android.widget.RemoteViews
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.yyplayer.MainActivity
import com.example.yyplayer.R
import com.example.yyplayer.YYPlayerApplication
import com.example.yyplayer.data.repository.PlayerStateManager
import com.example.yyplayer.data.repository.FavoritesManager
import com.example.yyplayer.data.model.PlayMode
import com.example.yyplayer.ui.viewmodel.PlayerViewModel

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // 缓存上次的封面 Bitmap 和歌曲标识，避免每 3 秒重读磁盘
    private var cachedTitle: String = ""
    private var cachedArtist: String = ""
    private var cachedArtwork: android.graphics.Bitmap? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            val session = mediaSession ?: return
            val player = session.player
            if (!player.isPlaying) {
                // 暂停时不继续轮询，由 onIsPlayingChanged 恢复时重新启动
                return
            }

            val metadata = player.mediaMetadata
            val title = metadata.title?.toString() ?: "MuMu Player"
            val artist = metadata.artist?.toString() ?: ""

            // 仅在歌曲变化时重读封面（省去磁盘 I/O）
            if (title != cachedTitle || artist != cachedArtist) {
                cachedTitle = title
                cachedArtist = artist
                cachedArtwork = loadArtwork(player)
            } else if (cachedArtwork == null) {
                // 封面可能尚未异步更新完成，额外尝试一次
                cachedArtwork = loadArtwork(player)
            }

            val notification = createNotification(title, artist, session)
            startForeground(1, notification)

            progressHandler.postDelayed(this, 1000)
        }
    }

    // 监听播放/暂停状态变化以管理前台服务和轮询
    private var playbackListener: Player.Listener? = null

    /** 从 MediaSession 读取专辑封面 Bitmap（缓存友好） */
    private fun loadArtwork(player: androidx.media3.common.Player): android.graphics.Bitmap? {
        try {
            val artworkUri = player.mediaMetadata.artworkUri ?: return null
            // 先读取图片尺寸（不分配像素），避免超大图片 OOM
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(artworkUri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            // 记录大图尺寸
            if (opts.outWidth > 1024 || opts.outHeight > 1024) {
                android.util.Log.w("AlbumArt", "loadArtwork 大图: ${opts.outWidth}x${opts.outHeight}, uri=$artworkUri")
            }
            // 通知栏封面不需要太高分辨率，降采样到 512px 以内
            opts.inSampleSize = 1
            while (opts.outWidth / opts.inSampleSize > 512 || opts.outHeight / opts.inSampleSize > 512) {
                opts.inSampleSize *= 2
            }
            if (opts.inSampleSize > 1) {
                android.util.Log.i("AlbumArt", "loadArtwork 降采样: inSampleSize=${opts.inSampleSize}, 最终尺寸=${opts.outWidth / opts.inSampleSize}x${opts.outHeight / opts.inSampleSize}")
            }
            opts.inJustDecodeBounds = false
            // 重新打开流进行实际解码
            contentResolver.openInputStream(artworkUri)?.use {
                return android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Throwable) {
            android.util.Log.w("AlbumArt", "loadArtwork 解码异常(OOM/Error)，已兜住")
        }
        return null
    }

    private var screenOnReceiver: BroadcastReceiver? = null

    // 跟踪 startForeground() 是否已被当前服务实例调用，防止 ForegroundServiceDidNotStartInTimeException
    private var foregroundStarted = false

    // 空闲超时：暂停后自动释放播放器资源
    private var idleHandler: Handler? = null
    private val idleTimeoutMs = 1800000L
    private val idleRunnable = Runnable {
        // 双重校验：播放器还在播放则跳过释放（防止旧 Service 实例残留的 idleRunnable 误释放当前播放器）
        if (PlayerController.hasPlayer() && PlayerController.getInstance(this@MusicService).isPlaying) {
            android.util.Log.i("MusicService", "[idleRunnable] 播放器正在播放，跳过释放")
            return@Runnable
        }
        android.util.Log.i("MusicService", "[idleRunnable] 空闲 $idleTimeoutMs ms，释放播放器资源")
        // 允许下次播放时重新创建服务
        PlayerViewModel.resetServiceStarted()
        try {
            playbackListener?.let { PlayerController.getInstance(this).removeListener(it) }
        } catch (_: Exception) {}
        playbackListener = null
        PlayerController.release()
        mediaSession?.release()
        mediaSession = null
        android.util.Log.i("MusicService", "[idleRunnable] 释放完成，stopForeground + stopSelf")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground() {
        val t0 = System.currentTimeMillis()
        android.util.Log.i("MusicService", "[enterForeground] 进入前台")
        // 取消空闲超时（用户恢复播放）
        idleHandler?.removeCallbacks(idleRunnable)

        refreshNotification()
        progressHandler.removeCallbacks(progressUpdater)
        progressHandler.post(progressUpdater)
        android.util.Log.i("MusicService", "[enterForeground] 完成, 耗时=${System.currentTimeMillis()-t0}ms")
    }

    private fun exitForeground() {
        val t0 = System.currentTimeMillis()
        android.util.Log.i("MusicService", "[exitForeground] 退出前台（保留通知）")
        progressHandler.removeCallbacks(progressUpdater)
        // 暂停时不移除通知，更新为暂停状态，让用户仍可通过通知栏控制
        refreshNotification()
        // 启动空闲超时：暂停 30 秒后自动释放播放器
        idleHandler = Handler(Looper.getMainLooper())
        idleHandler?.removeCallbacks(idleRunnable)
        idleHandler?.postDelayed(idleRunnable, idleTimeoutMs)
        android.util.Log.i("MusicService", "[exitForeground] 完成, idleRunnable 已排队 $idleTimeoutMs ms, 耗时=${System.currentTimeMillis()-t0}ms")
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("MusicService", "[onCreate] 服务创建")

        val player = PlayerController.getInstance(this)
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                    }
                    if (keyEvent != null) {
                        // 优先处理线控耳机按钮（单击暂停/播放, 双击下一首, 三击上一首）
                        val headsetHandled = GamepadHelper.handleHeadsetButton(
                            keyEvent,
                            onPlayPause = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            onNext = { player.seekToNextMediaItem() },
                            onPrevious = { player.seekToPreviousMediaItem() }
                        )
                        if (headsetHandled) return true

                        val handled = GamepadHelper.handleLRKeyEvent(
                            keyEvent,
                            onPrevious = { player.seekToPreviousMediaItem() },
                            onNext = { player.seekToNextMediaItem() },
                            onPlayPause = {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        )
                        if (handled) return true
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()

        // 监听播放状态：播放时进入前台，暂停时退出前台
        playbackListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    enterForeground()
                } else {
                    exitForeground()
                }
            }
        }
        player.addListener(playbackListener!!)

        // 必须始终调用 startForeground 满足系统要求（否则 startForegroundService 会崩溃）
        android.util.Log.i("MusicService", "[onCreate] enterForeground 初始")
        enterForeground()
        // 如果当前未播放，立即退出前台（移除通知和轮询）
        if (!player.isPlaying) {
            android.util.Log.i("MusicService", "[onCreate] 未播放，exitForeground")
            exitForeground()
        }
        android.util.Log.i("MusicService", "[onCreate] 完成，foregroundStarted=$foregroundStarted")

        // 注册屏幕点亮广播，自动弹出锁屏控制界面（仅在播放时有效）
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    if (player.isPlaying && player.mediaItemCount > 0) {
                        if (PlayerStateManager(this@MusicService).isLockScreenEnabled()) {
                            startActivity(LockScreenControlActivity.createIntent(this@MusicService))
                        }
                    }
                }
            }
        }
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionT0 = System.currentTimeMillis()
        android.util.Log.i("MusicService", "[onStartCommand] action=${intent?.action}, foregroundStarted=$foregroundStarted")

        // ！！！！必须第一时间调用 startForeground() ！！！！
        // Android 12+ 在 startForegroundService() 启动的服务中，若任意 onStartCommand
        // 未及时调用 startForeground()，系统会抛 ForegroundServiceDidNotStartInTimeException
        if (!foregroundStarted) {
            try {
                // 先调 startForeground 满足系统要求，占位通知后续由 handleAction/refreshNotification 更新
                startForeground(1, createPlaceholderNotification("正在启动"))
                foregroundStarted = true
                android.util.Log.i("MusicService", "[onStartCommand] startForeground 初始完成")
            } catch (_: Exception) {
                android.util.Log.e("MusicService", "[onStartCommand] startForeground 初始失败，继续处理")
            }
        }

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val player = PlayerController.getInstance(this)
                if (player.isPlaying) player.pause() else player.play()
                refreshNotification()
            }
            ACTION_SKIP_NEXT -> {
                PlayerController.getInstance(this).seekToNextMediaItem()
                refreshNotification()
            }
            ACTION_SKIP_PREV -> {
                PlayerController.getInstance(this).seekToPreviousMediaItem()
                refreshNotification()
            }
            ACTION_TOGGLE_FAVORITE -> {
                val player = PlayerController.getInstance(this)
                val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (songId != null) {
                    FavoritesManager(this).toggle(songId)
                }
                refreshNotification()
            }
            ACTION_TOGGLE_PLAY_MODE -> {
                val player = PlayerController.getInstance(this)
                val stateManager = PlayerStateManager(this)
                val currentMode = stateManager.getPlayMode()
                val newMode = when (currentMode) {
                    PlayMode.NORMAL -> PlayMode.REPEAT_FOLDER
                    PlayMode.REPEAT_FOLDER -> PlayMode.REPEAT_ONE
                    PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
                    PlayMode.SHUFFLE -> PlayMode.NORMAL
                }
                // 先保存到 SharedPreferences，再设置 ExoPlayer
                // 顺序关键：savePlayMode 在先，这样 onRepeatModeChanged 回调能读到新值
                stateManager.savePlayMode(newMode)
                when (newMode) {
                    PlayMode.NORMAL -> {
                        player.repeatMode = Player.REPEAT_MODE_OFF
                        player.shuffleModeEnabled = false
                    }
                    PlayMode.REPEAT_FOLDER -> {
                        player.repeatMode = Player.REPEAT_MODE_ALL
                        player.shuffleModeEnabled = false
                    }
                    PlayMode.REPEAT_ONE -> {
                        player.repeatMode = Player.REPEAT_MODE_ONE
                        player.shuffleModeEnabled = false
                    }
                    PlayMode.SHUFFLE -> {
                        player.repeatMode = Player.REPEAT_MODE_ALL
                        player.shuffleModeEnabled = false
                    }
                }
                android.util.Log.i("MusicService", "通知栏切换播放模式: " + currentMode.label + " -> " + newMode.label)
                refreshNotification()
            }
            ACTION_REFRESH_NOTIFICATION -> {
                if (!refreshNotification()) {
                    android.util.Log.i("MusicService", "[onStartCommand] refreshNotification 返回 false，停止服务")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        android.util.Log.i("MusicService", "[onStartCommand] 完成 action=${intent?.action}, 耗时=${System.currentTimeMillis()-actionT0}ms")
        return super.onStartCommand(intent, flags, startId)
    }

    /** 创建包含封面、进度条、媒体按钮和关闭按钮的前台通知 */
    private fun createNotification(
        title: String,
        content: String,
        session: MediaSession
    ): Notification {
        val player = session.player

        // 播放/暂停图标
        val playIcon = if (player.isPlaying)
            android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playLabel = if (player.isPlaying) "暂停" else "播放"

        // 使用缓存的封面 Bitmap（避免每秒读磁盘）
        var largeIcon = cachedArtwork
        if (largeIcon == null) {
            try {
                largeIcon = BitmapFactory.decodeResource(resources, R.drawable.disk)
            } catch (_: Throwable) {}
        }

        val showFullNotification = PlayerStateManager(this).isNotificationEnabled()

        // 进度信息
        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)

        val builder = Notification.Builder(this, YYPlayerApplication.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText("MuMu Player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(buildPendingIntent(this))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)

        if (showFullNotification) {
            // 米白色背景
            val bgColor = Color.parseColor("#FDF5E6")
            val iconTint = Color.parseColor("#555555")

            // 展开态：自定义布局（封面 + 信息 + 全部控制按钮）
            val contentView = RemoteViews(packageName, R.layout.notification_content)
            contentView.setImageViewBitmap(R.id.notification_album_art, largeIcon)
            contentView.setTextViewText(R.id.notification_title, title)
            contentView.setTextViewText(R.id.notification_artist, content)

            if (duration > 0) {
                val scaledProgress = ((position.toFloat() / duration.toFloat()) * 1000).toInt()
                contentView.setProgressBar(
                    R.id.notification_progress,
                    1000,
                    scaledProgress,
                    false
                )
            }

            // 控制按钮：设置图标颜色
            contentView.setInt(R.id.notification_btn_prev, "setColorFilter", iconTint)
            contentView.setInt(R.id.notification_btn_next, "setColorFilter", iconTint)
            val playIconRes = if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            contentView.setImageViewResource(R.id.notification_btn_play, playIconRes)
            contentView.setInt(R.id.notification_btn_play, "setColorFilter", iconTint)

            // 播放模式图标按当前状态切换
            val stateManager = PlayerStateManager(this)
            val playMode = stateManager.getPlayMode()
            val playModeIcon = when (playMode) {
                PlayMode.SHUFFLE -> R.drawable.ic_play_mode_shuffle
                PlayMode.REPEAT_ONE -> R.drawable.ic_play_mode_repeat_one
                PlayMode.REPEAT_FOLDER -> R.drawable.ic_play_mode_repeat
                PlayMode.NORMAL -> R.drawable.ic_play_mode_order
            }
            contentView.setImageViewResource(R.id.notification_btn_play_mode, playModeIcon)
            contentView.setInt(R.id.notification_btn_play_mode, "setColorFilter", iconTint)

            // 红心图标按收藏状态切换
            val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
            val isFav = if (songId != null) FavoritesManager(this).isFavorite(songId) else false
            contentView.setImageViewResource(
                R.id.notification_btn_favorite,
                if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            contentView.setInt(R.id.notification_btn_favorite, "setColorFilter",
                if (isFav) Color.parseColor("#E53935") else iconTint)

            // 自定义布局按钮点击事件
            contentView.setOnClickPendingIntent(R.id.notification_btn_play_mode, buildServicePI(20, ACTION_TOGGLE_PLAY_MODE))
            contentView.setOnClickPendingIntent(R.id.notification_btn_prev, buildServicePI(21, ACTION_SKIP_PREV))
            contentView.setOnClickPendingIntent(R.id.notification_btn_play, buildServicePI(22, ACTION_PLAY_PAUSE))
            contentView.setOnClickPendingIntent(R.id.notification_btn_next, buildServicePI(23, ACTION_SKIP_NEXT))
            contentView.setOnClickPendingIntent(R.id.notification_btn_favorite, buildServicePI(24, ACTION_TOGGLE_FAVORITE))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder
                    .setColor(bgColor)
                    .setColorized(true)
            }

            builder
                .setLargeIcon(largeIcon)
                .setStyle(
                    Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setCustomBigContentView(contentView)

            // 折叠态系统按钮（所有 Android 版本均可靠显示）
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "上一首",
                    PendingIntent.getService(
                        this, 0,
                        Intent(this, MusicService::class.java).apply { action = ACTION_SKIP_PREV },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    playIcon,
                    playLabel,
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "下一首",
                    PendingIntent.getService(
                        this, 2,
                        Intent(this, MusicService::class.java).apply { action = ACTION_SKIP_NEXT },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
        } else {
            // 通知栏控件关闭时：显示歌曲播放状态
            val statusText = if (player.isPlaying) "正在播放：$title" else "已暂停：$title"
            builder.setContentTitle(title)
                .setContentText(statusText)
                .setStyle(Notification.MediaStyle())
        }

        return builder.build()
    }

    /** 构建服务 PendingIntent（供自定义布局按钮点击使用） */
    private fun buildServicePI(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 创建占位通知，用于在播放器尚未就绪时满足 startForeground 系统要求 */
    private fun createPlaceholderNotification(subtitle: String): Notification {
        return Notification.Builder(this, YYPlayerApplication.CHANNEL_ID)
            .setContentTitle("YYPlayer")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /** 刷新前台通知。返回 false 表示 mediaSession 已释放（而非暂停），调用方应停止服务 */
    private fun refreshNotification(): Boolean {
        val t0 = System.currentTimeMillis()
        val session = mediaSession
        if (session == null) {
            // 服务已空闲释放，无法刷新
            android.util.Log.w("MusicService", "[refreshNotification] mediaSession=null")
            return false
        }
        val player = session.player
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString() ?: "MuMu Player"
        val artist = metadata.artist?.toString() ?: ""
        cachedTitle = title
        cachedArtist = artist
        cachedArtwork = loadArtwork(player)
        val notification = createNotification(title, artist, session)
        startForeground(1, notification)
        foregroundStarted = true
        android.util.Log.i("MusicService", "[refreshNotification] 完成: $title, 耗时=${System.currentTimeMillis()-t0}ms")
        return true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession) {
        refreshNotification()
    }

    override fun onDestroy() {
        android.util.Log.i("MusicService", "[onDestroy] 服务销毁, hasPlayer=${PlayerController.hasPlayer()}")
        progressHandler.removeCallbacks(progressUpdater)
        if (PlayerController.hasPlayer()) {
            playbackListener?.let {
                try { PlayerController.getInstance(this).removeListener(it) } catch (_: Exception) {}
            }
        }
        try {
            screenOnReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        // 保存最终播放位置（只在播放器尚未被 idleRunnable 释放时有效）
        try {
            if (PlayerController.hasPlayer()) {
                val p = PlayerController.getInstance(this)
                if (p.mediaItemCount > 0) {
                    android.util.Log.i("MusicService", "[onDestroy] 保存播放位置")
                    PlayerStateManager(this).savePosition(
                        p.currentMediaItemIndex,
                        p.currentPosition.coerceAtLeast(0L)
                    )
                }
            }
        } catch (_: Exception) {}
        // 只在播放器未被 idleRunnable 释放时才执行停止和释放，
        // 否则 getInstance() 会创建新 ExoPlayer，立即释放会导致 MediaCodec handler 线程崩溃
        if (PlayerController.hasPlayer()) {
            android.util.Log.i("MusicService", "[onDestroy] 播放器存在，停止并释放")
            try {
                exitForeground()
                PlayerController.getInstance(this).stop()
                PlayerController.release()
            } catch (_: Exception) {}
        } else {
            android.util.Log.i("MusicService", "[onDestroy] 播放器已被释放，跳过停止")
        }
        mediaSession?.run {
            android.util.Log.i("MusicService", "[onDestroy] 释放 mediaSession")
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.example.yyplayer.action.PLAY_PAUSE"
        private const val ACTION_SKIP_NEXT = "com.example.yyplayer.action.SKIP_NEXT"
        private const val ACTION_SKIP_PREV = "com.example.yyplayer.action.SKIP_PREV"
        private const val ACTION_TOGGLE_FAVORITE = "com.example.yyplayer.action.TOGGLE_FAVORITE"
        private const val ACTION_REFRESH_NOTIFICATION = "com.example.yyplayer.action.REFRESH_NOTIFICATION"
        private const val ACTION_TOGGLE_PLAY_MODE = "com.example.yyplayer.action.TOGGLE_PLAY_MODE"

        /** 从外部（如设置页）触发通知栏立刻刷新 */
        fun requestRefresh(context: Context) {
            val intent = Intent(context, MusicService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 构建服务 PendingIntent，供自定义布局按钮点击使用 */
        @JvmStatic
        private fun buildServicePI(context: Context, requestCode: Int, action: String): PendingIntent {
            val intent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
