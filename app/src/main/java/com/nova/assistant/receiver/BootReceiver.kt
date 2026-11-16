package com.nova.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nova.assistant.data.SettingsStore
import com.nova.assistant.voice.VoiceAssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Restarts the optional wake-word service after a reboot when enabled. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context.applicationContext).settings.first()
                if (settings.wakeWordEnabled) {
                    withContext(Dispatchers.Main) {
                        try {
                            VoiceAssistantService.start(context.applicationContext)
                        } catch (e: Exception) {
                            // Android may restrict background FGS starts; the user
                            // can always start voice mode manually from the app.
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
