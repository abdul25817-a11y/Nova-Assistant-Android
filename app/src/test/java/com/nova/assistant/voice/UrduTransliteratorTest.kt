package com.nova.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrduTransliteratorTest {

    @Test
    fun greetingBecomesRomanUrdu() {
        assertEquals(
            "Assalam o Alaikum, aap kaise hain?",
            UrduTransliterator.toRomanUrdu("السلام علیکم، آپ کیسے ہیں؟")
        )
    }

    @Test
    fun detectsUrduScript() {
        assertTrue(UrduTransliterator.containsUrdu("السلام علیکم"))
    }

    @Test
    fun englishTextIsUnchanged() {
        assertEquals(
            "Hello, how are you?",
            UrduTransliterator.toRomanUrdu("Hello, how are you?")
        )
    }

    @Test
    fun commonUrduWordsAreTransliterated() {
        assertEquals("shukriya", UrduTransliterator.toRomanUrdu("شکریہ"))
        assertEquals("Nova", UrduTransliterator.toRomanUrdu("نووا"))
    }
}
