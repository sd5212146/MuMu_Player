package com.example.yyplayer.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
object PlayerController {

    private var player: ExoPlayer? = null

    fun getInstance(context: Context): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                .build()
            player!!.setSkipSilenceEnabled(false)
        }
        return player!!
    }

    fun release() {
        player?.release()
        player = null
    }

    /** 检查播放器实例是否已创建（不触发创建） */
    fun hasPlayer(): Boolean = player != null
}
