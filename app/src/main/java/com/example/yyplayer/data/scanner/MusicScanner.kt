package com.example.yyplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import com.example.yyplayer.data.model.Song
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音乐文件扫描器
 *
 * 策略（参考 Phonograph/RetroMusic 等主流开源播放器）：
 * 1. 主方案：EXTERNAL_CONTENT_URI 一次查询覆盖所有外部存储卷（API 29+ 均适用）
 * 2. 降级：逐卷发现 + 逐卷扫描（仅当主方案返回空时启用）
 * 3. 过滤条件：MIME_TYPE LIKE 'audio/%'
 * 4. 详细日志
 */
class MusicScanner(val context: Context) {

    companion object {
        private const val TAG = "MusicScanner"
    }

    /** 从游标中提取一首 Song */
    private fun cursorToSong(
        c: android.database.Cursor
    ): Song? {
        val filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
        if (filePath.isBlank() || !File(filePath).exists()) return null
        val albumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
        return Song(
            id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
            title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "未知",
            artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "未知艺术家",
            album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "未知专辑",
            duration = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
            filePath = filePath,
            // 使用正确的 album art URI：content://media/external/audio/albumart/{albumId}
            albumArtUri = ContentUris.withAppendedId(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumId
            ),
            size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)),
            dateAdded = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))
        )
    }

    /** 从指定 URI 扫描音频文件 */
    private fun scanFromUri(uri: Uri, tag: String): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} LIKE ?"
        val args = arrayOf("audio/%")
        val songs = mutableListOf<Song>()
        try {
            val cursor = context.contentResolver.query(uri, projection, selection, args, null)
            cursor?.use { c ->
                while (c.moveToNext()) {
                    cursorToSong(c)?.let { songs.add(it) }
                }
            }
            Log.i(TAG, "$tag: ${songs.size} 首")
        } catch (e: Exception) {
            Log.e(TAG, "$tag 扫描异常: ${e.message}")
        }
        return songs
    }

    suspend fun scanAllMusic(): List<Song> = withContext(Dispatchers.IO) {
        // ==== 主方案：EXTERNAL_CONTENT_URI 一次覆盖所有外部存储（最可靠，业界标准做法）====
        Log.i(TAG, "主方案: EXTERNAL_CONTENT_URI 扫描...")
        val primarySongs = scanFromUri(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "EXTERNAL_CONTENT_URI"
        )
        Log.i(TAG, "主方案完成: ${primarySongs.size} 首")

        // ==== 兜底方案：始终再逐卷扫描一次，合并结果去重（覆盖 TF 卡等独立卷，不影响大多数正常用户）====
        Log.i(TAG, "兜底: 逐卷扫描...")
        val volumes = mutableSetOf<String>()

        // A: getExternalVolumeNames (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volumes.addAll(MediaStore.getExternalVolumeNames(context))
        }

        // B: StorageManager (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                for (sv in sm.storageVolumes) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        sv.mediaStoreVolumeName?.let { volumes.add(it) }
                    }
                }
            } catch (_: Exception) { }
        }

        // C: 硬编码兜底卷名
        if (volumes.isEmpty()) {
            volumes.add(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            volumes.add(MediaStore.VOLUME_EXTERNAL)
        }
        Log.i(TAG, "兜底卷列表: ${volumes.joinToString(", ")}")

        val fallbackSongs = mutableListOf<Song>()
        for (volName in volumes) {
            try {
                // 卷名必须小写（MediaStore 要求），否则 getContentUri 抛 IllegalArgumentException
                val lowerName = volName.lowercase()
                val volUri = MediaStore.Audio.Media.getContentUri(lowerName)
                fallbackSongs.addAll(scanFromUri(volUri, "[$lowerName]"))
            } catch (e: Exception) {
                Log.e(TAG, "[$volName] 异常: ${e.message}")
            }
        }
        Log.i(TAG, "兜底逐卷扫描完成: ${fallbackSongs.size} 首")

        // ==== 合并去重：按 filePath 去重，主方案优先 ====
        val seen = mutableSetOf<String>()
        val merged = (primarySongs + fallbackSongs).filter { song ->
            seen.add(song.filePath)
        }
        Log.i(TAG, "合并去重后: ${merged.size} 首")
        merged
    }
}
