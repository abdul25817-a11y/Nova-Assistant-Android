package com.nova.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.assistant.ai.AiClient
import com.nova.assistant.data.ChatMessage
import com.nova.assistant.data.ConversationStore
import com.nova.assistant.data.Role
import com.nova.assistant.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ChatState {
    data object Idle : ChatState
    data object Thinking : ChatState
    data class Error(val message: String) : ChatState
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val conversation = ConversationStore(app)
    private val settingsStore = SettingsStore(app)
    private val ai = AiClient()

    val messages = conversation.messages

    private val _state = MutableStateFlow<ChatState>(ChatState.Idle)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            conversation.add(ChatMessage(role = Role.USER, content = clean))
            _state.value = ChatState.Thinking
            try {
                val settings = settingsStore.settings.first()
                val history = conversation.messages.first()
                val reply = ai.respond(clean, history, settings)
                conversation.add(ChatMessage(role = Role.ASSISTANT, content = reply.text))
                _state.value = ChatState.Idle
            } catch (e: Exception) {
                _state.value = ChatState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun clear() {
        viewModelScope.launch { conversation.clear() }
    }
}
