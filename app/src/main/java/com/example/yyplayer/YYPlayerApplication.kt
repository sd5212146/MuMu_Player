package com.example.yyplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class YYPlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("Startup_Timing", "┌── Application.onCreate START, timeFromBoot=" + android.os.SystemClock.elapsedRealtime())
        val t0 = System.currentTimeMillis()
        createNotificationChannel()
        val elapsed = System.currentTimeMillis() - t0
        android.util.Log.i("Startup_Timing", "└── Application.onCreate END, 耗时=${elapsed}ms")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "音乐播放控制通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "music_playback"
    }
}
