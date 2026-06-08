package com.example.yyplayer.data.lyrics

import android.media.MediaMetadataRetriever
import com.example.yyplayer.data.model.LyricsLine
import com.example.yyplayer.data.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 歌词获取器
 *
 * 优先级：
 * 1. 伴生 .lrc 文件（歌曲同目录下的同名 .lrc 或 Lyrics/ 子目录）
 * 2. 网络API（按用户配置顺序：LRCLIB → QQ → 网易云 → 酷我 → 酷狗）
 * 3. 音频文件内嵌歌词（ID3 USLT / FLAC 内嵌）
 */
class LyricsFetcher {

    companion object {
        private const val TAG = "LyricsFetcher"
        private const val TIMEOUT_CONNECT = 10000
        private const val TIMEOUT_READ = 15000
        private val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    /** 所有可用歌词来源映射（名称→函数引用，按默认顺序排列） */
    private val allSources = mapOf(
        "LRCLIB" to ::fetchFromLRCLIB,
        "QQ音乐" to ::fetchFromQQ,
        "网易云音乐" to ::fetchFromNetease,
        "酷我音乐" to ::fetchFromKuWo,
        "酷狗音乐" to ::fetchFromKuGou
    )

    /**
     * 获取歌词（按优先级依次尝试）
     * @return LyricsResult，lines 为空表示未找到
     */
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        filePath: String = "",
        sourceOrder: List<String>? = null,
        onSourceProgress: ((String) -> Unit)? = null
    ): LyricsResult = withContext(Dispatchers.IO) {
        // ==================== 第一步：伴生 .lrc 文件 ====================
        if (filePath.isNotEmpty()) {
            val lrcFile = findCompanionLrcFile(filePath, title, artist)
            if (lrcFile != null) {
                try {
                    val rawLrc = lrcFile.readText()
                    val lines = parseLrc(rawLrc)
                    if (lines.isNotEmpty()) {
                        android.util.Log.d(TAG, "伴生 .lrc 文件: ${lrcFile.absolutePath}")
                        return@withContext LyricsResult(lines, "伴生文件", rawLrc = rawLrc)
                    }
                } catch (_: Exception) { }
            }
        }

        // ==================== 第二步：网络API获取 ====================
        val orderedSources = sourceOrder?.mapNotNull { name ->
            allSources[name]?.let { it to name }
        } ?: allSources.entries.map { it.value to it.key }

        for ((fetcher, sourceName) in orderedSources) {
            onSourceProgress?.let { cb ->
                withContext(Dispatchers.Main) { cb(sourceName) }
            }
            var lastError: Exception? = null
            for (attempt in 1..2) {
                try {
                    val rawLrc = fetcher(title, artist)
                    if (rawLrc != null && rawLrc.isNotBlank()) {
                        val lines = parseLrc(rawLrc)
                        if (lines.isNotEmpty()) {
                            android.util.Log.d(TAG, "$sourceName 获取成功: ${lines.size} 行")
                            return@withContext LyricsResult(lines, sourceName, rawLrc = rawLrc)
                        }
                    }
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 2) {
                        kotlinx.coroutines.delay(500)
                    }
                }
            }
            if (lastError != null) {
                android.util.Log.w(TAG, "$sourceName 获取失败 (重试2次): ${lastError.message}")
            }
        }

        // ==================== 第三步：音频文件内嵌歌词 ====================
        if (filePath.isNotEmpty()) {
            val embedded = extractEmbeddedLyrics(filePath)
            if (embedded != null) {
                val lines = parseLrc(embedded)
                if (lines.isNotEmpty()) {
                    android.util.Log.d(TAG, "文件内嵌歌词成功")
                    return@withContext LyricsResult(lines, "文件内置", rawLrc = embedded)
                }
            }
        }

