package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.SnowPreferences
import com.example.voice.provider.TTSProviderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class VoiceEngine(
    private val context: Context,
    private val preferences: SnowPreferences,
    private val onVoiceInputRecognized: (query: String, isFinal: Boolean) -> Unit,
    private val onWakeWordDetected: () -> Unit
) : TextToSpeech.OnInitListener, RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsAmplitude = MutableStateFlow(0f)
    val rmsAmplitude: StateFlow<Float> = _rmsAmplitude.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var isContinuousListeningRequested = true
    private var isSpeaking = false

    var onInterruptionRequested: ((reason: String) -> Unit)? = null

    fun isCurrentlySpeaking(): Boolean = isSpeaking || _voiceState.value == VoiceState.SPEAKING

    companion object {
        val INTERRUPT_COMMANDS = listOf(
            "ruko", "ruk jao", "stop", "bas", "chup", "wait", "sun meri baat",
            "ek minute", "cancel", "cancel it", "khamosh", "thehro", "rukna",
            "shh", "quiet", "shut up", "hold on", "hold on a second",
            // Urdu script
            "روکو", "رک جاؤ", "بس", "چپ", "سٹاپ", "ایک منٹ", "خاموش", "ٹھہرو", "رکنا", "کینسل",
            // Hindi / Devanagari script
            "रुको", "रुक जाओ", "बस", "चुप", "स्टॉप", "एक मिनट", "खामोश", "ठहरो", "रुकना", "कैंसिल"
        )

        fun isInterruptionWord(text: String): Boolean {
            val clean = text.trim().lowercase()
            return INTERRUPT_COMMANDS.any { cmd ->
                clean == cmd || clean.startsWith("$cmd ") || clean.endsWith(" $cmd") || clean.contains(" $cmd ")
            }
        }
    }


    val ttsProviderManager = TTSProviderManager(
        context = context,
        preferences = preferences,
        getTextToSpeech = { textToSpeech },
        onPlaybackStarted = {
            isSpeaking = true
            _voiceState.value = VoiceState.SPEAKING
            startListeningForBargeIn()
        },
        onPlaybackCompleted = {
            isSpeaking = false
            _voiceState.value = VoiceState.IDLE
            if (isContinuousListeningRequested && preferences.continuousListening) {
                mainHandler.postDelayed({
                    startListening()
                }, 400)
            }
        },
        onAmplitudeChanged = { amp ->
            if (isSpeaking) {
                _rmsAmplitude.value = amp
            }
        },
        onProviderFallback = { failedProvider, reason ->
            _statusMessage.value = "Falling back to System TTS: $reason"
        }
    )

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            configureFemaleVoice()
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    _voiceState.value = VoiceState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    _voiceState.value = VoiceState.IDLE
                    // Automatically resume listening mode after speaking
                    if (isContinuousListeningRequested && preferences.continuousListening) {
                        mainHandler.postDelayed({
                            startListening()
                        }, 400)
                    }
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    _voiceState.value = VoiceState.IDLE
                    if (isContinuousListeningRequested && preferences.continuousListening) {
                        mainHandler.postDelayed({
                            startListening()
                        }, 500)
                    }
                }
            })
        } else {
            Log.e("VoiceEngine", "TextToSpeech init failed")
        }
    }

    fun configureFemaleVoice(language: String = preferences.languageMode) {
        val tts = textToSpeech ?: return
        if (!isTtsReady) return

        val targetLocale = when (language) {
            SnowPreferences.LANG_HI -> Locale("hi", "IN")
            SnowPreferences.LANG_UR -> Locale("ur", "PK")
            SnowPreferences.LANG_ROMAN_UR -> Locale("hi", "IN")
            SnowPreferences.LANG_PS -> Locale("ps", "AF")
            else -> Locale.US
        }

        val result = tts.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }

        tts.setPitch(preferences.femaleVoicePitch)
        tts.setSpeechRate(preferences.speechRate)

        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val femaleVoice = voices.find { voice ->
                    val name = voice.name.lowercase()
                    (name.contains("female") || name.contains("sfg") || name.contains("tpd") || name.contains("wls")) &&
                            voice.locale.language == (tts.voice?.locale?.language ?: "en")
                } ?: voices.find { it.name.lowercase().contains("female") }

                if (femaleVoice != null) {
                    tts.voice = femaleVoice
                }
            }
        } catch (e: Exception) {
            Log.w("VoiceEngine", "Could not set custom female voice", e)
        }
    }

    fun speak(text: String, language: String = "English") {
        if (!preferences.autoSpeakEnabled) {
            _voiceState.value = VoiceState.IDLE
            if (isContinuousListeningRequested && preferences.continuousListening) {
                mainHandler.postDelayed({ startListening() }, 400)
            }
            return
        }

        // Stop current listening to avoid audio feedback
        stopListeningInternal()

        coroutineScope.launch {
            ttsProviderManager.synthesizeAndPlay(
                text = text,
                language = language,
                onSystemTtsRequested = {
                    speakWithSystemTts(text, language)
                }
            )
        }
    }

    private fun speakWithSystemTts(text: String, language: String) {
        val tts = textToSpeech ?: return
        if (!isTtsReady) return

        val locale = when {
            language.contains("Hindi", ignoreCase = true) || preferences.languageMode == SnowPreferences.LANG_HI -> Locale("hi", "IN")
            language.contains("Roman", ignoreCase = true) || preferences.languageMode == SnowPreferences.LANG_ROMAN_UR -> Locale("hi", "IN")
            language.contains("Urdu", ignoreCase = true) || preferences.languageMode == SnowPreferences.LANG_UR -> Locale("ur", "PK")
            language.contains("Pashto", ignoreCase = true) || preferences.languageMode == SnowPreferences.LANG_PS -> Locale("ps", "AF")
            else -> Locale.US
        }

        val langResult = tts.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }

        tts.setPitch(preferences.femaleVoicePitch)
        tts.setSpeechRate(preferences.speechRate)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "SNOW_REPLY_${System.currentTimeMillis()}")
        }

        _voiceState.value = VoiceState.SPEAKING
        isSpeaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "SNOW_REPLY")
        startListeningForBargeIn()
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        ttsProviderManager.stopAudio()
        isSpeaking = false
        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun startListeningForBargeIn() {
        if (!preferences.interruptWhileSpeaking) return
        mainHandler.postDelayed({
            if (isSpeaking && _voiceState.value == VoiceState.SPEAKING) {
                try {
                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(this@VoiceEngine)
                        }
                    }
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                        val languageTag = when (preferences.languageMode) {
                            SnowPreferences.LANG_HI -> "hi-IN"
                            SnowPreferences.LANG_UR -> "ur-PK"
                            SnowPreferences.LANG_ROMAN_UR -> "en-IN"
                            SnowPreferences.LANG_PS -> "ps-AF"
                            SnowPreferences.LANG_EN -> "en-US"
                            else -> Locale.getDefault().toLanguageTag()
                        }
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    }
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.w("VoiceEngine", "Could not start barge-in recognizer: ${e.message}")
                }
            }
        }, 350)
    }

    fun startListening() {
        mainHandler.post {
            // Immediate interruption handling: if currently speaking, stop AI output immediately
            if (isSpeaking && preferences.interruptWhileSpeaking) {
                stopSpeaking()
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _voiceState.value = VoiceState.ERROR
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@VoiceEngine)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    val languageTag = when (preferences.languageMode) {
                        SnowPreferences.LANG_HI -> "hi-IN"
                        SnowPreferences.LANG_UR -> "ur-PK"
                        SnowPreferences.LANG_ROMAN_UR -> "en-IN"
                        SnowPreferences.LANG_PS -> "ps-AF"
                        SnowPreferences.LANG_EN -> "en-US"
                        else -> Locale.getDefault().toLanguageTag()
                    }
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                }

                speechRecognizer?.startListening(intent)
                _voiceState.value = VoiceState.LISTENING
            } catch (e: Exception) {
                Log.e("VoiceEngine", "Error starting speech recognizer", e)
                _voiceState.value = VoiceState.ERROR
            }
        }
    }

    fun stopListening() {
        isContinuousListeningRequested = false
        stopListeningInternal()
        _voiceState.value = VoiceState.IDLE
    }

    private fun stopListeningInternal() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("VoiceEngine", "Error cancelling speech recognizer", e)
            }
            _rmsAmplitude.value = 0f
        }
    }

    fun setThinking() {
        _voiceState.value = VoiceState.THINKING
    }

    fun setIdle() {
        _voiceState.value = VoiceState.IDLE
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _voiceState.value = VoiceState.LISTENING
    }

    override fun onBeginningOfSpeech() {
        // Do NOT abruptly terminate TTS playback here on mic audio detection.
        // Android SpeechRecognizer will hear acoustic speaker output from TTS, which
        // would cause false positive cutoff. Only real interruption words or manual stop will cut off speech.
        if (!isSpeaking) {
            _voiceState.value = VoiceState.LISTENING
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Real-time microphone audio amplitude for Voice Orb animation
        _rmsAmplitude.value = (rmsdB.coerceIn(0f, 12f))
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _rmsAmplitude.value = 0f
    }

    override fun onError(error: Int) {
        _rmsAmplitude.value = 0f
        Log.w("VoiceEngine", "SpeechRecognizer error code: $error")

        // Auto restart for continuous listening if it was a timeout or no match
        if (preferences.continuousListening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            mainHandler.postDelayed({
                if (_voiceState.value != VoiceState.SPEAKING && _voiceState.value != VoiceState.THINKING) {
                    startListening()
                }
            }, 500)
        } else if (!isSpeaking) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    override fun onResults(results: Bundle?) {
        _rmsAmplitude.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull() ?: ""

        _partialTranscript.value = ""

        if (recognizedText.isNotBlank()) {
            if (isSpeaking && preferences.interruptWhileSpeaking) {
                if (isInterruptionWord(recognizedText)) {
                    Log.i("VoiceEngine", "Barge-in command verified in onResults: '$recognizedText'")
                    stopSpeaking()
                    _voiceState.value = VoiceState.LISTENING
                    onInterruptionRequested?.invoke(recognizedText)
                    return
                }
            }

            if (isInterruptionWord(recognizedText)) {
                stopSpeaking()
                _voiceState.value = VoiceState.LISTENING
                onVoiceInputRecognized(recognizedText, true)
            } else {
                checkWakeWordAndDispatch(recognizedText, isFinal = true)
            }
        } else if (preferences.continuousListening) {
            mainHandler.postDelayed({
                if (_voiceState.value != VoiceState.SPEAKING && _voiceState.value != VoiceState.THINKING) {
                    startListening()
                }
            }, 400)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _partialTranscript.value = text
            if (isSpeaking && preferences.interruptWhileSpeaking) {
                // Only interrupt if an actual interruption keyword is recognized
                if (isInterruptionWord(text)) {
                    Log.i("VoiceEngine", "Barge-in keyword verified in onPartialResults: '$text'")
                    stopSpeaking()
                    _voiceState.value = VoiceState.LISTENING
                    onInterruptionRequested?.invoke(text)
                }
            }
            // Check wake word in real-time
            if (checkWakeWord(text)) {
                onWakeWordDetected()
            }
        }
    }


    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun checkWakeWord(text: String): Boolean {
        if (!preferences.wakeWordEnabled) return false
        val lower = text.lowercase()
        val customWake = preferences.wakePhrase.trim().lowercase()
        if (customWake.isNotBlank() && lower.contains(customWake)) return true
        return lower.contains("snow") || lower.contains("hey snow") || text.contains("سنو") || text.contains("واوره")
    }

    private fun checkWakeWordAndDispatch(text: String, isFinal: Boolean) {
        if (checkWakeWord(text)) {
            onWakeWordDetected()
            val customWake = preferences.wakePhrase.trim()
            var query = text.replace(Regex("(?i)^(hey\\s+)?snow[,\\s]*"), "")
            if (customWake.isNotBlank()) {
                query = query.replace(Regex("(?i)^" + Regex.escape(customWake) + "[,\\s]*"), "")
            }
            query = query.trim()
            if (query.isBlank()) {
                query = text
            }
            onVoiceInputRecognized(query, isFinal)
        } else {
            onVoiceInputRecognized(text, isFinal)
        }
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        stopSpeaking()
        textToSpeech?.shutdown()
        ttsProviderManager.stopAudio()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
