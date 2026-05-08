package com.example.namiasistenteaioffline.data

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message as LiteRTMessage
import com.google.ai.edge.litertlm.MessageCallback
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class GemmaInferenceModel(
    private val context: Context,
    private val modelManager: ModelManager,
    private val appSettings: AppSettings
) {
    // MediaPipe (para Qwen)
    private var mpInference: LlmInference? = null
    
    // LiteRT-LM (para DeepSeek, Gemma 4, Phi)
    private var liteEngine: Engine? = null
    
    private var currentModelId: String? = null
    private var currentAccelerator: String? = null
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val inferenceMutex = Mutex()

    private suspend fun getEngine(modelId: String): Any? = withContext(Dispatchers.IO) {
        val modelPath = modelManager.getModelPath(modelId)
        if (modelPath.isEmpty()) {
            _lastError.value = "No se encontró el modelo $modelId."
            return@withContext null
        }

        val accelerator = appSettings.acceleratorFlow.first() // "cpu", "gpu", "npu"

        // Si el modelo cambió o cambió la preferencia de aceleración, liberamos RAM por completo
        if (currentModelId != modelId || currentAccelerator != accelerator) {
            Log.d("NamiHardware", "Cambiando acelerador a: $accelerator")
            Log.d("GemmaModel", "Cambio detectado (Modelo: $modelId, Acelerador: $accelerator). Reiniciando motor...")
            closeAll()
            currentModelId = modelId
            currentAccelerator = accelerator
        }

        // ---------- Qwen (MediaPipe) ----------
        if (modelId == "qwen_0.5b") {
            if (mpInference == null) {
                try {
                    val builder = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(1024)
                    
                    // Mapeamos GPU/NPU a GPU en MediaPipe (no tiene NPU propio)
                    val preferredBackend = when (accelerator) {
                        "gpu", "npu" -> LlmInference.Backend.GPU
                        else -> LlmInference.Backend.CPU
                    }
                    builder.setPreferredBackend(preferredBackend)
                    Log.d("GemmaModel", "Qwen: Solicitando backend $preferredBackend")
                    
                    mpInference = LlmInference.createFromOptions(context, builder.build())
                    Log.d("GemmaModel", "Qwen: Motor inicializado con éxito ($accelerator)")
                } catch (e: Exception) {
                    // Si el backend solicitado falla,Intentamos con CPU como último recurso.
                    if (accelerator != "cpu") {
                        Log.w("GemmaModel", "Qwen: Falló $accelerator, reintentando con CPU...", e)
                        try {
                            val builder = LlmInference.LlmInferenceOptions.builder()
                                .setModelPath(modelPath)
                                .setMaxTokens(1024)
                                .setPreferredBackend(LlmInference.Backend.CPU)
                            mpInference = LlmInference.createFromOptions(context, builder.build())
                            Log.d("GemmaModel", "Qwen: Motor inicializado en CPU (fallback)")
                        } catch (e2: Exception) {
                            _lastError.value = "Qwen falló totalmente: ${e2.message}"
                            Log.e("GemmaModel", "Qwen: Error crítico", e2)
                        }
                    } else {
                        _lastError.value = "Error en MediaPipe: ${e.message}"
                        Log.e("GemmaModel", "MP Error", e)
                    }
                }
            }
            return@withContext mpInference
        }

        // ---------- LiteRT‑LM (DeepSeek, Gemma 4, Phi) ----------
        if (liteEngine == null) {
            val backend = when (accelerator) {
                "gpu" -> try {
                    Backend.GPU()                     // puede lanzar excepción
                } catch (e: Exception) {
                    Log.w("GemmaModel", "GPU no disponible, usando CPU: ${e.message}")
                    Backend.CPU()
                }
                "npu" -> try {
                    Backend.NPU(
                        nativeLibraryDir = context.applicationInfo.nativeLibraryDir
                    )                                 // puede lanzar excepción (TF_LITE_AUX missing)
                } catch (e: Exception) {
                    Log.w(
                        "GemmaModel",
                        "NPU no disponible o modelo sin sección TF_LITE_AUX, usando CPU: ${e.message}"
                    )
                    Backend.CPU()
                }
                else -> Backend.CPU()
            }

            Log.d("GemmaModel", "LiteRT: Iniciando con backend $accelerator -> ${backend.javaClass.simpleName}")
            try {
                val config = EngineConfig(modelPath, backend = backend)
                val engine = Engine(config)
                engine.initialize()
                liteEngine = engine
                Log.d("GemmaModel", "LiteRT: Motor inicializado con éxito")
            } catch (e: Exception) {
                // Último intento: forzamos CPU por si el backend seleccionado seguía siendo problemático.
                Log.e("GemmaModel", "Fallo al crear Engine con $backend, intentando CPU...", e)
                try {
                    val config = EngineConfig(modelPath, backend = Backend.CPU())
                    liteEngine = Engine(config).apply { initialize() }
                    Log.d("GemmaModel", "LiteRT: Motor recuperado en CPU")
                } catch (e2: Exception) {
                    _lastError.value = "Error en LiteRT-LM: ${e2.message}"
                    Log.e("GemmaModel", "LiteRT Error crítico", e2)
                }
            }
        }
        return@withContext liteEngine
    }

    fun generateResponse(
        prompt: String,
        history: List<Message>,
        responseMode: String = "normal",
        systemInstruction: String = ""
    ): Flow<String> = channelFlow {
        inferenceMutex.withLock {
            val selectedId = appSettings.selectedModelFlow.first()
            
            // Si el modo es "normal" (default), usamos la preferencia del usuario en AppSettings.
            val finalResponseMode = if (responseMode == "normal") {
                appSettings.responseModeFlow.first()
            } else {
                responseMode
            }
            
            val engine = getEngine(selectedId)

            if (engine == null) {
                send("❌ ${_lastError.value ?: "Error al cargar motor"}")
                return@withLock
            }

            try {
                val startInference = System.currentTimeMillis()
                val fullPrompt = buildPrompt(selectedId, prompt, history, finalResponseMode)

                if (selectedId == "qwen_0.5b" && engine is LlmInference) {
                    val response = engine.generateResponse(fullPrompt)
                    val duration = System.currentTimeMillis() - startInference
                    Log.d("GemmaModel", "$selectedId: Inferencia completada en ${duration}ms")
                    send(cleanModelResponse(response ?: ""))
                } else if (engine is Engine) {
                    val conversation = if (systemInstruction.isNotEmpty()) {
                        engine.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(
                                    Content.Text(systemInstruction)
                                )
                            )
                        )
                    } else {
                        engine.createConversation()
                    }
                    val deferred = CompletableDeferred<Unit>()
                    
                    try {
                        conversation.sendMessageAsync(fullPrompt, object : MessageCallback {
                            override fun onMessage(message: LiteRTMessage) {
                                val token = message.toString()
                                if (token.isNotEmpty()) {
                                    val cleaned = cleanStreamingToken(token)
                                    if (cleaned.isNotEmpty()) {
                                        trySend(cleaned)
                                    }
                                }
                            }
                            override fun onDone() {
                                deferred.complete(Unit)
                            }
                            override fun onError(throwable: Throwable) {
                                trySend("❌ Error: ${throwable.localizedMessage}")
                                deferred.complete(Unit)
                            }
                        })
                        deferred.await()
                    } finally {
                        try { conversation.close() } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) {
                send("❌ Error: ${e.localizedMessage}")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun cleanStreamingToken(text: String): String {
        var result = text
        listOf(
            "<start_of_turn>", "<end_of_turn>",
            "<|im_end|>", "<|im_start|>", "<|end|>",
            "<｜Assistant｜>", "<｜User｜>",
            "<|user|>", "<|assistant|>", "<|endoftext|>",
            "model\n", "user\n"
        ).forEach { token ->
            result = result.replace(token, "")
        }
        return result.trimStart('\n')
    }

    private fun buildPrompt(modelId: String, prompt: String, history: List<Message>, responseMode: String): String {
        val id = modelId.lowercase()

        val lastMessages = history.takeLast(5)
        val modeInstruction = when (responseMode) {
            "corta" -> "Responde de forma muy breve y directa, máximo 2-3 oraciones. "
            "detallada" -> "Responde de forma completa y detallada, con ejemplos si es necesario. "
            else -> "" // normal, sin instrucción adicional
        }
        val promptWithMode = "$modeInstruction${prompt.trim()}"

        return when {
            id.contains("gemma") -> {
                buildString {
                    lastMessages.forEach { msg ->
                        append("<start_of_turn>${if (msg.isUser) "user" else "model"}\n${msg.text.trim()}<end_of_turn>\n")
                    }
                    append("<start_of_turn>user\n${promptWithMode}<end_of_turn>\n<start_of_turn>model\n")
                }
            }
            id.contains("deepseek") -> {
                buildString {
                    lastMessages.forEach { msg ->
                        if (msg.isUser) append("<｜User｜>${msg.text.trim()}")
                        else append("<｜Assistant｜>${msg.text.trim()}")
                    }
                    append("<｜User｜>${promptWithMode}<｜Assistant｜>")
                }
            }
            id.contains("phi") -> {
                buildString {
                    lastMessages.forEach { msg ->
                        append("<|${if (msg.isUser) "user" else "assistant"}|>\n${msg.text.trim()}\n<|end|>\n")
                    }
                    append("<|user|>\n${promptWithMode}\n<|end|>\n<|assistant|>\n")
                }
            }
            else -> { // Qwen o fallback
                buildString {
                    lastMessages.forEach { msg ->
                        append("<|im_start|>${if (msg.isUser) "user" else "assistant"}\n${msg.text.trim()}<|im_end|>\n")
                    }
                    append("<|im_start|>user\n${promptWithMode}<|im_end|>\n<|im_start|>assistant\n")
                }
            }
        }
    }

    private fun cleanModelResponse(text: String): String {
        var cleaned = text
        // Remove known control tokens
        val tokens = listOf(
            "<start_of_turn>", "<end_of_turn>",
            "<|im_end|>", "<|end|>", "<|im_start|>",
            "<｜Assistant｜>", "<｜User｜>",
            "<|user|>", "<|assistant|>", "<|endoftext|>"
        )
        tokens.forEach { cleaned = cleaned.replace(it, "") }
        
        // Remove role prefixes like "Assistant: " or "model:"
        cleaned = cleaned.replace(Regex("(?i)^(User|Assistant|Model|user|model):\\s*"), "")
        
        // Remove other HTML-like tags except <think>
        cleaned = cleaned.replace(Regex("<(?!/?think\\b)[^>]+>"), "")
        
        return cleaned.trim()
    }

    private fun closeAll() {
        Log.d("GemmaModel", "Liberando motores de inferencia para limpiar RAM...")
        logMemory("PRE-CLOSE")
        try {
            mpInference?.close()
            mpInference = null
            liteEngine?.close()
            liteEngine = null
            currentModelId = null
            currentAccelerator = null
            
            logMemory("POST-CLOSE")
            Log.d("GemmaModel", "Motores liberados completamente. RAM disponible aumentada.")
        } catch (e: Exception) {
            Log.e("GemmaModel", "Error al cerrar motores", e)
        }
    }

    private fun logMemory(tag: String) {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        Log.d("NamiHardware", "[$tag] RAM: ${usedMem}MB / ${maxMem}MB")
    }

    fun closeModel() = closeAll()

    fun clearLastError() {
        _lastError.value = null
    }
}