package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SnowApplication
import com.example.agent.AgentExecutionState
import com.example.agent.FinalAgentResponse
import com.example.ai.provider.ConnectionTestResult
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
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
    val permissionManager = app.permissionManager
    val memoryManager = app.memoryManager
    val notesManager = app.notesManager
    val aiProviderManager = app.aiProviderManager
    val agentManager = app.agentManager

    val allMessages: StateFlow<List<ChatMessage>> = database.chatDao().getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<NoteEntity>> = notesManager.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMemories: StateFlow<List<MemoryEntity>> = memoryManager.getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentState: StateFlow<AgentExecutionState> = agentManager.agentState

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

    private val _lastAiResponse = MutableStateFlow("Hi! I'm ${preferences.assistantName}, your personal AI voice agent. Tap the orb or say \"${preferences.wakePhrase}\" to begin.")
    val lastAiResponse: StateFlow<String> = _lastAiResponse.asStateFlow()

    private val _showConfigDialog = MutableStateFlow(false)
    val showConfigDialog: StateFlow<Boolean> = _showConfigDialog.asStateFlow()

    private val _showCameraSheet = MutableStateFlow(false)
    val showCameraSheet: StateFlow<Boolean> = _showCameraSheet.asStateFlow()

    private val _showHistorySheet = MutableStateFlow(false)
    val showHistorySheet: StateFlow<Boolean> = _showHistorySheet.asStateFlow()

    private val _showMemorySheet = MutableStateFlow(false)
    val showMemorySheet: StateFlow<Boolean> = _showMemorySheet.asStateFlow()

    private val _showDiagnosticsSheet = MutableStateFlow(false)
    val showDiagnosticsSheet: StateFlow<Boolean> = _showDiagnosticsSheet.asStateFlow()

    private val _statusBannerText = MutableStateFlow("Ready")
    val statusBannerText: StateFlow<String> = _statusBannerText.asStateFlow()

    private val _pendingConfirmationResponse = MutableStateFlow<FinalAgentResponse?>(null)
    val pendingConfirmationResponse: StateFlow<FinalAgentResponse?> = _pendingConfirmationResponse.asStateFlow()

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

    fun openMemorySheet() {
        _showMemorySheet.value = true
    }

    fun closeMemorySheet() {
        _showMemorySheet.value = false
    }

    fun openDiagnostics() {
        _showDiagnosticsSheet.value = true
    }

    fun closeDiagnostics() {
        _showDiagnosticsSheet.value = false
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

            _statusBannerText.value = "Planning…"
            voiceEngine.setThinking()

            val finalResponse = agentManager.processUserTurn(
                userPrompt = userText.ifBlank { "Analyze and describe what you see in this image in detail." },
                conversationHistory = allMessages.value,
                imageBitmap = capturedImage,
                onStatusCallback = { status ->
                    _statusBannerText.value = status
                }
            )

            if (finalResponse.requiresUserConfirmation) {
                _pendingConfirmationResponse.value = finalResponse
                _statusBannerText.value = "Confirmation required"
                _lastAiResponse.value = finalResponse.spokenText
                voiceEngine.speak(finalResponse.spokenText, finalResponse.detectedLanguage)
                return@launch
            }

            _lastAiResponse.value = finalResponse.spokenText
            _statusBannerText.value = "Speaking…"

            // Save agent message to database
            val agentMsg = ChatMessage(
                sender = "snow",
                content = finalResponse.spokenText,
                language = finalResponse.detectedLanguage,
                actionSummary = finalResponse.executedToolsSummary
            )
            database.chatDao().insert(agentMsg)

            // Speak natural response aloud
            voiceEngine.speak(finalResponse.spokenText, finalResponse.detectedLanguage)
        }
    }

    fun confirmPendingAction(confirmed: Boolean) {
        val pending = _pendingConfirmationResponse.value
        _pendingConfirmationResponse.value = null
        if (confirmed && pending != null) {
            handleUserPrompt("Proceed and execute the confirmed action: ${pending.pendingActionDescription}")
        } else {
            val cancelText = "Action was cancelled."
            _lastAiResponse.value = cancelText
            voiceEngine.speak(cancelText, "English")
            _statusBannerText.value = "Ready"
        }
    }

    fun saveNote(title: String, content: String) {
        viewModelScope.launch {
            notesManager.saveNote(title, content)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            notesManager.deleteNote(id)
        }
    }

    fun saveMemory(text: String) {
        viewModelScope.launch {
            memoryManager.saveMemory(text)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryManager.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryManager.clearAll()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.chatDao().clearAll()
            _lastAiResponse.value = "Conversation history cleared."
        }
    }

    suspend fun testProvider(providerId: String): ConnectionTestResult {
        return aiProviderManager.testConnection(providerId)
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
