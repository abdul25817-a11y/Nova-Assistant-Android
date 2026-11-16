package com.nova.assistant.ai

import com.nova.assistant.data.ChatMessage
import com.nova.assistant.data.NovaSettings
import com.nova.assistant.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiReply(val text: String, val online: Boolean)

/**
 * Nova is offline-first by design. An online answer is attempted ONLY when the
 * user has explicitly entered both an endpoint URL and a key in Settings.
 * No paid service is enabled by default and no key is bundled with the app.
 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun respond(
        userText: String,
        history: List<ChatMessage>,
        settings: NovaSettings
    ): AiReply = withContext(Dispatchers.IO) {
        val canGoOnline = settings.apiKey.isNotBlank() && settings.baseUrl.isNotBlank()
        if (canGoOnline) {
            try {
                val text = callEndpoint(userText, history, settings)
                if (text.isNotBlank()) return@withContext AiReply(text, online = true)
            } catch (e: Exception) {
                // Fall through to the offline engine with a short notice.
                val local = LocalBrain.reply(userText)
                return@withContext AiReply(
                    "$local\n\n(Online reply failed: ${e.message ?: "unknown error"}. Used offline mode.)",
                    online = false
                )
            }
        }
        AiReply(LocalBrain.reply(userText), online = false)
    }

    private fun callEndpoint(
        userText: String,
        history: List<ChatMessage>,
        settings: NovaSettings
    ): String {
        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put(
                    "content",
                    "You are Nova, a concise and friendly assistant. " +
                        "Reply in the same language as the user. If the user writes in Urdu, reply in Urdu script. " +
                        "If they write in English, reply in English."
                )
        )
        history.takeLast(12).forEach { msg ->
            messages.put(
                JSONObject()
                    .put("role", if (msg.role == Role.USER) "user" else "assistant")
                    .put("content", msg.content)
            )
        }
        if (history.lastOrNull()?.content != userText) {
            messages.put(JSONObject().put("role", "user").put("content", userText))
        }

        val body = JSONObject()
            .put("model", settings.model.ifBlank { "default" })
            .put("messages", messages)
            .put("temperature", 0.7)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val url = settings.baseUrl.trim().trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val root = JSONObject(raw)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msg = choices.getJSONObject(0).optJSONObject("message")
                val content = msg?.optString("content").orEmpty()
                if (content.isNotBlank()) return content
            }
            throw IllegalStateException("Empty response from endpoint")
        }
    }
}
