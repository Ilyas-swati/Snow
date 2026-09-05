package com.example.voice.provider

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.SnowPreferences
import com.example.voice.audio.TTSAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TTSProviderManager(
    private val context: Context,
    private val preferences: SnowPreferences,
    private val getTextToSpeech: () -> TextToSpeech?,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackCompleted: () -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onProviderFallback: (failedProvider: String, reason: String) -> Unit
) {
    val openAiProvider = OpenAITTSProvider { preferences.openAiApiKey }
    val elevenLabsProvider = ElevenLabsTTSProvider { preferences.elevenLabsApiKey }
    val googleCloudProvider = GoogleCloudTTSProvider { preferences.googleCloudApiKey }
    val systemProvider = SystemTTSProvider(context, getTextToSpeech)

    val allProviders: List<TTSProvider> = listOf(
        openAiProvider,
        elevenLabsProvider,
        googleCloudProvider,
        systemProvider
    )

    private val audioPlayer = TTSAudioPlayer(
        context = context,
        onPlaybackStarted = onPlaybackStarted,
        onPlaybackCompleted = onPlaybackCompleted,
        onAmplitudeChanged = onAmplitudeChanged,
        onError = { errMsg ->
            Log.w("TTSProviderManager", "Audio playback error: $errMsg")
            onPlaybackCompleted()
        }
    )

    fun getActiveProvider(): TTSProvider {
        return when (preferences.ttsProvider) {
            SnowPreferences.TTS_PROVIDER_OPENAI -> openAiProvider
            SnowPreferences.TTS_PROVIDER_ELEVENLABS -> elevenLabsProvider
            SnowPreferences.TTS_PROVIDER_GOOGLE_CLOUD -> googleCloudProvider
            else -> systemProvider
        }
    }

    suspend fun synthesizeAndPlay(
        text: String,
        language: String,
        onSystemTtsRequested: () -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        val activeProvider = getActiveProvider()

        // If system provider is selected, directly trigger system TTS
        if (activeProvider is SystemTTSProvider) {
            onSystemTtsRequested()
            return@withContext true
        }

        // External provider selected: attempt synthesis
        val voiceId = preferences.selectedVoiceId
        val speed = preferences.speechRate

        val synthResult = activeProvider.synthesizeSpeech(text, voiceId, speed, language)

        if (synthResult.isSuccess) {
            val audioResult = synthResult.getOrNull()
            if (audioResult is TTSAudioResult.AudioBytes) {
                audioPlayer.playAudio(audioResult.bytes, audioResult.mimeType)
                return@withContext true
            }
        }

        // External provider failed: graceful fallback
        val failureReason = synthResult.exceptionOrNull()?.message ?: "Unknown TTS error"
        Log.w("TTSProviderManager", "Provider ${activeProvider.displayName} failed: $failureReason. Fallback: ${preferences.fallbackVoiceEnabled}")

        if (preferences.fallbackVoiceEnabled) {
            onProviderFallback(activeProvider.displayName, failureReason)
            onSystemTtsRequested()
            return@withContext true
        }

        // If fallback disabled, complete cleanly without crash
        onPlaybackCompleted()
        return@withContext false
    }

    fun stopAudio() {
        audioPlayer.stopPlayback()
    }
}
