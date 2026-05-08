package com.example.namiasistenteaioffline.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namiasistenteaioffline.data.AppSettings
import com.example.namiasistenteaioffline.data.ChatHistoryManager
import com.example.namiasistenteaioffline.data.GemmaInferenceModel
import com.example.namiasistenteaioffline.data.Message
import com.example.namiasistenteaioffline.data.VoiceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(
    private val gemmaModel: GemmaInferenceModel,
    private val historyManager: ChatHistoryManager,
    private val appSettings: AppSettings,
    private val voiceManager: VoiceManager
) : ViewModel() {

    val messagesBySession = mutableStateMapOf<String, MutableList<Message>>()
    val generatingBySession = mutableStateMapOf<String, Boolean>()

    val lastError = gemmaModel.lastError

    fun clearLastError() {
        gemmaModel.clearLastError()
    }

    fun sendMessage(
        text: String,
        currentSessionId: String?,
        voiceAssistantEnabled: Boolean,
        onSessionCreated: (String) -> Unit
    ) {
        if (text.isBlank()) return

        viewModelScope.launch {
            var targetChatId = currentSessionId
            
            if (targetChatId == null) {
                val newChat = historyManager.createNewChat()
                targetChatId = newChat.id
                appSettings.setLastChatId(targetChatId)
                onSessionCreated(targetChatId)
            }

            val chatIdForThisRequest = targetChatId!!
            val sessionMessages = messagesBySession.getOrPut(chatIdForThisRequest) { 
                val existingChat = historyManager.chatsFlow.first().find { it.id == chatIdForThisRequest }
                val list = mutableStateListOf<Message>()
                existingChat?.messages?.let { list.addAll(it) }
                list
            }

            val isImageQuery = text.startsWith("IMAGE_QUERY:")
            val actualTextToModel = if (isImageQuery) text.removePrefix("IMAGE_QUERY:") else text
            val displayMessage = if (isImageQuery) "📷 $actualTextToModel" else text

            sessionMessages.add(Message(displayMessage, true))
            historyManager.updateChatMessages(chatIdForThisRequest, sessionMessages.toList())
            
            if (sessionMessages.size == 1) {
                val autoTitle = if (displayMessage.length > 20) displayMessage.take(17) + "..." else displayMessage
                historyManager.renameChat(chatIdForThisRequest, autoTitle)
            }
            
            var response = ""
            val historyContext = sessionMessages.toList()
            
            sessionMessages.add(Message("...", false))
            generatingBySession[chatIdForThisRequest] = true
            
            try {
                gemmaModel.generateResponse(actualTextToModel, historyContext).collect { chunk ->
                    response += chunk  // acumula, no reemplaza
                    if (sessionMessages.isNotEmpty()) {
                        sessionMessages[sessionMessages.size - 1] = Message(response, false)
                    }
                }
            } catch (e: Exception) {
                response = "Error: ${e.message}"
                if (sessionMessages.isNotEmpty() && sessionMessages.last().text == "...") {
                    sessionMessages[sessionMessages.size - 1] = Message(response, false)
                } else {
                    sessionMessages.add(Message(response, false))
                }
            } finally {
                generatingBySession[chatIdForThisRequest] = false
                historyManager.updateChatMessages(chatIdForThisRequest, sessionMessages.toList())
                
                if (voiceAssistantEnabled && response.isNotEmpty()) {
                    voiceManager.speak(response)
                }
            }
        }
    }

    fun handleExternalMessages(
        userMsg: String,
        aiResp: String,
        currentSessionId: String?,
        voiceAssistantEnabled: Boolean,
        onSessionCreated: (String) -> Unit,
        onConsumed: () -> Unit
    ) {
        viewModelScope.launch {
            var targetChatId = currentSessionId
            
            if (targetChatId == null) {
                val newChat = historyManager.createNewChat()
                targetChatId = newChat.id
                appSettings.setLastChatId(targetChatId)
                onSessionCreated(targetChatId)
            }

            val chatIdForThisRequest = targetChatId!!
            val sessionMessages = messagesBySession.getOrPut(chatIdForThisRequest) {
                val existingChat = historyManager.chatsFlow.first().find { it.id == chatIdForThisRequest }
                val list = mutableStateListOf<Message>()
                existingChat?.messages?.let { list.addAll(it) }
                list
            }

            sessionMessages.add(Message(userMsg, true))
            sessionMessages.add(Message(aiResp, false))
            
            historyManager.updateChatMessages(chatIdForThisRequest, sessionMessages.toList())
            
            if (sessionMessages.size <= 2) {
                val autoTitle = if (userMsg.length > 20) userMsg.take(17) + "..." else userMsg
                historyManager.renameChat(chatIdForThisRequest, autoTitle)
            }
            
            onConsumed()

            if (voiceAssistantEnabled && aiResp.isNotEmpty()) {
                voiceManager.speak(aiResp)
            }
        }
    }

    fun deleteChat(chatId: String, onNewChatCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            historyManager.deleteChat(chatId)
            messagesBySession.remove(chatId)
            generatingBySession.remove(chatId)

            val remaining = historyManager.chatsFlow.first()
            if (remaining.isEmpty()) {
                val newChat = historyManager.createNewChat()
                appSettings.setLastChatId(newChat.id)
                onNewChatCreated(newChat.id)
            } else {
                val lastId = appSettings.lastChatIdFlow.first()
                if (lastId == chatId) {
                    val nextId = remaining.first().id
                    appSettings.setLastChatId(nextId)
                    onNewChatCreated(nextId)
                }
            }
        }
    }
}
