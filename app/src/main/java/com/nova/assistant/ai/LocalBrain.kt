package com.nova.assistant.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic, on-device reply engine. Nova works fully offline with no key,
 * no account and no paid service. Online mode is strictly opt-in via Settings.
 */
internal object LocalBrain {

    fun reply(input: String): String {
        val q = input.trim()
        val urdu = isUrduScript(q) || isUrduLocale()
        if (q.isEmpty()) return pick(
            urdu,
            "Please type a message so I can help.",
            "مہربانی کر کے پیغام لکھیں تاکہ میں مدد کر سوں۔"
        )

        val lower = q.lowercase(Locale.getDefault())

        when {
            matches(lower, "hello", "hi", "hey", "salam", "assalam", "السلام", "ہیلو") ->
                return pick(urdu, "Hello! I'm Nova. How can I help you today?", "السلام علیکم! میں نووا ہوں۔ آج میں آپ کی کیا مدد کر سکتا ہوں؟")

            matches(lower, "how are you", "kaise ho", "کیسے ہو") ->
                return pick(urdu, "I'm doing great, thank you! What about you?", "میں ٹھیک ہوں، شکریہ! آپ کیسے ہیں؟")

            matches(lower, "your name", "who are you", "what are you", "تم کون ہو", "آپ کون ہیں", "نام") ->
                return pick(urdu, "I'm Nova, your offline AI assistant. I understand English and Urdu.", "میں نووا ہوں، آپ کا آف لائن AI معاون۔ میں انگریزی اور اردو دونوں سمجھتا ہوں۔")

            matches(lower, "help", "what can you do", "مدد", "کیا کر سکتے ہو") ->
                return pick(
                    urdu,
                    "I can chat in English and Urdu, tell you the time/date, solve simple maths, and tell jokes. " +
                        "For advanced answers you can optionally add your own AI-compatible endpoint in Settings.",
                    "میں انگریزی اور اردو میں بات کر سکتا ہوں، وقت/تاریخ بتا سکتا ہوں، سادہ حساب حل کر سکتا ہوں اور مذاق سنا سکتا ہوں۔ " +
                        "مزید جوابات کے لیے آپ ترتیبات میں اپنا AIendpoint شامل کر سکتے ہیں (اختیاری)۔"
                )

            matches(lower, "time", "what time", "کتنا بجے", "وقت", "ٹائم") ->
                return timeAnswer(urdu)

            matches(lower, "date", "today", "آج کی تاریخ", "تاریخ", "دن") ->
                return dateAnswer(urdu)

            matches(lower, "thanks", "thank you", "shukriya", "شکریہ") ->
                return pick(urdu, "You're welcome!", "خوش آمدید!")

            matches(lower, "bye", "goodbye", "الوداع", "خدا حافظ") ->
                return pick(urdu, "Goodbye! Talk to you soon.", "خدا حافظ! جلد ملتے ہیں۔")

            matches(lower, "joke", "mzaq", "مذاق", "چٹکلہ") ->
                return pick(
                    urdu,
                    "Why did the developer go broke? Because they used up all their cache!",
                    "استاد نے پوچھا: کمپیوٹر کو کب بخار ہوتا ہے؟ جب اس کے پاس بہت سارے 'ونڈوز' کھلے ہوں!"
                )

            matches(lower, "who made", "who created", "creator") ->
                return pick(urdu, "I was built as a private, offline-first assistant for this device.", "میں اس ڈیوائس کے لیے ایک نجی، آف لائن معاون کے طور پر بنایا گیا ہوں۔")

            isMath(lower) -> return mathAnswer(lower, urdu)

            else -> return pick(
                urdu,
                "I'm Nova, running in offline mode, so I don't have a live answer for that yet. " +
                    "I can still help with time, date, simple maths, jokes, or a chat. " +
                    "For deeper answers, add your own AI endpoint in Settings (optional).",
                "میں نووا ہوں اور آف لائن موڈ میں چل رہا ہوں، اس لیے میرے پاس اِس کا فوری جواب نہیں۔ " +
                    "میں وقت، تاریخ، سادہ حساب، مذاق یا گفتگو میں مدد کر سکتا ہوں۔ " +
                    "زیادہ گہرے جوابات کے لیے ترتیبات میں اپنا AIendpoint شامل کریں (اختیاری)۔"
            )
        }
    }

    private fun matches(text: String, vararg words: String) =
        words.any { w -> text == w || text.contains(w) }

    private fun pick(urdu: Boolean, en: String, ur: String) = if (urdu) ur else en

    private fun isUrduScript(text: String) = text.any { it in '\u0600'..'\u06FF' }

    private fun isUrduLocale() = Locale.getDefault().language.lowercase(Locale.ROOT) == "ur"

    private fun timeAnswer(urdu: Boolean): String {
        val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        return if (urdu) "اب وقت $t ہے۔" else "The current time is $t."
    }

    private fun dateAnswer(urdu: Boolean): String {
        val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        return if (urdu) "آج $d ہے۔" else "Today is $d."
    }

    private fun isMath(s: String): Boolean {
        if (s.isEmpty()) return false
        if (!s.any { it in "+-*/x×÷" }) return false
        return s.any { it.isDigit() }
    }

    private fun mathAnswer(expr: String, urdu: Boolean): String {
        val normalized = expr
            .replace("x", "*")
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")
            .replace(Regex("[^0-9+\\-*/().]"), "")
        return try {
            val value = evalSafe(normalized)
            val result = if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.4f", value)
            }
            "$normalized = $result"
        } catch (e: Exception) {
            if (urdu) "مجھے یہ حساب سمجھ نہیں آیا۔" else "I couldn't calculate that."
        }
    }

    private fun evalSafe(input: String): Double {
        val tokens = Regex("([0-9]+(?:\\.[0-9]+)?|[+\\-*/()])")
            .findAll(input).map { it.value }.toList()
        require(tokens.isNotEmpty())
        val ops = ArrayDeque<String>()
        val vals = ArrayDeque<Double>()
        val prec = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)

        fun apply() {
            val op = ops.removeLast()
            val b = vals.removeLast()
            val a = vals.removeLast()
            vals.addLast(
                when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b == 0.0) throw ArithmeticException("div0") else a / b
                    else -> throw IllegalArgumentException("op")
                }
            )
        }

        for (t in tokens) {
            when {
                t.toDoubleOrNull() != null -> vals.addLast(t.toDouble())
                t == "(" -> ops.addLast(t)
                t == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != "(") apply()
                    if (ops.isNotEmpty()) ops.removeLast()
                }
                else -> {
                    while (ops.isNotEmpty() && ops.last() != "(" &&
                        (prec[ops.last()] ?: 0) >= (prec[t] ?: 0)
                    ) apply()
                    ops.addLast(t)
                }
            }
        }
        while (ops.isNotEmpty()) apply()
        require(vals.size == 1)
        return vals.removeLast()
    }
}
