package com.example.yyplayer.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.SSLException

/**
 * 网络专辑封面获取器
 *
 * 策略：
 * 1. 先用「歌名+歌手」搜索
 * 2. 搜不到再用「纯歌名」搜索
 * 3. 再试「歌手+歌名」（部分 API 对歌手在前匹配更好）
 * 4. 依次尝试 iTunes → Deezer → MusicBrainz → 网易云 → QQ → 酷狗
 * 5. 兜底: 第三方聚合 API（Meting 聚合源）
 */
class AlbumArtFetcher {

    companion object {
        private const val TAG = "AlbumArtFetcher"
        private const val TIMEOUT_CONNECT = 10000
        private const val TIMEOUT_READ = 15000
        private val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        /** Meting API 是否已确认不可用（返回 HTML 而非 JSON） */
        private var metingDead = false
        private var metingFailCount = 0
        private var totalSearches = 0
        private var totalHits = 0
        /** 各源命中统计 */
        private val sourceHits = java.util.concurrent.ConcurrentHashMap<String, Int>()
    }

    /** 所有可用封面来源映射（按可靠性排序） */
    private val allSources = mapOf(
        "iTunes" to ::fetchFromITunes,
        "Deezer" to ::fetchFromDeezer,
        "MusicBrainz" to ::fetchFromMusicBrainz,
        "网易云音乐" to ::fetchFromNetease,
        "QQ音乐" to ::fetchFromQQ,
        "酷狗音乐" to ::fetchFromKuGou
    )

