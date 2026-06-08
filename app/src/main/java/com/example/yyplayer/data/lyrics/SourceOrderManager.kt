package com.example.yyplayer.data.lyrics

import android.content.Context

/**
 * 管理歌词获取源的启用/禁用状态
 * 固定搜索顺序: LRCLIB, QQ音乐, 网易云音乐, 酷我音乐, 酷狗音乐
 */
class SourceOrderManager(context: Context) {
    private val prefs = context.getSharedPreferences("source_order", Context.MODE_PRIVATE)

    companion object {
        // 固定搜索顺序（不可修改）
        val DEFAULT_ORDER = listOf("LRCLIB", "QQ音乐", "网易云音乐", "酷我音乐", "酷狗音乐")
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
        prefs.edit().putStringSet("disabled_sources", disabled).apply()
    }

    private fun getDisabledSet(): Set<String> {
        if (!prefs.contains("disabled_sources")) {
            // 首次使用：默认关闭酷我、酷狗
            val defaults = mutableSetOf("酷我音乐", "酷狗音乐")
            prefs.edit().putStringSet("disabled_sources", defaults).apply()
            return defaults
        }
        return prefs.getStringSet("disabled_sources", emptySet()) ?: emptySet()
    }
}
