package com.example.yyplayer.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.yyplayer.data.lyrics.AlbumArtCache
import com.example.yyplayer.data.lyrics.AlbumArtFetcher
import com.example.yyplayer.data.lyrics.AlbumArtSourceManager
import com.example.yyplayer.data.lyrics.LyricsCache
import com.example.yyplayer.data.lyrics.LyricsFetcher
import com.example.yyplayer.data.lyrics.SourceOrderManager
import com.example.yyplayer.data.model.PlayMode
import com.example.yyplayer.data.model.ScreenRatio
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.model.resolveAlbumArtUri
import com.example.yyplayer.data.repository.LyricsFontSizeSettings
import com.example.yyplayer.data.repository.MusicRepository
import com.example.yyplayer.data.repository.PlayerStateManager
import com.example.yyplayer.data.repository.ThemeRepository
import com.example.yyplayer.player.EqualizerController
import com.example.yyplayer.player.PlayerController
import com.example.yyplayer.player.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val player: ExoPlayer
        get() = PlayerController.getInstance(context)
    val themeRepository = ThemeRepository(context)
    val stateManager = PlayerStateManager(context)
    val lyricsCache = LyricsCache(context)
    val albumArtCache = AlbumArtCache(context)
    var equalizerController: EqualizerController? = null

    // 缓存 MediaItem 列表，避免重复构建 100+ 个 MediaItem
    private var cachedMediaItems: List<MediaItem> = emptyList()
    private var cachedSongIds: List<Long> = emptyList()

    /** 缓存本次构建的 MediaItem 列表，供后续复用 */
    private fun updateMediaItemCache(mediaItems: List<MediaItem>, songs: List<Song>) {
        cachedMediaItems = mediaItems
        cachedSongIds = songs.map { it.id }
    }

    /** 从缓存构建已排序的 MediaItem 列表（按新的排序顺序重排）*/
    private fun reorderCachedMediaItems(sortedSongs: List<Song>): List<MediaItem>? {
        if (cachedMediaItems.isEmpty()) return null
        val oldIds = cachedSongIds
        val newIds = sortedSongs.map { it.id }
        if (oldIds.toSet() != newIds.toSet()) return null
        // 用 Map 加速查找：songId -> MediaItem
        val idToItem = oldIds.zip(cachedMediaItems).toMap()
        return newIds.mapNotNull { idToItem[it] }
    }

    // 屏幕比例 + 自适应
    private val screenPrefs = context.getSharedPreferences("screen_settings", Context.MODE_PRIVATE)
    private val _screenRatio = MutableStateFlow(ScreenRatio.RATIO_1_1)
    val screenRatio: StateFlow<ScreenRatio> = _screenRatio.asStateFlow()
    private val _adaptiveEnabled = MutableStateFlow(true)
    val adaptiveEnabled: StateFlow<Boolean> = _adaptiveEnabled.asStateFlow()

    private val prefetchFetcher = LyricsFetcher()
    private val prefetchAlbumArt = AlbumArtFetcher()
    private val sourceOrderManager = SourceOrderManager(context)
    private val albumArtSourceManager = AlbumArtSourceManager(context)

    private var playerListener: Player.Listener? = null
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private var volumeMonitorJob: kotlinx.coroutines.Job? = null
    /** 防止重洗牌时 onMediaItemTransition 递归触发 */
    private var isReshuffling = false

    /** 防止切歌时 updateProgress() 用旧位置覆盖 _currentPosition */
    @Volatile
    private var isTransitioning = false

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.REPEAT_FOLDER)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _pauseOnVolumeZero = MutableStateFlow(false)
    val pauseOnVolumeZero: StateFlow<Boolean> = _pauseOnVolumeZero.asStateFlow()

    private val _lyricsFontScale = MutableStateFlow(1.0f)
    val lyricsFontScale: StateFlow<Float> = _lyricsFontScale.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _portraitMode = MutableStateFlow(false)
    val portraitMode: StateFlow<Boolean> = _portraitMode.asStateFlow()

    private val _autoRotate = MutableStateFlow(false)
    val autoRotate: StateFlow<Boolean> = _autoRotate.asStateFlow()

    private val _coverSizePercent = MutableStateFlow(0.5f)
    val coverSizePercent: StateFlow<Float> = _coverSizePercent.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.setPlaybackSpeed(speed)
    }

    private var musicRepository: MusicRepository? = null

    companion object {
        /** 由 MusicService 空闲超时回调重置，允许下次播放时重新启动服务 */
        var serviceStarted = false
            private set

        fun resetServiceStarted() {
            android.util.Log.i("ServiceLifecycle", "[resetServiceStarted] serviceStarted=false")
            serviceStarted = false
        }
    }

    init {
        _screenRatio.value = ScreenRatio.getById(screenPrefs.getString("ratio_id", "1:1") ?: "1:1")
        _adaptiveEnabled.value = screenPrefs.getBoolean("adaptive", true)
        _portraitMode.value = stateManager.isPortraitMode()
        _autoRotate.value = stateManager.isAutoRotate()
        _coverSizePercent.value = screenPrefs.getFloat("cover_${_screenRatio.value.id}",
            _screenRatio.value.coverSizePercent)
        _pauseOnVolumeZero.value = stateManager.isPauseOnVolumeZero()
        _lyricsFontScale.value = LyricsFontSizeSettings(context).getFontScale()
        startVolumeMonitor()
    }

    fun setScreenRatio(ratio: ScreenRatio) {
        _screenRatio.value = ratio
        screenPrefs.edit().putString("ratio_id", ratio.id).apply()
        _coverSizePercent.value = screenPrefs.getFloat("cover_${ratio.id}", ratio.coverSizePercent)
    }

    /** 获取指定比例的封面占比（从存储读取，无自定义则返回枚举默认值） */
    fun getCoverSizePercent(ratioId: String): Float {
        val ratio = ScreenRatio.getById(ratioId)
        return screenPrefs.getFloat("cover_$ratioId", ratio.coverSizePercent)
    }

    /** 设置指定比例的封面占比并写入存储 */
    fun setCoverSizePercent(ratioId: String, percent: Float) {
        screenPrefs.edit().putFloat("cover_$ratioId", percent).apply()
        // 如果当前使用该比例，同步更新 StateFlow
        if (ratioId == _screenRatio.value.id) {
            _coverSizePercent.value = percent
        }
    }

    fun setAdaptiveEnabled(enabled: Boolean) {
        _adaptiveEnabled.value = enabled
        screenPrefs.edit().putBoolean("adaptive", enabled).apply()
    }

    fun setPauseOnVolumeZero(enabled: Boolean) {
        _pauseOnVolumeZero.value = enabled
        stateManager.setPauseOnVolumeZero(enabled)
        if (enabled) startVolumeMonitor() else stopVolumeMonitor()
    }

    fun setLyricsFontScale(scale: Float) {
        _lyricsFontScale.value = scale
        LyricsFontSizeSettings(getApplication()).setFontScale(scale)
    }

    fun setPortraitMode(enabled: Boolean) {
        _portraitMode.value = enabled
        stateManager.setPortraitMode(enabled)
        // 开启竖屏模式时自动关闭自动识别
        if (enabled) {
            _autoRotate.value = false
            stateManager.setAutoRotate(false)
        }
    }

    fun setAutoRotate(enabled: Boolean) {
        _autoRotate.value = enabled
        stateManager.setAutoRotate(enabled)
        // 开启自动识别时自动关闭竖屏模式
        if (enabled) {
            _portraitMode.value = false
            stateManager.setPortraitMode(false)
        }
    }

    fun setMusicRepository(repo: MusicRepository) {
        musicRepository = repo
    }

    /** 启动前台 MusicService（用于锁屏通知和 MediaSession） */
    private fun startMusicService() {
        if (serviceStarted) {
            android.util.Log.i("ServiceLifecycle", "[startMusicService] 已启动, 跳过")
            return
        }
        serviceStarted = true
        android.util.Log.i("ServiceLifecycle", "[startMusicService] 首次启动 MusicService")
        try {
            val intent = Intent(context, MusicService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    /** 排序变化时重新排列 ExoPlayer 歌单，不切换页面、不保存状态 */
    fun reorderPlaylist(sortedSongs: List<Song>, currentIndex: Int) {
        // 随机模式下手动 Fisher-Yates 洗牌，避免 ExoPlayer 内置 shuffle 的确定性种子导致重复出现相同歌曲
        val (shuffledSongs, shuffledIndex) = if (_playMode.value == PlayMode.SHUFFLE) {
            shufflePlaylist(sortedSongs, currentIndex)
        } else {
            sortedSongs to currentIndex
        }
        _playlist.value = shuffledSongs
        if (shuffledSongs.isEmpty()) return
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        // 优先从缓存复用 MediaItem（避免重复构建 100+ 个对象）
        val mediaItems = reorderCachedMediaItems(shuffledSongs) ?:
            shuffledSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(Uri.fromFile(File(song.filePath)))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
        player.setMediaItems(mediaItems, shuffledIndex, currentPos)
        player.prepare()
        if (!player.isPlaying) player.play()
        applyPlayMode()
        android.util.Log.i("PlayLoop", "reorderPlaylist: 重排" + shuffledSongs.size + "首, shuffledIndex=" + shuffledIndex + ", 当前歌曲=\"" + (shuffledSongs.getOrNull(shuffledIndex)?.title ?: "-") + "\"")
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        val t0 = System.currentTimeMillis()
        // 随机模式下手动 Fisher-Yates 洗牌，避免 ExoPlayer 内置 shuffle 的确定性种子问题
        val (shuffledSongs, shuffledStartIndex) = if (_playMode.value == PlayMode.SHUFFLE) {
            shufflePlaylist(songs, startIndex)
        } else {
            songs to startIndex
        }
        _playlist.value = shuffledSongs
        if (shuffledSongs.isEmpty()) return

        val mediaItems = shuffledSongs.mapIndexed { i, song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(Uri.fromFile(File(song.filePath)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, shuffledStartIndex, 0L)
        player.prepare()
        player.play()
        updateMediaItemCache(mediaItems, shuffledSongs)
        applyPlayMode()  // 确保 ExoPlayer repeatMode 与当前播放模式同步
        android.util.Log.i("PlayLoop", "setPlaylist: 歌单" + shuffledSongs.size + "首, shuffledStartIndex=" + shuffledStartIndex + ", 播放模式=" + _playMode.value.label + ", repeatMode=" + player.repeatMode)
        // 日志：打印歌单头尾，检查排序是否正确
        val firstTitle = shuffledSongs.firstOrNull()?.title ?: "-"
        val lastTitle = shuffledSongs.lastOrNull()?.title ?: "-"
        val startTitle = shuffledSongs.getOrNull(shuffledStartIndex)?.title ?: "-"
        android.util.Log.i("PlayLoop", "setPlaylist: 排序检查: 第一首=\"" + firstTitle + "\" 最后一首=\"" + lastTitle + "\" 起始=\"" + startTitle + "\"")
        val setPlaylistElapsed = System.currentTimeMillis() - t0
        android.util.Log.i("MainClick", "setPlaylist: " + mediaItems.size + " 首歌, 耗时=" + setPlaylistElapsed + "ms")

        _currentSong.value = shuffledSongs.getOrNull(shuffledStartIndex)
        _duration.value = shuffledSongs.getOrNull(shuffledStartIndex)?.duration ?: 0L

        // 保存播放状态
        stateManager.saveState(shuffledSongs, shuffledStartIndex, _playMode.value, 0L)

        initEqualizer()
        setupPlayerListener()
        startMusicService()
        prefetchNextSongs()

        // 异步解析当前歌曲的封面（避免 MediaMetadataRetriever 在主线程阻塞）
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uri = shuffledSongs.getOrNull(shuffledStartIndex)?.resolveAlbumArtUri(getApplication())
                if (uri != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val updatedItem = mediaItems[shuffledStartIndex].buildUpon()
                            .setMediaMetadata(
                                mediaItems[shuffledStartIndex].mediaMetadata.buildUpon()
                                    .setArtworkUri(uri)
                                    .build()
                            )
                            .build()
                        if (player.currentMediaItemIndex == shuffledStartIndex) {
                            player.replaceMediaItem(shuffledStartIndex, updatedItem)
                            MusicService.requestRefresh(getApplication())
                        }
                        cachedMediaItems = cachedMediaItems.toMutableList().apply {
                            if (shuffledStartIndex < size) this[shuffledStartIndex] = updatedItem
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun initEqualizer() {
        try {
            equalizerController?.release()
            equalizerController = EqualizerController(player.audioSessionId)
            equalizerController?.init()
        } catch (_: Exception) {}
    }

    private fun setupPlayerListener() {
        // 移除旧监听器，防止累积
        playerListener?.let { player.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isTransitioning = false
                        _duration.value = player.duration.coerceAtLeast(0L)
                        android.util.Log.i("PlayLoop", "onPlaybackStateChanged: READY, currentIndex=" + player.currentMediaItemIndex + "/" + (player.mediaItemCount - 1) + ", repeatMode=" + player.repeatMode)
                    }
                    Player.STATE_ENDED -> {
                        android.util.Log.i("PlayLoop", "onPlaybackStateChanged: ENDED! currentIndex=" + player.currentMediaItemIndex + "/" + (player.mediaItemCount - 1) + ", repeatMode=" + player.repeatMode + ", playWhenReady=" + player.playWhenReady)
                    }
                    Player.STATE_BUFFERING -> {
                        android.util.Log.i("PlayLoop", "onPlaybackStateChanged: BUFFERING, currentIndex=" + player.currentMediaItemIndex)
                    }
                    Player.STATE_IDLE -> {
                        android.util.Log.i("PlayLoop", "onPlaybackStateChanged: IDLE")
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.i("PlayLoop", "onIsPlayingChanged: isPlaying=" + isPlaying + ", currentIndex=" + player.currentMediaItemIndex + "/" + (player.mediaItemCount - 1) + ", playWhenReady=" + player.playWhenReady)
                _isPlaying.value = isPlaying
                // 暂停时保存最终状态（用户可能直接退出）
                if (!isPlaying && _playlist.value.isNotEmpty()) {
                    stateManager.saveState(
                        _playlist.value,
                        player.currentMediaItemIndex,
                        _playMode.value,
                        player.currentPosition.coerceAtLeast(0L)
                    )
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // 通知栏切换播放模式后，ExoPlayer repeatMode 变化，从 PlayerStateManager 同步
                val savedMode = stateManager.getPlayMode()
                if (savedMode != _playMode.value) {
                    android.util.Log.i("PlayLoop", "onRepeatModeChanged: 同步播放模式 " + _playMode.value.label + " -> " + savedMode.label + " (repeatMode=" + repeatMode + ")")
                    _playMode.value = savedMode
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val reasonStr = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO(自动转曲)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK(跳转)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT(循环)"
                    else -> "未知(" + reason + ")"
                }
                android.util.Log.i("PlayLoop", "onMediaItemTransition: index=" + player.currentMediaItemIndex + ", reason=" + reasonStr)
                val index = player.currentMediaItemIndex

                // 随机模式完成一轮后重新洗牌（检测从最后一首循环回第一首）
                if (!isReshuffling && _playMode.value == PlayMode.SHUFFLE && index == 0 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _playlist.value.size > 1) {
                    reshuffleForNextRound()
                }

                isTransitioning = true
                _currentSong.value = _playlist.value.getOrNull(index)
                val song = _playlist.value.getOrNull(index)
                _duration.value = song?.duration ?: player.duration.coerceAtLeast(0L)
                _currentPosition.value = 0L  // 立即重置进度，避免上一首歌的进度残留
                // 切换歌曲时保存索引
                stateManager.saveState(_playlist.value, index, _playMode.value, 0L)
                // 后台预取后续歌曲
                prefetchNextSongs()

                // 异步更新当前歌曲的 artworkUri（避免创建时全部解析 672 次 IPC）
                val currentSong = _playlist.value.getOrNull(index)
                if (currentSong != null && (mediaItem?.mediaMetadata?.artworkUri == null)) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val artworkUri = currentSong.resolveAlbumArtUri(getApplication())
                            if (artworkUri != null) {
                                val updatedItem = mediaItem?.buildUpon()
                                    ?.setMediaMetadata(
                                        mediaItem.mediaMetadata.buildUpon()
                                            .setArtworkUri(artworkUri)
                                            .build()
                                    )
                                    ?.build()
                                if (updatedItem != null) {
                                    // replaceMediaItem 必须在主线程调用
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        player.replaceMediaItem(index, updatedItem)
                                        // 封面加载完成，立即刷新通知栏
                                        MusicService.requestRefresh(getApplication())
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
    }

    /** 检查播放器是否已被 idleRunnable 释放，若是则从歌单恢复 */
    fun restorePlayerIfNeeded() {
        if (player.mediaItemCount == 0 && _playlist.value.isNotEmpty()) {
            android.util.Log.i("PlayerViewModel", "restorePlayerIfNeeded: 播放器空闲释放，从歌单恢复")
            val songs = _playlist.value
            val currentSong = _currentSong.value
            val startIndex = if (currentSong != null) songs.indexOf(currentSong).coerceAtLeast(0) else 0
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(android.net.Uri.fromFile(File(song.filePath)))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(mediaItems, startIndex, stateManager.getPosition())
            player.prepare()
            // 重建播放器后清除过渡标志，避免 isTransitioning 卡死导致切歌被拦截
            isTransitioning = false
            initEqualizer()
            setupPlayerListener()
            applyPlayMode()
            startMusicService()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            restorePlayerIfNeeded()
            player.play()
        }
    }

    fun playNext() {
        restorePlayerIfNeeded()
        if (isTransitioning) {
            // 安全兜底：若播放器已处于 READY 状态（说明过渡早已完成但 isTransitioning 未正常重置），
            // 自动清除卡死标志并继续执行。例如：长时间锁屏后 idleRunnable 释放了播放器，
            // 导致 onPlaybackStateChanged(READY) 回调丢失。
            if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                android.util.Log.w("PlayLoop", "playNext: 检测到 isTransitioning 卡死, 自动重置")
                isTransitioning = false
            } else {
                android.util.Log.i("PlayLoop", "playNext: 过渡中, 忽略")
                return
            }
        }
        val prevIndex = player.currentMediaItemIndex
        android.util.Log.i("PlayLoop", "playNext: 当前索引=" + prevIndex + "/" + (player.mediaItemCount - 1) + ", isPlaying=" + player.isPlaying + ", repeatMode=" + player.repeatMode)
        player.seekToNextMediaItem()
        // 索引没变 → 已到列表末尾，手动循环到第一首
        if (player.currentMediaItemIndex == prevIndex && player.mediaItemCount > 0) {
            android.util.Log.i("PlayLoop", "playNext: 已到末尾, 手动跳到第一首")
            player.seekToDefaultPosition(0)
            // 日志：验证跳转到的是哪首歌
            val nextSong = _playlist.value.getOrNull(0)
            android.util.Log.i("PlayLoop", "playNext: 跳到第一首: \"" + (nextSong?.title ?: "-") + "\"")
        }
        if (!player.isPlaying) {
            android.util.Log.i("PlayLoop", "playNext: player.play()")
            player.play()
        }
    }

    fun playPrevious() {
        restorePlayerIfNeeded()
        if (isTransitioning) {
            // 安全兜底：若播放器已处于 READY 状态，自动清除卡死标志
            if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                android.util.Log.w("PlayLoop", "playPrevious: 检测到 isTransitioning 卡死, 自动重置")
                isTransitioning = false
            } else {
                android.util.Log.i("PlayLoop", "playPrevious: 过渡中, 忽略")
                return
            }
        }
        val prevIndex = player.currentMediaItemIndex
        android.util.Log.i("PlayLoop", "playPrevious: 当前索引=" + prevIndex + "/" + (player.mediaItemCount - 1) + ", isPlaying=" + player.isPlaying + ", repeatMode=" + player.repeatMode)
        player.seekToPreviousMediaItem()
        // 索引没变 → 已到列表开头，手动循环到最后一首
        if (player.currentMediaItemIndex == prevIndex && player.mediaItemCount > 0) {
            android.util.Log.i("PlayLoop", "playPrevious: 已到开头, 手动跳到最后一首")
            player.seekToDefaultPosition(player.mediaItemCount - 1)
        }
        if (!player.isPlaying) {
            android.util.Log.i("PlayLoop", "playPrevious: player.play()")
            player.play()
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    /**
     * 后台预取后续歌曲的歌词和封面（最多3首）
     */
    private fun prefetchNextSongs(count: Int = 3) {
        // 取消前一次预取，避免并发竞争
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val songs = _playlist.value
            val currentIndex = player.currentMediaItemIndex
            if (songs.isEmpty() || currentIndex < 0) return@launch
            val order = sourceOrderManager.getEnabledSources()
            val artOrder = albumArtSourceManager.getEnabledSources()
            for (i in 1..count) {
                val nextIndex = currentIndex + i
                if (nextIndex >= songs.size) break
                val song = songs[nextIndex]
                val key = "${song.title}|${song.artist}|${song.filePath}"
                // 封面缓存 key 需包含 filePath，确保同专辑不同文件各有独立缓存
                val artKey = "${song.title}|${song.artist}|${song.filePath}"

                // 预取歌词
                if (lyricsCache.get(key) == null) {
                    val result = prefetchFetcher.fetchLyrics(
                        title = song.title,
                        artist = song.artist,
                        filePath = song.filePath,
                        sourceOrder = order
                    )
                    if (result.lines.isNotEmpty()) {
                        lyricsCache.put(key, result.rawLrc, result.source)
                    }
                }

                // 预取封面（在 IO 线程执行，因 MediaMetadataRetriever.setDataSource 是阻塞 I/O）
                if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        song.resolveAlbumArtUri(getApplication())
                    } == null && albumArtCache.get(artKey) == null) {
                    val result = prefetchAlbumArt.fetchAlbumArtUrl(song.title, song.artist, artOrder)
                    val url = result?.second
                    if (url != null) {
                        albumArtCache.put(artKey, url)
                    }
                }
            }
        }
    }

    fun updateProgress() {
        if (isTransitioning) return  // 过渡期跳过，防止旧位置覆盖
        val pos = player.currentPosition.coerceAtLeast(0L)
        _currentPosition.value = pos

        // 每2秒自动保存一次进度
        if (pos % 2000L < 500L && _playlist.value.isNotEmpty()) {
            stateManager.saveState(
                _playlist.value,
                player.currentMediaItemIndex,
                _playMode.value,
                pos
            )
        }
    }

    fun hasSavedPlayState(): Boolean = stateManager.hasSavedState()

    /**
     * 尝试从保存的状态恢复播放
     * @return true 表示恢复成功
     */
    fun tryRestore(musicRepository: MusicRepository): Boolean {
        val t0 = System.currentTimeMillis()
        if (!stateManager.hasSavedState()) {
            android.util.Log.i("Startup_Timing", "tryRestore: 无保存状态, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
            return false
        }
        val ids = stateManager.loadPlaylistIds()
        android.util.Log.i("Startup_Timing", "tryRestore: loadPlaylistIds count=" + ids.size + ", 耗时=" + (System.currentTimeMillis() - t0) + "ms")
        if (ids.isEmpty()) {
            stateManager.clear()
            android.util.Log.i("Startup_Timing", "tryRestore: ids为空, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
            return false
        }
        val songs = ids.mapNotNull { id -> musicRepository.getSongById(id) }
        android.util.Log.i("Startup_Timing", "tryRestore: 从仓库找回 " + songs.size + " 首歌, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
        if (songs.isEmpty()) {
            stateManager.clear()
            android.util.Log.i("Startup_Timing", "tryRestore: songs为空, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
            return false
        }
        val index = stateManager.getCurrentIndex().coerceIn(0, songs.size - 1)
        val restoredPlayMode = stateManager.getPlayMode()
        val position = stateManager.getPosition()

        // 先设置播放模式，再设置播放列表
        _playMode.value = restoredPlayMode

        _playlist.value = songs
        if (songs.isEmpty()) {
            android.util.Log.i("Startup_Timing", "tryRestore: songs空, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
            return false
        }

        // 只给当前歌曲设置 artworkUri，避免 672 次主线程 IPC
        val currentSong = songs.getOrNull(index)
        val mediaItems = songs.mapIndexed { i, song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(Uri.fromFile(File(song.filePath)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        val buildMediaItemsElapsed = System.currentTimeMillis() - t0
        android.util.Log.i("Startup_Timing", "tryRestore: 构建 " + mediaItems.size + " 个 MediaItem（仅当前歌设artwork）, 耗时=" + buildMediaItemsElapsed + "ms")

        player.setMediaItems(mediaItems, index, position)
        player.prepare()
        updateMediaItemCache(mediaItems, songs)
        val prepareElapsed = System.currentTimeMillis() - t0
        android.util.Log.i("Startup_Timing", "tryRestore: setMediaItems+prepare, 耗时=" + prepareElapsed + "ms")
        // 根据启动后自动播放设置控制是否自动播放
        player.playWhenReady = stateManager.isAutoPlayEnabled()


        _currentSong.value = songs.getOrNull(index)
        _duration.value = songs.getOrNull(index)?.duration ?: 0L
        _currentPosition.value = position

        val setupT0 = System.currentTimeMillis()
        initEqualizer()
        setupPlayerListener()
        applyPlayMode()
        android.util.Log.i("Startup_Timing", "tryRestore: 初始化equalizer+listener, 耗时=" + (System.currentTimeMillis() - setupT0) + "ms")

        val svcT0 = System.currentTimeMillis()
        startMusicService()
        // 恢复播放状态时清除过渡标志，避免遗留的 isTransitioning 卡死切歌
        isTransitioning = false
        android.util.Log.i("Startup_Timing", "tryRestore: startMusicService, 耗时=" + (System.currentTimeMillis() - svcT0) + "ms")

        prefetchNextSongs()
        android.util.Log.i("Startup_Timing", "tryRestore: 全部完成, 总耗时=" + (System.currentTimeMillis() - t0) + "ms")

        // 异步解析当前歌曲的封面（避免 MediaMetadataRetriever 在主线程阻塞）
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uri = songs.getOrNull(index)?.resolveAlbumArtUri(getApplication())
                if (uri != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val updatedItem = mediaItems[index].buildUpon()
                            .setMediaMetadata(
                                mediaItems[index].mediaMetadata.buildUpon()
                                    .setArtworkUri(uri)
                                    .build()
                            )
                            .build()
                        if (player.currentMediaItemIndex == index) {
                            player.replaceMediaItem(index, updatedItem)
                        }
                        cachedMediaItems = cachedMediaItems.toMutableList().apply {
                            if (index < size) this[index] = updatedItem
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        return true
    }

    fun togglePlayMode() {
        // 先同步通知栏可能已修改的播放模式，避免基于过期值循环
        val savedMode = stateManager.getPlayMode()
        if (savedMode != _playMode.value) {
            android.util.Log.i("PlayLoop", "togglePlayMode: 同步通知栏模式 " + _playMode.value.label + " -> " + savedMode.label)
            _playMode.value = savedMode
        }
        _playMode.value = when (_playMode.value) {
            PlayMode.NORMAL -> PlayMode.REPEAT_FOLDER
            PlayMode.REPEAT_FOLDER -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.NORMAL
        }
        // 先保存到 SharedPreferences，再调用 applyPlayMode()，
        // 确保 onRepeatModeChanged 回调能读到新值，不会覆盖回旧值
        stateManager.savePlayMode(_playMode.value)
        applyPlayMode()
        stateManager.saveState(_playlist.value, player.currentMediaItemIndex, _playMode.value, player.currentPosition.coerceAtLeast(0L))
        // 立即刷新通知栏显示新模式图标
        try {
            MusicService.requestRefresh(getApplication())
        } catch (_: Exception) {}
    }

    private fun applyPlayMode() {
        android.util.Log.i("PlayLoop", "applyPlayMode: 模式=" + _playMode.value.label + ", 设置前 repeatMode=" + player.repeatMode + ", shuffle=" + player.shuffleModeEnabled)
        when (_playMode.value) {
            PlayMode.NORMAL -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = false
                android.util.Log.i("PlayLoop", "applyPlayMode: 设置后 repeatMode=OFF")
            }
            PlayMode.REPEAT_FOLDER -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
                android.util.Log.i("PlayLoop", "applyPlayMode: 设置后 repeatMode=ALL")
            }
            PlayMode.REPEAT_ONE -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
                android.util.Log.i("PlayLoop", "applyPlayMode: 设置后 repeatMode=ONE")
            }
            PlayMode.SHUFFLE -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
                android.util.Log.i("PlayLoop", "applyPlayMode: 设置后 repeatMode=ALL, shuffle=false（手动洗牌）")
            }
        }
    }

    /**
     * 手动 Fisher-Yates 洗牌，确保每首歌曲等概率出现
     * 保留当前歌曲在洗牌后的正确索引
     */
    private fun shufflePlaylist(songs: List<Song>, currentIndex: Int): Pair<List<Song>, Int> {
        val currentSong = songs.getOrNull(currentIndex) ?: return songs to currentIndex
        val shuffled = songs.toMutableList()
        // 用 java.util.Random() 确保每次构造不同种子，避免确定性重复
        shuffled.shuffle(java.util.Random())
        val newIndex = shuffled.indexOfFirst { it.id == currentSong.id }
        android.util.Log.i("PlayLoop", "shufflePlaylist: " + songs.size + "首, 原索引=" + currentIndex + ", 新索引=" + newIndex + ", 当前歌曲=\"" + (shuffled.getOrNull(newIndex)?.title ?: "-") + "\"")
        return if (newIndex >= 0) shuffled to newIndex else songs to currentIndex
    }

    /**
     * 随机播放完成一轮后重新洗牌（保留当前歌曲在索引0，打乱后续顺序）
     */
    private fun reshuffleForNextRound() {
        isReshuffling = true
        val songs = _playlist.value
        if (songs.size <= 1) {
            isReshuffling = false
            return
        }

        val currentSong = songs.first()
        val rest = songs.drop(1).toMutableList()
        rest.shuffle(java.util.Random())
        val reshuffled = listOf(currentSong) + rest
        _playlist.value = reshuffled

        // 更新 ExoPlayer 歌单（保留当前歌曲位置和进度）
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val mediaItems = reshuffled.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(android.net.Uri.fromFile(File(song.filePath)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        updateMediaItemCache(mediaItems, reshuffled)
        player.setMediaItems(mediaItems, 0, currentPos)
        player.prepare()
        applyPlayMode()
        android.util.Log.i("PlayLoop", "reshuffleForNextRound: 完成一轮随机重新洗牌，下一轮" + reshuffled.size + "首")
        isReshuffling = false
    }

    override fun onCleared() {
        super.onCleared()
        prefetchJob?.cancel()
        volumeMonitorJob?.cancel()
        playerListener?.let { player.removeListener(it) }
        equalizerController?.release()
    }

    /** 独立协程：持续监控系统音量，降到0时自动暂停 */
    private fun startVolumeMonitor() {
        volumeMonitorJob?.cancel()
        volumeMonitorJob = viewModelScope.launch {
            while (true) {
                try {
                    if (_pauseOnVolumeZero.value && player.isPlaying) {
                        val audioManager = getApplication<android.app.Application>()
                            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (audioManager != null) {
                            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (vol <= 0) {
                                player.pause()
                                android.util.Log.i("VolumeMonitor", "音量归零，已暂停播放")
                            }
                        }
                    }
                } catch (_: Exception) { }
                delay(300)
            }
        }
    }

    private fun stopVolumeMonitor() {
        volumeMonitorJob?.cancel()
        volumeMonitorJob = null
    }
}
