package com.nova.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFailureTest {

    @Test
    fun networkErrorsAreMapped() {
        assertEquals(SpeechFailure.NETWORK, SpeechErrorMapper.map(1))
        assertEquals(SpeechFailure.NETWORK, SpeechErrorMapper.map(2))
    }

    @Test
    fun noMatchAndTimeoutAreMapped() {
        assertEquals(SpeechFailure.UNCLEAR, SpeechErrorMapper.map(7))
        assertEquals(SpeechFailure.NO_SPEECH, SpeechErrorMapper.map(6))
    }

    @Test
    fun unavailableAndPermissionAreMapped() {
        assertEquals(SpeechFailure.PERMISSION, SpeechErrorMapper.map(9))
        assertEquals(SpeechFailure.BUSY, SpeechErrorMapper.map(8))
        assertEquals(SpeechFailure.UNKNOWN, SpeechErrorMapper.map(999))
    }

    @Test
    fun onlyLanguageRelatedFailuresRetryWithAnotherLocale() {
        assertTrue(SpeechErrorMapper.shouldRetryWithAnotherLocale(SpeechFailure.UNCLEAR))
        assertTrue(SpeechErrorMapper.shouldRetryWithAnotherLocale(SpeechFailure.NO_SPEECH))
        assertFalse(SpeechErrorMapper.shouldRetryWithAnotherLocale(SpeechFailure.NETWORK))
        assertFalse(SpeechErrorMapper.shouldRetryWithAnotherLocale(SpeechFailure.PERMISSION))
    }
}
