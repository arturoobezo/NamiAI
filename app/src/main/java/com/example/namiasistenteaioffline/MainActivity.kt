package com.example.namiasistenteaioffline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.namiasistenteaioffline.data.AppSettings
import com.example.namiasistenteaioffline.data.ChatHistoryManager
import com.example.namiasistenteaioffline.data.GemmaInferenceModel
import com.example.namiasistenteaioffline.data.ModelManager
import com.example.namiasistenteaioffline.data.VisionManager
import com.example.namiasistenteaioffline.data.VoiceManager
import com.example.namiasistenteaioffline.ui.screens.ChatScreen
import com.example.namiasistenteaioffline.ui.viewmodel.ChatViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.namiasistenteaioffline.ui.screens.ModelSelectionScreen
import com.example.namiasistenteaioffline.ui.screens.SettingsScreen
import com.example.namiasistenteaioffline.ui.screens.TranslatorScreen
import com.example.namiasistenteaioffline.ui.theme.NamiAsistenteAIOfflineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class Screen {
    Chat, Settings, ModelSelection, Translator
}

class MainActivity : ComponentActivity() {
    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appSettings = AppSettings(this)
        val historyManager = ChatHistoryManager(this)
        val modelManager = ModelManager(this)
        val gemmaModel = GemmaInferenceModel(this, modelManager, appSettings)
        val visionManager = VisionManager(modelManager, appSettings)
        voiceManager = VoiceManager(this)

        // Aplicar voz guardada al iniciar
        val scope = lifecycleScope
        scope.launch {
            val savedVoice = appSettings.selectedVoiceFlow.first()
            if (savedVoice.isNotEmpty()) {
                // Esperar a que TTS esté listo
                kotlinx.coroutines.delay(1000)
                voiceManager.applyVoice(savedVoice)
            }
        }
        
        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf(Screen.Chat) }
            val scope = rememberCoroutineScope()
            
            val darkMode by appSettings.darkModeFlow.collectAsState(initial = false)
            val voiceAssistantEnabled by appSettings.voiceEnabledFlow.collectAsState(initial = false)

            var hasAudioPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }

            NamiAsistenteAIOfflineTheme(darkTheme = darkMode) {
                when (currentScreen) {
                    Screen.Chat -> {
                        var pendingUserMessage by remember { mutableStateOf<String?>(null) }
                        var pendingAIResponse by remember { mutableStateOf<String?>(null) }
                        var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
                        var photoChatUri by remember { mutableStateOf<Uri?>(null) }
                        var showImageQueryDialog by remember { mutableStateOf(false) }
                        var userImageQuery by remember { mutableStateOf("") }

                        val imagePickerLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.GetContent()
                        ) { uri: Uri? ->
                            uri?.let {
                                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                                    @Suppress("DEPRECATION")
                                    MediaStore.Images.Media.getBitmap(contentResolver, it)
                                } else {
                                    val source = ImageDecoder.createSource(contentResolver, it)
                                    ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                                }
                                pendingBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                                showImageQueryDialog = true
                            }
                        }

                        val cameraLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.TakePicture()
                        ) { success: Boolean ->
                            if (success) {
                                photoChatUri?.let { uri ->
                                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                                        @Suppress("DEPRECATION")
                                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                                    } else {
                                        val source = ImageDecoder.createSource(contentResolver, uri)
                                        ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                                    }
                                    pendingBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                                    showImageQueryDialog = true
                                }
                            }
                        }

                        if (showImageQueryDialog) {
                            AlertDialog(
                                onDismissRequest = { 
                                    showImageQueryDialog = false
                                    userImageQuery = ""
                                },
                                title = { Text("Análisis de Imagen") },
                                text = {
                                    Column {
                                        Text("¿Qué quieres saber de esta imagen?")
                                        Spacer(Modifier.height(8.dp))
                                        TextField(
                                            value = userImageQuery,
                                            onValueChange = { userImageQuery = it },
                                            placeholder = { Text("Ej: ¿Qué hay en esta foto?") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        if (userImageQuery.isNotBlank()) {
                                            val bitmap = pendingBitmap
                                            val query = userImageQuery  // captura antes de limpiar
                                            
                                            if (bitmap != null) {
                                                // 1. Mostrar la pregunta INMEDIATAMENTE antes de procesar
                                                pendingUserMessage = "📷 $query"
                                                pendingAIResponse = "⏳ Analizando imagen..."
                                                
                                                // 2. Limpiar el diálogo
                                                pendingBitmap = null
                                                showImageQueryDialog = false
                                                userImageQuery = ""
                                                
                                                // 3. Procesar en background
                                                scope.launch {
                                                    val result = visionManager.analyzeImage(
                                                        bitmap, 
                                                        "Responde siempre en español. $query"
                                                    )
                                                    // 4. Actualizar con la respuesta real
                                                    pendingUserMessage = "📷 $query"
                                                    pendingAIResponse = result
                                                }
                                            } else {
                                                pendingBitmap = null
                                                showImageQueryDialog = false
                                                userImageQuery = ""
                                            }
                                        }
                                    }) { Text("Analizar") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { 
                                        showImageQueryDialog = false
                                        userImageQuery = ""
                                    }) { Text("Cancelar") }
                                }
                            )
                        }

                        val chatViewModel: ChatViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return ChatViewModel(gemmaModel, historyManager, appSettings, voiceManager) as T
                                }
                            }
                        )

                        ChatScreen(
                            onCameraClick = {
                                val photoFile = java.io.File(cacheDir, "chat_photo_${System.currentTimeMillis()}.jpg")
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${packageName}.provider",
                                    photoFile
                                )
                                photoChatUri = uri
                                cameraLauncher.launch(uri)
                            },
                            onSettingsClick = { currentScreen = Screen.Settings },
                            onAddFileClick = { imagePickerLauncher.launch("image/*") },
                            chatViewModel = chatViewModel,
                            voiceManager = voiceManager,
                            voiceAssistantEnabled = voiceAssistantEnabled,
                            hasAudioPermission = hasAudioPermission,
                            onRequestAudioPermission = {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            },
                            historyManager = historyManager,
                            appSettings = appSettings,
                            externalUserMessage = pendingUserMessage,
                            externalAIResponse = pendingAIResponse,
                            onMessagesConsumed = { 
                                pendingUserMessage = null
                                pendingAIResponse = null
                            },
                            onTranslatorClick = { currentScreen = Screen.Translator }
                        )
                    }
                    Screen.Translator -> {
                        BackHandler { currentScreen = Screen.Chat }
                        TranslatorScreen(
                            gemmaModel = gemmaModel,
                            voiceManager = voiceManager,
                            visionManager = visionManager,
                            appSettings = appSettings,
                            modelManager = modelManager,
                            hasAudioPermission = hasAudioPermission,
                            onBack = { currentScreen = Screen.Chat }
                        )
                    }
                    Screen.Settings -> {
                        BackHandler { currentScreen = Screen.Chat }
                        SettingsScreen(
                            appSettings = appSettings,
                            gemmaModel = gemmaModel,
                            voiceManager = voiceManager,
                            onBack = { currentScreen = Screen.Chat },
                            onChangeModel = { currentScreen = Screen.ModelSelection }
                        )
                    }
                    Screen.ModelSelection -> {
                        BackHandler { currentScreen = Screen.Settings }
                        ModelSelectionScreen(
                            modelManager = modelManager,
                            appSettings = appSettings,
                            onBack = { currentScreen = Screen.Settings }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}
