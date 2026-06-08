package com.example.yyplayer.data.repository

import android.content.Context

class LyricsOffsetManager(context: Context) {

    private val prefs = context.getSharedPreferences("lyrics_offset_v2", Context.MODE_PRIVATE)

    fun getOffset(key: String): Long = prefs.getLong(key, 0L)

    fun setOffset(key: String, offset: Long) {
        prefs.edit().putLong(key, offset).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun getKey(title: String, artist: String): String = "$title|$artist"
    }
}
