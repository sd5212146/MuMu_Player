package com.example.yyplayer.data.repository

import android.content.Context

/**
 * 歌词/封面管理偏好设置
 *
 * 两项开关：
 * - writeLyricsToFile: 获取到的歌词是否写入歌曲所在目录（创建伴生 .lrc 文件）
 * - loadEmbeddedLyrics: 是否尝试读取歌曲本身内嵌的歌词
 *
 * 封面获取后始终写入侧边 .jpg 文件，不写入 MediaStore，不再提供开关。
 */
class LyricsCoverSettings(context: Context) {

    private val prefs = context.getSharedPreferences("lyrics_cover_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WRITE_LYRICS = "write_lyrics_to_file"
        private const val KEY_LOAD_EMBEDDED = "load_embedded_lyrics"

        // 默认值
        private const val DEFAULT_WRITE_LYRICS = true
        private const val DEFAULT_LOAD_EMBEDDED = true
    }

    /** 歌词写入原始文件 */
    fun isWriteLyricsToFile(): Boolean =
        prefs.getBoolean(KEY_WRITE_LYRICS, DEFAULT_WRITE_LYRICS)

    fun setWriteLyricsToFile(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WRITE_LYRICS, enabled).apply()
    }

    /** 加载歌曲本身的歌词 */
    fun isLoadEmbeddedLyrics(): Boolean =
        prefs.getBoolean(KEY_LOAD_EMBEDDED, DEFAULT_LOAD_EMBEDDED)

    fun setLoadEmbeddedLyrics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOAD_EMBEDDED, enabled).apply()
    }
}
