package com.example.yyplayer.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

/**
 * 专辑封面写入器 —— 只写入侧边图片文件 {songFilePath}.jpg
 * 不再写入 MediaStore，避免跨进程阻塞和权限问题。
 */
class AlbumArtWriter(
    private val context: android.content.Context? = null
) {

    companion object {
        private const val TAG = "AlbumArtWriter"
        /** 需要绕过 SSL 主机名校验的域名（证书 CN 是 CDN 泛域名但内容正确） */
        private val SSL_INSECURE_HOSTS = setOf(
            "img1.kwcdn.kuwo.cn"
        )
    }

    /**
     * 下载图片并写入侧边文件 {filePath}.jpg
     */
    suspend fun writeToSongFile(
        filePath: String,
        imageUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        android.util.Log.d(TAG, "开始下载并写入: path=«${filePath.takeLast(40)}» url=«${imageUrl.take(80)}»")
        try {
            // 1. 下载图片（DNS 预解析 + 自动重试）
            val bytes = downloadBytes(imageUrl) ?: run {
                android.util.Log.w(TAG, "下载失败, 放弃写入 | ${System.currentTimeMillis() - t0}ms")
                return@withContext false
            }

            if (bytes.isEmpty()) {
                android.util.Log.w(TAG, "下载结果为 0 字节，放弃写入")
                return@withContext false
            }

            // 2. 直接写入侧边文件
            return@withContext writeFile(filePath, bytes)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "写入异常 ${System.currentTimeMillis() - t0}ms: ${e::class.simpleName}: ${e.message}")
            false
        }
    }

    /** 直接写入图片字节数组到侧边文件 {filePath}.jpg（用于提取内嵌封面） */
    suspend fun writeBytesToSongFile(
        filePath: String,
        imageBytes: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "writeBytes: path=«${filePath.takeLast(40)}» size=${imageBytes.size}")
        try {
            return@withContext writeFile(filePath, imageBytes)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "writeBytes 异常: ${e::class.simpleName}: ${e.message}")
            false
        }
    }

    /** 写入侧边图片文件 {filePath}.jpg，失败时尝试写入应用内部缓存 */
    private fun writeFile(filePath: String, bytes: ByteArray): Boolean {
        // 尝试写入侧边文件（外部存储）
        val imgFile = java.io.File(filePath + ".jpg")
        try {
            imgFile.parentFile?.mkdirs()
            imgFile.writeBytes(bytes)
            android.util.Log.i(TAG, "侧边文件写入成功: ${imgFile.absolutePath}, ${bytes.size}bytes")
            return true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "侧边文件写入失败: ${e::class.simpleName}: ${e.message}")
        }

        // 回退：写入应用内部缓存（Android 10+ Scoped Storage）
        val cacheCtx = context ?: return false
        try {
            val cacheFile = java.io.File(
                cacheCtx.cacheDir,
                "album_art/${java.io.File(filePath).name}.jpg"
            )
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(bytes)
            android.util.Log.i(TAG, "内部缓存写入成功: ${cacheFile.absolutePath}, ${bytes.size}bytes")
            return true
        } catch (e2: Exception) {
            android.util.Log.w(TAG, "内部缓存写入也失败: ${e2::class.simpleName}: ${e2.message}")
            return false
        }
    }

    /**
     * 下载图片字节数组（带 DNS 预解析 + 自动重试 + SSL 容错）
     */
    private fun downloadBytes(imageUrl: String): ByteArray? {
        val url = URL(imageUrl)
        val host = url.host

        // 1. DNS 预解析
        try {
            InetAddress.getAllByName(host)
        } catch (dnsE: UnknownHostException) {
            android.util.Log.w(TAG, "DNS 首次解析失败: $host, 1s 后重试...")
            try {
                Thread.sleep(1000)
                InetAddress.getAllByName(host)
                android.util.Log.i(TAG, "DNS 重试解析成功: $host")
            } catch (retryDns: Exception) {
                android.util.Log.e(TAG, "DNS 重试仍然失败: $host, ${retryDns.message}")
                return null
            }
        }

        // 2. HTTP 下载
        return try {
            val conn = url.openConnection() as HttpURLConnection
            if (conn is HttpsURLConnection && SSL_INSECURE_HOSTS.contains(host)) {
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
                android.util.Log.v(TAG, "已跳过 SSL 主机名校验: $host")
            }
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            bytes
        } catch (e: Exception) {
            android.util.Log.w(TAG, "HTTP 下载失败: ${e::class.simpleName}: ${e.message}")
            null
        }
    }
}
