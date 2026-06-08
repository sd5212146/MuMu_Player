package com.example.yyplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Library : Screen("library", "音乐库", Icons.Default.LibraryMusic)
    data object NowPlaying : Screen("now_playing", "正在播放", Icons.Default.MusicNote)
    data object Equalizer : Screen("equalizer", "均衡器", Icons.Default.Equalizer)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}
