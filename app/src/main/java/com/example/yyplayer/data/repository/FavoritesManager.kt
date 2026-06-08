package com.example.yyplayer.data.repository

import android.content.Context
import android.content.SharedPreferences

class FavoritesManager(context: Context) {

    private val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)

    fun getIds(): Set<Long> {
        return prefs.getStringSet("ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    fun toggle(id: Long): Boolean {
        val current = getIds().toMutableSet()
        return if (id in current) {
            current.remove(id)
            save(current)
            false
        } else {
            current.add(id)
            save(current)
            true
        }
    }

    fun isFavorite(id: Long): Boolean = id in getIds()

    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun save(ids: Set<Long>) {
        prefs.edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()
    }
}
