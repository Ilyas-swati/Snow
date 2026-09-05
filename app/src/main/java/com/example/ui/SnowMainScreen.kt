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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.ui.components.CameraSheet
import com.example.ui.components.ConfigDialog
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.HistorySheet
import com.example.ui.components.MemorySheet
import com.example.ui.components.QuickCommandsBar
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

    val showConfigDialog by viewModel.showConfigDialog.collectAsStateWithLifecycle()
    val showCameraSheet by viewModel.showCameraSheet.collectAsStateWithLifecycle()
    val showHistorySheet by viewModel.showHistorySheet.collectAsStateWithLifecycle()
    val showMemorySheet by viewModel.showMemorySheet.collectAsStateWithLifecycle()
    val showDiagnosticsSheet by viewModel.showDiagnosticsSheet.collectAsStateWithLifecycle()

    var showTextInput by remember { mutableStateOf(false) }
    var manualTextQuery by remember { mutableStateOf("") }

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

    LaunchedEffect(Unit) {
        viewModel.wakeWordDetectedEvent.collect {
            Toast.makeText(context, "❄ \"Snow\" Wake Word Detected!", Toast.LENGTH_SHORT).show()
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
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Bar with Action Icons
                TopBar(
                    assistantName = viewModel.preferences.assistantName,
                    language = viewModel.preferences.languageMode,
                    wakeWordActive = viewModel.preferences.wakeWordEnabled,
                    onMemoryClick = { viewModel.openMemorySheet() },
                    onDiagnosticsClick = { viewModel.openDiagnostics() },
                    onHistoryClick = { viewModel.openHistory() },
                    onSettingsClick = { viewModel.openConfigDialog() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Central Voice Orb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        VoiceOrb(
                            voiceState = voiceState,
                            rmsAmplitude = rmsAmplitude,
                            onClick = { viewModel.openConfigDialog() },
                            modifier = Modifier.padding(12.dp),
                            size = 260.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status Badge / Agent Progress Banner
                        StatusBadge(
                            voiceState = voiceState,
                            statusBanner = statusBannerText
                        )
                    }
                }

                // 3. Live Speech & AI Transcript Display
                TranscriptCard(
                    partialTranscript = partialTranscript,
                    lastAiResponse = lastResponse,
                    voiceState = voiceState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                // Optional Manual Text Input Row
                AnimatedVisibility(
                    visible = showTextInput,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualTextQuery,
                            onValueChange = { manualTextQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_text_input"),
                            placeholder = { Text("Ask Snow anything…", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0A1120),
                                unfocusedContainerColor = Color(0xFF0A1120)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (manualTextQuery.isNotBlank()) {
                                    viewModel.handleUserPrompt(manualTextQuery)
                                    manualTextQuery = ""
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFF00E5FF), CircleShape)
                                .testTag("send_manual_text_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF050B14))
                        }
                    }
                }

                // 4. Quick Commands Bar
                QuickCommandsBar(
                    onCommandSelected = { prompt ->
                        viewModel.handleUserPrompt(prompt)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Bottom Action Controls Bar
                BottomControlsBar(
                    voiceState = voiceState,
                    showTextInput = showTextInput,
                    onToggleTextInput = { showTextInput = !showTextInput },
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
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
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
                        viewModel.handleUserPrompt(prompt, bitmap)
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
    onSettingsClick: () -> Unit
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

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onMemoryClick,
                modifier = Modifier.size(36.dp).testTag("memory_button")
            ) {
                Icon(Icons.Default.Psychology, contentDescription = "Memory & Notes", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onDiagnosticsClick,
                modifier = Modifier.size(36.dp).testTag("diagnostics_button")
            ) {
                Icon(Icons.Default.Build, contentDescription = "Diagnostics", tint = Color(0xFF38BDF8), modifier = Modifier.size(19.dp))
            }

            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.size(36.dp).testTag("history_button")
            ) {
                Icon(Icons.Default.History, contentDescription = "History", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(36.dp).testTag("settings_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
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
        VoiceState.IDLE -> (if (statusBanner != "Ready" && statusBanner.isNotBlank()) statusBanner else "Tap orb or say wake phrase") to Color(0xFF94A3B8)
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

@Composable
private fun TranscriptCard(
    partialTranscript: String,
    lastAiResponse: String,
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
            .testTag("transcript_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09111E).copy(alpha = 0.85f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (partialTranscript.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "You: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7DD3FC),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = partialTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "Snow: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = lastAiResponse,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun BottomControlsBar(
    voiceState: VoiceState,
    showTextInput: Boolean,
    onToggleTextInput: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionButton(
            onClick = onCameraClick,
            containerColor = Color(0xFF131D31),
            contentColor = Color(0xFF00E5FF),
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            modifier = Modifier.testTag("camera_vision_fab")
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Camera Vision")
        }

        FloatingActionButton(
            onClick = onMicClick,
            containerColor = if (voiceState == VoiceState.LISTENING) Color(0xFF00E5FF) else if (voiceState == VoiceState.SPEAKING) Color(0xFFEF4444) else Color(0xFF0284C7),
            contentColor = if (voiceState == VoiceState.LISTENING) Color(0xFF050B14) else Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size(68.dp)
                .testTag("microphone_fab")
        ) {
            Icon(
                imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.GraphicEq else if (voiceState == VoiceState.SPEAKING) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Microphone",
                modifier = Modifier.size(32.dp)
            )
        }

        FloatingActionButton(
            onClick = onToggleTextInput,
            containerColor = if (showTextInput) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF131D31),
            contentColor = Color(0xFF00E5FF),
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            modifier = Modifier.testTag("keyboard_toggle_fab")
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Text Query")
        }
    }
}
