package com.example.voice.provider

data class TTSVoice(
    val id: String,
    val name: String,
    val gender: String = "Female",
    val description: String = "",
    val supportedLanguages: List<String> = emptyList(),
    val isDefault: Boolean = false
)

sealed class TTSAudioResult {
    data class AudioBytes(
        val bytes: ByteArray,
        val mimeType: String = "audio/mpeg"
    ) : TTSAudioResult()

    object SystemTtsHandled : TTSAudioResult()
}
