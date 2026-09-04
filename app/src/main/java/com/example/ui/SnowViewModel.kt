package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SnowApplication
import com.example.ai.ActionCommand
import com.example.ai.AiResponse
import com.example.data.model.ChatMessage
import com.example.voice.VoiceEngine
import com.example.voice.VoiceState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnowViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SnowApplication
    val preferences = app.preferences
    private val database = app.database
    private val geminiClient = app.geminiClient
    private val deviceCommander = app.deviceCommander

    val allMessages: StateFlow<List<ChatMessage>> = database.chatDao().getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceEngine = VoiceEngine(
        context = app,
        preferences = preferences,
        onVoiceInputRecognized = { query, isFinal ->
            if (isFinal && query.isNotBlank()) {
                handleUserPrompt(query)
            }
        },
        onWakeWordDetected = {
            viewModelScope.launch {
                _wakeWordDetectedEvent.emit(Unit)
            }
        }
    )

    val voiceState: StateFlow<VoiceState> = voiceEngine.voiceState
    val rmsAmplitude: StateFlow<Float> = voiceEngine.rmsAmplitude
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript

    private val _lastAiResponse = MutableStateFlow("Hi! I'm Snow, your intelligent voice assistant. Tap the orb or say \"Hey Snow\" to begin.")
    val lastAiResponse: StateFlow<String> = _lastAiResponse.asStateFlow()

    private val _showConfigDialog = MutableStateFlow(false)
    val showConfigDialog: StateFlow<Boolean> = _showConfigDialog.asStateFlow()

    private val _showCameraSheet = MutableStateFlow(false)
    val showCameraSheet: StateFlow<Boolean> = _showCameraSheet.asStateFlow()

    private val _showHistorySheet = MutableStateFlow(false)
    val showHistorySheet: StateFlow<Boolean> = _showHistorySheet.asStateFlow()

    private val _statusBannerText = MutableStateFlow("Ready")
    val statusBannerText: StateFlow<String> = _statusBannerText.asStateFlow()

    private val _wakeWordDetectedEvent = MutableSharedFlow<Unit>()
    val wakeWordDetectedEvent: SharedFlow<Unit> = _wakeWordDetectedEvent.asSharedFlow()

    fun openConfigDialog() {
        _showConfigDialog.value = true
    }

    fun closeConfigDialog() {
        _showConfigDialog.value = false
        voiceEngine.configureFemaleVoice()
    }

    fun openCamera() {
        _showCameraSheet.value = true
    }

    fun closeCamera() {
        _showCameraSheet.value = false
    }

    fun openHistory() {
        _showHistorySheet.value = true
    }

    fun closeHistory() {
        _showHistorySheet.value = false
    }

    fun toggleListening() {
        if (voiceState.value == VoiceState.LISTENING) {
            voiceEngine.stopListening()
            _statusBannerText.value = "Paused"
        } else if (voiceState.value == VoiceState.SPEAKING) {
            voiceEngine.stopSpeaking()
            voiceEngine.startListening()
            _statusBannerText.value = "Listening…"
        } else {
            voiceEngine.startListening()
            _statusBannerText.value = "Listening…"
        }
    }

    fun handleUserPrompt(userText: String, capturedImage: Bitmap? = null) {
        if (userText.isBlank() && capturedImage == null) return

        viewModelScope.launch {
            val userMsg = ChatMessage(
                sender = "user",
                content = userText.ifBlank { "Describe this image" },
                language = preferences.languageMode
            )
            database.chatDao().insert(userMsg)

            _statusBannerText.value = "Thinking…"
            voiceEngine.setThinking()

            val response = geminiClient.generateVoiceResponse(
                prompt = userText.ifBlank { "Analyze and describe what you see in this image in detail." },
                apiKey = preferences.effectiveApiKey,
                modelName = preferences.apiEndpointModel,
                languagePreference = preferences.languageMode,
                conversationHistory = allMessages.value,
                imageBitmap = capturedImage
            )

            _lastAiResponse.value = response.spokenText
            _statusBannerText.value = "Speaking…"

            // Execute any structured device actions
            var actionSummary: String? = null
            response.actionCommand?.let { cmd ->
                when (cmd) {
                    is ActionCommand.OpenApp -> {
                        val success = deviceCommander.openAppByName(cmd.appName)
                        actionSummary = if (success) "Opened ${cmd.appName}" else "Could not open ${cmd.appName}"
                    }
                    is ActionCommand.WhatsAppMessage -> {
                        val success = deviceCommander.sendWhatsAppMessage(cmd.recipient, cmd.message)
                        actionSummary = if (success) "Sent WhatsApp to ${cmd.recipient}" else "WhatsApp action failed"
                    }
                    is ActionCommand.ToggleFlashlight -> {
                        deviceCommander.toggleFlashlight(cmd.enable)
                        actionSummary = if (cmd.enable) "Flashlight turned on" else "Flashlight turned off"
                    }
                    is ActionCommand.AdjustVolume -> {
                        deviceCommander.adjustVolume(cmd.isUp)
                        actionSummary = if (cmd.isUp) "Volume raised" else "Volume lowered"
                    }
                    is ActionCommand.OpenWifiSettings -> {
                        deviceCommander.openWifiSettings()
                        actionSummary = "Opened Wi-Fi settings"
                    }
                    is ActionCommand.OpenBluetoothSettings -> {
                        deviceCommander.openBluetoothSettings()
                        actionSummary = "Opened Bluetooth settings"
                    }
                }
            }

            // Save Snow's reply in database
            val snowMsg = ChatMessage(
                sender = "snow",
                content = response.spokenText,
                language = response.detectedLanguage,
                actionSummary = actionSummary
            )
            database.chatDao().insert(snowMsg)

            // Speak response aloud with female voice in appropriate language
            voiceEngine.speak(response.spokenText, response.detectedLanguage)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.chatDao().clearAll()
            _lastAiResponse.value = "Conversation history cleared."
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
