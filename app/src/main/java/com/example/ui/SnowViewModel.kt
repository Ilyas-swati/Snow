package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SnowApplication
import com.example.agent.AgentExecutionState
import com.example.agent.FinalAgentResponse
import com.example.ai.provider.ConnectionTestResult
import com.example.data.SnowPreferences
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
import com.example.voice.VoiceEngine
import com.example.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConversationState {
    IDLE,
    LISTENING,
    THINKING,
    ACTING,
    SPEAKING,
    INTERRUPTED,
    ERROR
}

class SnowViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SnowApplication
    val preferences = app.preferences
    private val database = app.database
    val permissionManager = app.permissionManager
    val memoryManager = app.memoryManager
    val notesManager = app.notesManager
    val aiProviderManager = app.aiProviderManager
    val agentManager = app.agentManager
    val imageGenerationManager = app.imageGenerationManager

    private var activeConversationJob: Job? = null

    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

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

    init {
        // Wire image generation callback to display newly generated images inside chat
        app.toolExecutor.onImageGeneratedListener = { filePath, prompt ->
            viewModelScope.launch {
                val imgMsg = ChatMessage(
                    sender = "snow",
                    content = "Ye rahi aapki generated image: \"$prompt\"",
                    language = preferences.languageMode,
                    imageUri = filePath,
                    actionSummary = "Generated Image"
                )
                database.chatDao().insert(imgMsg)
            }
        }

        // Real-time interruption from VoiceEngine
        voiceEngine.onInterruptionRequested = { reason ->
            interruptCurrentTask(reason)
        }
    }

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
        if (voiceState.value == VoiceState.SPEAKING || _conversationState.value == ConversationState.SPEAKING) {
            interruptCurrentTask("User tapped while speaking")
            return
        }

        if (voiceState.value == VoiceState.LISTENING) {
            voiceEngine.stopListening()
            _conversationState.value = ConversationState.IDLE
            _statusBannerText.value = "Paused"
        } else {
            voiceEngine.startListening()
            _conversationState.value = ConversationState.LISTENING
            _statusBannerText.value = "Listening…"
        }
    }

    private val _isSetupDismissed = MutableStateFlow(false)
    val isSetupDismissed: StateFlow<Boolean> = _isSetupDismissed.asStateFlow()

    fun dismissSetupCard() {
        _isSetupDismissed.value = true
    }

    fun reopenSetupCard() {
        _isSetupDismissed.value = false
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            database.chatDao().clearAll()
        }
    }

    fun interruptCurrentTask(reason: String = "Stop / Interrupt requested") {
        activeConversationJob?.cancel()
        activeConversationJob = null
        com.example.agent.TaskManager.cancelCurrentTask(reason)
        agentManager.cancelCurrentTask()
        voiceEngine.stopSpeaking()
        _isProcessingPrompt.value = false
        _conversationState.value = ConversationState.INTERRUPTED
        _statusBannerText.value = "Interrupted"

        viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            _conversationState.value = ConversationState.LISTENING
            _statusBannerText.value = "Listening…"
            voiceEngine.startListening()
        }
    }

    private val _isProcessingPrompt = MutableStateFlow(false)
    val isProcessingPrompt: StateFlow<Boolean> = _isProcessingPrompt.asStateFlow()

    private var lastProcessedPrompt: String = ""
    private var lastProcessedPromptTime: Long = 0L

    fun handleUserPrompt(userText: String, capturedImage: Bitmap? = null, isTyped: Boolean = false) {
        val cleanText = userText.trim()
        if (cleanText.isBlank() && capturedImage == null) return

        val now = System.currentTimeMillis()
        if (cleanText.isNotBlank() && cleanText == lastProcessedPrompt && (now - lastProcessedPromptTime) < 800L && capturedImage == null) {
            android.util.Log.d("SnowViewModel", "Ignoring duplicate prompt within 800ms: $cleanText")
            return
        }
        lastProcessedPrompt = cleanText
        lastProcessedPromptTime = now

        // 1. High-Priority Global Cancel Intent (Req 35)
        if (VoiceEngine.isInterruptionWord(cleanText)) {
            activeConversationJob?.cancel()
            activeConversationJob = null
            com.example.agent.TaskManager.cancelCurrentTask("User requested stop: $cleanText")
            agentManager.cancelCurrentTask()
            voiceEngine.stopSpeaking()
            _isProcessingPrompt.value = false
            _conversationState.value = ConversationState.INTERRUPTED

            val ackText = when (preferences.languageMode) {
                SnowPreferences.LANG_UR -> "ٹھیک ہے جانو، رک گئی۔ ❤️"
                SnowPreferences.LANG_HI -> "ठीक है जानू, रुक गई। ❤️"
                SnowPreferences.LANG_ROMAN_UR -> "Okay jaanu, ruk gayi. ❤️"
                else -> "Okay jaanu, ruk gayi. ❤️"
            }

            _lastAiResponse.value = ackText
            _statusBannerText.value = "Stopped"

            val stopRequestId = java.util.UUID.randomUUID().toString()
            viewModelScope.launch {
                val userMsg = ChatMessage(
                    sender = "user",
                    content = cleanText,
                    language = preferences.languageMode,
                    requestId = stopRequestId,
                    messageId = "usr_$stopRequestId"
                )
                val replyMsg = ChatMessage(
                    sender = "snow",
                    content = ackText,
                    language = preferences.languageMode,
                    requestId = stopRequestId,
                    messageId = "asst_$stopRequestId"
                )
                database.chatDao().insert(userMsg)
                database.chatDao().insert(replyMsg)

                if (!isTyped && preferences.autoSpeakEnabled) {
                    _conversationState.value = ConversationState.SPEAKING
                    voiceEngine.speak(ackText, preferences.languageMode)
                }
                kotlinx.coroutines.delay(600)
                _conversationState.value = ConversationState.LISTENING
                _statusBannerText.value = "Listening…"
                voiceEngine.startListening()
            }
            return
        }

        // 2. Cancel any previously running task (Req 33: Single foreground task)
        activeConversationJob?.cancel()
        agentManager.cancelCurrentTask()
        voiceEngine.stopSpeaking()

        val turnRequestId = java.util.UUID.randomUUID().toString()
        val userMessageId = "usr_$turnRequestId"
        val agentMessageId = "asst_$turnRequestId"

        activeConversationJob = viewModelScope.launch {
            _isProcessingPrompt.value = true
            _conversationState.value = ConversationState.THINKING
            try {
                val userMsg = ChatMessage(
                    sender = "user",
                    content = cleanText.ifBlank { "Describe this image" },
                    language = preferences.languageMode,
                    requestId = turnRequestId,
                    messageId = userMessageId
                )
                database.chatDao().insert(userMsg)

                _statusBannerText.value = "Planning…"
                voiceEngine.setThinking()

                val finalResponse = agentManager.processUserTurn(
                    userPrompt = cleanText.ifBlank { "Analyze and describe what you see in this image in detail." },
                    conversationHistory = allMessages.value,
                    imageBitmap = capturedImage,
                    onStatusCallback = { status ->
                        _statusBannerText.value = status
                        if (status.contains("Action:") || status.contains("Executing")) {
                            _conversationState.value = ConversationState.ACTING
                        }
                    }
                )

                if (finalResponse.requiresUserConfirmation) {
                    _pendingConfirmationResponse.value = finalResponse
                    _statusBannerText.value = "Confirmation required"
                    _lastAiResponse.value = finalResponse.spokenText
                    _conversationState.value = ConversationState.SPEAKING
                    voiceEngine.speak(finalResponse.spokenText, finalResponse.detectedLanguage)
                    return@launch
                }

                _lastAiResponse.value = finalResponse.spokenText

                // Save or update canonical agent message to database (prevent duplicate insertion)
                val existingAgentMsg = database.chatDao().getMessageByMessageId(agentMessageId)
                if (existingAgentMsg != null) {
                    val updated = existingAgentMsg.copy(
                        content = finalResponse.spokenText,
                        language = finalResponse.detectedLanguage,
                        actionSummary = finalResponse.executedToolsSummary
                    )
                    database.chatDao().update(updated)
                } else {
                    val agentMsg = ChatMessage(
                        sender = "snow",
                        content = finalResponse.spokenText,
                        language = finalResponse.detectedLanguage,
                        actionSummary = finalResponse.executedToolsSummary,
                        requestId = turnRequestId,
                        messageId = agentMessageId
                    )
                    database.chatDao().insert(agentMsg)
                }

                // Determine if TTS should speak response (Strict separation of TEXT OUTPUT from VOICE OUTPUT)
                val shouldSpeak = if (isTyped) {
                    preferences.speakTypedResponses == SnowPreferences.SPEAK_TYPED_ALWAYS && preferences.autoSpeakEnabled
                } else {
                    preferences.autoSpeakEnabled
                }

                if (shouldSpeak) {
                    val spokenPortion = com.example.voice.SpeechTextFilter.filterForSpeech(finalResponse.spokenText, finalResponse.detectedLanguage)
                    if (spokenPortion.isNotBlank()) {
                        _conversationState.value = ConversationState.SPEAKING
                        _statusBannerText.value = "Speaking…"
                        voiceEngine.speak(spokenPortion, finalResponse.detectedLanguage)
                    } else {
                        _conversationState.value = ConversationState.IDLE
                        _statusBannerText.value = "Ready"
                        voiceEngine.setIdle()
                    }
                } else {
                    _conversationState.value = ConversationState.IDLE
                    _statusBannerText.value = "Ready"
                    voiceEngine.setIdle()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _statusBannerText.value = "Cancelled"
                _conversationState.value = ConversationState.INTERRUPTED
            } catch (e: Exception) {
                _statusBannerText.value = "Error: ${e.localizedMessage ?: "Unexpected error"}"
                _conversationState.value = ConversationState.ERROR
                voiceEngine.setIdle()
            } finally {
                _isProcessingPrompt.value = false
                if (_conversationState.value == ConversationState.THINKING || _conversationState.value == ConversationState.ACTING) {
                    _conversationState.value = ConversationState.IDLE
                }
            }
        }
    }

    fun saveImageToGallery(filePath: String): Boolean {
        val saved = imageGenerationManager.saveImageToGallery(filePath)
        if (saved) {
            _statusBannerText.value = "Image saved to gallery"
        } else {
            _statusBannerText.value = "Failed to save image"
        }
        return saved
    }

    fun shareImage(filePath: String, recipientApp: String? = null) {
        imageGenerationManager.shareImage(filePath, "Shared from Snow AI", recipientApp)
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            database.chatDao().deleteMessage(id)
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
        activeConversationJob?.cancel()
        voiceEngine.release()
    }
}
