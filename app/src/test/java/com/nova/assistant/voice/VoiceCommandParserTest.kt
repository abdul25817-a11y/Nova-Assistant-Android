package com.nova.assistant.voice

import com.nova.assistant.automation.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    private fun target(input: String): AppTarget? =
        (VoiceCommandParser.parse(input) as? VoiceCommand.OpenApp)?.target

    @Test
    fun englishOpenCommands() {
        assertEquals(AppTarget.YOUTUBE, target("open youtube"))
        assertEquals(AppTarget.FACEBOOK, target("launch facebook"))
        assertEquals(AppTarget.WHATSAPP, target("start whatsapp"))
        assertEquals(AppTarget.CHROME, target("open chrome"))
        assertEquals(AppTarget.SETTINGS, target("open settings"))
    }

    @Test
    fun urduOpenCommands() {
        assertEquals(AppTarget.YOUTUBE, target("یوٹیوب کھولو"))
        assertEquals(AppTarget.FACEBOOK, target("فیس بک کھولو"))
        assertEquals(AppTarget.WHATSAPP, target("واٹس ایپ کھولو"))
        assertEquals(AppTarget.CHROME, target("کروم کھولو"))
        assertEquals(AppTarget.SETTINGS, target("ترتیبات کھولو"))
    }

    @Test
    fun romanUrduOpenCommands() {
        assertEquals(AppTarget.YOUTUBE, target("youtube kholo"))
        assertEquals(AppTarget.FACEBOOK, target("facebook kholo"))
        assertEquals(AppTarget.WHATSAPP, target("whatsapp kholo"))
        assertEquals(AppTarget.CHROME, target("chrome kholo"))
        assertEquals(AppTarget.SETTINGS, target("settings kholo"))
    }

    @Test
    fun wakeWordIsIgnored() {
        assertEquals(AppTarget.YOUTUBE, target("nova open youtube"))
        assertEquals(AppTarget.YOUTUBE, target("hey nova, open youtube"))
        assertEquals(AppTarget.YOUTUBE, target("نووا یوٹیوب کھولو"))
        assertEquals("", VoiceCommandParser.stripWakeWord("nova"))
        assertEquals("open youtube", VoiceCommandParser.stripWakeWord("hey nova open youtube"))
    }

    @Test
    fun unknownAndIrrelevantCommandsDoNothing() {
        assertNull(target("open twitter"))
        assertNull(target("i like youtube"))
        assertNull(target("how do i open youtube"))
        assertNull(target("don't open facebook"))
        assertNull(target("open facebook and youtube"))
        assertNull(target(""))
        assertNull(target("nova"))
    }

    @Test
    fun outOfScopeActionsDoNothing() {
        assertNull(target("whatsapp message bhejo"))
        assertNull(target("send sms to ali"))
        assertNull(target("facebook post karo"))
        assertNull(target("open whatsapp and send a message"))
        assertNull(target("وائٹس ایپ میں پیغام بھیجو"))
    }

    @Test
    fun assistantRepliesAreNeverTreatedAsCommands() {
        assertNull(target("I opened youtube for you already"))
        assertNull(target("Nova cannot open facebook right now"))
        assertNull(target("You asked me to launch chrome but I can only chat"))
    }

    @Test
    fun appTargetsAreConfigured() {
        assertEquals(5, AppTarget.entries.size)
        assertEquals(emptyList<String>(), AppTarget.SETTINGS.packages)
        assert(AppTarget.WHATSAPP.packages.contains("com.whatsapp"))
        assert(AppTarget.YOUTUBE.packages.contains("com.google.android.youtube"))
    }
}
