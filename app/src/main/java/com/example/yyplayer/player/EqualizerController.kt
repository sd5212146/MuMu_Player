package com.example.yyplayer.player

import android.media.audiofx.Equalizer
import android.util.Log

class EqualizerController(private val audioSessionId: Int) {

    private var equalizer: Equalizer? = null

    companion object {
        private const val TAG = "EqualizerController"

        // EQ预设: 增益值数组(对应各频段)
        val PRESETS = mapOf(
            "normal" to "普通",
            "classical" to "古典",
            "rock" to "摇滚",
            "pop" to "流行",
            "jazz" to "爵士",
            "dance" to "舞曲",
            "vocal" to "人声",
            "custom" to "自定义"
        )

        val PRESET_VALUES = mapOf(
            "normal" to shortArrayOf(0, 0, 0, 0, 0),
            "classical" to shortArrayOf(0, 0, 0, 0, -2),
            "rock" to shortArrayOf(4, 3, -1, 2, 4),
            "pop" to shortArrayOf(-1, 3, 4, 3, -1),
            "jazz" to shortArrayOf(3, 2, 0, 2, 3),
            "dance" to shortArrayOf(5, 3, 0, -1, 3),
            "vocal" to shortArrayOf(-2, 2, 4, 3, -1)
        )
    }

    fun init(): Boolean {
        return try {
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Equalizer: ${e.message}")
            false
        }
    }

    fun getBandLevelRange(): ShortArray? {
        return try {
            equalizer?.bandLevelRange
        } catch (e: Exception) {
            null
        }
    }

    fun getNumberOfBands(): Int {
        return try {
            equalizer?.numberOfBands?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getCenterFreq(band: Int): Int {
        return try {
            equalizer?.getCenterFreq(band.toShort())?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun setBandLevel(band: Int, level: Short): Boolean {
        return try {
            equalizer?.setBandLevel(band.toShort(), level)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set band level: ${e.message}")
            false
        }
    }

    fun getBandLevel(band: Int): Short {
        return try {
            equalizer?.getBandLevel(band.toShort()) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun applyPreset(preset: String) {
        val values = PRESET_VALUES[preset] ?: return
        for (i in values.indices) {
            if (i < getNumberOfBands()) {
                setBandLevel(i, values[i])
            }
        }
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Equalizer: ${e.message}")
        }
    }
}
