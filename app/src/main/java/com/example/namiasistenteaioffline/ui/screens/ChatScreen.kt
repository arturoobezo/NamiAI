package com.example.namiasistenteaioffline.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namiasistenteaioffline.R
import com.example.namiasistenteaioffline.data.AppSettings
import com.example.namiasistenteaioffline.data.ChatHistoryManager
import com.example.namiasistenteaioffline.data.ChatSession
import com.example.namiasistenteaioffline.data.GemmaInferenceModel
import com.example.namiasistenteaioffline.data.Message
import com.example.namiasistenteaioffline.data.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


import com.example.namiasistenteaioffline.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onCameraClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddFileClick: () -> Unit,
    chatViewModel: ChatViewModel,
    voiceManager: VoiceManager,
    voiceAssistantEnabled: Boolean,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    historyManager: ChatHistoryManager,
    appSettings: AppSettings,
    externalUserMessage: String? = null,
    externalAIResponse: String? = null,
    onMessagesConsumed: () -> Unit = {},
    onTranslatorClick: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val chats by historyManager.chatsFlow.collectAsState(initial = emptyList())
    val lastChatId by appSettings.lastChatIdFlow.collectAsState(initial = null)
    
    // Guardamos solo el ID de la sesión actual
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    
    // Obtenemos la sesión real buscando por ID en la lista de chats
    val currentSession = remember(currentSessionId, chats) {
        chats.find { it.id == currentSessionId }
    }
    
    // Mensajes reactivos vinculados al ID de sesión actual
    val messages = remember(currentSessionId) {
        currentSessionId?.let { id ->
            chatViewModel.messagesBySession.getOrPut(id) { mutableStateListOf() }
        } ?: mutableStateListOf()
    }

    // Sincronización: Cargar mensajes desde la base de datos a la memoria del ViewModel
    LaunchedEffect(currentSessionId, currentSession) {
        val id = currentSessionId ?: return@LaunchedEffect
        val session = currentSession ?: return@LaunchedEffect
        val list = chatViewModel.messagesBySession.getOrPut(id) { mutableStateListOf() }

        if (list.isEmpty() && session.messages.isNotEmpty()) {
            list.addAll(session.messages)
        }
    }
    
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showRenameDialog by remember { mutableStateOf<ChatSession?>(null) }
    var newTitleText by remember { mutableStateOf("") }
    val isGenerating = chatViewModel.generatingBySession[currentSessionId] ?: false

    // Si no hay sesión seleccionada, restauramos la última
    LaunchedEffect(chats, lastChatId) {
        if (currentSessionId == null && chats.isNotEmpty()) {
            currentSessionId = lastChatId ?: chats.first().id
        }
    }

    val sendMessage: (String) -> Unit = { text ->
        chatViewModel.sendMessage(
            text = text,
            currentSessionId = currentSessionId,
            voiceAssistantEnabled = voiceAssistantEnabled,
            onSessionCreated = { newId -> currentSessionId = newId }
        )
    }

    // Efecto para manejar mensajes externos (análisis de imagen)
    LaunchedEffect(externalUserMessage, externalAIResponse) {
        if (externalUserMessage != null && externalAIResponse != null) {
            chatViewModel.handleExternalMessages(
                userMsg = externalUserMessage,
                aiResp = externalAIResponse,
                currentSessionId = currentSessionId,
                voiceAssistantEnabled = voiceAssistantEnabled,
                onSessionCreated = { newId -> currentSessionId = newId },
                onConsumed = onMessagesConsumed
            )
        }
    }

    val isSpeaking by voiceManager.isSpeakingFlow.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val lastError by chatViewModel.lastError.collectAsState()

    LaunchedEffect(lastError) {
        lastError?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "OK",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                chatViewModel.clearLastError()
            }
        }
    }

    // Auto-start voice if enabled and not already listening
    LaunchedEffect(voiceAssistantEnabled, isSpeaking, isGenerating) {
        if (!voiceAssistantEnabled) return@LaunchedEffect
        if (isSpeaking || isGenerating || isListening) return@LaunchedEffect

        delay(2500)

        if (!voiceManager.isSpeaking() && !isGenerating && !isListening && voiceAssistantEnabled) {
            voiceManager.startListening(
                onResult = { text ->
                    if (text.length > 2) {
                        inputText = text
                        sendMessage(text)
                    }
                },
                onListeningStateChange = { listening ->
                    isListening = listening
                }
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Spacer(Modifier.height(48.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Nami AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            val newChat = historyManager.createNewChat()
                            currentSessionId = newChat.id
                            appSettings.setLastChatId(newChat.id)
                            drawerState.close()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nuevo Chat")
                }

                Text(
                    "Historial",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(chats) { chat ->
                        val isSelected = currentSession?.id == chat.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    if (!isSelected) {
                                        currentSessionId = chat.id
                                        scope.launch { appSettings.setLastChatId(chat.id) }
                                    }
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSelected) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                chat.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones", modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Renombrar") },
                                        onClick = {
                                            showMenu = false
                                            newTitleText = chat.title
                                            showRenameDialog = chat
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            val wasCurrent = (currentSessionId == chat.id)
                                            chatViewModel.deleteChat(chat.id) { newId ->
                                                if (wasCurrent) {
                                                    currentSessionId = newId
                                                }
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text("Cámara Vision") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onCameraClick() 
                    },
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Configuración") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onSettingsClick() 
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    ) {
        // DIALOGO PARA RENOMBRAR
        if (showRenameDialog != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                title = { Text("Renombrar Chat") },
                text = {
                    TextField(
                        value = newTitleText,
                        onValueChange = { newTitleText = it },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            historyManager.renameChat(showRenameDialog!!.id, newTitleText)
                            showRenameDialog = null
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) { Text("Cancelar") }
                }
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Nami AI",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = (-0.01).sp
                                )
                            }
                            Text(
                                "Asistente Offline y Facil con AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onTranslatorClick) {
                            Icon(Icons.Default.Translate, contentDescription = "Traductor", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onCameraClick) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Indicator (Simplified wave)
                    AnimatedVisibility(
                        visible = isListening,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                "Escuchando...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        tonalElevation = 2.dp,
                        modifier = Modifier.clickable { 
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            IconButton(onClick = onAddFileClick) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                enabled = !isGenerating,
                                placeholder = { Text("Escribe un mensaje...", color = MaterialTheme.colorScheme.outline) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send,
                                    keyboardType = KeyboardType.Text,
                                    autoCorrectEnabled = true
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (!isGenerating && inputText.isNotBlank()) {
                                            val text = inputText
                                            inputText = ""
                                            sendMessage(text)
                                        }
                                    }
                                )
                            )

                            IconButton(onClick = {
                                if (voiceAssistantEnabled || isListening || voiceManager.isSpeaking()) {
                                    voiceManager.stopAll()
                                    isListening = false
                                } else {
                                    if (hasAudioPermission) {
                                        voiceManager.startListening(
                                            onResult = { text -> inputText = text },
                                            onListeningStateChange = { listening -> isListening = listening }
                                        )
                                    } else {
                                        onRequestAudioPermission()
                                    }
                                }
                            }) {
                                Icon(
                                    if (isListening || voiceManager.isSpeaking()) Icons.Default.StopCircle else Icons.Default.Mic,
                                    contentDescription = "Mic",
                                    tint = if (isListening || voiceManager.isSpeaking()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    if (!isGenerating && inputText.isNotBlank()) {
                                        val text = inputText
                                        inputText = ""
                                        sendMessage(text)
                                    }
                                },
                                shape = CircleShape,
                                containerColor = if (isGenerating) MaterialTheme.colorScheme.surfaceVariant 
                                                 else MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    
                    Text(
                        "Meni Asistente AI utiliza procesamiento local. Tus datos nunca salen de este dispositivo.",
                        modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { 
                        keyboardController?.hide()
                    },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Privacy Badge
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Totalmente Privado y Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                items(messages) { message ->
                    ChatMessageBubble(message)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(message: Message) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!message.isUser) {
                Surface(
                    modifier = Modifier.size(32.dp).padding(bottom = 4.dp, end = 8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Surface(
                color = color,
                shape = shape,
                shadowElevation = if (message.isUser) 2.dp else 0.dp,
                border = if (!message.isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
                modifier = Modifier.combinedClickable(
                    onClick = { },
                    onLongClick = {
                        // Limpiar de etiquetas <think> para el portapapeles si el usuario prefiere
                        val textToCopy = message.text.replace("<think>", "").replace("</think>", "").trim()
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    }
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val thinkStart = "<think>"
                    val thinkEnd = "</think>"
                    val text = message.text

                    var currentIndex = 0
                    while (currentIndex < text.length) {
                        val startIdx = text.indexOf(thinkStart, currentIndex)
                        if (startIdx != -1) {
                            // Texto antes de <think>
                            val preThinkText = text.substring(currentIndex, startIdx).trim()
                            if (preThinkText.isNotEmpty()) {
                                Text(
                                    text = preThinkText,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            val endIdx = text.indexOf(thinkEnd, startIdx)
                            val thinkContent = if (endIdx != -1) {
                                text.substring(startIdx + thinkStart.length, endIdx).trim()
                            } else {
                                text.substring(startIdx + thinkStart.length).trim()
                            }

                            if (thinkContent.isNotEmpty()) {
                                Surface(
                                    color = textColor.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, textColor.copy(alpha = 0.1f)),
                                    modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            Icons.Default.Psychology,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                            tint = textColor.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = thinkContent,
                                            color = textColor.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontStyle = FontStyle.Italic,
                                                lineHeight = 16.sp
                                            )
                                        )
                                    }
                                }
                            }

                            if (endIdx != -1) {
                                currentIndex = endIdx + thinkEnd.length
                            } else {
                                currentIndex = text.length
                            }
                        } else {
                            // Texto final
                            val postThinkText = text.substring(currentIndex).trim()
                            if (postThinkText.isNotEmpty()) {
                                Text(
                                    text = postThinkText,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            currentIndex = text.length
                        }
                    }
                }
            }
        }
    }
}
