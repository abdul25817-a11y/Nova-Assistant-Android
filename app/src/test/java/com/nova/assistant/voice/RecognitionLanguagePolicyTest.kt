package com.nova.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecognitionLanguagePolicyTest {

    @Test
    fun englishLocaleStartsWithEnglishAndFallsBackToUrdu() {
        val plan = RecognitionLanguagePolicy.plan(Locale.US)
        assertEquals("en-US", plan.primary)
        assertTrue(plan.fallbacks.contains("ur-PK"))
        assertEquals(3, plan.all.size)
    }

    @Test
    fun urduLocaleStartsWithUrduPakistan() {
        val plan = RecognitionLanguagePolicy.plan(Locale("ur", "PK"))
        assertEquals("ur-PK", plan.primary)
        assertTrue(plan.fallbacks.contains("en-US"))
    }

    @Test
    fun romanUrduIsCoveredByEnglishIndianLocale() {
        val plan = RecognitionLanguagePolicy.plan(Locale.ENGLISH)
        assertTrue(plan.all.contains("en-IN"))
    }

    @Test
    fun planAlwaysContainsBothScripts() {
        for (locale in listOf(Locale.US, Locale("ur", "PK"), Locale.ENGLISH)) {
            val all = RecognitionLanguagePolicy.plan(locale).all
            assertTrue(all.any { it.startsWith("en") })
            assertTrue(all.any { it.startsWith("ur") })
        }
    }
}
