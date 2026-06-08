package com.example.yyplayer.ui.screens.components

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yyplayer.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ManualScan"
private const val PREFS_SCAN = "manual_scan_settings"
private const val KEY_SCAN_PATHS = "scan_paths"

/** 获取保存的扫描路径列表 */
private fun getScanPaths(context: Context): MutableList<String> {
    val set = context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)
        .getStringSet(KEY_SCAN_PATHS, emptySet()) ?: emptySet()
    return set.toMutableList()
}

/** 保存扫描路径列表 */
private fun setScanPaths(context: Context, paths: List<String>) {
    context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(KEY_SCAN_PATHS, paths.toSet())
        .apply()
}

@Composable
fun ManualScanPage(
    padding: PaddingValues,
    libraryViewModel: LibraryViewModel,
    context: Context,
    scope: CoroutineScope
) {
    var manualScanning by remember { mutableStateOf(false) }
    var scanPaths by remember { mutableStateOf(getScanPaths(context)) }
    var isClearConfirmShow by remember { mutableStateOf(false) }
    var addPathResult by remember { mutableStateOf<String?>(null) }
    // 扫描结果：目录路径 → 歌曲数量
    var scanResults by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    var scanResultTime by remember { mutableStateOf<String?>(null) }
    // 启动时自动扫描自定义目录开关
    var autoScanCustomDirs by remember {
        mutableStateOf(
            LibraryViewModel.isAutoScanCustomDirsEnabled(context)
        )
    }

    // 使用 SAF 选择目录的 Launcher
    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            val path = resolvePathFromUri(context, uri)
            if (path != null && path !in scanPaths) {
                scanPaths += path
                setScanPaths(context, scanPaths)
                addPathResult = "已添加: $path"
                Log.i(TAG, "添加扫描路径: $path")
                // 立即更新音乐库：有缓存则直接过滤，无缓存则全量扫描
                val repo = libraryViewModel.getRepository()
                if (repo.hasFullScanCache()) {
                    repo.filterExistingByPaths(scanPaths)
                } else {
                    scope.launch { repo.scanAndFilterByPaths(scanPaths) }
                }
            } else if (path != null) {
                addPathResult = "该路径已在列表中"
            } else {
                addPathResult = "无法解析该路径"
            }
        }
    }

    // 扫描结果由扫描协程直接设置，移除 LaunchedEffect 监听

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ===== 说明 =====
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            Text(
                text = "手动管理音乐扫描。选择需要扫描的目录，或清空缓存后手动触发扫描。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // ===== 一键清空扫描结果 =====
        SectionTitle("清空结果")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isClearConfirmShow)
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable {
                    if (isClearConfirmShow) {
                        // 确认清空：清空音乐库
                        isClearConfirmShow = false
                        Log.i(TAG, "一键清空扫描结果")
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                setScanPaths(context, emptyList())
                            }
                            scanPaths = mutableListOf()
                            scanResults = null
                            scanResultTime = null
                            libraryViewModel.getRepository().clearManualScan()
                            // 清空后自动关闭「进入音乐库时自动扫描」
                            com.example.yyplayer.ui.viewmodel.LibraryViewModel.setAutoScanEnabled(context, false)
                            Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        isClearConfirmShow = true
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Delete, null,
                Modifier.size(24.dp),
                tint = if (isClearConfirmShow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isClearConfirmShow) "确认清空？" else "一键清空扫描结果",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isClearConfirmShow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isClearConfirmShow) "再次点击确认，将清空所有扫描结果和缓存"
                           else "清空缓存和扫描设置，不自动扫描",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ===== 手动选择扫描位置 =====
        SectionTitle("扫描位置")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !manualScanning) {
                    Log.d(TAG, "选择扫描目录")
                    folderPickerLauncher.launch(null)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add, null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "添加扫描目录",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "选择本地存储中的音乐目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (addPathResult != null) {
            Text(
                text = addPathResult!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (scanPaths.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无自定义扫描目录\n将扫描设备中所有音频文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            scanPaths.forEach { path ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderOpen, null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            scanPaths.remove(path)
                            setScanPaths(context, scanPaths)
                            // 移除路径后重新过滤音乐库
                            val repo = libraryViewModel.getRepository()
                            if (repo.hasFullScanCache()) {
                                repo.filterExistingByPaths(scanPaths)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close, "删除",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // ===== 执行扫描 =====
        SectionTitle("执行扫描")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !manualScanning) {
                    Log.d(TAG, "开始手动扫描: paths=$scanPaths")
                    manualScanning = true
                    scanResults = null
                    scanResultTime = null
                    scope.launch {
                        try {
                            val repo = libraryViewModel.getRepository()
                            repo.scanAndFilterByPaths(scanPaths)
                            // 从 _songs 提取结果
                            val allSongs = repo.songs.value
                            val grouped = allSongs.groupBy { it.folderPath }
                                .map { (path, songs) -> path to songs.size }
                                .sortedByDescending { it.second }
                            scanResults = grouped
                            scanResultTime = if (grouped.isNotEmpty()) {
                                val total = grouped.sumOf { it.second }
                                "共扫描到 $total 首歌曲，${grouped.size} 个目录"
                            } else {
                                "未扫描到歌曲"
                            }
                            Log.i(TAG, "手动扫描完成: ${grouped.size} 个目录")
                        } catch (e: Exception) {
                            Log.e(TAG, "手动扫描异常: ${e.message}")
                            Toast.makeText(context, "扫描失败: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            manualScanning = false
                        }
                    }
                }
                .padding(14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (manualScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Default.Refresh, null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (manualScanning) "正在扫描..." else "开始扫描",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        // ===== 扫描结果展示 =====
        if (scanResults != null && scanResults!!.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("扫描结果")
            if (scanResultTime != null) {
                Text(
                    text = scanResultTime!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            scanResults!!.forEach { (dirPath, count) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MusicNote, null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dirPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$count 首",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            scanResults = scanResults!!.filter { it.first != dirPath }
                            libraryViewModel.toggleHiddenFolder(dirPath)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close, "移除该目录",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        } else if (scanResults != null && scanResults!!.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "扫描完成，未发现歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ===== 启动行为 =====
        Spacer(Modifier.height(20.dp))
        SectionTitle("启动行为")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Sync, null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "启动时自动扫描自定义目录",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (autoScanCustomDirs) "每次启动程序自动更新自定义目录的音乐信息"
                           else "已关闭，需手动触发扫描",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoScanCustomDirs,
                onCheckedChange = {
                    autoScanCustomDirs = it
                    LibraryViewModel.setAutoScanCustomDirsEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            Text(
                text = "说明：\n\n" +
                        "• 「一键清空」只清除缓存和扫描配置，不会自动触发扫描\n" +
                        "• 「扫描位置」默认为空（扫描全部设备音频文件），添加后只扫描指定目录\n" +
                        "• 「扫描限制」已在设置 > 音乐设置中全局配置\n" +
                        "• 清空后需要点击「开始扫描」才会重新扫描歌曲\n" +
                        "• 开启「启动时自动扫描自定义目录」后，每次启动程序将自动更新已选目录的音乐信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 从 SAF 返回的 URI 中提取可读路径 */
private fun resolvePathFromUri(context: Context, uri: Uri): String? {
    try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        return if (parts.size >= 2) {
            val volume = parts[0]
            val path = parts.drop(1).joinToString(":")
            if (volume == "primary") {
                "/storage/emulated/0/$path"
            } else {
                "/storage/$volume/$path"
            }
        } else {
            docId
        }
    } catch (e: Exception) {
        Log.e(TAG, "解析路径失败: ${e.message}")
        return uri.path
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
