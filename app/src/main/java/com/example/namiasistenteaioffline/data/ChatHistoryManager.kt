package com.example.namiasistenteaioffline.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nuevo Chat",
    val messages: List<Message> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

private val Context.historyDataStore by preferencesDataStore(name = "chat_history_v2")

class ChatHistoryManager(private val context: Context) {
    private val mutex = Mutex()

    companion object {
        val CHATS_KEY = stringPreferencesKey("chats_list")
    }

    val chatsFlow: Flow<List<ChatSession>> = context.historyDataStore.data.map { preferences ->
        val jsonString = preferences[CHATS_KEY] ?: "[]"
        parseChats(jsonString)
    }

    suspend fun saveChats(chats: List<ChatSession>) {
        val jsonString = serializeChats(chats)
        context.historyDataStore.edit { preferences ->
            preferences[CHATS_KEY] = jsonString
        }
    }

    suspend fun createNewChat(): ChatSession = mutex.withLock {
        val newChat = ChatSession()
        val currentChats = parseChats(getRawJson())
        val updatedChats = (listOf(newChat) + currentChats).distinctBy { it.id }
        saveChats(updatedChats)
        return newChat
    }

    suspend fun deleteChat(chatId: String) = mutex.withLock {
        val currentChats = parseChats(getRawJson()).toMutableList()
        currentChats.removeAll { it.id == chatId }
        saveChats(currentChats)
    }

    suspend fun renameChat(chatId: String, newTitle: String) = mutex.withLock {
        val currentChats = parseChats(getRawJson()).toMutableList()
        val index = currentChats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            currentChats[index] = currentChats[index].copy(title = newTitle)
            saveChats(currentChats)
        }
    }

    suspend fun updateChatMessages(chatId: String, messages: List<Message>) = mutex.withLock {
        val currentChats = parseChats(getRawJson()).toMutableList()
        val index = currentChats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            currentChats[index] = currentChats[index].copy(messages = messages, timestamp = System.currentTimeMillis())
            saveChats(currentChats)
        }
    }

    private suspend fun getRawJson(): String {
        return context.historyDataStore.data.map { it[CHATS_KEY] ?: "[]" }.first()
    }

    private fun parseChats(jsonString: String): List<ChatSession> {
        val list = mutableListOf<ChatSession>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val messagesArray = obj.getJSONArray("messages")
                val messages = mutableListOf<Message>()
                for (j in 0 until messagesArray.length()) {
                    val msgObj = messagesArray.getJSONObject(j)
                    messages.add(Message(msgObj.getString("text"), msgObj.getBoolean("isUser")))
                }
                list.add(ChatSession(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    messages = messages,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun serializeChats(chats: List<ChatSession>): String {
        val jsonArray = JSONArray()
        chats.forEach { chat ->
            val obj = JSONObject()
            obj.put("id", chat.id)
            obj.put("title", chat.title)
            obj.put("timestamp", chat.timestamp)
            val messagesArray = JSONArray()
            chat.messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("text", msg.text)
                msgObj.put("isUser", msg.isUser)
                messagesArray.put(msgObj)
            }
            obj.put("messages", messagesArray)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}
