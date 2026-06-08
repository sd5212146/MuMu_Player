package com.example.yyplayer.data.repository

import android.util.Log
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.scanner.MusicScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicRepository(
    private val scanner: MusicScanner
) {

    companion object {
        private const val TAG = "MusicRepository"
    }

    private val songCache = SongCache(scanner.context)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** 是否已加载过，避免重复扫描 */
    private var hasLoaded = false

    /** 全量扫描缓存（未按路径过滤），避免多次全量扫描 */
    private var fullScanCache: List<Song>? = null

    /** 上一次设置的手动扫描路径（供 refreshMusic 恢复路径过滤使用） */
    private var lastManualPaths: List<String> = emptyList()

    init {
        // 启动时从缓存加载，秒开；缓存有效则不再触发全量扫描
        val t0 = System.currentTimeMillis()
        val cached = songCache.load()
        if (cached.isNotEmpty()) {
            _songs.value = cached
            hasLoaded = true
            Log.i(TAG, "init: 从缓存加载 " + cached.size + " 首歌曲, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
        } else {
            Log.d(TAG, "init: 缓存为空, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
        }
    }

    suspend fun refreshMusic(force: Boolean = false) {
        if (!force && hasLoaded) {
            Log.d(TAG, "refreshMusic: 已加载过，跳过 (hasLoaded=true)")
            return
        }
        _isScanning.value = true
        Log.i(TAG, "refreshMusic: 开始扫描 (force=$force)...")
        val t0 = System.currentTimeMillis()
        try {
            val result = scanner.scanAllMusic()
            fullScanCache = result // 缓存全量结果
            val elapsed = System.currentTimeMillis() - t0
            // 如果设置了手动扫描路径，只保留这些路径的歌曲
            val filtered = if (lastManualPaths.isNotEmpty()) {
                result.filter { song ->
                    lastManualPaths.any { path -> song.filePath.startsWith(path) }
                }
            } else {
                result
            }
            _songs.value = filtered
            hasLoaded = true
            Log.i(TAG, "refreshMusic: 扫描完成, ${result.size}→${filtered.size} 首 (路径: ${lastManualPaths.size}), 耗时 ${elapsed}ms")
            songCache.save(filtered)
            Log.d(TAG, "refreshMusic: 缓存已保存")
        } catch (e: Exception) {
            Log.e(TAG, "refreshMusic: 扫描异常: ${e::class.simpleName}: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    fun getSongById(id: Long): Song? {
        return _songs.value.find { it.id == id }
    }

    /** 全量扫描后按手动路径过滤，更新 _songs 和缓存 */
    suspend fun scanAndFilterByPaths(paths: List<String>) {
        Log.i(TAG, "scanAndFilterByPaths: paths=$paths")
        _isScanning.value = true
        try {
            fullScanCache = scanner.scanAllMusic()
            applyPathFilter(paths)
        } catch (e: Exception) {
            Log.e(TAG, "scanAndFilterByPaths 异常: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    /** 用已有全量缓存重新按路径过滤（不重新扫描） */
    fun filterExistingByPaths(paths: List<String>) {
        Log.i(TAG, "filterExistingByPaths: paths=$paths")
        applyPathFilter(paths)
    }

    /** 是否有全量扫描缓存 */
    fun hasFullScanCache(): Boolean = fullScanCache != null

    /** 从外部注入手动扫描路径（供 LibraryViewModel 启动时设置） */
    fun setManualPaths(paths: List<String>) {
        lastManualPaths = paths.toList()
        Log.i(TAG, "setManualPaths: ${paths.size} 个路径")
    }

    /** 清空手动扫描：清除缓存、清空歌曲列表 */
    fun clearManualScan() {
        Log.d(TAG, "clearManualScan: 清空手动扫描")
        lastManualPaths = emptyList()
        fullScanCache = null
        _songs.value = emptyList()
        hasLoaded = false
        songCache.clear()
    }

    private fun applyPathFilter(paths: List<String>) {
        lastManualPaths = paths
        val allSongs = fullScanCache
        if (allSongs == null) {
            _songs.value = emptyList()
            return
        }
        val filtered = if (paths.isNotEmpty()) {
            allSongs.filter { song ->
                paths.any { path -> song.filePath.startsWith(path) }
            }
        } else {
            emptyList()
        }
        _songs.value = filtered
        hasLoaded = true
        songCache.save(filtered)
        Log.i(TAG, "applyPathFilter: ${allSongs.size} → ${filtered.size} 首 (路径: ${paths.size})")
    }

    fun forceRefresh() {
        Log.d(TAG, "forceRefresh: 重置扫描状态")
        hasLoaded = false
    }

    /** 从列表中移除指定歌曲（不删除文件），并更新缓存 */
    fun removeSong(song: Song) {
        val filtered = _songs.value.filter { it.id != song.id }
        _songs.value = filtered
        songCache.save(filtered)
        Log.i(TAG, "removeSong: 已移除 ${song.title}，剩余 ${filtered.size} 首")
    }
}
