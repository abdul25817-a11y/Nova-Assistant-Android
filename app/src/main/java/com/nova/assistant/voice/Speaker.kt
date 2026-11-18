package com.nova.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Shared, app-wide text-to-speech speaker.
 *
 * Voice output is never skipped just because a language voice is missing: when
 * an Urdu voice is unavailable the reply is romanised and spoken with the
 * English voice instead. The original text is left untouched for the chat UI.
 */
object Speaker {

    const val SOURCE_VOICE = "voice"
    const val SOURCE_CHAT = "chat"

    enum class OutputState { IDLE, READY, SPEAKING, ERROR }

    private val _outputState = MutableStateFlow(OutputState.IDLE)
    val outputState: StateFlow<OutputState> = _outputState.asStateFlow()

    private val _errors = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val errors: SharedFlow<Unit> = _errors.asSharedFlow()

    private enum class State { IDLE, INITIALIZING, READY, FAILED }

    private data class Candidate(val text: String, val locale: Locale)

    private data class Attempt(
        val id: String,
        val source: String,
        val text: String,
        val candidates: List<Candidate>,
        var next: Int = 0,
        var awaiting: Boolean = false
    )

    private var state = State.IDLE
    private var tts: TextToSpeech? = null
    private var appContext: Context? = null
    private var pending: String? = null
    private var pendingSource = SOURCE_CHAT
    private var generation = 0
    private var utteranceCounter = 0
    private var active: Attempt? = null

    @Synchronized
    fun init(context: Context) {
        if (state == State.READY || state == State.INITIALIZING) return
        val ctx = context.applicationContext
        appContext = ctx
        val gen = ++generation
        state = State.INITIALIZING

        val ref = arrayOfNulls<TextToSpeech>(1)
        val engine: TextToSpeech
        try {
            engine = TextToSpeech(ctx) { status -> onInit(status, gen, ref) }
        } catch (e: Exception) {
            if (gen == generation && state == State.INITIALIZING) {
                state = State.FAILED
                reportError()
            }
            return
        }

        ref[0] = engine
        if (gen != generation || state == State.FAILED) {
            safeShutdown(engine)
            return
        }
        tts = engine
        if (state == State.READY) {
            installListener(engine)
            flushPending()
        }
    }

    @Synchronized
    private fun onInit(status: Int, gen: Int, ref: Array<TextToSpeech?>) {
        if (gen != generation) return
        if (status == TextToSpeech.SUCCESS) {
            state = State.READY
            _outputState.value = OutputState.READY
            ref[0]?.let { installListener(it) }
            flushPending()
        } else {
            state = State.FAILED
            ref[0]?.let { safeShutdown(it) }
            tts = null
            active = null
            reportError()
        }
    }

    @Synchronized
    fun speak(text: String, source: String = SOURCE_CHAT) {
        if (text.isBlank()) return
        if (state != State.READY || tts == null) {
            pending = text
            pendingSource = source
            if (state == State.FAILED) reportError()
            return
        }
        speakNow(text, source)
    }

    @Synchronized
    fun stop(source: String? = null) {
        val attempt = active ?: return
        if (source != null && attempt.source != source) return
        attempt.awaiting = false
        if (active === attempt) active = null
        _outputState.value = OutputState.READY
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun shutdown() {
        generation++
        active = null
        pending = null
        pendingSource = SOURCE_CHAT
        appContext = null
        safeShutdown(tts)
        tts = null
        state = State.IDLE
        _outputState.value = OutputState.IDLE
    }

    private fun flushPending() {
        val text = pending ?: return
        val source = pendingSource
        pending = null
        pendingSource = SOURCE_CHAT
        speakNow(text, source)
    }

    private fun speakNow(text: String, source: String) {
        val engine = tts ?: return
        val attempt = Attempt(
            id = "nova_${++utteranceCounter}_${System.currentTimeMillis()}",
            source = source,
            text = text,
            candidates = candidatesFor(engine, text)
        )
        active = attempt
        _outputState.value = OutputState.SPEAKING
        advance(attempt)
    }

    private fun advance(attempt: Attempt) {
        val engine = tts
        if (engine == null || state != State.READY) {
            pending = attempt.text
            pendingSource = attempt.source
            if (active === attempt) active = null
            reportError()
            return
        }
        while (attempt.next < attempt.candidates.size) {
            val candidate = attempt.candidates[attempt.next++]
            attempt.awaiting = true
            if (speakWith(engine, candidate, attempt.id)) return
            attempt.awaiting = false
        }
        if (active === attempt) active = null
        reportError()
    }

    private fun reportError() {
        _outputState.value = OutputState.ERROR
        _errors.tryEmit(Unit)
    }

    @Synchronized
    fun isSpeaking(): Boolean = active?.awaiting == true

    @Synchronized
    private fun onUtteranceDone(utteranceId: String?) {
        val attempt = active ?: return
        if (utteranceId == null || attempt.id != utteranceId) return
        attempt.awaiting = false
        if (active === attempt) active = null
        _outputState.value = OutputState.READY
    }

    @Synchronized
    private fun onUtteranceError(utteranceId: String?) {
        val attempt = active ?: return
        if (utteranceId == null || attempt.id != utteranceId || !attempt.awaiting) return
        attempt.awaiting = false
        advance(attempt)
    }

    private fun installListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onUtteranceDone(utteranceId) }
            override fun onError(utteranceId: String?) { onUtteranceError(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onUtteranceError(utteranceId)
            }
        })
    }

    private fun candidatesFor(engine: TextToSpeech, text: String): List<Candidate> {
        val hasUrdu = UrduTransliterator.containsUrdu(text)
        val result = mutableListOf<Candidate>()
        if (hasUrdu) {
            for (locale in listOf(Locale("ur", "PK"), Locale("ur"))) {
                if (available(engine, locale)) result.add(Candidate(text, locale))
            }
        }
        val roman = if (hasUrdu) UrduTransliterator.toRomanUrdu(text) else text
        for (locale in listOf(Locale.US, Locale.ENGLISH, Locale.getDefault())) {
            if (available(engine, locale)) result.add(Candidate(roman, locale))
        }
        if (result.isEmpty()) result.add(Candidate(roman, Locale.getDefault()))
        return result.distinctBy { it.locale }
    }

    private fun available(engine: TextToSpeech, locale: Locale): Boolean =
        engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE

    private fun speakWith(engine: TextToSpeech, candidate: Candidate, utteranceId: String): Boolean {
        if (!available(engine, candidate.locale)) return false
        if (engine.setLanguage(candidate.locale) < TextToSpeech.LANG_AVAILABLE) return false
        return engine.speak(
            candidate.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        ) == TextToSpeech.SUCCESS
    }

    private fun safeShutdown(engine: TextToSpeech?) {
        try {
            engine?.stop()
            engine?.shutdown()
        } catch (e: Exception) {
        }
    }
}
