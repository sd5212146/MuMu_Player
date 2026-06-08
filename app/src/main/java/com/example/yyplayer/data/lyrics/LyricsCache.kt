package com.example.yyplayer.data.lyrics

import android.content.Context
import com.example.yyplayer.data.model.LyricsLine
import com.example.yyplayer.data.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌词缓存，按 "title|artist" 为 key 持久化到独立 .lrc 文件
 *
 * 每个缓存项存为两个文件：
 * - {cacheDir}/lyrics/{md5key}.lrc   → 原始 LRC 文本
 * - {cacheDir}/lyrics/{md5key}.meta  → JSON { "source": "xxx", "time": "2024-..." }
 *
 * 比之前统一的 JSON 文件更健壮：单个文件损坏不影响其他缓存，且 .lrc 文件可直接用文本编辑器查看。
 */
class LyricsCache(context: Context) {

    private val cacheDir = File(context.cacheDir, "lyrics").also { it.mkdirs() }
    private val lyricsFetcher = LyricsFetcher()

    /** 存储歌词到缓存 */
    fun put(key: String, rawLrc: String, source: String) {
        try {
            val file = getCacheFile(key)
            val metaFile = getMetaFile(key)
            file.writeText(rawLrc)
            metaFile.writeText(
                """{"source":"${source.replace("\"", "\\\"")}","time":"${timestamp()}"}"""
            )
        } catch (_: Exception) { }
    }

    /** 从缓存读取歌词，返回 LyricsResult 或 null */
    suspend fun get(key: String): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(key)
            if (!file.exists()) return@withContext null
            val rawLrc = file.readText()
            val metaFile = getMetaFile(key)
            // 如果 .lrc 为空白但 .meta 存在 → 之前搜索过但没找到歌词，返回空结果阻止重复请求
            if (rawLrc.isBlank()) {
                return@withContext if (metaFile.exists()) {
                    LyricsResult(emptyList(), "")
                } else null
            }

            val source = if (metaFile.exists()) {
                try {
                    val meta = org.json.JSONObject(metaFile.readText())
                    meta.optString("source", "")
                } catch (_: Exception) { "" }
            } else ""

            val lines = lyricsFetcher.parseLrc(rawLrc)
            LyricsResult(lines, source, rawLrc = rawLrc)
        } catch (_: Exception) { null }
    }

    /** 获取缓存的原始 LRC 文本 */
    fun getRawLrc(key: String): String? {
        try {
            val file = getCacheFile(key)
            return if (file.exists()) file.readText() else null
        } catch (_: Exception) { return null }
    }

    /** key 是否存在缓存 */
    fun has(key: String): Boolean {
        return getCacheFile(key).exists()
    }

    /** 删除缓存 */
    fun remove(key: String) {
        getCacheFile(key).delete()
        getMetaFile(key).delete()
    }

    /** 清空所有缓存 */
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** 缓存条目数 */
    val size: Int get() = cacheDir.listFiles()?.filter { it.extension == "lrc" }?.size ?: 0

    /** key → 缓存文件（取 MD5 哈希作为文件名，避免特殊字符问题） */
    private fun getCacheFile(key: String): File {
        val hash = md5(key)
        return File(cacheDir, "$hash.lrc")
    }

    private fun getMetaFile(key: String): File {
        val hash = md5(key)
        return File(cacheDir, "$hash.meta")
    }

    private fun timestamp(): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        } catch (_: Exception) { "" }
    }

    /** 简单 MD5 哈希（Android 标准 API） */
    private fun md5(input: String): String {
        try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            // 降级：用输入长度+内容哈希
            return input.hashCode().toUInt().toString(16)
        }
    }
}
