package com.nova.assistant.voice

import com.nova.assistant.automation.AppTarget
import java.util.Locale

sealed interface VoiceCommand {
    data class OpenApp(val target: AppTarget) : VoiceCommand
}

object VoiceCommandParser {

    private val WAKE_PREFIXES = listOf(
        "ہے نووا", "اے نووا", "hey nova", "hello nova", "نووا", "nova"
    )

    private val OPEN_WORDS = listOf(
        "open", "launch", "start", "run", "go to",
        "kholo", "khol", "kholein", "kholiye", "chalao", "chalo", "chalu",
        "shuru", "shuru karo", "شروع",
        "کھولو", "کھول", "کھولیں", "کھولیے", "کھولے", "کھولوں", "چلاؤ", "چلائیں", "چلاو"
    )

    private val ALIASES = mapOf(
        AppTarget.YOUTUBE to listOf(
            "youtube", "youtube app", "یوٹیوب", "یوٹوب", "يوتيوب"
        ),
        AppTarget.FACEBOOK to listOf(
            "facebook", "face book", "فیس بک", "فیس بوک", "فيس بوك"
        ),
        AppTarget.WHATSAPP to listOf(
            "whatsapp", "whats app", "واٹس ایپ", "وائٹس ایپ", "واتساپ", "واتس ایپ", "واتس اپ"
        ),
        AppTarget.CHROME to listOf(
            "chrome", "google chrome", "کروم", "گوگل کروم", "گولگل کروم"
        ),
        AppTarget.SETTINGS to listOf(
            "settings", "setting", "system settings", "سیٹنگز", "سیٹنگ", "سیٹنگس", "ترتیبات"
        )
    )

    private val NEGATIONS = listOf(
        "not", "dont", "cannot", "cant", "never", "unable", "مت", "نہیں", "نہ"
    )

    private val QUESTION_WORDS = listOf(
        "how", "why", "kaise", "kyun", "کیسے", "کیوں", "کیا"
    )

    private val OUT_OF_SCOPE = listOf(
        "message", "messages", "messaging", "sms", "send", "post", "posting",
        "call", "dial", "compose", "reply", "text",
        "میسج", "پیغام", "پیغامات", "ایس ایم ایس", "پوسٹ", "کال", "بھیجو",
        "بھیجیں", "لکھو", "لکھیں", "جواب"
    )

    fun normalize(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("['’ʼ]"), "")
            .replace(Regex("[\\p{M}]"), "")
            .replace(Regex("[\\p{P}\\p{S}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun stripWakeWord(raw: String): String {
        var text = normalize(raw)
        for (prefix in WAKE_PREFIXES) {
            if (text == prefix) return ""
            if (text.startsWith(prefix) && (text.length == prefix.length || text[prefix.length] == ' ')) {
                return text.substring(prefix.length).trim()
            }
        }
        return text
    }

    fun parse(raw: String): VoiceCommand? {
        val text = stripWakeWord(raw)
        if (text.isEmpty()) return null
        if (text.split(' ').size > 10) return null
        if (NEGATIONS.any { containsPhrase(text, it) }) return null
        if (QUESTION_WORDS.any { containsPhrase(text, it) }) return null
        if (OUT_OF_SCOPE.any { containsPhrase(text, it) }) return null
        if (!OPEN_WORDS.any { containsPhrase(text, it) }) return null

        val matches = ALIASES.filter { (_, aliases) ->
            aliases.any { containsPhrase(text, it) }
        }.keys
        if (matches.size != 1) return null

        return VoiceCommand.OpenApp(matches.first())
    }

    private fun containsPhrase(text: String, phrase: String): Boolean {
        val body = phrase.trim().split(Regex("\\s+")).joinToString("\\s+") {
            Regex.escape(it)
        }
        if (body.isEmpty()) return false
        val regex = Regex(
            "(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        return regex.containsMatchIn(text)
    }
}
