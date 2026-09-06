package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SnowPreferences
import com.example.data.model.ChatMessage
import com.example.service.SnowAccessibilityService
import com.example.ui.components.CameraSheet
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.ChatSuggestionsRow
import com.example.ui.components.ConfigDialog
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.HistorySheet
import com.example.ui.components.MemorySheet
import com.example.ui.components.MessageInputBar
import com.example.ui.components.StartupSetupCard
import com.example.ui.components.ThinkingBubble
import com.example.ui.orb.VoiceOrb
import com.example.voice.VoiceState


@Composable
fun SnowMainScreen(
    viewModel: SnowViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val rmsAmplitude by viewModel.rmsAmplitude.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastAiResponse.collectAsStateWithLifecycle()
    val allMessages by viewModel.allMessages.collectAsStateWithLifecycle()
    val allMemories by viewModel.allMemories.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val statusBannerText by viewModel.statusBannerText.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmationResponse.collectAsStateWithLifecycle()
    val isProcessingPrompt by viewModel.isProcessingPrompt.collectAsStateWithLifecycle()

    val showConfigDialog by viewModel.showConfigDialog.collectAsStateWithLifecycle()
    val showCameraSheet by viewModel.showCameraSheet.collectAsStateWithLifecycle()
    val showHistorySheet by viewModel.showHistorySheet.collectAsStateWithLifecycle()
    val showMemorySheet by viewModel.showMemorySheet.collectAsStateWithLifecycle()
    val showDiagnosticsSheet by viewModel.showDiagnosticsSheet.collectAsStateWithLifecycle()

    var typedInputText by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.toggleListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice assistant", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            viewModel.openCamera()
        } else {
            Toast.makeText(context, "Camera permission is required for visual recognition", Toast.LENGTH_SHORT).show()
        }
    }

    val isSetupDismissed by viewModel.isSetupDismissed.collectAsStateWithLifecycle()
    val isAccessibilityEnabled = SnowAccessibilityService.isServiceRunning
    val isAiProviderConfigured = when (viewModel.preferences.activeAiProvider) {
        SnowPreferences.PROVIDER_OLLAMA -> viewModel.preferences.ollamaBaseUrl.isNotBlank()
        SnowPreferences.PROVIDER_GEMINI -> viewModel.preferences.customApiKey.isNotBlank() || com.example.BuildConfig.GEMINI_API_KEY.isNotBlank()
        SnowPreferences.PROVIDER_OPENAI -> viewModel.preferences.openAiApiKey.isNotBlank()
        SnowPreferences.PROVIDER_ANTHROPIC -> viewModel.preferences.anthropicApiKey.isNotBlank()
        else -> true
    }

    LaunchedEffect(Unit) {
        viewModel.wakeWordDetectedEvent.collect {
            Toast.makeText(context, "❄ \"Snow\" Wake Word Detected!", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll to latest message whenever messages update, or transcript changes, or thinking state changes
    LaunchedEffect(allMessages.size, partialTranscript, isProcessingPrompt) {
        val totalCount = allMessages.size + (if (isProcessingPrompt) 1 else 0) + (if (partialTranscript.isNotBlank()) 1 else 0)
        if (totalCount > 0) {
            chatListState.animateScrollToItem(totalCount)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF030712)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0C192E),
                            Color(0xFF050B14),
                            Color(0xFF020408)
                        ),
                        radius = 1200f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Bar with Action Icons in Three-Dot Menu
                TopBar(
                    assistantName = viewModel.preferences.assistantName,
                    language = viewModel.preferences.languageMode,
                    wakeWordActive = viewModel.preferences.wakeWordEnabled,
                    onMemoryClick = { viewModel.openMemorySheet() },
                    onDiagnosticsClick = { viewModel.openDiagnostics() },
                    onHistoryClick = { viewModel.openHistory() },
                    onSettingsClick = { viewModel.openConfigDialog() },
                    onPermissionsClick = { viewModel.reopenSetupCard() },
                    onClearChatClick = { viewModel.clearChatHistory() }
                )

                // 2. Startup Setup Card (Req 20)
                StartupSetupCard(
                    hasMicrophone = hasAudioPermission,
                    hasAccessibility = isAccessibilityEnabled,
                    hasAiProviderReady = isAiProviderConfigured,
                    aiProviderName = viewModel.preferences.activeAiProvider,
                    isDismissed = isSetupDismissed,
                    onRequestMicrophone = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onRequestAccessibility = { viewModel.permissionManager.openAccessibilitySettings() },
                    onConfigureAi = { viewModel.openConfigDialog() },
                    onDismiss = { viewModel.dismissSetupCard() },
                    onReopen = { viewModel.reopenSetupCard() }
                )


                // 2. Central Area: Chat Stream or Empty State with Voice Orb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (allMessages.isEmpty()) {
                        // Empty State: Hero Voice Orb + Suggestions
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            VoiceOrb(
                                voiceState = voiceState,
                                rmsAmplitude = rmsAmplitude,
                                onClick = {
                                    if (hasAudioPermission) {
                                        viewModel.toggleListening()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.padding(12.dp),
                                size = 220.dp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            StatusBadge(
                                voiceState = voiceState,
                                statusBanner = statusBannerText
                            )

                            if (voiceState == VoiceState.SPEAKING || isProcessingPrompt) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.interruptCurrentTask() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("hero_interrupt_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Interrupt", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("STOP / INTERRUPT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "How can I help you today?",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type below or tap the microphone to speak.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            ChatSuggestionsRow(
                                onSuggestionSelected = { prompt ->
                                    viewModel.handleUserPrompt(prompt, isTyped = true)
                                }
                            )
                        }
                    } else {
                        // Ongoing Conversation Stream
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_messages_list")
                        ) {
                            // Header: Compact Voice Orb & Status
                            item(key = "orb_header") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    VoiceOrb(
                                        voiceState = voiceState,
                                        rmsAmplitude = rmsAmplitude,
                                        onClick = {
                                            if (hasAudioPermission) {
                                                viewModel.toggleListening()
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier.size(90.dp),
                                        size = 90.dp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusBadge(
                                        voiceState = voiceState,
                                        statusBanner = statusBannerText
                                    )
                                    if (voiceState == VoiceState.SPEAKING || isProcessingPrompt) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { viewModel.interruptCurrentTask() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp).testTag("list_header_interrupt_button")
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = "Interrupt", tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("STOP / INTERRUPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }

                            // Saved Messages
                            items(
                                items = allMessages,
                                key = { it.id }
                            ) { message ->
                                ChatMessageBubble(
                                    message = message,
                                    onDelete = { id -> viewModel.deleteMessage(id) },
                                    onSaveImage = { uri -> viewModel.saveImageToGallery(uri) },
                                    onShareImage = { uri -> viewModel.shareImage(uri) }
                                )
                            }

                            // Live Voice Input Transcript
                            if (partialTranscript.isNotBlank()) {
                                item(key = "live_partial_transcript") {
                                    ChatMessageBubble(
                                        message = ChatMessage(
                                            id = -1,
                                            sender = "user",
                                            content = partialTranscript,
                                            timestamp = System.currentTimeMillis()
                                        ),
                                        onDelete = {}
                                    )
                                }
                            }

                            // Thinking / Agent Executing Indicator
                            if (isProcessingPrompt) {
                                item(key = "thinking_indicator") {
                                    ThinkingBubble(status = statusBannerText)
                                }
                            }
                        }
                    }
                }

                // 3. Compact Suggestions Bar above Input Field
                if (allMessages.isNotEmpty()) {
                    ChatSuggestionsRow(
                        onSuggestionSelected = { prompt ->
                            viewModel.handleUserPrompt(prompt, isTyped = true)
                        }
                    )
                }

                // 4. Modern Message Input Bar
                MessageInputBar(
                    text = typedInputText,
                    onTextChange = { typedInputText = it },
                    onSend = { textToSend ->
                        val trimmed = textToSend.trim()
                        if (trimmed.isNotBlank()) {
                            typedInputText = ""
                            viewModel.handleUserPrompt(trimmed, isTyped = true)
                        }
                    },
                    voiceState = voiceState,
                    onMicClick = {
                        if (hasAudioPermission) {
                            viewModel.toggleListening()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCameraClick = {
                        if (hasCameraPermission) {
                            viewModel.openCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onInterruptClick = {
                        viewModel.interruptCurrentTask()
                    },
                    isProcessing = isProcessingPrompt
                )
            }

            // Dialogs & Sheets
            if (showConfigDialog) {
                ConfigDialog(
                    preferences = viewModel.preferences,
                    onDismiss = { viewModel.closeConfigDialog() },
                    onSave = { viewModel.closeConfigDialog() },
                    onTestVoice = { pitch, rate, lang ->
                        viewModel.voiceEngine.configureFemaleVoice(lang)
                        viewModel.voiceEngine.speak(
                            when (lang) {
                                SnowPreferences.LANG_UR -> "السلام علیکم! میں سنو ہوں، آپ کی ذاتی آواز کی معاون۔"
                                SnowPreferences.LANG_HI -> "नमस्ते! मैं स्नो हूँ, आपकी पर्सनल वॉइस एजेंट।"
                                SnowPreferences.LANG_ROMAN_UR -> "Assalam o Alaikum! Main Snow hoon, aap ki personal AI voice agent."
                                SnowPreferences.LANG_PS -> "سلام! زه واوره يم، ستاسو د غږ ځیرکه همکاره."
                                else -> "Hello! I am Snow, your personal AI voice agent."
                            },
                            when (lang) {
                                SnowPreferences.LANG_UR -> "Urdu"
                                SnowPreferences.LANG_HI -> "Hindi"
                                SnowPreferences.LANG_ROMAN_UR -> "Roman Urdu"
                                SnowPreferences.LANG_PS -> "Pashto"
                                else -> "English"
                            }
                        )
                    }
                )
            }

            if (showCameraSheet) {
                CameraSheet(
                    onDismiss = { viewModel.closeCamera() },
                    onAnalyzeImage = { prompt, bitmap ->
                        viewModel.handleUserPrompt(prompt, bitmap, isTyped = true)
                    }
                )
            }

            if (showHistorySheet) {
                HistorySheet(
                    messages = allMessages,
                    onDismiss = { viewModel.closeHistory() },
                    onClearHistory = { viewModel.clearHistory() }
                )
            }

            if (showMemorySheet) {
                MemorySheet(
                    memories = allMemories,
                    notes = allNotes,
                    onDismiss = { viewModel.closeMemorySheet() },
                    onSaveMemory = { viewModel.saveMemory(it) },
                    onDeleteMemory = { viewModel.deleteMemory(it) },
                    onClearAllMemories = { viewModel.clearAllMemories() },
                    onSaveNote = { title, content -> viewModel.saveNote(title, content) },
                    onDeleteNote = { viewModel.deleteNote(it) }
                )
            }

            if (showDiagnosticsSheet) {
                DiagnosticsSheet(
                    preferences = viewModel.preferences,
                    permissionManager = viewModel.permissionManager,
                    onDismiss = { viewModel.closeDiagnostics() },
                    onTestProvider = { providerId ->
                        val result = viewModel.testProvider(providerId)
                        result.isSuccess to (if (result.isSuccess) "✓ Latency: ${result.latencyMs}ms (${result.message})" else "✗ ${result.message}")
                    },
                    onTestVoice = {
                        viewModel.voiceEngine.speak("Voice output operational.", "English")
                    }
                )
            }

            if (pendingConfirmation != null) {
                ConfirmationDialog(
                    promptMessage = pendingConfirmation!!.spokenText,
                    actionDescription = pendingConfirmation!!.pendingActionDescription,
                    onConfirm = { viewModel.confirmPendingAction(true) },
                    onDismiss = { viewModel.confirmPendingAction(false) }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    assistantName: String,
    language: String,
    wakeWordActive: Boolean,
    onMemoryClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onClearChatClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("❄", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = assistantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00F0FF)
                )
                Text(
                    text = if (wakeWordActive) "● Wake: \"Snow\"" else "○ Wake off",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wakeWordActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                    fontSize = 10.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Language badge
            Box(
                modifier = Modifier
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                val langLabel = when (language) {
                    SnowPreferences.LANG_UR -> "اردو"
                    SnowPreferences.LANG_HI -> "हिन्दी"
                    SnowPreferences.LANG_PS -> "پښتو"
                    SnowPreferences.LANG_ROMAN_UR -> "RomUr"
                    SnowPreferences.LANG_EN -> "EN"
                    else -> "Auto"
                }
                Text(
                    text = langLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Three-dot menu containing ALL settings and tools (Req 18 & 19)
            var menuExpanded by remember { mutableStateOf(false) }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp).testTag("three_dot_menu_button")
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .testTag("three_dot_dropdown_menu")
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings & AI Brain", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onSettingsClick()
                        },
                        modifier = Modifier.testTag("menu_settings_item")
                    )

                    DropdownMenuItem(
                        text = { Text("Ollama Diagnostics & Test", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDiagnosticsClick()
                        },
                        modifier = Modifier.testTag("menu_diagnostics_item")
                    )

                    DropdownMenuItem(
                        text = { Text("Memory & Saved Notes", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onMemoryClick()
                        },
                        modifier = Modifier.testTag("menu_memory_item")
                    )

                    DropdownMenuItem(
                        text = { Text("Conversation History", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onHistoryClick()
                        },
                        modifier = Modifier.testTag("menu_history_item")
                    )

                    DropdownMenuItem(
                        text = { Text("Permissions & Setup", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onPermissionsClick()
                        },
                        modifier = Modifier.testTag("menu_permissions_item")
                    )

                    DropdownMenuItem(
                        text = { Text("Clear Conversation", color = Color(0xFFEF4444), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onClearChatClick()
                        },
                        modifier = Modifier.testTag("menu_clear_chat_item")
                    )
                }
            }
        }
    }
}


@Composable
private fun StatusBadge(
    voiceState: VoiceState,
    statusBanner: String
) {
    val (baseText, statusColor) = when (voiceState) {
        VoiceState.LISTENING -> "● Listening for voice…" to Color(0xFF00F0FF)
        VoiceState.THINKING -> "✦ $statusBanner" to Color(0xFFC084FC)
        VoiceState.SPEAKING -> "► Snow is speaking…" to Color(0xFF38BDF8)
        VoiceState.ERROR -> "⚠ Microphone / Network error" to Color(0xFFF87171)
        VoiceState.IDLE -> (if (statusBanner != "Ready" && statusBanner.isNotBlank()) statusBanner else "Tap orb or type below") to Color(0xFF94A3B8)
    }

    Box(
        modifier = Modifier
            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("voice_status_badge")
    ) {
        Text(
            text = baseText,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}
