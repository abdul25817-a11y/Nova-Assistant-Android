package com.nova.assistant.voice

enum class SpeechFailure {
    NETWORK,
    NO_SPEECH,
    UNCLEAR,
    BUSY,
    PERMISSION,
    AUDIO,
    SERVER,
    UNAVAILABLE,
    CANCELLED,
    UNKNOWN
}

object SpeechErrorMapper {

    fun map(error: Int): SpeechFailure = when (error) {
        1, 2 -> SpeechFailure.NETWORK
        3 -> SpeechFailure.AUDIO
        4, 5 -> SpeechFailure.SERVER
        6 -> SpeechFailure.NO_SPEECH
        7 -> SpeechFailure.UNCLEAR
        8 -> SpeechFailure.BUSY
        9 -> SpeechFailure.PERMISSION
        else -> SpeechFailure.UNKNOWN
    }

    fun shouldRetryWithAnotherLocale(failure: SpeechFailure): Boolean =
        failure == SpeechFailure.UNCLEAR || failure == SpeechFailure.NO_SPEECH
}
