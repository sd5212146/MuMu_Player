package com.example.yyplayer.data.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 专辑封面 URL 缓存，按 "title|artist|filePath" 为 key 缓存到文件
 * 加入 filePath 确保同专辑不同文件各有独立缓存
 */
class AlbumArtCache(context: Context) {
    private val cacheFile = File(context.cacheDir, "album_art_cache.json")
    private val cache = linkedMapOf<String, String>()

    init {
        load()
    }

    fun get(key: String): String? = cache[key]

    fun put(key: String, url: String) {
        cache[key] = url
        save()
    }

    private fun load() {
        if (!cacheFile.exists()) return
        try {
            val text = cacheFile.readText()
            if (text.isBlank()) return
            val json = JSONObject(text)
            for (k in json.keys()) {
                cache[k] = json.getString(k)
            }
        } catch (_: Exception) { /* ignore corrupt cache */ }
    }

    private fun save() {
        try {
            val json = JSONObject()
            for ((key, url) in cache) {
                json.put(key, url)
            }
            cacheFile.writeText(json.toString())
        } catch (_: Exception) { /* ignore save error */ }
    }
}
