package com.example.yyplayer.data.repository

import android.content.Context

/** 管理用户隐藏的目录列表 */
class HiddenFolderManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "hidden_folders"
        private const val KEY_HIDDEN = "hidden_folder_paths"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 获取所有已隐藏的文件夹路径 */
    fun getHiddenPaths(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
    }

    /** 切换隐藏状态（已隐藏则取消隐藏，否则加入隐藏列表） */
    fun toggleHidden(folderPath: String) {
        val current = getHiddenPaths().toMutableSet()
        if (current.contains(folderPath)) {
            current.remove(folderPath)
        } else {
            current.add(folderPath)
        }
        prefs.edit().putStringSet(KEY_HIDDEN, current).apply()
    }

    /** 判断文件夹是否已被隐藏 */
    fun isHidden(folderPath: String): Boolean {
        return getHiddenPaths().contains(folderPath)
    }

    /** 清空所有隐藏记录（重新扫描时调用） */
    fun clearAll() {
        prefs.edit().remove(KEY_HIDDEN).apply()
    }
}
