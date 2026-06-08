package com.example.yyplayer.ui.screens

import android.media.audiofx.AudioEffect
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yyplayer.player.EqualizerController
import com.example.yyplayer.ui.viewmodel.EqualizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    equalizerViewModel: EqualizerViewModel,
    equalizerController: EqualizerController?,
    audioSessionId: Int = 0,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentPreset by equalizerViewModel.currentPreset.collectAsState()
    val bandLevels by equalizerViewModel.bandLevels.collectAsState()
    val bandFrequencies by equalizerViewModel.bandFrequencies.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("均衡器") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val context = LocalContext.current

            // 系统均衡器入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        openSystemEqualizer(context, audioSessionId)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "打开系统均衡器",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "使用设备原生均衡器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 预设列表
            Text(
                text = "预设",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(EqualizerController.PRESETS.toList()) { (key, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (currentPreset == key)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                equalizerViewModel.applyPreset(equalizerController, key)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = if (currentPreset == key)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (currentPreset == key) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentPreset == key)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (currentPreset == key) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 频段调节
            if (bandLevels.isNotEmpty() && bandFrequencies.isNotEmpty()) {
                Text(
                    text = "自定义调节",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                bandLevels.forEachIndexed { index, level ->
                    val freqLabel = bandFrequencies.getOrElse(index) { "?Hz" }
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${level}dB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { newLevel ->
                                equalizerViewModel.setBandLevel(equalizerController, index, newLevel.toInt().toShort())
                            },
                            valueRange = -15f..15f,
                            steps = 30,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "请先播放音乐以激活均衡器",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 尝试打开系统均衡器面板 */
private fun openSystemEqualizer(context: android.content.Context, audioSessionId: Int) {
    Log.d("Equalizer", "openSystemEqualizer: audioSessionId=$audioSessionId")
    if (audioSessionId <= 0) {
        Log.w("Equalizer", "audioSessionId无效")
        android.widget.Toast.makeText(context, "请先播放音乐", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val pm = context.packageManager

    // 方式1：ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL (API 31+)
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val ri = pm.resolveActivity(intent, 0)
    Log.d("Equalizer", "方式1 resolveActivity: ${ri?.activityInfo?.name ?: "null (无处理者)"}")
    if (ri != null) {
        try {
            context.startActivity(intent)
            Log.d("Equalizer", "方式1 startActivity 成功")
            return
        } catch (e: Exception) {
            Log.e("Equalizer", "方式1 startActivity 异常: ${e::class.simpleName}: ${e.message}")
        }
    }

    // 方式2：广播
    try {
        Log.d("Equalizer", "尝试 sendBroadcast ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION")
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
        )
        Log.d("Equalizer", "方式2 广播已发送")
        // 广播后等片刻让UI启动
        return
    } catch (e: Exception) {
        Log.e("Equalizer", "方式2 失败: ${e::class.simpleName}: ${e.message}")
    }

    // 方式3：尝试打开声音设置
    try {
        Log.d("Equalizer", "尝试打开声音设置")
        context.startActivity(
            Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        Log.d("Equalizer", "方式3 声音设置已打开")
    } catch (e: Exception) {
        Log.e("Equalizer", "方式3 失败: ${e::class.simpleName}: ${e.message}")
    }
}
