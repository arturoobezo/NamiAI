package com.example.namiasistenteaioffline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namiasistenteaioffline.data.AppSettings
import com.example.namiasistenteaioffline.data.AIModel
import com.example.namiasistenteaioffline.data.ModelManager
import com.example.namiasistenteaioffline.data.ModelStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    modelManager: ModelManager,
    appSettings: AppSettings,
    onBack: () -> Unit
) {
    val models by modelManager.models.collectAsState()
    val selectedModelId by appSettings.selectedModelFlow.collectAsState(initial = "qwen_0.5b")
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Menu AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Configura tu Nami", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("El procesamiento es 100% local y privado.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(models) { model ->
                ModelCard(
                    model = model,
                    isSelected = model.id == selectedModelId,
                    onSelect = {
                        scope.launch {
                            appSettings.setSelectedModel(model.id)
                            snackbarHostState.showSnackbar("Modelo ${model.name} seleccionado")
                        }
                    },
                    onDownload = {
                        modelManager.downloadModel(model.id)
                    },
                    onDelete = {
                        modelManager.deleteModel(model.id)
                        scope.launch {
                            snackbarHostState.showSnackbar("Modelo eliminado")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: AIModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when(model.iconName) {
        "Bolt" -> Icons.Default.Bolt
        "AutoAwesome" -> Icons.Default.AutoAwesome
        "Visibility" -> Icons.Default.Visibility
        "BatterySaver" -> Icons.Default.BatterySaver
        "Psychology" -> Icons.Default.Psychology
        "SmartToy" -> Icons.Default.SmartToy
        else -> Icons.Default.SmartToy
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            2.dp, 
            if (isSelected) MaterialTheme.colorScheme.primary 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(model.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                // Menú de opciones siempre presente para todos los modelos
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (model.status == ModelStatus.DOWNLOADING) {
                            DropdownMenuItem(
                                text = { Text("Detener Descarga", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete() // Llama a deleteModel que ahora detiene la descarga
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Eliminar Archivos", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (model.status == ModelStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                val downloadedMB = (model.downloadedSize / 1_048_576f)
                val totalMB = (model.totalSize / 1_048_576f)
                
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Descargando...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (totalMB > 0) {
                            Text(
                                text = "${downloadedMB.toInt()} MB / ${totalMB.toInt()} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "${downloadedMB.toInt()} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (model.progress > 0) model.progress else 0f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            when (model.status) {
                ModelStatus.INSTALLED -> {
                    Button(
                        onClick = onSelect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSelected,
                        colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors()
                    ) {
                        Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSelected) "Modelo Activo" else "Usar este modelo")
                    }
                }
                ModelStatus.DOWNLOADING -> {
                    FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false) {
                        Text("Instalando (${(model.progress * 100).toInt()}%)...")
                    }
                }
                ModelStatus.NOT_DOWNLOADED -> {
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Descargar (${model.size})")
                    }
                }
            }
        }
    }
}
