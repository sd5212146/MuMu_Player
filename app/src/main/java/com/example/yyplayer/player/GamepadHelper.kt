package com.example.yyplayer.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * 游戏手柄 + 线控耳机操作辅助类
 *
 * 线控耳机:
 *   单击 → 暂停/播放
 *   双击 → 下一首
 *   三击 → 上一首
 *
 * 游戏手柄 L1/R1 双击 → 上一首/下一首
 * L+R 同时按 → 暂停/播放
 * Select → 暂停/播放
 * X → 音量+
 * Y → 音量-
 * A → 确认
 * B → 返回
 */
object GamepadHelper {

    private const val TAG = "GamepadHelper"
    private const val DOUBLE_TAP_TIMEOUT = 300L

    private var lastL1Press = 0L
    private var lastR1Press = 0L
    private var isL1Down = false
    private var isR1Down = false

    // 线控耳机连击检测
    private var headsetClickCount = 0
    private val headsetHandler = Handler(Looper.getMainLooper())
    private val headsetClickRunnable = Runnable {
        when (headsetClickCount) {
            1 -> headsetOnPlayPause?.invoke()
            2 -> headsetOnNext?.invoke()
        }
        headsetClickCount = 0
    }
    private var headsetOnPlayPause: (() -> Unit)? = null
    private var headsetOnNext: (() -> Unit)? = null
    private var headsetOnPrevious: (() -> Unit)? = null

    /** 处理 L1/R1 相关按键 */
    fun handleLRKeyEvent(
        event: KeyEvent,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        onPlayPause: () -> Unit
    ): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> handleL1(event, onPrevious, onPlayPause)
            KeyEvent.KEYCODE_BUTTON_R1 -> handleR1(event, onNext, onPlayPause)
            else -> false
        }
    }

    /** 处理全局控制按键（Select → 暂停, X/Y → 音量） */
    fun handleGlobalKeyEvent(
        event: KeyEvent,
        context: Context,
        onPlayPause: () -> Unit
    ): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                Log.d(TAG, "Select 按下 → 暂停/播放")
                onPlayPause()
                true
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                adjustVolume(context, true)
                true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                adjustVolume(context, false)
                true
            }
            else -> false
        }
    }

    /** 处理线控耳机按钮（KEYCODE_HEADSETHOOK / KEYCODE_MEDIA_PLAY_PAUSE）
     *  单击→暂停/播放, 双击→下一首, 三击→上一首 */
    fun handleHeadsetButton(
        event: KeyEvent,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit
    ): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val isHeadsetKey = event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                           event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        if (!isHeadsetKey) return false

        headsetOnPlayPause = onPlayPause
        headsetOnNext = onNext
        headsetOnPrevious = onPrevious

        headsetClickCount++
        headsetHandler.removeCallbacks(headsetClickRunnable)

        if (headsetClickCount == 3) {
            // 三击立即响应，不等待延迟
            Log.d(TAG, "线控三击 → 上一首")
            headsetClickCount = 0
            onPrevious()
            return true
        }

        // 单击/双击等待 500ms 确认无后续点击
        headsetHandler.postDelayed(headsetClickRunnable, 500)
        return true
    }

    /** 判断是否为 A/B 键（用于 LibraryScreen 确认/返回） */
    fun isABButton(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B

    /** 判断方向键 */
    fun isDpadKey(keyCode: Int): Boolean = keyCode in listOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    )

    /** 判断是否为确认/返回键 */
    fun isConfirmKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

    /** 调整系统媒体音量 */
    private fun adjustVolume(context: Context, up: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            Log.d(TAG, if (up) "音量+" else "音量-")
        } catch (e: Exception) {
            Log.w(TAG, "音量调节失败", e)
        }
    }

    private fun handleL1(event: KeyEvent, onPrevious: () -> Unit, onPlayPause: () -> Unit): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // L1 按下时如果 R1 已经按着 → 同时按下
            if (isR1Down) {
                Log.d(TAG, "L+R 同时按下 → 暂停/播放")
                isL1Down = true
                onPlayPause()
                return true
            }
            isL1Down = true

            // 双击检测
            val now = System.currentTimeMillis()
            val isDouble = (now - lastL1Press) in 1..DOUBLE_TAP_TIMEOUT
            lastL1Press = now
            if (isDouble) {
                Log.d(TAG, "双击 L1 → 上一首")
                onPrevious()
            } else {
                Log.d(TAG, "单击 L1 (忽略)")
            }
            return true // 消费所有 DOWN 事件
        }
        if (event.action == KeyEvent.ACTION_UP) {
            isL1Down = false
            return true
        }
        return false
    }

    private fun handleR1(event: KeyEvent, onNext: () -> Unit, onPlayPause: () -> Unit): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // R1 按下时如果 L1 已经按着 → 同时按下
            if (isL1Down) {
                Log.d(TAG, "L+R 同时按下 → 暂停/播放")
                isR1Down = true
                onPlayPause()
                return true
            }
            isR1Down = true

            // 双击检测
            val now = System.currentTimeMillis()
            val isDouble = (now - lastR1Press) in 1..DOUBLE_TAP_TIMEOUT
            lastR1Press = now
            if (isDouble) {
                Log.d(TAG, "双击 R1 → 下一首")
                onNext()
            } else {
                Log.d(TAG, "单击 R1 (忽略)")
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            isR1Down = false
            return true
        }
        return false
    }

    /** 重置状态 */
    fun reset() {
        lastL1Press = 0L
        lastR1Press = 0L
        isL1Down = false
        isR1Down = false
        headsetClickCount = 0
        headsetHandler.removeCallbacks(headsetClickRunnable)
    }
}
