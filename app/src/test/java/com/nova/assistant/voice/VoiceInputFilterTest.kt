package com.nova.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceInputFilterTest {

    @Test
    fun blankAndShortInputIsRejected() {
        assertNull(VoiceInputFilter.clean(null))
        assertNull(VoiceInputFilter.clean(""))
        assertNull(VoiceInputFilter.clean("   "))
        assertNull(VoiceInputFilter.clean("a"))
    }

    @Test
    fun symbolsOnlyInputIsRejected() {
        assertNull(VoiceInputFilter.clean("123"))
        assertNull(VoiceInputFilter.clean("!!!"))
        assertNull(VoiceInputFilter.clean("؟؟؟"))
    }

    @Test
    fun validSpeechIsTrimmed() {
        assertEquals("open youtube", VoiceInputFilter.clean("  open youtube  "))
        assertEquals("یوٹیوب کھولو", VoiceInputFilter.clean("یوٹیوب کھولو"))
    }

    @Test
    fun bestResultSkipsEmptyAndUnclearMatches() {
        assertEquals(
            "open chrome",
            VoiceInputFilter.best(listOf("", " ", "!", "open chrome", "another result"))
        )
    }

    @Test
    fun emptyResultListReturnsNull() {
        assertNull(VoiceInputFilter.best(null))
        assertNull(VoiceInputFilter.best(emptyList()))
    }
}
