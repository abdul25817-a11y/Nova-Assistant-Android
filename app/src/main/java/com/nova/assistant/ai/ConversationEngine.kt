package com.nova.assistant.ai

import android.app.Application
import com.nova.assistant.data.ChatMessage
import com.nova.assistant.data.ConversationStore
import com.nova.assistant.data.Role
import com.nova.assistant.data.SettingsStore
import com.nova.assistant.voice.Speaker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ConversationEngine(app: Application) {

    private val conversation = ConversationStore(app)
    private val settingsStore = SettingsStore(app)
    private val ai = AiClient()

    val messages: Flow<List<ChatMessage>> = conversation.messages

    suspend fun send(text: String, source: String = Speaker.SOURCE_CHAT): String {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "empty message" }
        conversation.add(ChatMessage(Role.USER, clean))
        val settings = settingsStore.settings.first()
        val history = conversation.messages.first()
        val reply = ai.respond(clean, history, settings)
        conversation.add(ChatMessage(Role.ASSISTANT, reply.text))
        Speaker.speak(reply.text, source)
        return reply.text
    }

    suspend fun recordTurn(userText: String, reply: String, source: String) {
        if (userText.isBlank() || reply.isBlank()) return
        conversation.add(ChatMessage(Role.USER, userText.trim()))
        conversation.add(ChatMessage(Role.ASSISTANT, reply))
        Speaker.speak(reply, source)
    }

    suspend fun clear() = conversation.clear()
}
