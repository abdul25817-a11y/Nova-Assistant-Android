package com.nova.assistant.voice

object VoiceInputFilter {

    private const val MIN_LENGTH = 2

    fun best(matches: List<String>?): String? {
        if (matches.isNullOrEmpty()) return null
        for (candidate in matches) {
            val clean = clean(candidate) ?: continue
            return clean
        }
        return null
    }

    fun clean(raw: String?): String? {
        if (raw == null) return null
        val text = raw.trim()
        if (text.length < MIN_LENGTH) return null
        if (!text.any { it.isLetter() }) return null
        return text
    }
}
