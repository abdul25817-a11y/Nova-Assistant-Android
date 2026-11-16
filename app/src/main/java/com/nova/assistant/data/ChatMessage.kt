package com.nova.assistant.data

enum class Role { USER, ASSISTANT }

data class ChatMessage(
    val role: Role,
    val content: String,
    val id: Long = System.currentTimeMillis()
)