    /**
     * 搜索封面 URL（返回 Pair<源名, URL>，方便调用方在下载失败时排除当前源重试）
     * @param skipSources 需要跳过的源名集合（之前下载失败的源）
     */
    suspend fun fetchAlbumArtUrl(
        title: String,
        artist: String,
        sourceOrder: List<String>? = null,
        searchId: String = "",
        skipSources: Set<String> = emptySet()
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val id = searchId.ifBlank { title.take(20) }

        // 过滤无效 artist（<unknown>、空白等占位符）
        val cleanArtist = if (artist.isBlank() ||
            artist.equals("<unknown>", ignoreCase = true) ||
            artist.equals("未知", ignoreCase = true)
        ) {
            android.util.Log.v(TAG, "[$id] 忽略无效 artist: «$artist»，仅用歌名搜索")
            ""
        } else artist

        val orderedSources = if (sourceOrder != null) {
            // 用户指定排序时，仅使用列表中存在的源（尊重用户禁用选择）
            sourceOrder.mapNotNull { name ->
                allSources[name]?.let { it to name }
            }.filter { (_, name) -> name !in skipSources }
        } else {
            allSources.map { (name, fetcher) -> fetcher to name }.filter { (_, name) ->
                name !in skipSources
            }
        }

        // 三种搜索策略：歌名+歌手 → 纯歌名 → 歌手+歌名
        val queryLabels = mutableListOf<String>()
        val queries = mutableListOf<String>()

        val q1 = if (cleanArtist.isNotBlank()) "$title $cleanArtist" else title
        queries.add(q1); queryLabels.add(if (cleanArtist.isNotBlank()) "歌名+歌手" else "纯歌名")

        queries.add(title); queryLabels.add("纯歌名")

        if (cleanArtist.isNotBlank() && "$title $cleanArtist" != "$cleanArtist $title") {
            queries.add("$cleanArtist $title"); queryLabels.add("歌手+歌名")
        }

        android.util.Log.i(TAG, "[$id] 开始搜索: title=«$title» artist=«${cleanArtist.ifBlank { "(空)" }}» 策略=$queryLabels")

        // —— 阶段1：直接源搜索 ——
        for ((qi, query) in queries.withIndex()) {
            for ((fetcher, sourceName) in orderedSources) {
                var lastError: Exception? = null
                for (attempt in 1..2) {
                    val t0 = System.currentTimeMillis()
                    try {
                        val url = fetcher(query)
                        val elapsed = System.currentTimeMillis() - t0
                        if (url != null) {
                                totalHits++
                            sourceHits.merge(sourceName, 1, Int::plus)
                            android.util.Log.i(TAG, "[$id] ✓ $sourceName[${queryLabels[qi]}] ${elapsed}ms → $url")
                            return@withContext sourceName to url
                        } else {
                            android.util.Log.d(TAG, "[$id] ✗ $sourceName[${queryLabels[qi]}] ${elapsed}ms → 无结果")
                        }
                        break
                    } catch (e: Exception) {
                        lastError = e
                        val elapsed = System.currentTimeMillis() - t0
                        android.util.Log.w(TAG, "[$id] ✗ $sourceName[${queryLabels[qi]}] ${elapsed}ms 异常: ${e::class.simpleName}: ${e.message}")
                        if (attempt < 2) {
                            android.util.Log.d(TAG, "[$id] 重试 $sourceName[${queryLabels[qi]}]...")
                            kotlinx.coroutines.delay(500)
                        }
                    }
                }
                if (lastError != null) {
                    android.util.Log.w(TAG, "[$id] ✗ $sourceName[${queryLabels[qi]}] 放弃（重试2次均失败）")
                }
            }
        }

        // —— 阶段2：第三方聚合 API 兜底 ——
        val phase2Start = System.currentTimeMillis()
        for ((qi, query) in queries.withIndex()) {
            val result = tryMetingApi(query, id)
            if (result != null) {
                val elapsed = System.currentTimeMillis() - phase2Start
                android.util.Log.i(TAG, "[$id] ✓ Meting[${queryLabels[qi]}] ${elapsed}ms → $result")
                return@withContext "Meting" to result
            }
        }

        val totalElapsed = System.currentTimeMillis() - startTime
        totalSearches++
        android.util.Log.w(TAG, "[$id] ✗ 所有源均失败，耗时 ${totalElapsed}ms  |  全局统计: ${totalSearches}次搜索 ${totalHits}次命中 (${
            if (totalSearches > 0) (totalHits * 100 / totalSearches) else 0
        }%)  各源: ${sourceHits.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key}=${it.value}" }}")
        null
    }

    /**
     * Meting 聚合 API 兜底搜索
     * 依次尝试 网易云/QQ/酷狗/酷我 四个源
     * 如果某个源返回 HTML（API 已死），标记 metingDead 并跳过后续全部调用
     */
    private fun tryMetingApi(keyword: String, id: String = ""): String? {
        // 一旦确认 Meting 死了，直接跳过，不再浪费任何时间
        if (metingDead) {
            metingFailCount++
            android.util.Log.v(TAG, "[$id] Meting 已标记不可用，跳过（累计跳过 $metingFailCount 次）")
            return null
        }

        val servers = listOf("netease", "tencent", "kugou", "kuwo")
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        for (server in servers) {
            val t0 = System.currentTimeMillis()
            try {
                val json = httpGet(
                    "https://api.injahow.cn/meting/?type=search&name=$encoded&server=$server&count=1",
                    headers = mapOf("Referer" to "https://api.injahow.cn/meting/")
                ) ?: continue

                val elapsed = System.currentTimeMillis() - t0
                // 如果返回的是 HTML（<!DOCTYPE...），说明 API 已失效
                if (json.isNotEmpty() && json[0] == '<') {
                    android.util.Log.w(TAG, "[$id] Meting($server) ${elapsed}ms 返回 HTML（<!DOCTYPE...），标记为不可用")
                    metingDead = true
                    return null
                }

                val arr = org.json.JSONArray(json)
                if (arr.length() == 0) {
                    android.util.Log.d(TAG, "[$id] Meting($server) ${elapsed}ms → 无结果")
                    continue
                }
                val pic = arr.getJSONObject(0).optString("pic", "")
                if (pic.isNotEmpty()) {
                    android.util.Log.d(TAG, "[$id] ✓ Meting($server) ${elapsed}ms → $pic")
                    return pic
                } else {
                    android.util.Log.d(TAG, "[$id] Meting($server) ${elapsed}ms → 有结果但无 pic 字段")
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - t0
                android.util.Log.w(TAG, "[$id] Meting($server) ${elapsed}ms 异常: ${e::class.simpleName}: ${e.message}")
            }
        }
        return null
    }

    // ==================== 统一 HTTP 请求 ====================

    /** 发起 GET 请求并返回 JSON 字符串，失败返回 null */
    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): String? {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                for ((key, value) in headers) {
                    conn.setRequestProperty(key, value)
                }
                conn.connectTimeout = TIMEOUT_CONNECT
                conn.readTimeout = TIMEOUT_READ
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    android.util.Log.w(TAG, "HTTP $code for ${urlStr.take(80)}...")
                    return null
                }

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
        } catch (e: SSLException) {
            android.util.Log.w(TAG, "SSL 错误 ${e.message}: $urlStr")
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "HTTP 请求失败: ${e::class.simpleName}: ${e.message}")
            return null
        }
    }

    /** 发起 POST 请求并返回 JSON 字符串，失败返回 null */
    private fun httpPost(urlStr: String, postData: String, headers: Map<String, String> = emptyMap()): String? {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                for ((key, value) in headers) {
                    conn.setRequestProperty(key, value)
                }
                conn.connectTimeout = TIMEOUT_CONNECT
                conn.readTimeout = TIMEOUT_READ

                conn.outputStream.write(postData.toByteArray())

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    android.util.Log.w(TAG, "HTTP $code for $urlStr")
                    return null
                }

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
            android.util.Log.w(TAG, "HTTP POST 失败: ${e::class.simpleName}: ${e.message}")
            return null
        }
    }

    // ==================== Deezer（免费免 Key，全球最稳定） ====================

    private fun fetchFromDeezer(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = httpGet(
            "https://api.deezer.com/search?q=$encoded&limit=5",
            headers = mapOf("Accept" to "application/json")
        ) ?: return null

        try {
            val obj = JSONObject(json)
            val data = obj.optJSONArray("data") ?: return null
            if (data.length() == 0) return null

            for (i in 0 until data.length()) {
                val album = data.getJSONObject(i).optJSONObject("album") ?: continue
                val cover = album.optString("cover_big", "")
                if (cover.isNotEmpty()) {
                    return cover  // 500x500 大图
                }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Deezer 解析失败: ${e.message}")
            return null
        }
    }

    // ==================== MusicBrainz Cover Art Archive（免费，全球最全元数据） ====================
    // 需要两步：1) 搜索 recording 获取 release MBID  2) 从 Cover Art Archive 获取封面

    private fun fetchFromMusicBrainz(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        // MusicBrainz 要求标识 UA：应用名/版本 ( 联系方式 )
        val mbHeaders = mapOf(
            "User-Agent" to "YYPlayer/1.0 ( com.example.yyplayer )",
            "Accept" to "application/json"
        )

        // Step 1: 搜索 recording
        val searchJson = httpGet(
            "https://musicbrainz.org/ws/2/recording/?query=$encoded&fmt=json&limit=3",
            headers = mbHeaders
        ) ?: return null

        try {
            val searchObj = JSONObject(searchJson)
            val recordings = searchObj.optJSONArray("recordings") ?: return null
            if (recordings.length() == 0) return null

            for (i in 0 until recordings.length()) {
                val recording = recordings.getJSONObject(i)
                val releases = recording.optJSONArray("releases") ?: continue
                if (releases.length() == 0) continue

                for (j in 0 until releases.length()) {
                    val release = releases.getJSONObject(j)
                    val releaseId = release.optString("id", "")
                    if (releaseId.isEmpty()) continue

                    // Step 2: 从 Cover Art Archive 获取封面
                    val coverJson = httpGet(
                        "https://coverartarchive.org/release/$releaseId/",
                        headers = mbHeaders
                    ) ?: continue

                    try {
                        val coverObj = JSONObject(coverJson)
                        val images = coverObj.optJSONArray("images") ?: continue
                        if (images.length() == 0) continue

                        for (k in 0 until images.length()) {
                            val image = images.getJSONObject(k)
                            if (image.optBoolean("front", false)) {
                                val thumbnails = image.optJSONObject("thumbnails")
                                if (thumbnails != null) {
                                    val large = thumbnails.optString("1200", "")
                                    if (large.isNotEmpty()) return large
                                    val medium = thumbnails.optString("500", "")
                                    if (medium.isNotEmpty()) return medium
                                }
                                val imgUrl = image.optString("image", "")
                                if (imgUrl.isNotEmpty()) return imgUrl
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "MusicBrainz 封面解析失败: ${e.message}")
                    }
                }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "MusicBrainz 搜索解析失败: ${e.message}")
            return null
        }
    }

    // ==================== 网易云音乐 ====================
    // 优先用 GET 方式，失败则用 POST 兜底

    private fun fetchFromNetease(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")

        // 策略A: GET /api/search/get/web（更简洁，部分设备兼容性好）
        val json = httpGet(
            "https://music.163.com/api/search/get/web?s=$encoded&type=1&limit=5",
            headers = mapOf(
                "Referer" to "https://music.163.com",
                "Cookie" to "os=pc; appver=2.7.5.198925"
            )
        )
        if (json != null) {
            val result = parseNeteaseJson(json)
            if (result != null) return result
        }

        // 策略B: POST /api/search/pc（老接口，部分场景仍有效）
        val json2 = httpPost(
            "https://music.163.com/api/search/pc",
            postData = "s=$encoded&type=1&offset=0&limit=5",
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Referer" to "https://music.163.com",
                "Cookie" to "os=pc; appver=2.7.5.198925"
            )
        )
        if (json2 != null) {
            val result = parseNeteaseJson(json2)
            if (result != null) return result
        }

        return null
    }

    /** 解析网易云搜索 JSON，提取封面 URL */
    private fun parseNeteaseJson(json: String): String? {
        try {
            val obj = JSONObject(json)
            if (obj.optInt("code", -1) != 200) return null
            val songs = obj.optJSONObject("result")?.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null
            val album = songs.getJSONObject(0).optJSONObject("album") ?: return null
            val picUrl = album.optString("picUrl", "")
            return picUrl.ifEmpty { null }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "网易云解析失败: ${e.message}")
            return null
        }
    }

    // ==================== QQ音乐 ====================

    private fun fetchFromQQ(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = httpGet(
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&t=0&n=5&format=json",
            headers = mapOf("Referer" to "https://y.qq.com")
        ) ?: return null

        try {
            val obj = JSONObject(json)
            val songs = obj.optJSONObject("data")
                ?.optJSONObject("song")
                ?.optJSONArray("list") ?: return null
            if (songs.length() == 0) return null

            // 取搜索结果中第一个有效的封面
            for (i in 0 until songs.length()) {
                val album = songs.getJSONObject(i).optJSONObject("album") ?: continue
                val albumMid = album.optString("mid", "")
                if (albumMid.isNotEmpty()) {
                    return "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
                }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "QQ音乐解析失败: ${e.message}")
            return null
        }
    }

    // ==================== 酷狗音乐 ====================

    private fun fetchFromKuGou(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = httpGet("https://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=1&pagesize=5")
            ?: return null

        try {
            val obj = JSONObject(json)
            val songs = obj.optJSONObject("data")
                ?.optJSONArray("lists") ?: return null
            if (songs.length() == 0) return null

            for (i in 0 until songs.length()) {
                val song = songs.getJSONObject(i)
                val albumId = song.optString("album_id", "")
                if (albumId.isNotEmpty()) {
                    return "https://imge.kugou.com/stdmusic/500/$albumId.jpg"
                }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "酷狗解析失败: ${e.message}")
            return null
        }
    }

    // ==================== 酷我音乐 ====================
    // img.kuwo.cn 域名已永久下线，改用 img1.kwcdn.kuwo.cn（需绕过 SSL 主机名校验）
    // 优先使用搜索结果的 web_albumpic_short 字段（精确的 CDN 路径）

    private fun fetchFromKuWo(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = httpGet(
            "https://search.kuwo.cn/r.s?all=$encoded&ft=music&itemset=web_2013&client=kt&encoding=utf8&pn=0&rn=5&rformat=json",
            headers = mapOf("Referer" to "https://kuwo.cn")
        ) ?: return null

        try {
            val obj = JSONObject(json)
            val abslist = obj.optJSONArray("abslist") ?: return null
            if (abslist.length() == 0) return null

            // 策略1: 优先使用 web_albumpic_short（封面直链路径）
            for (i in 0 until abslist.length()) {
                val picPath = abslist.getJSONObject(i).optString("web_albumpic_short", "")
                if (picPath.isNotEmpty()) {
                    return "https://img1.kwcdn.kuwo.cn/star/albumcover/$picPath"
                }
            }

            // 策略2: 兜底用 MUSICRID 构造（路径可能不对，但值得一试）
            for (i in 0 until abslist.length()) {
                val musicRid = abslist.getJSONObject(i).optString("MUSICRID", "")
                if (musicRid.isNotEmpty()) {
                    val musicId = musicRid.removePrefix("MUSIC_")
                    if (musicId.isNotEmpty()) {
                        return "https://img1.kwcdn.kuwo.cn/star/albumcover/$musicId.jpg"
                    }
                }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "酷我解析失败: ${e.message}")
            return null
        }
    }

    // ==================== iTunes ====================

    private fun fetchFromITunes(keyword: String): String? {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = httpGet("https://itunes.apple.com/search?term=$encoded&entity=song&limit=5")
            ?: return null

        try {
            val obj = JSONObject(json)
            val results = obj.optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            for (i in 0 until results.length()) {
                val artworkUrl = results.getJSONObject(i).optString("artworkUrl100", "")
                if (artworkUrl.isNotEmpty()) {
                    return artworkUrl.replace("100x100", "600x600")
                }
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "iTunes 解析失败: ${e.message}")
            return null
        }
    }
}
