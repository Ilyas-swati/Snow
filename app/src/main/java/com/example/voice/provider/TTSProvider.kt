package com.example.voice.provider

interface TTSProvider {
    val id: String
    val displayName: String
    val requiresApiKey: Boolean

    suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        speed: Float,
        language: String
    ): Result<TTSAudioResult>

    suspend fun getAvailableVoices(language: String): List<TTSVoice>

    suspend fun testConnection(): Result<String>
}
