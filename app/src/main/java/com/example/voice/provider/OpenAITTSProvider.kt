package com.example.voice.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAITTSProvider(
    private val getApiKey: () -> String
) : TTSProvider {

    override val id: String = "OPENAI"
    override val displayName: String = "OpenAI Neural TTS"
    override val requiresApiKey: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        speed: Float,
        language: String
    ): Result<TTSAudioResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("OpenAI API key is missing. Please configure it in Settings."))
        }

        try {
            val actualVoice = voiceId.ifBlank { "nova" }
            val clampedSpeed = speed.coerceIn(0.5f, 2.0f)

            val payload = JSONObject().apply {
                put("model", "tts-1")
                put("input", text)
                put("voice", actualVoice)
                put("response_format", "mp3")
                put("speed", clampedSpeed)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                val msg = parseErrorMessage(response.code, errBody)
                return@withContext Result.failure(IOException(msg))
            }

            val audioBytes = response.body?.bytes()
            if (audioBytes == null || audioBytes.isEmpty()) {
                return@withContext Result.failure(IOException("Empty audio returned from OpenAI TTS"))
            }

            Result.success(TTSAudioResult.AudioBytes(audioBytes, "audio/mpeg"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableVoices(language: String): List<TTSVoice> {
        return listOf(
            TTSVoice("nova", "Nova (Warm & Friendly Female)", "Female", "Natural, warm, expressive female voice", isDefault = true),
            TTSVoice("shimmer", "Shimmer (Clear & Expressive Female)", "Female", "Bright, clear, feminine tone"),
            TTSVoice("alloy", "Alloy (Balanced & Smooth)", "Female-Neutral", "Smooth, versatile conversational voice"),
            TTSVoice("fable", "Fable (British Accented Female)", "Female", "Expressive, storytelling cadence")
        )
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is empty"))
        }
        try {
            val payload = JSONObject().apply {
                put("model", "tts-1")
                put("input", "Hello")
                put("voice", "nova")
                put("response_format", "mp3")
            }
            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("OpenAI connection successful! Natural voice ready.")
            } else {
                val err = response.body?.string() ?: ""
                Result.failure(IOException(parseErrorMessage(response.code, err)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message", "")
            when (code) {
                401 -> "Invalid OpenAI API key: $message"
                429 -> "OpenAI quota exceeded or rate limited: $message"
                else -> "OpenAI API error ($code): $message"
            }
        } catch (e: Exception) {
            "OpenAI API error ($code)"
        }
    }
}
