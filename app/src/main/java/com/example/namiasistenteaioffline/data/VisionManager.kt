package com.example.namiasistenteaioffline.data

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ConversationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream

class VisionManager(
    private val modelManager: ModelManager, 
    private val appSettings: AppSettings
) {
    private var liteEngine: Engine? = null
    private var currentPath: String? = null

    private suspend fun setupEngine() {
        val selectedId = appSettings.selectedModelFlow.first()
        val isVisionModel = selectedId.contains("gemma_4", ignoreCase = true)
        
        val modelPath = if (isVisionModel) {
            modelManager.getModelPath(selectedId)
        } else {
            modelManager.getModelPath("gemma_4_e2b")
        }

        if (modelPath.isEmpty()) return

        if (liteEngine == null || currentPath != modelPath) {
            close()
            try {
                // Implementación oficial de Google AI Edge Gallery
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.GPU()
                )
                liteEngine = Engine(config)
                liteEngine?.initialize()
                currentPath = modelPath
                Log.d("VisionManager", "✅ Motor de visión LiteRT-LM cargado (CPU + Vision GPU)")
            } catch (e: Exception) {
                Log.e("VisionManager", "❌ Error cargando LiteRT-LM", e)
            }
        }
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String = "Describe esta imagen"): String = withContext(Dispatchers.IO) {
        try {
            setupEngine()
            val engine = liteEngine ?: return@withContext "Error: No se pudo inicializar el motor de visión."

            val conversation = engine.createConversation()
            
            // Convertir Bitmap a PNG 100 (solicitado)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()

            val contents = listOf(
                Content.ImageBytes(byteArray),
                Content.Text(prompt)
            )
            
            return@withContext suspendCancellableCoroutine<String> { continuation ->
                val fullResponse = StringBuilder()
                
                // Uso de sendMessageAsync con MessageCallback (solicitado)
                conversation.sendMessageAsync(
                    Contents.of(contents),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val token = message.toString()
                            if (token.isNotEmpty()) {
                                fullResponse.append(token)
                            }
                        }

                        override fun onDone() {
                            if (continuation.isActive) {
                                continuation.resume(fullResponse.toString().trim())
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            if (continuation.isActive) {
                                Log.e("VisionManager", "Error en análisis multimodal", throwable)
                                continuation.resume("Error en el motor multimodal: ${throwable.localizedMessage}")
                            }
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            Log.e("VisionManager", "❌ Error crítico analizando imagen", e)
            return@withContext "Error: ${e.localizedMessage ?: e.toString()}"
        } finally {
            close()
        }
    }

    fun close() {
        liteEngine?.close()
        liteEngine = null
        currentPath = null
    }
}
