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

class ElevenLabsTTSProvider(
    private val getApiKey: () -> String
) : TTSProvider {

    override val id: String = "ELEVENLABS"
    override val displayName: String = "ElevenLabs Neural Voice"
    override val requiresApiKey: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Curated high quality female voices as reliable defaults
    private val fallbackVoices = listOf(
        TTSVoice("21m00Tcm4TlvDq8ikWAM", "Rachel (Calm & Natural Female)", "Female", "Natural American female voice, very warm", isDefault = true),
        TTSVoice("EXAVITQu4vr4xnSDxMaL", "Bella (Soft & Friendly Female)", "Female", "Sweet, expressive conversational tone"),
        TTSVoice("MF3mGyEYCl7XYWbV9V6O", "Elli (Young & Expressive Female)", "Female", "Bright, clear young female voice"),
        TTSVoice("AZnzlk1XvdvUeBnXmlld", "Domi (Confident & Strong Female)", "Female", "Emphatic and polished female voice"),
        TTSVoice("jsCqWAovK2LkecY7zXl4", "Freya (Expressive Conversational)", "Female", "Dynamic, friendly conversational cadence")
    )

    override suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        speed: Float,
        language: String
    ): Result<TTSAudioResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("ElevenLabs API key is missing. Please enter it in Settings."))
        }

        try {
            val targetVoiceId = voiceId.ifBlank { "21m00Tcm4TlvDq8ikWAM" }

            val payload = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                val voiceSettings = JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.8)
                    put("use_speaker_boost", true)
                }
                put("voice_settings", voiceSettings)
            }

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$targetVoiceId")
                .addHeader("xi-api-key", apiKey)
                .addHeader("Accept", "audio/mpeg")
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
                return@withContext Result.failure(IOException("Empty audio stream from ElevenLabs"))
            }

            Result.success(TTSAudioResult.AudioBytes(audioBytes, "audio/mpeg"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableVoices(language: String): List<TTSVoice> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey.isBlank()) return@withContext fallbackVoices

        try {
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/voices")
                .addHeader("xi-api-key", apiKey)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext fallbackVoices

            val body = response.body?.string() ?: return@withContext fallbackVoices
            val json = JSONObject(body)
            val voicesArray = json.optJSONArray("voices") ?: return@withContext fallbackVoices

            val result = mutableListOf<TTSVoice>()
            for (i in 0 until voicesArray.length()) {
                val vObj = voicesArray.getJSONObject(i)
                val id = vObj.optString("voice_id")
                val name = vObj.optString("name")
                val labels = vObj.optJSONObject("labels")
                val gender = labels?.optString("gender", "Female") ?: "Female"
                val desc = labels?.optString("description", "") ?: ""

                // Prioritize female voices
                if (gender.equals("female", ignoreCase = true) || name.contains("female", ignoreCase = true)) {
                    result.add(TTSVoice(id, "$name (Female)", "Female", desc, isDefault = (result.isEmpty())))
                }
            }

            if (result.isNotEmpty()) result else fallbackVoices
        } catch (e: Exception) {
            fallbackVoices
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is empty"))
        }
        try {
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/user")
                .addHeader("xi-api-key", apiKey)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("ElevenLabs connection verified! Natural voices available.")
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
            val detail = json.optJSONObject("detail")?.optString("message")
                ?: json.optString("detail", "")
            when (code) {
                401 -> "Invalid ElevenLabs API key: $detail"
                429 -> "ElevenLabs quota exceeded: $detail"
                else -> "ElevenLabs API error ($code): $detail"
            }
        } catch (e: Exception) {
            "ElevenLabs API error ($code)"
        }
    }
}