        LyricsResult(emptyList(), "")
    }

    // ==================== 伴生 .lrc 文件查找 ====================

    /**
     * 查找与歌曲同目录的 .lrc 文件
     * 搜索路径依次为：
     * 1. {songFilePath}.lrc（如 song.mp3.lrc）
     * 2. {folder}/{title}.lrc
     * 3. {folder}/Lyrics/{title}.lrc
     * 4. {folder}/lyrics/{title}.lrc
     */
    fun findCompanionLrcFile(filePath: String, title: String, artist: String): File? {
        val audioFile = File(filePath)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        val candidates = listOfNotNull(
            // 同名 .lrc
            File(parent, "$baseName.lrc").takeIf { it.exists() },
            // 歌名.lrc
            File(parent, "$title.lrc").takeIf { it.exists() },
            // 歌名 - 歌手.lrc
            File(parent, "$title - $artist.lrc").takeIf { it.exists() },
            // Lyrics/ 子目录
            File(parent, "Lyrics/$title.lrc").takeIf { it.exists() },
            File(parent, "lyrics/$title.lrc").takeIf { it.exists() }
        )
        return candidates.firstOrNull()
    }

    // ==================== 文件内嵌歌词提取 ====================

    /** 读取音频文件中嵌入的歌词标签 (ID3 USLT / FLAC 内嵌) */
    private fun extractEmbeddedLyrics(filePath: String): String? {
        // 方法1：MediaMetadataRetriever（多数Android版本支持 ID3 USLT）
        try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(filePath)
                // METADATA_KEY_LYRICS = 23
                @Suppress("DEPRECATION")
                val lyrics = mmr.extractMetadata(23)
                if (!lyrics.isNullOrBlank()) return lyrics
            } finally {
                mmr.release()
            }
        } catch (_: Throwable) { }

        // 方法2：部分 FLAC 文件歌词存为 VorbisComment，尝试直接读取文件开头查找歌词标签
        // 注意：只读前 100KB，防止大文件（如 58MB FLAC）OOM
        try {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) return null
            // 对于 FLAC，歌词有时以 "lyrics=" 标签形式存在
            if (filePath.lowercase().endsWith(".flac")) {
                // 只读取文件前 100KB（Vorbis 注释块通常在文件开头）
                val maxRead = minOf(file.length(), 100L * 1024L).toInt()
                val raw = ByteArray(maxRead)
                FileInputStream(file).use { it.read(raw) }
                val text = raw.toString(Charsets.UTF_8)
                // 搜索 "lyrics=" 标签
                val idx = text.indexOf("lyrics=")
                if (idx >= 0) {
                    val start = idx + 7
                    val end = text.indexOf('\u0000', start).takeIf { it >= 0 } ?: text.indexOf('=', start).takeIf { it >= 0 } ?: text.length
                    val lyricText = text.substring(start, end.coerceAtMost(start + 5000)).trim()
                    if (lyricText.isNotBlank() && (lyricText.contains("[") || lyricText.length > 20)) {
                        return lyricText
                    }
                }
            }
        } catch (_: Throwable) { }

        return null
    }

    // ==================== 网易云音乐 ====================

    private fun fetchFromNetease(title: String, artist: String): String? {
        val keyword = URLEncoder.encode("$title $artist", "UTF-8")

        // 搜索歌曲（GET 方式，更稳定）
        val searchJson = httpGet(
            "https://music.163.com/api/search/get/web?s=$keyword&type=1&limit=5&offset=0",
            headers = mapOf("Referer" to "https://music.163.com")
        ) ?: return null

        try {
            val obj = JSONObject(searchJson)
            val code = obj.optInt("code", -1)
            if (code != 200) {
                android.util.Log.w(TAG, "网易云搜索返回 code=$code")
                return null
            }
            val songs = obj.optJSONObject("result")?.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null

            // 取第一个匹配的歌曲 ID
            val songId = songs.getJSONObject(0).optLong("id", 0)
            if (songId == 0L) return null

            // 获取歌词（lv=1 请求 LRC 格式，kv=0&tv=0 不请求其他格式）
            val lyricJson = httpGet(
                "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=0&tv=0",
                headers = mapOf("Referer" to "https://music.163.com")
            ) ?: return null

            val lyricObj = JSONObject(lyricJson)
            val lrcObj = lyricObj.optJSONObject("lrc") ?: return null
            val lyric = lrcObj.optString("lyric", "")
            return lyric.ifBlank { null }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "网易云解析失败: ${e.message}")
            return null
        }
    }

    // ==================== QQ音乐 ====================

    private fun fetchFromQQ(title: String, artist: String): String? {
        val keyword = URLEncoder.encode("$title $artist", "UTF-8")

        // 搜索歌曲
        val searchJson = httpGet(
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$keyword&t=0&n=5&format=json",
            headers = mapOf("Referer" to "https://y.qq.com")
        ) ?: return null

        try {
            val obj = JSONObject(searchJson)
            val songs = obj.optJSONObject("data")
                ?.optJSONObject("song")
                ?.optJSONArray("list") ?: return null
            if (songs.length() == 0) return null

            val songMid = songs.getJSONObject(0).optString("mid", "")
            if (songMid.isEmpty()) return null

            // 获取歌词（需要 g_tk 参数）
            val lyricJson = httpGet(
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&g_tk=0&uin=0",
                headers = mapOf("Referer" to "https://y.qq.com")
            ) ?: run {
                // 备用API
                val backupJson = httpGet(
                    "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json",
                    headers = mapOf("Referer" to "https://y.qq.com")
                ) ?: return null
                backupJson
            }

            val lyricObj = JSONObject(lyricJson)
            val base64Lyric = lyricObj.optString("lyric", "")
            if (base64Lyric.isBlank()) return null
            val decodedBytes = android.util.Base64.decode(base64Lyric, android.util.Base64.DEFAULT)
            return String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "QQ音乐解析失败: ${e.message}")
            return null
        }
    }

    // ==================== 酷狗音乐 ====================

    private fun fetchFromKuGou(title: String, artist: String): String? {
        val keyword = URLEncoder.encode("$title $artist", "UTF-8")

        val searchJson = httpGet(
            "https://songsearch.kugou.com/song_search_v2?keyword=$keyword&page=1&pagesize=5"
        ) ?: return null

        try {
            val obj = JSONObject(searchJson)
            val songs = obj.optJSONObject("data")
                ?.optJSONArray("lists") ?: return null
            if (songs.length() == 0) return null

            val song = songs.getJSONObject(0)
            // 优先用 FileHash（酷狗新API），降级用 hash
            val fileHash = song.optString("FileHash", "")
            val hash = if (fileHash.isNotEmpty()) fileHash else song.optString("hash", "")
            if (hash.isEmpty()) return null

            val lyricJson = httpGet(
                "https://lyrics.kugou.com/download?ver=1&client=pc&id=$hash&accesskey=&fmt=lrc&charset=utf8",
                headers = mapOf("Referer" to "https://www.kugou.com")
            ) ?: return null

            val lyricObj = JSONObject(lyricJson)
            val status = lyricObj.optInt("status", -1)
            if (status != 200 && status != 1) return null
            val base64Lyric = lyricObj.optString("content", "")
            if (base64Lyric.isBlank()) return null
            val decodedBytes = android.util.Base64.decode(base64Lyric, android.util.Base64.DEFAULT)
            return String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "酷狗解析失败: ${e.message}")
            return null
        }
    }

    // ==================== 酷我音乐 ====================

    private fun fetchFromKuWo(title: String, artist: String): String? {
        val keyword = URLEncoder.encode("$title $artist", "UTF-8")

        val searchJson = httpGet(
            "http://search.kuwo.cn/r.s?all=$keyword&ft=music&itemset=web_2013&client=kt&encoding=utf8&pn=0&rn=5&rformat=json",
            headers = mapOf("Referer" to "http://www.kuwo.cn")
        ) ?: return null

        try {
            val obj = JSONObject(searchJson)
            val abslist = obj.optJSONArray("abslist") ?: return null
            if (abslist.length() == 0) return null

            val musicRid = abslist.getJSONObject(0).optString("MUSICRID", "")
            if (musicRid.isEmpty()) return null
            val musicId = musicRid.removePrefix("MUSIC_")
            if (musicId.isEmpty()) return null

            val lyricJson = httpGet(
                "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$musicId",
                headers = mapOf("Referer" to "http://www.kuwo.cn")
            ) ?: return null

            val lyricObj = JSONObject(lyricJson)
            val lrcList = lyricObj.optJSONArray("lrclist") ?: return null
            val sb = StringBuilder()
            for (i in 0 until lrcList.length()) {
                val item = lrcList.getJSONObject(i)
                val time = item.optString("time", "")
                val lineLyric = item.optString("lineLyric", "")
                if (time.isNotEmpty() && lineLyric.isNotEmpty()) {
                    sb.append("[$time]$lineLyric\n")
                }
            }
            return sb.toString().ifEmpty { null }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "酷我解析失败: ${e.message}")
            return null
        }
    }

    // ==================== LRCLIB（国际带时间轴歌词） ====================

    /**
     * LRCLIB 提供了带时间轴的 LRC 格式歌词，对国际和中文歌曲都有不错的覆盖。
     * API: https://lrclib.net/api/get?artist_name=X&track_name=Y
     * 返回: { "syncedLyrics": "[00:00.00]歌词...", "plainLyrics": "...", ... }
     */
    private fun fetchFromLRCLIB(title: String, artist: String): String? {
        val artistEnc = URLEncoder.encode(artist, "UTF-8")
        val titleEnc = URLEncoder.encode(title, "UTF-8")

        // 先尝试精确匹配
        val json = httpGet(
            "https://lrclib.net/api/get?artist_name=$artistEnc&track_name=$titleEnc"
        ) ?: return null

        try {
            val obj = JSONObject(json)
            // syncedLyrics 优先（带时间轴），降级到 plainLyrics
            val synced = obj.optString("syncedLyrics", "")
            if (synced.isNotBlank()) return synced
            val plain = obj.optString("plainLyrics", "")
            if (plain.isNotBlank()) {
                // plainLyrics 没有时间轴，转成 LRC 格式（每行加 [00:00.00] 占位）
                val sb = StringBuilder()
                for (line in plain.lines()) {
                    if (line.isNotBlank()) {
                        sb.append("[00:00.00]$line\n")
                    }
                }
                return sb.toString().ifEmpty { null }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "LRCLIB 解析失败: ${e.message}")
            return null
        }
    }

    // ==================== 通用 HTTP 工具 ====================

    /** GET 请求，返回响应文本，失败返回 null */
    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String? {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                for ((key, value) in headers) {
                    conn.setRequestProperty(key, value)
                }
                conn.connectTimeout = TIMEOUT_CONNECT
                conn.readTimeout = TIMEOUT_READ
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) return null

                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append('\n')
                }
                reader.close()
                return sb.toString()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "HTTP 请求失败: ${e::class.simpleName}: ${e.message}")
            return null
        }
    }

    // ==================== LRC 解析 ====================

    /** 解析 LRC 格式歌词为行列表 */
    fun parseLrc(lrcText: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val lineRegex = Regex("""\[(\d{2}):(\d{2})[\.:](\d{2,3})](.*)""")
        for (line in lrcText.lines()) {
            val match = lineRegex.find(line.trim())
            if (match != null) {
                val minutes = match.groupValues[1].toIntOrNull() ?: 0
                val seconds = match.groupValues[2].toIntOrNull() ?: 0
                val millis = when (match.groupValues[3].length) {
                    3 -> match.groupValues[3].toIntOrNull() ?: 0
                    else -> (match.groupValues[3].toIntOrNull() ?: 0) * 10
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    lines.add(LyricsLine(
                        time = minutes * 60_000L + seconds * 1000L + millis,
                        text = text
                    ))
                }
            }
        }
        lines.sortBy { it.time }
        // 校验：如果所有时间戳都远大于正常歌曲长度，丢弃（避免异常 LRC 导致显示卡死）
        if (lines.size >= 3) {
            val maxTime = lines.last().time
            val minTime = lines.first().time
            val durationRange = maxTime - minTime
            // 条件：最大时间 > 30分钟 && 时间跨度 < 60秒 → 所有时间戳集中在高段，明显异常
            if (maxTime > 1_800_000L && durationRange < 60_000L) {
                android.util.Log.w(TAG, "parseLrc: 时间戳异常，全部集中在 ${minTime/1000}~${maxTime/1000}s, " +
                        "跨度仅 ${durationRange/1000}s，丢弃此 LRC")
                return emptyList()
            }
            // 条件：最小时间 > 10分钟 → 起始时间可疑
            if (minTime > 600_000L) {
                android.util.Log.w(TAG, "parseLrc: 起始时间异常 ${minTime/1000}s，第一行时间戳过大，丢弃此 LRC")
                return emptyList()
            }
        }
        return lines
    }
}
