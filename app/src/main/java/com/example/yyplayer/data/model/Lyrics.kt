package com.example.yyplayer.data.model

data class LyricsLine(
    val time: Long,    // 毫秒
    val text: String
)

data class LyricsResult(
    val lines: List<LyricsLine>,
    val source: String,  // "netease", "qq", "kugou", ""
    val isLoading: Boolean = false,
    val rawLrc: String = ""  // 原始 LRC 文本（用于缓存）
)
