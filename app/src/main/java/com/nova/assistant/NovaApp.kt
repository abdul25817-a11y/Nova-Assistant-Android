package com.nova.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nova.assistant.voice.Speaker

class NovaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Speaker.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_service_desc)
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SERVICE = "nova_service"
        lateinit var instance: NovaApp
            private set
    }
}
