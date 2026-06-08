package com.example.yyplayer.data.repository

import android.content.Context
import android.net.Uri
import com.example.yyplayer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * NAS 网络音乐仓库
 * 负责扫描远程服务器的音乐文件、缓存结果、提供数据流
 *
 * 支持的协议：
 * - HTTP/HTTPS：通过解析目录列表 HTML 发现文件
 * - SMB：存储 URL 格式，实际解析需要 jcifs-ng 等第三方库（预留）
 */
class NasRepository(private val context: Context) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val cacheFile = File(context.filesDir, "nas_song_cache.json")

    private val settings = NasSettings(context)

    /** 支持扫描的音频文件扩展名 */
    companion object {
        val AUDIO_EXTENSIONS = setOf(
            ".mp3", ".flac", ".wav", ".aac", ".ogg",
            ".m4a", ".wma", ".ape", ".opus", ".aiff"
        )
        /** NAS 歌曲使用负数 ID，从 -1 开始递减 */
        private var nextNasId: Long = -1L
            get() = field--
    }

    /** 歌曲 ID 是否为 NAS 来源 */
    fun isNasSong(songId: Long): Boolean = songId < 0

    init {
        // 启动时从缓存加载
        val cached = loadCache()
        if (cached.isNotEmpty()) {
            _songs.value = cached
        }
    }

    /**
     * 扫描 NAS 服务器
     * 支持 HTTP/HTTPS 目录列表和 SMB URL 存储
     */
    suspend fun scan(): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = settings.getServerUrl().trim()
        if (serverUrl.isBlank()) {
            _lastError.value = "请先设置服务器地址"
            return@withContext false
        }

        _isScanning.value = true
        _lastError.value = null
        try {
            val songs = mutableListOf<Song>()
            val url = normalizeUrl(serverUrl)

            when {
                url.startsWith("http://") || url.startsWith("https://") -> {
                    // HTTP 扫描：递归解析目录列表
                    scanHttpDirectory(url, songs, maxDepth = 3)
                }
                url.startsWith("smb://") -> {
                    // SMB 扫描：存储路径信息，实际解析需 jcifs-ng 支持
                    // 当前仅记录路径，用户可以手动添加文件或等待后续协议支持
                    _lastError.value = "SMB 协议需要第三方库支持，请改用 HTTP"
                }
                else -> {
                    _lastError.value = "不支持的协议，请使用 http:// 或 https://"
                }
            }

            if (songs.isNotEmpty()) {
                _songs.value = songs
                saveCache(songs)
                settings.setLastSyncTime(System.currentTimeMillis())
                return@withContext true
            } else if (_lastError.value == null) {
                _lastError.value = "未找到音乐文件，请检查路径"
            }
            return@withContext false
        } catch (e: Exception) {
            _lastError.value = "连接失败: ${e.localizedMessage ?: e.message ?: "未知错误"}"
            return@withContext false
        } finally {
            _isScanning.value = false
        }
    }

    /** 是否已扫描且有数据 */
    fun hasSongs(): Boolean = _songs.value.isNotEmpty()

    /** 清空缓存 */
    fun clear() {
        _songs.value = emptyList()
        cacheFile.delete()
        settings.setLastSyncTime(0L)
    }

    // ========== HTTP 目录扫描 ==========

    /**
     * 递归扫描 HTTP 目录
     * 解析 Apache/Nginx 风格的 HTML 目录列表，也支持简易 JSON 列表
     */
    private fun scanHttpDirectory(
        url: String,
        results: MutableList<Song>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth) return
        val lines = fetchUrlLines(url) ?: return
        val content = lines.joinToString("\n")

        // 判断返回类型
        val isJson = lines.firstOrNull()?.trimStart()?.startsWith("[") == true
                || lines.firstOrNull()?.trimStart()?.startsWith("{") == true

        if (isJson) {
            // JSON 列表格式
            parseJsonFileList(content, results, url)
        } else {
            // HTML 目录列表格式
            parseHtmlFileList(content, lines, results, url, maxDepth, currentDepth)
        }
    }

    /** 从 URL 读取文本行 */
    private fun fetchUrlLines(url: String): List<String>? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "text/html,application/json,*/*")
            conn.setRequestProperty("User-Agent", "MuMuPlayer-NAS/1.0")

            if (settings.getUsername().isNotBlank()) {
                val auth = android.util.Base64.encodeToString(
                    "${settings.getUsername()}:${settings.getPassword()}".toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                conn.setRequestProperty("Authorization", "Basic $auth")
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) return null

            BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                .readLines()
        } catch (_: Exception) {
            null
        }
    }

    /** 解析 HTML 目录列表（兼容 Apache/Nginx/简单 HTTP 服务器） */
    private fun parseHtmlFileList(
        html: String,
        lines: List<String>,
        results: MutableList<Song>,
        baseUrl: String,
        maxDepth: Int,
        currentDepth: Int
    ) {
        // 匹配 <a href="xxx"> 中的链接
        val linkPattern = Pattern.compile(
            """<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = linkPattern.matcher(html)

        while (matcher.find()) {
            val hrefGroup = matcher.group(1) ?: continue
            var href = hrefGroup
            // 忽略父目录链接、锚点、查询参数链接
            if (href == "../" || href == "./" || href == "/" || href.startsWith("?")
                || href.contains("..") || href.contains("#")
            ) continue
            // 去掉尾部空格
            href = href.trimEnd('/')

            val fullUrl = resolveUrl(baseUrl, href)

            if (href.endsWith("/")) {
                // 子目录 — 递归扫描
                scanHttpDirectory(fullUrl, results, maxDepth, currentDepth + 1)
            } else {
                // 文件 — 检查是否为音频
                val lower = href.lowercase()
                if (AUDIO_EXTENSIONS.any { lower.endsWith(it) }) {
                    // 从文件名提取标题
                    val fileName = href.substringAfterLast("/").substringBeforeLast(".")
                    // 尝试提取 "artist - title" 格式
                    val (title, artist) = parseFileName(fileName)
                    results.add(
                        Song(
                            id = nextNasId,
                            title = title,
                            artist = artist,
                            album = "NAS 音乐",
                            filePath = fullUrl,
                            size = 0L,
                            dateAdded = System.currentTimeMillis() / 1000
                        )
                    )
                }
            }
        }
    }

    /** 解析简易 JSON 格式的文件列表 */
    private fun parseJsonFileList(
        content: String,
        results: MutableList<Song>,
        baseUrl: String
    ) {
        try {
            val jsonArray = JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val name = item.optString("name", "")
                val isDir = item.optBoolean("isDir", false) || item.optBoolean("directory", false)
                val path = item.optString("path", name)

                if (isDir) {
                    // 递归扫描子目录（最多再深入一层）
                    scanHttpDirectory(resolveUrl(baseUrl, path), results, maxDepth = 1, currentDepth = 1)
                } else {
                    val lower = name.lowercase()
                    if (AUDIO_EXTENSIONS.any { lower.endsWith(it) }) {
                        val fileName = name.substringBeforeLast(".")
                        val (title, artist) = parseFileName(fileName)
                        results.add(
                            Song(
                                id = nextNasId,
                                title = title,
                                artist = artist,
                                album = "NAS 音乐",
                                filePath = resolveUrl(baseUrl, path),
                                size = item.optLong("size", 0L),
                                dateAdded = System.currentTimeMillis() / 1000
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // JSON 解析失败，跳过
        }
    }

    /** 从文件名猜测 "艺术家 - 标题" 格式 */
    private fun parseFileName(fileName: String): Pair<String, String> {
        val dashPattern = Regex("""^\s*(.+?)\s*-\s*(.+?)\s*$""")
        val match = dashPattern.find(fileName)
        return if (match != null) {
            Pair(match.groupValues[2].trim(), match.groupValues[1].trim())
        } else {
            Pair(fileName.trim(), "未知艺术家")
        }
    }

    /** 拼接完整 URL */
    private fun resolveUrl(base: String, href: String): String {
        return try {
            URL(URL(base), href).toString()
        } catch (_: Exception) {
            base.trimEnd('/') + "/" + href.trimStart('/')
        }
    }

    /** 确保 URL 以 / 结尾 */
    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.endsWith("/")) "$trimmed/" else trimmed
    }

    // ========== 缓存 ==========

    private fun saveCache(songs: List<Song>) {
        try {
            val jsonArray = JSONArray()
            for (song in songs) {
                val obj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("duration", song.duration)
                    put("filePath", song.filePath)
                    put("albumArtUri", song.albumArtUri?.toString() ?: "")
                    put("size", song.size)
                    put("dateAdded", song.dateAdded)
                }
                jsonArray.put(obj)
            }
            cacheFile.writeText(jsonArray.toString(2))
        } catch (_: Exception) {}
    }

    private fun loadCache(): List<Song> {
        try {
            if (!cacheFile.exists()) return emptyList()
            val text = cacheFile.readText()
            if (text.isBlank()) return emptyList()
            val jsonArray = JSONArray(text)
            val songs = mutableListOf<Song>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                songs.add(
                    Song(
                        id = obj.optLong("id"),
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        album = obj.optString("album", ""),
                        duration = obj.optLong("duration"),
                        filePath = obj.optString("filePath", ""),
                        albumArtUri = obj.optString("albumArtUri", "").ifEmpty { null }?.let { Uri.parse(it) },
                        size = obj.optLong("size"),
                        dateAdded = obj.optLong("dateAdded")
                    )
                )
            }
            return songs
        } catch (_: Exception) {
            return emptyList()
        }
    }
}
