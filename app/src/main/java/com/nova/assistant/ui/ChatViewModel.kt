package com.nova.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.assistant.ai.ConversationEngine
import com.nova.assistant.voice.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChatState {
    data object Idle : ChatState
    data object Thinking : ChatState
    data class Error(val message: String) : ChatState
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ConversationEngine(app)

    val messages = engine.messages

    private val _state = MutableStateFlow<ChatState>(ChatState.Idle)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _state.value = ChatState.Thinking
            try {
                engine.send(clean, Speaker.SOURCE_CHAT)
                _state.value = ChatState.Idle
            } catch (e: Exception) {
                _state.value = ChatState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun clear() {
        viewModelScope.launch { engine.clear() }
    }
}
