package com.example.yyplayer.data.model

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** 判断路径是否为内部共享存储根目录 */
private fun isInternalStorageRoot(path: String): Boolean {
    if (path == "/storage/emulated/0") return true
    if (path == "/sdcard") return true
    try {
        if (path == Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')) return true
    } catch (_: Exception) { }
    return false
}

data class Song(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val filePath: String = "",
    val albumArtUri: Uri? = null,
    val size: Long = 0L,
    val dateAdded: Long = 0L
) {
    /** 从文件路径提取父文件夹名。存储卷根目录的名称由 LibraryViewModel.groupByFolder 覆盖。 */
    val folderName: String
        get() {
            val parent = File(filePath).parentFile ?: return "ROOT"
            val path = parent.absolutePath.trimEnd('/')
            if (isInternalStorageRoot(path)) return "内部存储"
            if (parent.parentFile?.name == "storage" && !path.startsWith("/storage/emulated/"))
                return "SD卡"
            return parent.name
        }

    /** 从文件路径提取父文件夹路径 */
    val folderPath: String
        get() {
            val parent = File(filePath).parentFile
            return parent?.absolutePath ?: ""
        }

    companion object {
        /** 串行化内嵌封面提取操作，防止多线程并发分配大内存导致 OOM */
        val embeddedPictureLock = Any()
    }
}

/** 解析专辑封面 URI。优先级：侧边 .jpg > 内嵌封面 > MediaStore */
fun Song.resolveAlbumArtUri(context: Context): Uri? {
    if (filePath.isNotEmpty()) {
        // 1. 侧边 .jpg（最快，已有则直接返回）
        val sidecarFile = File(filePath + ".jpg")
        if (sidecarFile.exists()) {
            // 异常大的侧边文件（如旧版本写入的 50M+ 内嵌封面），删除并跳过
            if (sidecarFile.length() > 3 * 1024 * 1024) {
                android.util.Log.w("AlbumArt", "侧边文件过大(${sidecarFile.length() / 1024 / 1024}MB)，删除: ${sidecarFile.name}")
                sidecarFile.delete()
            } else {
                return Uri.fromFile(sidecarFile)
            }
        }

        // 2. 内嵌封面（ID3 标签），提取后写入侧边文件供下次快速命中
        synchronized(Song.embeddedPictureLock) {
            android.util.Log.i("AlbumArt", "synchronized 进入 embeddedPicture: $filePath")
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    // 优先使用 MediaStore URI（Android 10+ 直接文件路径受限）
                    val mediaStoreUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    mmr.setDataSource(context, mediaStoreUri)
                } catch (_: Exception) {
                    // 降级：直接路径（旧设备或特殊权限时可用）
                    mmr.setDataSource(filePath)
                }
                val bytes = try {
                    mmr.embeddedPicture
                } finally {
                    mmr.release()
                }
                if (bytes != null) {
                    android.util.Log.i("AlbumArt", "内嵌封面提取成功: ${bytes.size / 1024}KB, file=$filePath")
                    try {
                        sidecarFile.outputStream().use { it.write(bytes) }
                        return Uri.fromFile(sidecarFile)
                    } catch (e: Exception) {
                        // 侧边文件写入失败（Android 10+ Scoped Storage EPERM），回退到内部缓存
                        android.util.Log.w("AlbumArt", "侧边文件写入失败(${e.message})，回退到内部缓存")
                        try {
                            val cacheFile = java.io.File(
                                context.cacheDir,
                                "album_art/${java.io.File(filePath).name}.jpg"
                            )
                            cacheFile.parentFile?.mkdirs()
                            cacheFile.outputStream().use { it.write(bytes) }
                            android.util.Log.i("AlbumArt", "内部缓存写入成功: ${cacheFile.absolutePath}")
                            return Uri.fromFile(cacheFile)
                        } catch (e2: Exception) {
                            android.util.Log.w("AlbumArt", "内部缓存写入也失败: ${e2.message}")
                        }
                    }
                } else {
                    android.util.Log.i("AlbumArt", "内嵌封面为空: file=$filePath")
                }
            } catch (_: Throwable) {
                android.util.Log.w("AlbumArt", "内嵌封面提取异常(OOM/Error)，已兜住: $filePath")
                System.gc()
            }
        }
    }

    // 3. MediaStore Albums 表直接查询封面路径（跨版本最可靠，不受 Scoped Storage 影响）
    try {
        val albumProjection = arrayOf(MediaStore.Audio.Albums.ALBUM_ART)
        val albumCursor = context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            albumProjection,
            "${MediaStore.Audio.Albums._ID}=?",
            arrayOf(getAlbumIdFromSong(context, id).toString()),
            null
        )
        albumCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val artPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART))
                if (artPath != null) {
                    val fileUri = Uri.parse("file://$artPath")
                    if (File(artPath).exists()) return fileUri
                }
            }
        }
    } catch (_: Exception) { }

    // 4. 备用：直接用 Song.albumArtUri（已缓存的 MediaStore album URI）
    val mediaUri = albumArtUri
    if (mediaUri != null) {
        try {
            context.contentResolver.openInputStream(mediaUri)?.use { return mediaUri }
        } catch (_: Exception) { }
    }
    return null
}

/** 从 MediaStore 查询歌曲对应的 ALBUM_ID */
private fun getAlbumIdFromSong(context: Context, songId: Long): Long {
    try {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
            }
        }
    } catch (_: Exception) { }
    return 0L
}

data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val songs: List<Song>
)