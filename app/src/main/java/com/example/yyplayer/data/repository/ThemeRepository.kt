package com.example.yyplayer.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.yyplayer.data.model.PlayerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemeRepository(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("selected_theme_id")
        private const val DEFAULT_THEME = "walkman_blackgold"

        // 自定义主题颜色 key
        private val CUSTOM_PRIMARY = longPreferencesKey("custom_primary")
        private val CUSTOM_ACCENT = longPreferencesKey("custom_accent")
        private val CUSTOM_BG = longPreferencesKey("custom_background")
        private val CUSTOM_TEXT = longPreferencesKey("custom_text")
        private val CUSTOM_BUTTON = longPreferencesKey("custom_button")
        private val CUSTOM_SECONDARY = longPreferencesKey("custom_secondary")

        const val CUSTOM_THEME_ID = "custom"
    }

    val currentThemeId: Flow<String> = context.themeDataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: DEFAULT_THEME
    }

    suspend fun setTheme(themeId: String) {
        context.themeDataStore.edit { prefs ->
            prefs[THEME_KEY] = themeId
        }
    }

    suspend fun getCurrentThemeId(): String {
        return context.themeDataStore.data.first()[THEME_KEY] ?: DEFAULT_THEME
    }

    /** 保存自定义主题颜色 */
    suspend fun saveCustomColors(theme: PlayerTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[CUSTOM_PRIMARY] = theme.primaryColor.value.toLong()
            prefs[CUSTOM_ACCENT] = theme.accentColor.value.toLong()
            prefs[CUSTOM_BG] = theme.backgroundColor.value.toLong()
            prefs[CUSTOM_TEXT] = theme.textColor.value.toLong()
            prefs[CUSTOM_BUTTON] = theme.buttonColor.value.toLong()
            prefs[CUSTOM_SECONDARY] = theme.secondaryColor.value.toLong()
        }
    }

    /** 读取自定义主题颜色，若无保存则返回默认值 */
    suspend fun getCustomColors(): PlayerTheme {
        val prefs = context.themeDataStore.data.first()
        val primary = prefs[CUSTOM_PRIMARY]?.let { Color(it.toULong()) } ?: Color(0xFF1565C0)
        val accent = prefs[CUSTOM_ACCENT]?.let { Color(it.toULong()) } ?: Color(0xFF1976D2)
        val bg = prefs[CUSTOM_BG]?.let { Color(it.toULong()) } ?: Color.White
        val text = prefs[CUSTOM_TEXT]?.let { Color(it.toULong()) } ?: Color(0xFF1A1A1A)
        val button = prefs[CUSTOM_BUTTON]?.let { Color(it.toULong()) } ?: Color(0xFF90CAF9)
        val secondary = prefs[CUSTOM_SECONDARY]?.let { Color(it.toULong()) } ?: Color(0xFFE3F2FD)
        return PlayerTheme(
            id = CUSTOM_THEME_ID,
            name = "自定义",
            primaryColor = primary,
            secondaryColor = secondary,
            backgroundColor = bg,
            textColor = text,
            accentColor = accent,
            buttonColor = button
        )
    }

    val customColorsFlow: Flow<PlayerTheme> = context.themeDataStore.data.map { prefs ->
        val primary = prefs[CUSTOM_PRIMARY]?.let { Color(it.toULong()) } ?: Color(0xFF1565C0)
        val accent = prefs[CUSTOM_ACCENT]?.let { Color(it.toULong()) } ?: Color(0xFF1976D2)
        val bg = prefs[CUSTOM_BG]?.let { Color(it.toULong()) } ?: Color.White
        val text = prefs[CUSTOM_TEXT]?.let { Color(it.toULong()) } ?: Color(0xFF1A1A1A)
        val button = prefs[CUSTOM_BUTTON]?.let { Color(it.toULong()) } ?: Color(0xFF90CAF9)
        val secondary = prefs[CUSTOM_SECONDARY]?.let { Color(it.toULong()) } ?: Color(0xFFE3F2FD)
        PlayerTheme(
            id = CUSTOM_THEME_ID,
            name = "自定义",
            primaryColor = primary,
            secondaryColor = secondary,
            backgroundColor = bg,
            textColor = text,
            accentColor = accent,
            buttonColor = button
        )
    }

    /** 获取当前完整主题（自动处理自定义主题） */
    val currentTheme: Flow<PlayerTheme> = combine(currentThemeId, customColorsFlow) { id, custom ->
        if (id == CUSTOM_THEME_ID) custom
        else PlayerTheme.getById(id)
    }

    /** 同步获取当前主题 */
    suspend fun getCurrentTheme(): PlayerTheme {
        val id = getCurrentThemeId()
        return if (id == CUSTOM_THEME_ID) getCustomColors()
        else PlayerTheme.getById(id)
    }
}
