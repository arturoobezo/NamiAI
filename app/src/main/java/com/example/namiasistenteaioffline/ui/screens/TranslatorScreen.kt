package com.example.namiasistenteaioffline.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.namiasistenteaioffline.data.GemmaInferenceModel
import com.example.namiasistenteaioffline.data.ModelManager
import com.example.namiasistenteaioffline.data.VisionManager
import com.example.namiasistenteaioffline.data.VoiceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    gemmaModel: GemmaInferenceModel,
    voiceManager: VoiceManager,
    visionManager: VisionManager,
    appSettings: com.example.namiasistenteaioffline.data.AppSettings,
    modelManager: ModelManager,
    hasAudioPermission: Boolean,
    onBack: () -> Unit
) {
    val languages = listOf(
        "Español" to "es", "Inglés" to "en", "Francés" to "fr", "Alemán" to "de",
        "Italiano" to "it", "Portugués" to "pt", "Chino" to "zh", "Japonés" to "ja",
        "Coreano" to "ko", "Árabe" to "ar", "Ruso" to "ru", "Hindi" to "hi"
    )

    var sourceLang by remember { mutableStateOf(languages[0]) }
    var targetLang by remember { mutableStateOf(languages[1]) }
    var inputText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val isSpeaking by voiceManager.isSpeakingFlow.collectAsState()
    var isReloadingModel by remember { mutableStateOf(false) }
    var ttsErrorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
            }
            bitmap?.let { b ->
                scope.launch {
                    isTranslating = true
                    val extractionPrompt = "Extrae todo el texto visible en esta imagen. Responde ÚNICAMENTE con el texto extraído, sin traducciones, sin explicaciones, sin comentarios adicionales."
                    val extractedText = visionManager.analyzeImage(b.copy(Bitmap.Config.ARGB_8888, true), extractionPrompt)
                    
                    if (extractedText.isNotBlank()) {
                        inputText = extractedText
                        translatedText = ""
                        
                        val translationPrompt = "Eres un traductor profesional. Traduce el siguiente texto de ${sourceLang.first} a ${targetLang.first}. Responde ÚNICAMENTE con la traducción, sin explicaciones, sin notas, sin comentarios, sin el texto original:\n\n$extractedText"

                        gemmaModel.generateResponse(translationPrompt, emptyList()).collect { word ->
                            translatedText += word
                        }
                    }
                    isTranslating = false
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            photoUri?.let { uri ->
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                }
                bitmap?.let { b ->
                    scope.launch {
                        isTranslating = true
                        val extractionPrompt = "Extrae todo el texto visible en esta imagen. Responde ÚNICAMENTE con el texto extraído, sin traducciones, sin explicaciones, sin comentarios adicionales."
                        val extractedText = visionManager.analyzeImage(b.copy(Bitmap.Config.ARGB_8888, true), extractionPrompt)
                        
                        if (extractedText.isNotBlank()) {
                            inputText = extractedText
                            translatedText = ""
                            
                            val translationPrompt = "Eres un traductor profesional. Traduce el siguiente texto de ${sourceLang.first} a ${targetLang.first}. Responde ÚNICAMENTE con la traducción, sin explicaciones, sin notas, sin comentarios, sin el texto original:\n\n$extractedText"

                            gemmaModel.generateResponse(translationPrompt, emptyList()).collect { word ->
                                translatedText += word
                            }
                        }
                        isTranslating = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traductor Offline") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selectores de Idioma
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LanguageSelector(
                    selectedLang = sourceLang,
                    languages = languages,
                    onLangSelected = { sourceLang = it },
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = {
                    val temp = sourceLang
                    sourceLang = targetLang
                    targetLang = temp
                }) {
                    Icon(Icons.Default.SwapHoriz, "Intercambiar")
                }

                LanguageSelector(
                    selectedLang = targetLang,
                    languages = languages,
                    onLangSelected = { targetLang = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Campo de entrada
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Escribe aquí el texto a traducir...") },
                trailingIcon = {
                    Column {
                        IconButton(onClick = {
                            if (isListening) {
                                voiceManager.stopListening()
                                isListening = false
                            } else {
                                if (hasAudioPermission) {
                                    voiceManager.startListening(
                                        onResult = { result -> 
                                            inputText = result
                                        },
                                        onListeningStateChange = { listening ->
                                            isListening = listening
                                        }
                                    )
                                }
                            }
                        }) {
                            Icon(
                                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Dictar",
                                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Image, "Traducir imagen")
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = {
                    val photoFile = File(context.cacheDir, "translator_photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        photoFile
                    )
                    photoUri = uri
                    cameraLauncher.launch(uri)
                }) {
                    Icon(Icons.Default.PhotoCamera, "Cámara")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        scope.launch {
                            isTranslating = true
                            translatedText = ""
                            val prompt = "Eres un traductor profesional. Traduce el siguiente texto de ${sourceLang.first} a ${targetLang.first}. Responde ÚNICAMENTE con la traducción, sin explicaciones, sin notas, sin comentarios, sin el texto original:\n\n$inputText"
                            gemmaModel.generateResponse(prompt, emptyList()).collect { word ->
                                translatedText += word
                            }
                            isTranslating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTranslating && !isReloadingModel && inputText.isNotBlank()
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Traducir")
                }
            }

            Spacer(Modifier.height(24.dp))

            // Resultado
            if (translatedText.isNotEmpty() || isTranslating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Traducción:", style = MaterialTheme.typography.labelLarge)
                        Row {
                            IconButton(onClick = {
                                if (isSpeaking) {
                                    voiceManager.stopAll()
                                } else {
                                    if (translatedText.isNotEmpty()) {
                                        voiceManager.speakWithLanguage(
                                            text = translatedText,
                                            languageCode = targetLang.second,
                                            onLanguageNotAvailable = { langName ->
                                                ttsErrorMessage = langName
                                            }
                                        )
                                    }
                                }
                            }) {
                                Icon(
                                    if (isSpeaking) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                    contentDescription = "Escuchar traducción",
                                    tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(translatedText))
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copiar")
                            }
                        }
                    }
                    Text(
                        text = translatedText,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ttsErrorMessage?.let { langName ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Voz en $langName no disponible. Ve a Configuración de Android → Idioma → Texto a voz para instalarla.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    selectedLang: Pair<String, String>,
    languages: List<Pair<String, String>>,
    onLangSelected: (Pair<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLang.first,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.first) },
                    onClick = {
                        onLangSelected(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}
