package com.example.voice.provider

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SystemTTSProvider(
    private val context: Context,
    private val getTextToSpeech: () -> TextToSpeech?
) : TTSProvider {

    override val id: String = "SYSTEM"
    override val displayName: String = "Android System TTS (Fallback)"
    override val requiresApiKey: Boolean = false

    override suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        speed: Float,
        language: String
    ): Result<TTSAudioResult> {
        val tts = getTextToSpeech()
            ?: return Result.failure(IllegalStateException("Android TextToSpeech is not initialized"))

        // System TTS handles playback directly through TextToSpeech
        return Result.success(TTSAudioResult.SystemTtsHandled)
    }

    override suspend fun getAvailableVoices(language: String): List<TTSVoice> {
        val tts = getTextToSpeech() ?: return emptyList()
        val result = mutableListOf<TTSVoice>()
        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                for (v in voices) {
                    val nameLower = v.name.lowercase()
                    if (nameLower.contains("female") || nameLower.contains("f0") || nameLower.contains("sfg")) {
                        result.add(
                            TTSVoice(
                                id = v.name,
                                name = "${v.name} (${v.locale.displayLanguage})",
                                gender = "Female",
                                description = "System Female Voice [${v.locale}]"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        if (result.isEmpty()) {
            result.add(TTSVoice("default_female", "System Default Female Voice", "Female", "Built-in tuned system voice", isDefault = true))
        }
        return result
    }

    override suspend fun testConnection(): Result<String> {
        val tts = getTextToSpeech()
        return if (tts != null) {
            Result.success("Android System TTS is ready.")
        } else {
            Result.failure(IllegalStateException("Android TextToSpeech engine not ready yet."))
        }
    }
}
