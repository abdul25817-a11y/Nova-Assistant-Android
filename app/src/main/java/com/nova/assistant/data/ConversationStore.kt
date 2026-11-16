package com.nova.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.chatStore by preferencesDataStore(name = "nova_chat")

class ConversationStore(private val context: Context) {

    private val key = stringPreferencesKey("conversation")

    val messages: Flow<List<ChatMessage>> = context.chatStore.data.map { p ->
        decode(p[key] ?: "")
    }

    suspend fun add(message: ChatMessage) {
        context.chatStore.edit { p ->
            val list = decode(p[key] ?: "").toMutableList()
            list.add(message)
            p[key] = encode(list)
        }
    }

    suspend fun clear() {
        context.chatStore.edit { it.remove(key) }
    }

    private fun encode(list: List<ChatMessage>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("role", it.role.name)
                    .put("content", it.content)
                    .put("id", it.id)
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<ChatMessage> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    role = if (o.getString("role") == "USER") Role.USER else Role.ASSISTANT,
                    content = o.getString("content"),
                    id = if (o.has("id")) o.getLong("id") else System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}