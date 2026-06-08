package com.example.yyplayer.data.repository

import android.content.Context

class LyricsFontSizeSettings(context: Context) {

    private val prefs = context.getSharedPreferences("lyrics_font_size", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SCALE = "lyrics_font_scale"
        private const val DEFAULT_SCALE = 1.0f

        val SCALE_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f)

        fun scaleDisplayName(scale: Float): String = when (scale) {
            0.75f -> "小"
            1.0f -> "默认"
            1.25f -> "大"
            1.5f -> "特大"
            1.75f -> "超大"
            else -> "${(scale * 100).toInt()}%"
        }
    }

    fun getFontScale(): Float = prefs.getFloat(KEY_SCALE, DEFAULT_SCALE)

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_SCALE, scale).apply()
    }
}
