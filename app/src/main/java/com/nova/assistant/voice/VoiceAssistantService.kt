package com.nova.assistant.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import com.nova.assistant.NovaApp
import com.nova.assistant.R
import com.nova.assistant.ai.AiClient
import com.nova.assistant.ai.LocalBrain
import com.nova.assistant.ai.AiReply
import com.nova.assistant.data.ChatMessage
import com.nova.assistant.data.ConversationStore
import com.nova.assistant.data.NovaSettings
import com.nova.assistant.data.Role
import com.nova.assistant.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class VoiceAssistantService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ai = AiClient()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var listening = false
    private var stopped = false

    @Volatile
    private var wakeEnabled = true

    @Volatile
    private var apiKey = ""

    @Volatile
    private var baseUrl = ""

    @Volatile
    private var model = ""

    override fun onCreate() {
        super.onCreate()
        stopped = false
        loadSettings()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = if (Locale.getDefault().language.lowercase(Locale.ROOT) == "ur") {
                    Locale("ur", "PK")
                } else {
                    Locale.US
                }
                if (tts?.setLanguage(lang) == TextToSpeech.LANG_MISSING_DATA) {
                    tts?.setLanguage(Locale.US)
                }
            }
        }
        startAsForeground()
        startListening()
        Toast.makeText(this, R.string.voice_started, Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, VoiceAssistantService::class.java).setAction(ACTION_STOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stop = PendingIntent.getService(this, 1, stopIntent, flags)
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, com.nova.assistant.ui.MainActivity::class.java), flags
        )
        return NotificationCompat.Builder(this, NovaApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_delete, getString(R.string.stop_action), stop)
            .build()
    }

    private fun loadSettings() {
        scope.launch(Dispatchers.IO) {
            try {
                val s = SettingsStore(applicationContext).settings.first()
                wakeEnabled = s.wakeWordEnabled
                apiKey = s.apiKey
                baseUrl = s.baseUrl
                model = s.model
            } catch (e: Exception) {
                // Keep offline defaults if settings cannot be read.
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (stopped || !hasMicPermission()) {
            stopSelf()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.speech_unavailable, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }

            override fun onError(error: Int) {
                listening = false
                scheduleRestart()
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onSpeech(text)
                else scheduleRestart()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                if (Locale.getDefault().language.lowercase(Locale.ROOT) == "ur") "ur-PK" else "en-US"
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            scheduleRestart()
        }
    }

    private fun onSpeech(text: String) {
        val lower = text.lowercase(Locale.ROOT)
        val addressed = !wakeEnabled || lower.contains("nova") || lower.contains("نووا")
        if (!addressed) {
            scheduleRestart()
            return
        }
        process(text)
    }

    private fun process(text: String) {
        scope.launch {
            val settings = NovaSettings(apiKey, baseUrl, model, wakeEnabled)
            val reply = try {
                ai.respond(text, listOf(ChatMessage(Role.USER, text)), settings)
            } catch (e: Exception) {
                AiReply(LocalBrain.reply(text), online = false)
            }
            speak(reply.text)
            withContext(Dispatchers.IO) {
                val store = ConversationStore(applicationContext)
                store.add(ChatMessage(Role.USER, text))
                store.add(ChatMessage(Role.ASSISTANT, reply.text))
            }
            scheduleRestart()
        }
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            // Ignore TTS failures; the reply is still stored in chat.
        }
    }

    private fun scheduleRestart() {
        if (stopped) return
        scope.launch {
            delay(1200)
            startListening()
        }
    }

    override fun onDestroy() {
        stopped = true
        listening = false
        recognizer?.destroy()
        recognizer = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nova.assistant.voice.STOP"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java).setAction(ACTION_STOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getService(context, 3, intent, flags).send()
        }
    }
}
