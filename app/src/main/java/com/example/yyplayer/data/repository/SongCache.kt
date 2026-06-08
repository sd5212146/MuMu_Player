package com.example.yyplayer.data.repository

import android.content.Context
import com.example.yyplayer.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SongCache(private val context: Context) {

    private val cacheFile = File(context.filesDir, "song_cache.json")

    fun save(songs: List<Song>) {
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

    fun load(): List<Song> {
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
                        albumArtUri = obj.optString("albumArtUri", "").ifEmpty { null }?.let { android.net.Uri.parse(it) },
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

    fun clear() {
        cacheFile.delete()
    }
}
