package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SnowPreferences
import com.example.service.SnowAccessibilityService
import com.example.service.SnowVoiceService
import com.example.voice.provider.ElevenLabsTTSProvider
import com.example.voice.provider.GoogleCloudTTSProvider
import com.example.voice.provider.OpenAITTSProvider
import com.example.voice.provider.TTSVoice
import kotlinx.coroutines.launch

@Composable
fun ConfigDialog(
    preferences: SnowPreferences,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTestVoice: (pitch: Float, rate: Float, lang: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    // AI Providers
    var activeAiProvider by remember { mutableStateOf(preferences.activeAiProvider) }
    var fallbackAiProvider by remember { mutableStateOf(preferences.fallbackAiProvider) }

    var geminiApiKeyText by remember { mutableStateOf(preferences.customApiKey) }
    var showGeminiApiKey by remember { mutableStateOf(false) }
    var selectedGeminiModel by remember { mutableStateOf(preferences.apiEndpointModel) }

    var openAiApiKeyText by remember { mutableStateOf(preferences.openAiApiKey) }
    var showOpenAiApiKey by remember { mutableStateOf(false) }
    var selectedOpenAiModel by remember { mutableStateOf(preferences.openAiModel) }

    var anthropicApiKeyText by remember { mutableStateOf(preferences.anthropicApiKey) }
    var showAnthropicApiKey by remember { mutableStateOf(false) }
    var selectedAnthropicModel by remember { mutableStateOf(preferences.anthropicModel) }

    var customRestEndpoint by remember { mutableStateOf(preferences.customRestEndpoint) }
    var customRestApiKey by remember { mutableStateOf(preferences.customRestApiKey) }
    var customRestModel by remember { mutableStateOf(preferences.customRestModel) }

    // Ollama Local / Remote Provider
    var ollamaBaseUrl by remember { mutableStateOf(preferences.ollamaBaseUrl) }
    var ollamaModel by remember { mutableStateOf(preferences.ollamaModel) }
    var ollamaApiKey by remember { mutableStateOf(preferences.ollamaApiKey) }
    var ollamaDiscoveredModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingOllamaModels by remember { mutableStateOf(false) }
    var speakTypedResponses by remember { mutableStateOf(preferences.speakTypedResponses) }

    // Image Generation (Req 29)
    var imageGenProvider by remember { mutableStateOf(preferences.imageGenProvider) }
    var imageGenModel by remember { mutableStateOf(preferences.imageGenModel) }
    var imageGenEndpoint by remember { mutableStateOf(preferences.imageGenEndpoint) }
    var imageGenApiKey by remember { mutableStateOf(preferences.imageGenApiKey) }
    var showImageGenApiKey by remember { mutableStateOf(false) }

    // Agent & Tools
    var assistantName by remember { mutableStateOf(preferences.assistantName) }
    var wakePhrase by remember { mutableStateOf(preferences.wakePhrase) }
    var searchProvider by remember { mutableStateOf(preferences.searchProvider) }
    var requireConfirmation by remember { mutableStateOf(preferences.requireConfirmationForSensitive) }
    var proactiveAssist by remember { mutableStateOf(preferences.proactiveAssistance) }
    var screenControlEnabled by remember { mutableStateOf(preferences.screenControlEnabled) }
    var personality by remember { mutableStateOf(preferences.personality) }

    // Voice & TTS
    var selectedTtsProvider by remember { mutableStateOf(preferences.ttsProvider) }
    var elevenLabsKey by remember { mutableStateOf(preferences.elevenLabsApiKey) }
    var showElevenLabsKey by remember { mutableStateOf(false) }
    var googleCloudKey by remember { mutableStateOf(preferences.googleCloudApiKey) }
    var showGoogleCloudKey by remember { mutableStateOf(false) }
    var selectedVoiceId by remember { mutableStateOf(preferences.selectedVoiceId) }
    var availableVoices by remember { mutableStateOf<List<TTSVoice>>(emptyList()) }
    var speedRateValue by remember { mutableFloatStateOf(preferences.speechRate) }
    var pitchValue by remember { mutableFloatStateOf(preferences.femaleVoicePitch) }

    // Language
    var selectedLang by remember { mutableStateOf(preferences.languageMode) }

    // Toggles
    var autoSpeakEnabled by remember { mutableStateOf(preferences.autoSpeakEnabled) }
    var interruptWhileSpeaking by remember { mutableStateOf(preferences.interruptWhileSpeaking) }
    var fallbackVoiceEnabled by remember { mutableStateOf(preferences.fallbackVoiceEnabled) }
    var wakeWordEnabled by remember { mutableStateOf(preferences.wakeWordEnabled) }
    var continuousListen by remember { mutableStateOf(preferences.continuousListening) }
    var bgServiceEnabled by remember { mutableStateOf(preferences.backgroundServiceEnabled) }

    // Connection testing
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(selectedTtsProvider, openAiApiKeyText, elevenLabsKey, googleCloudKey) {
        val provider = when (selectedTtsProvider) {
            SnowPreferences.TTS_PROVIDER_OPENAI -> OpenAITTSProvider { openAiApiKeyText }
            SnowPreferences.TTS_PROVIDER_ELEVENLABS -> ElevenLabsTTSProvider { elevenLabsKey }
            SnowPreferences.TTS_PROVIDER_GOOGLE_CLOUD -> GoogleCloudTTSProvider { googleCloudKey }
            else -> null
        }
        if (provider != null) {
            val voices = provider.getAvailableVoices(selectedLang)
            availableVoices = voices
            if (voices.none { it.id == selectedVoiceId }) {
                selectedVoiceId = voices.firstOrNull { it.isDefault }?.id ?: voices.firstOrNull()?.id ?: ""
            }
        } else {
            availableVoices = listOf(
                TTSVoice("system_female", "System Female Voice", "Female", "Built-in Android female synthesized voice", isDefault = true)
            )
            selectedVoiceId = "system_female"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .testTag("config_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070D1A))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Snow AI Agent Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Models, Tools, Voice & Security",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_config_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color(0xFF00F0FF),
                    edgePadding = 4.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF00F0FF)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("AI Providers", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Agent & Tools", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Voice & TTS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Languages", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Security", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        text = { Text("Image Gen", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // TAB 0: AI Providers
                        Text(
                            text = "Active AI Brain",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Select primary engine for understanding and multi-step tool execution:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val aiProvidersList = listOf(
                            SnowPreferences.PROVIDER_GEMINI to "Google Gemini (Recommended)",
                            SnowPreferences.PROVIDER_OLLAMA to "Ollama (Local / Remote AI Server)",
                            SnowPreferences.PROVIDER_OPENAI to "OpenAI (GPT-4o)",
                            SnowPreferences.PROVIDER_ANTHROPIC to "Anthropic (Claude 3.5)",
                            SnowPreferences.PROVIDER_CUSTOM_REST to "Custom REST / Local (Groq, vLLM)"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            aiProvidersList.forEach { (id, label) ->
                                val isSelected = activeAiProvider == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) Color(0xFF00F0FF) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .clickable { activeAiProvider = id }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, color = if (isSelected) Color(0xFF00F0FF) else Color.White, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Configuration based on selected provider
                        when (activeAiProvider) {
                            SnowPreferences.PROVIDER_GEMINI -> {
                                Text("Gemini API Key", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                OutlinedTextField(
                                    value = geminiApiKeyText,
                                    onValueChange = { geminiApiKeyText = it },
                                    modifier = Modifier.fillMaxWidth().testTag("gemini_key_field"),
                                    placeholder = { Text("Stored in Android Keystore (Blank = default key)", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF00E5FF)) },
                                    trailingIcon = {
                                        IconButton(onClick = { showGeminiApiKey = !showGeminiApiKey }) {
                                            Icon(if (showGeminiApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF94A3B8))
                                        }
                                    },
                                    visualTransformation = if (showGeminiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Gemini Model", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-3.1-flash-lite-preview", "gemini-3.1-pro-preview").forEach { m ->
                                        FilterChip(
                                            selected = selectedGeminiModel == m,
                                            onClick = { selectedGeminiModel = m },
                                            label = { Text(m, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            SnowPreferences.PROVIDER_OPENAI -> {
                                Text("OpenAI API Key", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                OutlinedTextField(
                                    value = openAiApiKeyText,
                                    onValueChange = { openAiApiKeyText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("sk-...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF00E5FF)) },
                                    visualTransformation = if (showOpenAiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showOpenAiApiKey = !showOpenAiApiKey }) {
                                            Icon(if (showOpenAiApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF94A3B8))
                                        }
                                    },
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("OpenAI Model", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("gpt-4o-mini", "gpt-4o").forEach { m ->
                                        FilterChip(selected = selectedOpenAiModel == m, onClick = { selectedOpenAiModel = m }, label = { Text(m, fontSize = 11.sp) })
                                    }
                                }
                            }

                            SnowPreferences.PROVIDER_OLLAMA -> {
                                Text("Ollama Base URL", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                Text("Reach your local Ollama server (e.g. on your LAN or host machine)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                OutlinedTextField(
                                    value = ollamaBaseUrl,
                                    onValueChange = { ollamaBaseUrl = it },
                                    modifier = Modifier.fillMaxWidth().testTag("ollama_url_field"),
                                    placeholder = { Text("http://10.0.2.2:11434 or http://192.168.1.50:11434", fontSize = 12.sp) },
                                    singleLine = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { ollamaBaseUrl = "http://10.0.2.2:11434" },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Host Emulator (10.0.2.2)", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { ollamaBaseUrl = "http://127.0.0.1:11434" },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Localhost (127.0.0.1)", fontSize = 10.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Model Name", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                OutlinedTextField(
                                    value = ollamaModel,
                                    onValueChange = { ollamaModel = it },
                                    modifier = Modifier.fillMaxWidth().testTag("ollama_model_field"),
                                    placeholder = { Text("llama3.2:3b, qwen2.5:7b, mistral, llava", fontSize = 12.sp) },
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Popular Models:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("llama3.2:3b", "qwen2.5:7b", "mistral:latest", "llava:7b", "gemma2:9b").forEach { m ->
                                        FilterChip(
                                            selected = ollamaModel == m,
                                            onClick = { ollamaModel = m },
                                            label = { Text(m, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                if (ollamaDiscoveredModels.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Installed on Server:", fontSize = 11.sp, color = Color(0xFF00E5FF))
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ollamaDiscoveredModels.forEach { m ->
                                            FilterChip(
                                                selected = ollamaModel == m,
                                                onClick = { ollamaModel = m },
                                                label = { Text(m, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isFetchingOllamaModels = true
                                            val fetched = com.example.ai.provider.OllamaAIProvider.fetchInstalledModels(ollamaBaseUrl, ollamaApiKey)
                                            ollamaDiscoveredModels = fetched
                                            isFetchingOllamaModels = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isFetchingOllamaModels
                                ) {
                                    if (isFetchingOllamaModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Querying Ollama...", fontSize = 11.sp)
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fetch Installed Models from Server", fontSize = 12.sp)
                                    }
                                }
                            }

                            SnowPreferences.PROVIDER_ANTHROPIC -> {
                                Text("Anthropic API Key", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                OutlinedTextField(
                                    value = anthropicApiKeyText,
                                    onValueChange = { anthropicApiKeyText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("sk-ant-...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    visualTransformation = if (showAnthropicApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true
                                )
                            }

                            SnowPreferences.PROVIDER_CUSTOM_REST -> {
                                Text("Custom Endpoint", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                OutlinedTextField(
                                    value = customRestEndpoint,
                                    onValueChange = { customRestEndpoint = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("https://api.groq.com/openai/v1 or http://10.0.2.2:11434/v1", fontSize = 11.sp) },
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Model Name", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                                OutlinedTextField(
                                    value = customRestModel,
                                    onValueChange = { customRestModel = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("e.g. llama-3.3-70b-versatile", fontSize = 11.sp) },
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fallback Provider Selector
                        Text("Automatic Quota Fallback Provider", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("If the primary provider hits 429 quota exhaustion, Snow seamlessly cascades to this provider:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SnowPreferences.PROVIDER_NONE to "None",
                                SnowPreferences.PROVIDER_GEMINI to "Gemini",
                                SnowPreferences.PROVIDER_OPENAI to "OpenAI",
                                SnowPreferences.PROVIDER_ANTHROPIC to "Anthropic"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = fallbackAiProvider == id,
                                    onClick = { fallbackAiProvider = id },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: Agent & Tools
                        Text("Agent Identity & Voice Persona", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Assistant Name", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                        OutlinedTextField(
                            value = assistantName,
                            onValueChange = { assistantName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Wake Phrase", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                        OutlinedTextField(
                            value = wakePhrase,
                            onValueChange = { wakePhrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Hey Snow, Snow, سنو", fontSize = 12.sp) },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Assistant Personality", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00E5FF))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("FRIENDLY" to "Empathetic & Warm", "PROFESSIONAL" to "Concise & Polite", "HUMOROUS" to "Playful & Charming").forEach { (p, lbl) ->
                                FilterChip(
                                    selected = personality == p,
                                    onClick = { personality = p },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Web Search Tool Provider", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SnowPreferences.SEARCH_PROVIDER_DUCKDUCKGO to "DuckDuckGo (Free / Instant)",
                                SnowPreferences.SEARCH_PROVIDER_NONE to "Disabled"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = searchProvider == id,
                                    onClick = { searchProvider = id },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Require Confirmation", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text("Confirm before destructive or sensitive actions (delete memory, dial phone)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Switch(
                                checked = requireConfirmation,
                                onCheckedChange = { requireConfirmation = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00E5FF), uncheckedTrackColor = Color(0xFF1E293B))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Screen Control (Accessibility)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text("Read active screen and automate taps via Accessibility", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Switch(
                                checked = screenControlEnabled,
                                onCheckedChange = { screenControlEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00E5FF), uncheckedTrackColor = Color(0xFF1E293B))
                            )
                        }
                    }

                    2 -> {
                        // TAB 2: Voice & TTS
                        Text("TTS Voice Engine", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        val ttsProviders = listOf(
                            SnowPreferences.TTS_PROVIDER_SYSTEM to "System TTS (Offline)",
                            SnowPreferences.TTS_PROVIDER_OPENAI to "OpenAI Neural",
                            SnowPreferences.TTS_PROVIDER_ELEVENLABS to "ElevenLabs Neural",
                            SnowPreferences.TTS_PROVIDER_GOOGLE_CLOUD to "Google Cloud TTS"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ttsProviders.forEach { (provId, provName) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (selectedTtsProvider == provId) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (selectedTtsProvider == provId) Color(0xFF00F0FF) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .clickable { selectedTtsProvider = provId }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(provName, color = if (selectedTtsProvider == provId) Color(0xFF00F0FF) else Color.White, fontSize = 13.sp)
                                    if (selectedTtsProvider == provId) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Speak Typed Responses Selector
                        Text("Speak Typed Responses", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Choose when Snow speaks out loud if you type a message:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SnowPreferences.SPEAK_TYPED_ALWAYS to "Always Speak",
                                SnowPreferences.SPEAK_TYPED_VOICE_ONLY to "Voice Input Only",
                                SnowPreferences.SPEAK_TYPED_NEVER to "Never Speak (Text Only)"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = speakTypedResponses == mode,
                                    onClick = { speakTypedResponses = mode },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Pitch and Rate Sliders
                        Text("Female Voice Pitch: ${String.format("%.2f", pitchValue)}x", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = pitchValue,
                            onValueChange = { pitchValue = it },
                            valueRange = 0.8f..1.6f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                        )

                        Text("Speech Speed: ${String.format("%.2f", speedRateValue)}x", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = speedRateValue,
                            onValueChange = { speedRateValue = it },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onTestVoice(pitchValue, speedRateValue, selectedLang) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF050B14)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sample Female Voice", fontWeight = FontWeight.Bold)
                        }
                    }

                    3 -> {
                        // TAB 3: Languages
                        Text("Supported Spoken Languages", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Snow natively speaks and recognizes English, Hindi, Urdu, Roman Urdu, and Pashto:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 8.dp))

                        val langs = listOf(
                            SnowPreferences.LANG_AUTO to ("Auto Detect" to "Automatically matches spoken language & accent"),
                            SnowPreferences.LANG_EN to ("English" to "Warm, conversational English"),
                            SnowPreferences.LANG_HI to ("Hindi (हिन्दी)" to "Conversational Hindi speech"),
                            SnowPreferences.LANG_UR to ("Urdu (اردو)" to "Natural Urdu speech and Urdu script"),
                            SnowPreferences.LANG_ROMAN_UR to ("Roman Urdu" to "Urdu written in Latin alphabet"),
                            SnowPreferences.LANG_PS to ("Pashto (پښتو)" to "Conversational Pashto voice and script")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            langs.forEach { (code, pair) ->
                                val (title, subtitle) = pair
                                val isSelected = selectedLang == code
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) Color(0xFF00F0FF) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .clickable { selectedLang = code }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(title, color = if (isSelected) Color(0xFF00F0FF) else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                        Text(subtitle, color = Color(0xFF64748B), fontSize = 11.sp)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // TAB 4: Security
                        Text("Hardware Keystore Security", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("All API keys and credentials are encrypted using AES/GCM hardware-backed Android KeyStore. Keys are never logged or exported.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Storage Isolation", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Database: Local Room SQLite\nEncrypted: AES/GCM/NoPadding\nNetwork: HTTPS TLS 1.3 only", color = Color(0xFFCBD5E1), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { preferences.clearAllData() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset All Stored Data & Keys")
                        }
                    }

                    5 -> {
                        // TAB 5: Image Generation (Req 29)
                        Text(
                            text = "Image Generation Provider",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Configure image-generation services (Pollinations, OpenAI DALL-E, Ollama, or Custom). API keys are optional and never hardcoded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val imgProviders = listOf(
                            SnowPreferences.IMG_PROVIDER_POLLINATIONS to "Pollinations (Free / No Key)",
                            SnowPreferences.IMG_PROVIDER_OPENAI to "OpenAI (DALL-E 3)",
                            SnowPreferences.IMG_PROVIDER_OLLAMA to "Ollama (Local Models)",
                            SnowPreferences.IMG_PROVIDER_CUSTOM to "Custom REST / HTTP"
                        )

                        imgProviders.forEach { (provId, label) ->
                            val isSelected = imageGenProvider == provId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        imageGenProvider = provId
                                        when (provId) {
                                            SnowPreferences.IMG_PROVIDER_POLLINATIONS -> {
                                                imageGenModel = "flux"
                                                imageGenEndpoint = "https://image.pollinations.ai/prompt"
                                            }
                                            SnowPreferences.IMG_PROVIDER_OPENAI -> {
                                                imageGenModel = "dall-e-3"
                                                imageGenEndpoint = "https://api.openai.com/v1/images/generations"
                                            }
                                            SnowPreferences.IMG_PROVIDER_OLLAMA -> {
                                                imageGenModel = "stable-diffusion"
                                                imageGenEndpoint = "http://10.0.2.2:11434"
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF00384D) else Color(0xFF0F172A)
                                ),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, color = if (isSelected) Color(0xFF00F0FF) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = imageGenModel,
                            onValueChange = { imageGenModel = it },
                            label = { Text("Image Generation Model", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("e.g. flux, dall-e-3, stable-diffusion", color = Color(0xFF475569)) },
                            modifier = Modifier.fillMaxWidth().testTag("image_gen_model_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = imageGenEndpoint,
                            onValueChange = { imageGenEndpoint = it },
                            label = { Text("Image Generation Endpoint / URL", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("e.g. https://api.openai.com/v1/images/generations", color = Color(0xFF475569)) },
                            modifier = Modifier.fillMaxWidth().testTag("image_gen_endpoint_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = imageGenApiKey,
                            onValueChange = { imageGenApiKey = it },
                            label = { Text("Optional API Key / Token", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("Leave blank if no key is needed", color = Color(0xFF475569)) },
                            visualTransformation = if (showImageGenApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showImageGenApiKey = !showImageGenApiKey }) {
                                    Icon(
                                        imageVector = if (showImageGenApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Key Visibility",
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("image_gen_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(14.dp))

                // Save / Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_config_button")) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            preferences.activeAiProvider = activeAiProvider
                            preferences.fallbackAiProvider = fallbackAiProvider

                            preferences.customApiKey = geminiApiKeyText
                            preferences.apiEndpointModel = selectedGeminiModel

                            preferences.ollamaBaseUrl = ollamaBaseUrl
                            preferences.ollamaModel = ollamaModel
                            preferences.ollamaApiKey = ollamaApiKey
                            preferences.speakTypedResponses = speakTypedResponses

                            preferences.imageGenProvider = imageGenProvider
                            preferences.imageGenModel = imageGenModel
                            preferences.imageGenEndpoint = imageGenEndpoint
                            preferences.imageGenApiKey = imageGenApiKey

                            preferences.openAiApiKey = openAiApiKeyText
                            preferences.openAiModel = selectedOpenAiModel

                            preferences.anthropicApiKey = anthropicApiKeyText
                            preferences.customRestEndpoint = customRestEndpoint
                            preferences.customRestApiKey = customRestApiKey
                            preferences.customRestModel = customRestModel

                            preferences.assistantName = assistantName
                            preferences.wakePhrase = wakePhrase
                            preferences.searchProvider = searchProvider
                            preferences.requireConfirmationForSensitive = requireConfirmation
                            preferences.proactiveAssistance = proactiveAssist
                            preferences.screenControlEnabled = screenControlEnabled
                            preferences.personality = personality

                            preferences.ttsProvider = selectedTtsProvider
                            preferences.elevenLabsApiKey = elevenLabsKey
                            preferences.googleCloudApiKey = googleCloudKey
                            preferences.selectedVoiceId = selectedVoiceId
                            preferences.speechRate = speedRateValue
                            preferences.femaleVoicePitch = pitchValue
                            preferences.languageMode = selectedLang

                            preferences.autoSpeakEnabled = autoSpeakEnabled
                            preferences.interruptWhileSpeaking = interruptWhileSpeaking
                            preferences.fallbackVoiceEnabled = fallbackVoiceEnabled
                            preferences.wakeWordEnabled = wakeWordEnabled
                            preferences.continuousListening = continuousListen
                            preferences.backgroundServiceEnabled = bgServiceEnabled

                            if (bgServiceEnabled) {
                                SnowVoiceService.start(context)
                            } else {
                                SnowVoiceService.stop(context)
                            }

                            onSave()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF050B14)),
                        modifier = Modifier.testTag("save_config_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
