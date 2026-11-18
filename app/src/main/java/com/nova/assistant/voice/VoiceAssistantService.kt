package com.nova.assistant.voice

import android.app.ActivityManager
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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import com.nova.assistant.NovaApp
import com.nova.assistant.R
import com.nova.assistant.automation.ActionRouter
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
    private lateinit var actionRouter: ActionRouter
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var stopped = false
    private var localeAttempt = 0
    private val locales = RecognitionLanguagePolicy.plan(Locale.getDefault()).all

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
        actionRouter = ActionRouter(this)
        loadSettings()
        Speaker.init(this)
        scope.launch {
            Speaker.errors.collect {
                Toast.makeText(
                    this@VoiceAssistantService,
                    R.string.speech_output_error,
                    Toast.LENGTH_LONG
                ).show()
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
                val failure = SpeechErrorMapper.map(error)
                if (SpeechErrorMapper.shouldRetryWithAnotherLocale(failure) &&
                    localeAttempt < locales.lastIndex
                ) {
                    localeAttempt++
                    scope.launch {
                        delay(400)
                        startListening()
                    }
                } else {
                    scheduleRestart()
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = VoiceInputFilter.best(matches)
                if (text == null) {
                    scheduleRestart()
                } else {
                    localeAttempt = 0
                    onSpeech(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locales[localeAttempt])
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locales[localeAttempt])
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.listening))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
            val command = VoiceCommandParser.parse(text)
            if (command != null) {
                handleCommand(command, text)
                scheduleRestart()
                return@launch
            }
            val settings = NovaSettings(apiKey, baseUrl, model, wakeEnabled)
            val reply = try {
                ai.respond(text, listOf(ChatMessage(Role.USER, text)), settings)
            } catch (e: Exception) {
                AiReply(LocalBrain.reply(text), online = false)
            }
            Speaker.init(applicationContext)
            Speaker.speak(reply.text, Speaker.SOURCE_VOICE)
            withContext(Dispatchers.IO) {
                val store = ConversationStore(applicationContext)
                store.add(ChatMessage(Role.USER, text))
                store.add(ChatMessage(Role.ASSISTANT, reply.text))
            }
            scheduleRestart()
        }
    }

    private suspend fun handleCommand(command: VoiceCommand, original: String) {
        val feedback = when (val result = actionRouter.execute(command)) {
            is ActionRouter.Result.Launched ->
                getString(R.string.command_opening, result.target.displayName)
            is ActionRouter.Result.NotInstalled ->
                getString(R.string.command_not_installed, result.target.displayName)
            is ActionRouter.Result.Failed ->
                getString(R.string.command_failed, result.target.displayName)
            ActionRouter.Result.NoAction -> return
        }
        Speaker.init(applicationContext)
        Speaker.speak(feedback, Speaker.SOURCE_VOICE)
        withContext(Dispatchers.IO) {
            val store = ConversationStore(applicationContext)
            store.add(ChatMessage(Role.USER, original))
            store.add(ChatMessage(Role.ASSISTANT, feedback))
        }
    }

    private fun scheduleRestart() {
        if (stopped) return
        scope.launch {
            var attempts = 0
            while (Speaker.isSpeaking() && !stopped && attempts < 120) {
                delay(250)
                attempts++
            }
            delay(600)
            startListening()
        }
    }

    override fun onDestroy() {
        stopped = true
        listening = false
        Speaker.stop(Speaker.SOURCE_VOICE)
        recognizer?.destroy()
        recognizer = null
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

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            return manager.getRunningServices(Int.MAX_VALUE).any { service ->
                service.service.className == VoiceAssistantService::class.java.name
            }
        }

        fun stopIfRunning(context: Context) {
            if (isRunning(context)) stop(context)
        }
    }
}
