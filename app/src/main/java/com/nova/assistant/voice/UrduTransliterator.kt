package com.nova.assistant.voice

/**
 * Lightweight Urdu (Arabic script) to Roman Urdu converter.
 *
 * It is used only to feed an English TTS voice when no Urdu voice is installed.
 * No third-party library is required, so it stays on-device and unit-testable.
 */
object UrduTransliterator {

    private val URDU_RANGE = '\u0600'..'\u06FF'
    private val EXTENDED_RANGE = '\u0750'..'\u077F'

    fun containsUrdu(text: String): Boolean =
        text.any { it in URDU_RANGE || it in EXTENDED_RANGE }

    private val phrases = listOf(
        "السلام علیکم" to "Assalam o Alaikum",
        "خدا حافظ" to "Khuda Hafiz",
        "آپ کیسے ہیں" to "aap kaise hain",
        "آپ کون ہیں" to "aap kaun hain",
        "خوش آمدید" to "Khush Aamdeed",
        "کیسے ہو" to "kaise ho",
        "شکریہ" to "shukriya",
        "الوداع" to "Alvida",
        "نووا" to "Nova"
    ).sortedByDescending { it.first.length }

    private val letters = mapOf(
        'آ' to "aa", 'أ' to "a", 'إ' to "i", 'ا' to "a",
        'ب' to "b", 'پ' to "p", 'ت' to "t", 'ٹ' to "t", 'ث' to "s",
        'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh",
        'د' to "d", 'ڈ' to "d", 'ذ' to "z",
        'ر' to "r", 'ڑ' to "r", 'ز' to "z", 'ژ' to "zh",
        'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z",
        'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh",
        'ف' to "f", 'ق' to "q",
        'ک' to "k", 'ك' to "k", 'گ' to "g",
        'ل' to "l", 'م' to "m", 'ن' to "n", 'ں' to "n",
        'و' to "w", 'ؤ' to "w",
        'ہ' to "h", 'ه' to "h", 'ھ' to "h", 'ة' to "a",
        'ی' to "y", 'ي' to "y", 'ئ' to "y", 'ے' to "e",
        'ء' to "'", 'ٔ' to "", 'ٓ' to ""
    )

    private val punctuation = mapOf(
        '،' to ",", '؛' to ";", '؟' to "?", '۔' to ".",
        '٪' to "%", '٫' to ".", '٬' to ","
    )

    fun toRomanUrdu(text: String): String {
        var result = text
        for ((urdu, roman) in phrases) {
            result = result.replace(urdu, roman)
        }

        val sb = StringBuilder()
        for (ch in result) {
            when {
                ch in letters -> sb.append(letters[ch])
                ch in punctuation -> sb.append(punctuation[ch])
                ch in '\u064B'..'\u065F' || ch == '\u0670' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }
}
