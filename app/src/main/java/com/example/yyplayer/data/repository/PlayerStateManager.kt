package com.example.yyplayer.data.repository

import android.content.Context
import com.example.yyplayer.data.model.PlayMode
import com.example.yyplayer.data.model.Song

class PlayerStateManager(context: Context) {

    private val prefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOCK_SCREEN = "lock_screen_enabled"
        private const val KEY_SHOW_LOCK_BUTTON = "show_lock_button"
        private const val KEY_SHOW_NOTIFICATION = "show_notification"
        private const val KEY_PAUSE_ON_VOLUME_ZERO = "pause_on_volume_zero"
        private const val KEY_AUTO_PLAY = "auto_play_on_startup"
        private const val KEY_GAMEPAD_ENABLED = "gamepad_enabled"
        private const val KEY_PORTRAIT_MODE = "portrait_mode"
        private const val KEY_AUTO_ROTATE = "auto_rotate"
    }

    fun isLockScreenEnabled(): Boolean = prefs.getBoolean(KEY_LOCK_SCREEN, false)

    fun setLockScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_SCREEN, enabled).apply()
    }

    /** 全屏模式下是否显示锁屏按钮，默认关闭 */
    fun isShowLockButton(): Boolean = prefs.getBoolean(KEY_SHOW_LOCK_BUTTON, false)

    fun setShowLockButton(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LOCK_BUTTON, show).apply()
    }

    /** 通知栏控件是否开启，默认关闭 */
    fun isNotificationEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_NOTIFICATION, false)

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTIFICATION, enabled).apply()
    }

    /** 音量降到0时暂停播放，默认关闭 */
    fun isPauseOnVolumeZero(): Boolean = prefs.getBoolean(KEY_PAUSE_ON_VOLUME_ZERO, false)

    fun setPauseOnVolumeZero(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_ON_VOLUME_ZERO, enabled).apply()
    }

    /** 启动后自动播放，默认关闭 */
    fun isAutoPlayEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY, false)

    fun setAutoPlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY, enabled).apply()
    }

    /** 游戏手柄切歌控制，默认开启 */
    fun isGamepadEnabled(): Boolean = prefs.getBoolean(KEY_GAMEPAD_ENABLED, true)

    fun setGamepadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAMEPAD_ENABLED, enabled).apply()
    }

    /** 播放器竖屏模式，默认关闭（横屏排布）*/
    fun isPortraitMode(): Boolean = prefs.getBoolean(KEY_PORTRAIT_MODE, false)

    fun setPortraitMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PORTRAIT_MODE, enabled).apply()
    }

    /** 自动识别屏幕方向切换布局，默认关闭 */
    fun isAutoRotate(): Boolean = prefs.getBoolean(KEY_AUTO_ROTATE, false)

    fun setAutoRotate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ROTATE, enabled).apply()
    }

    fun saveState(playlist: List<Song>, index: Int, playMode: PlayMode, position: Long) {
        prefs.edit()
            .putString("playlist_ids", playlist.joinToString(",") { it.id.toString() })
            .putInt("current_index", index)
            .putString("play_mode", playMode.name)
            .putLong("current_position", position)
            .apply()
    }

    fun loadPlaylistIds(): List<Long> {
        val ids = prefs.getString("playlist_ids", "") ?: ""
        return if (ids.isNotEmpty()) ids.split(",").mapNotNull { it.toLongOrNull() } else emptyList()
    }

    fun getCurrentIndex(): Int = prefs.getInt("current_index", 0)

    fun getPlayMode(): PlayMode {
        val name = prefs.getString("play_mode", PlayMode.REPEAT_FOLDER.name) ?: PlayMode.REPEAT_FOLDER.name
        return try { PlayMode.valueOf(name) } catch (_: Exception) { PlayMode.REPEAT_FOLDER }
    }

    fun getPosition(): Long = prefs.getLong("current_position", 0L)

    fun hasSavedState(): Boolean = prefs.contains("playlist_ids") &&
            prefs.getString("playlist_ids", "")?.isNotEmpty() == true

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** 只保存当前播放索引和位置（不覆盖歌单和模式）。用于服务销毁时的快速保存 */
    fun savePosition(index: Int, position: Long) {
        prefs.edit()
            .putInt("current_index", index)
            .putLong("current_position", position)
            .apply()
    }

    /** 只保存播放模式（不覆盖其他状态）。用于通知栏切换播放模式 */
    fun savePlayMode(playMode: PlayMode) {
        prefs.edit()
            .putString("play_mode", playMode.name)
            .apply()
    }
}
