package com.nova.assistant.voice

import java.util.Locale

object RecognitionLanguagePolicy {

    data class Plan(val primary: String, val fallbacks: List<String>) {
        val all: List<String> get() = listOf(primary) + fallbacks
    }

    fun plan(system: Locale): Plan {
        val urduFirst = system.language.equals("ur", ignoreCase = true) ||
            system.language.equals("fa", ignoreCase = true)
        return if (urduFirst) {
            Plan("ur-PK", listOf("en-IN", "en-US"))
        } else {
            Plan("en-US", listOf("en-IN", "ur-PK"))
        }
    }
}
