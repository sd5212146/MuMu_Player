package com.example.yyplayer.data.lyrics

import android.content.Context

/**
 * 管理专辑封面获取源的启用/禁用状态
 * 固定搜索顺序: iTunes, Deezer, MusicBrainz, 网易云音乐, QQ音乐, 酷狗音乐
 */
class AlbumArtSourceManager(context: Context) {
    private val prefs = context.getSharedPreferences("album_art_source_order", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_ORDER = listOf("iTunes", "Deezer", "MusicBrainz", "网易云音乐", "QQ音乐", "酷狗音乐")
    }

    /** 获取启用的来源列表（按固定顺序返回） */
    fun getEnabledSources(): List<String> {
        val disabled = getDisabledSet()
        return DEFAULT_ORDER.filter { it !in disabled }
    }

    /** 判断某个源是否启用 */
    fun isEnabled(source: String): Boolean {
        return source !in getDisabledSet()
    }

    /** 设置某个源的启用/禁用状态 */
    fun setEnabled(source: String, enabled: Boolean) {
        val disabled = getDisabledSet().toMutableSet()
        if (enabled) {
            disabled.remove(source)
        } else {
            // 至少保留一个源，不允许全关
            val remaining = DEFAULT_ORDER.filter { it != source && it !in disabled }
            if (remaining.isEmpty()) return
            disabled.add(source)
        }
        prefs.edit().putStringSet("disabled_album_art_sources", disabled).apply()
    }

    private fun getDisabledSet(): Set<String> {
        if (!prefs.contains("disabled_album_art_sources")) {
            // 首次使用：默认开启全部封面源
            val defaults = emptySet<String>()
            prefs.edit().putStringSet("disabled_album_art_sources", defaults).apply()
            return defaults
        }
        return prefs.getStringSet("disabled_album_art_sources", emptySet()) ?: emptySet()
    }
}
