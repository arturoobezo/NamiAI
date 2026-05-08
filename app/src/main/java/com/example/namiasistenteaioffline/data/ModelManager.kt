package com.example.namiasistenteaioffline.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream

enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, INSTALLED }

data class AIModel(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val size: String,
    val iconName: String = "",
    val isAsset: Boolean = false,
    val fileName: String = "",
    var status: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    var progress: Float = 0f,
    var downloadedSize: Long = 0,
    var totalSize: Long = 0
)

class ModelManager(private val context: Context) {

    private val _models = MutableStateFlow(listOf(
        AIModel(
            "qwen_0.5b",
            "Qwen 0.5B (Base)",
            "Modelo ultra ligero pre-instalado, ideal para tareas básicas.",
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
            "547 MB",
            iconName = "Bolt",
            isAsset = true,
            fileName = "qwen_0.5.task"
        ),
        AIModel(
            "deepseek_r1",
            "DeepSeek R1 1.5B",
            "Excelente razonamiento, balanceado entre velocidad y precisión.",
            "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            "1.83 GB",
            iconName = "Psychology",
            fileName = "deepseek-r1-1.5b.litertlm"
        ),
        AIModel(
            "gemma_4_e2b",
            "Gemma 4 E2B (Vision)",
            "Modelo multimodal de Google optimizado para visión y análisis de imágenes.",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            "2.58 GB",
            iconName = "Visibility",
            fileName = "gemma-4-e2b.litertlm"
        ),
        AIModel(
            "gemma_4_e4b",
            "Gemma 4 E4B (Vision+)",
            "Versión potente para visión, requiere más memoria RAM.",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            "3.65 GB",
            iconName = "AutoAwesome",
            fileName = "gemma-4-e4b.litertlm"
        ),
        AIModel(
            "phi_4_mini",
            "Phi-4 Mini",
            "Modelo compacto de Microsoft con gran capacidad de instrucción.",
            "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            "3.91 GB",
            iconName = "SmartToy",
            fileName = "phi4-mini.litertlm"
        )
    ))
    val models: StateFlow<List<AIModel>> = _models

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val modelId = intent?.getStringExtra("MODEL_ID") ?: return
            val progress = intent.getFloatExtra("PROGRESS", -1f)
            val status = intent.getStringExtra("STATUS")

            if (status == "COMPLETED") {
                updateInstalledStatus()
            } else if (progress >= 0) {
                val totalBytes = intent.getLongExtra("TOTAL_BYTES", 0L)
                val expectedBytes = intent.getLongExtra("EXPECTED_BYTES", 0L)
                updateProgress(modelId, progress, totalBytes, expectedBytes)
            }
        }
    }

    init {
        updateInstalledStatus()
        registerReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction("MODEL_DOWNLOAD_PROGRESS")
            addAction("MODEL_DOWNLOAD_COMPLETE")
        }
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun updateInstalledStatus() {
        val currentModels = _models.value.toMutableList()
        currentModels.forEachIndexed { index, model ->
            val fileName = model.fileName
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.download")

            // Reconocer tanto .task como .litertlm
            val isInstalled = (file.exists() && file.length() > 0) || hasModelInAssets(fileName)
            val isDownloading = tempFile.exists() && !isInstalled

            currentModels[index] = model.copy(
                status = when {
                    isInstalled -> ModelStatus.INSTALLED
                    isDownloading -> ModelStatus.DOWNLOADING
                    else -> ModelStatus.NOT_DOWNLOADED
                }
            )
        }
        _models.value = currentModels
    }

    private fun hasModelInAssets(fileName: String): Boolean {
        return try {
            context.assets.list("")?.contains(fileName) == true
        } catch (e: Exception) {
            false
        }
    }

    fun getModelPath(modelId: String): String {
        val model = _models.value.find { it.id == modelId } ?: return ""
        val fileName = model.fileName

        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        if (model.isAsset && hasModelInAssets(fileName)) {
            val internalFile = File(context.filesDir, fileName)
            if (!internalFile.exists() || internalFile.length() <= 0) {
                copyAssetToFile(fileName, internalFile)
            }
            return internalFile.absolutePath
        }

        return ""
    }

    private fun copyAssetToFile(assetName: String, destination: File) {
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("ModelManager", "Error copiando asset $assetName", e)
        }
    }

    fun deleteModel(modelId: String) {
        val model = _models.value.find { it.id == modelId } ?: return
        val file = File(context.filesDir, model.fileName)
        if (file.exists()) file.delete()
        updateInstalledStatus()
    }

    fun downloadModel(modelId: String) {
        val model = _models.value.find { it.id == modelId } ?: return
        if (model.status != ModelStatus.DOWNLOADING) {
            updateModelInList(model.copy(status = ModelStatus.DOWNLOADING, progress = 0.01f))
            ModelDownloadService.startDownload(context, model.id, model.name, model.url, model.fileName)
        }
    }

    private fun updateProgress(modelId: String, progress: Float, downloadedBytes: Long = 0L, totalBytes: Long = 0L) {
        val model = _models.value.find { it.id == modelId } ?: return
        updateModelInList(model.copy(
            progress = progress,
            downloadedSize = downloadedBytes,
            totalSize = totalBytes
        ))
    }

    private fun updateModelInList(updatedModel: AIModel) {
        val currentList = _models.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedModel.id }
        if (index != -1) {
            currentList[index] = updatedModel
            _models.value = currentList
        }
    }
}