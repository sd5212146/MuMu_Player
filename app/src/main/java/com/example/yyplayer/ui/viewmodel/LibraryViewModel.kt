package com.example.yyplayer.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yyplayer.data.model.FolderGroup
import com.example.yyplayer.data.model.Song
import com.example.yyplayer.data.repository.HiddenFolderManager
import com.example.yyplayer.data.repository.MusicRepository
import com.example.yyplayer.data.repository.NasRepository
import com.example.yyplayer.data.repository.NasSettings
import com.example.yyplayer.data.repository.FavoritesManager
import com.example.yyplayer.data.scanner.MusicScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val KEY_AUTO_SCAN = "auto_scan_enabled"
        private const val KEY_MIN_SONGS = "min_songs_threshold"
        private const val KEY_AUTO_SCAN_CUSTOM_DIRS = "auto_scan_custom_dirs"
        private const val PREFS_SCAN = "manual_scan_settings"
        private const val KEY_SCAN_PATHS = "scan_paths"

        /** 获取保存的扫描路径列表 */
        fun getScanPaths(context: Context): List<String> {
            val set = context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)
                .getStringSet(KEY_SCAN_PATHS, emptySet()) ?: emptySet()
            return set.toList()
        }

        /** 是否启用自动扫描 */
        fun isAutoScanEnabled(context: Context): Boolean {
            return context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_SCAN, true)
        }

        /** 设置自动扫描开关 */
        fun setAutoScanEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_SCAN, enabled).apply()
        }

        /** 是否启用启动时自动扫描自定义目录 */
        fun isAutoScanCustomDirsEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_SCAN_CUSTOM_DIRS, false)
        }

        /** 设置启动时自动扫描自定义目录开关 */
        fun setAutoScanCustomDirsEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_SCAN_CUSTOM_DIRS, enabled).apply()
        }

        /** 获取最小歌曲数阈值（全局） */
        fun getMinSongsThreshold(context: Context): Int {
            return context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
                .getInt(KEY_MIN_SONGS, 2)
        }

        /** 设置最小歌曲数阈值 */
        fun setMinSongsThreshold(context: Context, threshold: Int) {
            context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
                .edit().putInt(KEY_MIN_SONGS, threshold).apply()
        }
    }

    private val repository = MusicRepository(MusicScanner(application.applicationContext))
    val hiddenFolderManager = HiddenFolderManager(application.applicationContext)
    val favoritesManager = FavoritesManager(application.applicationContext)
    val nasRepository = NasRepository(application.applicationContext)
    val nasSettings = NasSettings(application.applicationContext)
    private val favoritesPrefs = application.applicationContext.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
    private val nasPrefs = application.applicationContext.getSharedPreferences("nas_settings", Context.MODE_PRIVATE)
    private val libraryPrefs = application.applicationContext.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    /** 存储卷根路径 → 系统本地化卷名（如 "内部存储"、"SD 卡 (Samsung)"） */
    private val volumeNameMap: Map<String, String> by lazy { buildVolumeNameMap() }

    private fun buildVolumeNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val sm = getApplication<android.app.Application>()
                .getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (sv in sm.storageVolumes) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val dir = sv.directory ?: continue
                    val desc = sv.getDescription(getApplication()) ?: continue
                    map[dir.absolutePath.trimEnd('/')] = desc
                }
            }
        } catch (_: Exception) { }
        return map
    }

    private val favoritesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "ids") {
            _folderGroups.value = groupByFolder(_filteredSongs.value)
        }
    }

    private val nasSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "nas_enabled") {
            _folderGroups.value = groupByFolder(_filteredSongs.value)
        }
    }

    val songs: StateFlow<List<Song>> = repository.songs
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val nasSongs: StateFlow<List<Song>> = nasRepository.songs
    val isNasScanning: StateFlow<Boolean> = nasRepository.isScanning
    val nasError: StateFlow<String?> = nasRepository.lastError

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()

    private val _folderGroups = MutableStateFlow<List<FolderGroup>>(emptyList())
    val folderGroups: StateFlow<List<FolderGroup>> = _folderGroups.asStateFlow()

    private val _lastOpenedFolderPath = MutableStateFlow<String?>(libraryPrefs.getString("last_folder", null))
    val lastOpenedFolderPath: StateFlow<String?> = _lastOpenedFolderPath.asStateFlow()

    fun setLastOpenedFolder(folderPath: String?) {
        _lastOpenedFolderPath.value = folderPath
        libraryPrefs.edit().putString("last_folder", folderPath).apply()
    }

    fun getRepository(): MusicRepository = repository

    init {
        favoritesPrefs.registerOnSharedPreferenceChangeListener(favoritesListener)
        nasPrefs.registerOnSharedPreferenceChangeListener(nasSettingsListener)
        viewModelScope.launch {
            repository.songs.collect { songs ->
                val t0 = System.currentTimeMillis()
                val filtered = filterSongs(songs, _searchQuery.value)
                _filteredSongs.value = filtered
                _folderGroups.value = groupByFolder(filtered)
                Log.i(TAG, "songs更新: " + songs.size + "/" + filtered.size + " 首, 分组=" + _folderGroups.value.size + " 个, 耗时=" + (System.currentTimeMillis() - t0) + "ms")
            }
        }
        // 搜索查询变化时重新过滤所有歌曲（包括 NAS）
        viewModelScope.launch {
            _searchQuery.collect { query ->
                val allSongs = repository.songs.value + nasRepository.songs.value
                val filtered = filterSongs(allSongs, query)
                _filteredSongs.value = filtered
                _folderGroups.value = groupByFolder(filtered)
            }
        }
        // NAS 歌曲变化时刷新分组
        viewModelScope.launch {
            nasRepository.songs.collect {
                _folderGroups.value = groupByFolder(_filteredSongs.value)
            }
        }
        // 启动时尝试加载：自动扫描自定义目录优先，其次全局自动扫描
        viewModelScope.launch {
            val ctx = getApplication<android.app.Application>()
            val customPaths = getScanPaths(ctx)
            if (isAutoScanCustomDirsEnabled(ctx) && customPaths.isNotEmpty()) {
                // 启用「自动扫描自定义目录」且有路径：注入路径后强制重新扫描
                Log.i(TAG, "启动时自动扫描自定义目录: ${customPaths.size} 个路径")
                repository.setManualPaths(customPaths)
                repository.forceRefresh()
                repository.refreshMusic()
            } else if (isAutoScanEnabled(ctx)) {
                repository.refreshMusic()
            } else {
                Log.i(TAG, "自动扫描已关闭，跳过启动扫描")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        favoritesPrefs.unregisterOnSharedPreferenceChangeListener(favoritesListener)
        nasPrefs.unregisterOnSharedPreferenceChangeListener(nasSettingsListener)
    }

    fun refreshMusic() {
        Log.d(TAG, "refreshMusic: 触发重新扫描")
        hiddenFolderManager.clearAll()
        viewModelScope.launch {
            repository.forceRefresh()
            repository.refreshMusic()
            // 扫描完成后强制重新分组，确保最新的阈值/隐藏设置生效
            Log.d(TAG, "refreshMusic: 扫描完成，强制重新分组")
            _folderGroups.value = groupByFolder(_filteredSongs.value)
        }
    }

    /** 强制重新分组（由外部在阈值/隐藏变更后调用） */
    fun recomputeFolderGroups() {
        Log.d(TAG, "recomputeFolderGroups: 强制重新分组")
        _folderGroups.value = groupByFolder(_filteredSongs.value)
    }

    /** 扫描 NAS 服务器 */
    fun scanNas() {
        viewModelScope.launch {
            nasRepository.scan()
        }
    }

    /** 清空 NAS 缓存 */
    fun clearNas() {
        nasRepository.clear()
        _folderGroups.value = groupByFolder(_filteredSongs.value)
    }

    /** 获取需要自动打开的文件夹路径（由 currentSong 推断）*/
    fun getFolderForSong(songId: Long, songs: List<Song>): String? {
        return songs.find { it.id == songId }?.folderPath
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    /** 隐藏/取消隐藏文件夹 */
    fun toggleHiddenFolder(folderPath: String) {
        hiddenFolderManager.toggleHidden(folderPath)
        // 原地刷新分组（不需要重新扫描）
        _folderGroups.value = groupByFolder(_filteredSongs.value)
    }

    private fun filterSongs(songs: List<Song>, query: String): List<Song> {
        return if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }

    private fun groupByFolder(songs: List<Song>): List<FolderGroup> {
        val context = getApplication<android.app.Application>()
        val hiddenPaths = hiddenFolderManager.getHiddenPaths()
        val minSongs = getMinSongsThreshold(context)
        Log.d(TAG, "groupByFolder: songs=${songs.size}, minSongsThreshold=$minSongs")
        val grouped = songs.groupBy { it.folderPath }
            .filterKeys { it !in hiddenPaths }
            .filter { (folderPath, songsInFolder) ->
                val keep = songsInFolder.size >= minSongs
                if (!keep) {
                    Log.d(TAG, "groupByFolder: 跳过目录(歌曲数=${songsInFolder.size} < $minSongs): $folderPath")
                }
                keep
            }
            .map { (folderPath, songsInFolder) ->
                // 存储卷根目录优先用系统本地化名称，其次用 Song.folderName
                val folderName = volumeNameMap[folderPath]
                    ?: songsInFolder.first().folderName
                FolderGroup(
                    folderName = folderName,
                    folderPath = folderPath,
                    songs = songsInFolder.sortedBy { it.title }
                )
            }
            .sortedWith(compareBy<FolderGroup> { it.folderName.firstOrNull()?.isLetter() == true }.thenBy { it.folderName.lowercase() })
            .toMutableList()

        // 红心收藏文件夹（始终置顶显示，空时显示0首）
        val favoriteIds = favoritesManager.getIds()
        val favoriteSongs = songs.filter { it.id in favoriteIds }
        grouped.add(0, FolderGroup(
            folderName = "红心收藏",
            folderPath = "__favorites__",
            songs = favoriteSongs.sortedBy { it.title }
        ))

        // NAS 网络音乐文件夹（红心收藏下方，开启即显示）
        if (nasSettings.isEnabled()) {
            val nasSongList = nasRepository.songs.value
            grouped.add(1, FolderGroup(
                folderName = "NAS 网络",
                folderPath = "__nas__",
                songs = nasSongList.sortedBy { it.title }
            ))
        }

        return grouped
    }
}
