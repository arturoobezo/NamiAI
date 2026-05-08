package com.example.namiasistenteaioffline.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.namiasistenteaioffline.data.AppSettings
import com.example.namiasistenteaioffline.data.GemmaInferenceModel
import com.example.namiasistenteaioffline.data.HardwareDetector
import com.example.namiasistenteaioffline.data.VoiceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    gemmaModel: GemmaInferenceModel,
    voiceManager: VoiceManager,
    onBack: () -> Unit,
    onChangeModel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val darkMode by appSettings.darkModeFlow.collectAsState(initial = false)
    val selectedModel by appSettings.selectedModelFlow.collectAsState(initial = "qwen_0.5b")
    val localHistory by appSettings.localHistoryFlow.collectAsState(initial = true)
    val voiceEnabled by appSettings.voiceEnabledFlow.collectAsState(initial = false)

    fun clearCache() {
        try {
            context.cacheDir.deleteRecursively()
            Toast.makeText(context, "Caché limpiada correctamente", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al limpiar caché", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sección Rendimiento
            SettingsSection(title = "Apariencia y Rendimiento") {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    SettingsToggleItem(
                        icon = Icons.Default.DarkMode,
                        title = "Tema Oscuro",
                        checked = darkMode,
                        onCheckedChange = { scope.launch { appSettings.setDarkMode(it) } }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Box(modifier = Modifier.padding(16.dp)) {
                        AcceleratorSelector(
                            appSettings = appSettings,
                            gemmaModel = gemmaModel
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Box(modifier = Modifier.padding(16.dp)) {
                        ResponseModeSelector(appSettings = appSettings)
                    }
                }
            }

            // Sección Modelo Dinámica
            SettingsSection(title = "Modelo en uso") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                val modelDisplay = when {
                                    selectedModel.contains("qwen") -> "Qwen 0.5B"
                                    selectedModel.contains("deepseek") -> "DeepSeek R1"
                                    selectedModel.contains("phi") -> "Phi-3.5"
                                    selectedModel.contains("gemma") -> "Gemma 2 2B"
                                    else -> "Modelo Local"
                                }
                                Text(modelDisplay, fontWeight = FontWeight.Bold)
                                Text("ID: $selectedModel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick = onChangeModel, shape = RoundedCornerShape(8.dp)) {
                            Text("Cambiar", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Sección Privacidad
            SettingsSection(title = "Privacidad y Almacenamiento") {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    SettingsClickItem(icon = Icons.Default.DeleteSweep, title = "Limpiar caché") { clearCache() }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleItem(
                        icon = Icons.Default.History,
                        title = "Historial Local",
                        checked = localHistory,
                        onCheckedChange = { scope.launch { appSettings.setLocalHistory(it) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleItem(
                        icon = Icons.Default.Mic,
                        title = "Asistente de Voz",
                        checked = voiceEnabled,
                        onCheckedChange = { scope.launch { appSettings.setVoiceEnabled(it) } }
                    )
                }
            }

            // Sección Voz
            SettingsSection(title = "Voz del Asistente") {
                VoiceSelector(
                    appSettings = appSettings,
                    voiceManager = voiceManager
                )
            }

            // Banner de Privacidad
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Privacidad Local Garantizada", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Nami AI no envía tus conversaciones a la nube. Todo el procesamiento ocurre en tu dispositivo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            // Sección Soporte
            SettingsSection(title = "Soporte") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "¿Te gustó Nami AI? Apoye al Desarrollador",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/paypalme/azulacero"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Donar")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelector(
    appSettings: AppSettings,
    voiceManager: VoiceManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val selectedVoiceName by appSettings.selectedVoiceFlow.collectAsState(initial = "")

    val availableVoices = remember { voiceManager.getAvailableVoices() }

    if (availableVoices.isEmpty()) {
        Text(
            "No hay voces disponibles en este dispositivo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val selectedVoice = availableVoices.find { it.name == selectedVoiceName }
    val displayName = selectedVoice?.locale?.displayName ?: "Voz por defecto"

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Voz seleccionada", fontWeight = FontWeight.Medium)
                    Text(displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            TextButton(onClick = { expanded = true }) {
                Text("Cambiar")
            }
        }

        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                title = { Text("Seleccionar Voz") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableVoices) { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            appSettings.setSelectedVoice(voice.name)
                                            voiceManager.applyVoice(voice.name)
                                            voiceManager.speak("Hola, esta es mi voz")
                                        }
                                        expanded = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(voice.locale.displayName, fontWeight = FontWeight.Medium)
                                    Text(voice.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                if (voice.name == selectedVoiceName) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { expanded = false }) { Text("Cerrar") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceleratorSelector(
    appSettings: AppSettings,
    gemmaModel: GemmaInferenceModel,
    onAcceleratorChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Detectamos una sola vez las capacidades reales del dispositivo
    val capabilities by produceState(initialValue = HardwareDetector.detectCapabilities(context)) {
        value = HardwareDetector.detectCapabilities(context)
    }

    // Estado del acelerador seleccionado (se mantiene mediante rememberSaveable)
    val selectedAccelerator = rememberSaveable { mutableStateOf("cpu") }
    
    // Sincronizar con el valor real de DataStore una vez al inicio
    LaunchedEffect(Unit) {
        selectedAccelerator.value = appSettings.acceleratorFlow.first()
    }

    // Lista de aceleradores que queremos mostrar (en el mismo orden que Edge Gallery)
    val allAccelerators = listOf("CPU", "GPU", "NPU", "TPU")

    // Filtramos según lo que el dispositivo realmente soporta
    val compatibleAccelerators = allAccelerators.filter { acc ->
        when (acc) {
            "CPU"   -> true
            "GPU"   -> capabilities.supportsGpu
            "NPU"   -> capabilities.supportsNpu
            "TPU"   -> false   // aún no implementado
            else    -> false
        }
    }

    // ------------------- UI: SegmentedButton al estilo Edge Gallery -------------------
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            compatibleAccelerators.forEachIndexed { index, acc ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = compatibleAccelerators.size),
                    onClick = {
                        val lowerAcc = acc.lowercase()
                        val isSupported = when (lowerAcc) {
                            "cpu" -> true
                            "gpu" -> capabilities.supportsGpu
                            "npu" -> capabilities.supportsNpu
                            else  -> false
                        }
                        if (isSupported) {
                            scope.launch {
                                appSettings.setAccelerator(lowerAcc)
                                selectedAccelerator.value = lowerAcc
                                // Cerrar el motor en background para no bloquear la UI
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    gemmaModel.closeModel()
                                }
                                Toast.makeText(context, "Acelerador cambiado a $acc — se aplicará en la próxima consulta", Toast.LENGTH_SHORT).show()
                                onAcceleratorChanged(acc)
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "El dispositivo no soporta $acc. Se mantendrá la opción actual.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    selected = selectedAccelerator.value.equals(acc, ignoreCase = true),
                    label = { Text(acc) }
                )
            }
        }

        // Descripción informativa debajo del selector
        Text(
            when (selectedAccelerator.value) {
                "gpu" -> "⚡ GPU — más rápida en modelos grandes (Gemma, Phi)"
                "npu" -> "🚀 NPU — máxima velocidad si el modelo lo soporta"
                else  -> "🔧 CPU — compatible con todos los modelos, estable"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseModeSelector(appSettings: AppSettings) {
    val scope = rememberCoroutineScope()
    val responseMode by appSettings.responseModeFlow.collectAsState(initial = "normal")
    val modes = listOf("corta", "normal", "detallada")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Longitud de respuesta",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    onClick = { scope.launch { appSettings.setResponseMode(mode) } },
                    selected = responseMode == mode,
                    label = { Text(mode.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                )
            }
        }
        Text(
            when (responseMode) {
                "corta" -> "Respuestas breves y directas"
                "detallada" -> "Respuestas completas con ejemplos"
                else -> "Equilibrio entre brevedad y detalle"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
        content()
    }
}

@Composable
fun SettingsToggleItem(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Medium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
