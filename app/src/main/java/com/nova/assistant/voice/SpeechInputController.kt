package com.nova.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.nova.assistant.R
import java.util.Locale

enum class ListeningState { IDLE, LISTENING, PROCESSING, CANCELLED }

class SpeechInputController(
    context: Context,
    private val hasPermission: () -> Boolean,
    private val onPermissionNeeded: () -> Unit,
    private val listener: Listener
) {

    interface Listener {
        fun onState(state: ListeningState)
        fun onPartial(text: String)
        fun onRecognized(text: String)
        fun onFailure(failure: SpeechFailure)
    }

    private val app = context.applicationContext
    private val locales = RecognitionLanguagePolicy.plan(Locale.getDefault()).all
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var destroyed = false
    private var attempt = 0
    private var generation = 0

    fun start() {
        if (destroyed) return
        if (!hasPermission()) {
            onPermissionNeeded()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            listener.onFailure(SpeechFailure.UNAVAILABLE)
            return
        }
        active = true
        attempt = 0
        startAttempt()
    }

    private fun startAttempt() {
        releaseRecognizer()
        val gen = ++generation
        val engine = SpeechRecognizer.createSpeechRecognizer(app)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {

            private fun current() = active && gen == generation

            override fun onReadyForSpeech(params: Bundle?) {
                if (current()) listener.onState(ListeningState.LISTENING)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (current()) listener.onState(ListeningState.PROCESSING)
            }

            override fun onError(error: Int) {
                if (!current()) return
                val failure = SpeechErrorMapper.map(error)
                if (SpeechErrorMapper.shouldRetryWithAnotherLocale(failure) &&
                    attempt < locales.lastIndex
                ) {
                    attempt++
                    listener.onState(ListeningState.PROCESSING)
                    startAttempt()
                    return
                }
                active = false
                listener.onState(ListeningState.IDLE)
                listener.onFailure(failure)
            }

            override fun onResults(results: Bundle?) {
                if (!current()) return
                active = false
                listener.onState(ListeningState.PROCESSING)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = VoiceInputFilter.best(matches)
                if (text == null) listener.onFailure(SpeechFailure.UNCLEAR)
                else listener.onRecognized(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!current()) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                listener.onPartial(VoiceInputFilter.best(matches).orEmpty())
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            engine.startListening(recognitionIntent())
        } catch (e: Exception) {
            if (gen != generation) return
            active = false
            listener.onState(ListeningState.IDLE)
            listener.onFailure(SpeechFailure.BUSY)
        }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locales[attempt])
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locales[attempt])
            putExtra(RecognizerIntent.EXTRA_PROMPT, app.getString(R.string.listening))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    fun cancel() {
        if (!active) return
        active = false
        generation++
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
        }
        listener.onState(ListeningState.CANCELLED)
        releaseRecognizer()
    }

    fun destroy() {
        destroyed = true
        active = false
        generation++
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        val engine = recognizer
        recognizer = null
        if (engine == null) return
        try {
            engine.stopListening()
        } catch (e: Exception) {
        }
        try {
            engine.destroy()
        } catch (e: Exception) {
        }
    }
}
