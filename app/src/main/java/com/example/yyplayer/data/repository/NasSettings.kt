package com.example.yyplayer.data.repository

import android.content.Context

/**
 * NAS 网络音乐设置管理器
 * 保存服务器地址、认证信息及功能开关
 */
class NasSettings(context: Context) {

    private val prefs = context.getSharedPreferences("nas_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENABLED = "nas_enabled"
        private const val KEY_SERVER_URL = "nas_server_url"
        private const val KEY_USERNAME = "nas_username"
        private const val KEY_PASSWORD = "nas_password"
        private const val KEY_LAST_SYNC = "nas_last_sync"
    }

    /** NAS 功能是否开启 */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** NAS 服务器 URL（支持 http / smb 协议） */
    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, "") ?: ""

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    /** 登录用户名（可选） */
    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun setUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    /** 登录密码（可选） */
    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""

    fun setPassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    /** 上次同步时间戳 */
    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, time).apply()
    }
}
