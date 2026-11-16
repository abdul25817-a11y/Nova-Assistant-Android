package com.nova.assistant.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBrainTest {

    @Test
    fun greetingsReplyInEnglish() {
        assertTrue(LocalBrain.reply("hello").contains("Nova"))
    }

    @Test
    fun urduScriptProducesUrduReply() {
        assertTrue(LocalBrain.reply("السلام علیکم").contains("نووا"))
    }

    @Test
    fun simpleArithmeticIsEvaluated() {
        assertTrue(LocalBrain.reply("2+3*4").contains("= 14"))
    }

    @Test
    fun parenthesesAreRespected() {
        assertTrue(LocalBrain.reply("(2+3)*4").contains("= 20"))
    }

    @Test
    fun divisionByZeroIsHandledGracefully() {
        assertTrue(LocalBrain.reply("9/0").contains("couldn't"))
    }

    @Test
    fun helpListsCapabilities() {
        assertTrue(LocalBrain.reply("help").contains("time/date"))
    }

    @Test
    fun unknownPromptStillGetsOfflineReply() {
        assertTrue(LocalBrain.reply("something unfamiliar").contains("offline mode"))
    }
}
