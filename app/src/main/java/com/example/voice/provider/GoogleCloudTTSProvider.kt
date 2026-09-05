package com.example.voice.provider

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GoogleCloudTTSProvider(
    private val getApiKey: () -> String
) : TTSProvider {

    override val id: String = "GOOGLE_CLOUD"
    override val displayName: String = "Google Cloud Neural TTS"
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
            return@withContext Result.failure(IllegalStateException("Google Cloud TTS API key is missing."))
        }

        try {
            val (langCode, resolvedVoice) = resolveVoiceForLanguage(language, voiceId)
            val clampedSpeed = speed.coerceIn(0.5f, 2.0f)

            val inputObj = JSONObject().put("text", text)
            val voiceObj = JSONObject().apply {
                put("languageCode", langCode)
                put("name", resolvedVoice)
                put("ssmlGender", "FEMALE")
            }
            val audioConfigObj = JSONObject().apply {
                put("audioEncoding", "MP3")
                put("speakingRate", clampedSpeed)
            }

            val payload = JSONObject().apply {
                put("input", inputObj)
                put("voice", voiceObj)
                put("audioConfig", audioConfigObj)
            }

            val url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(response.code, errBody)))
            }

            val respJson = JSONObject(response.body?.string() ?: "{}")
            val base64Audio = respJson.optString("audioContent", "")
            if (base64Audio.isBlank()) {
                return@withContext Result.failure(IOException("Empty audioContent returned by Google Cloud TTS"))
            }

            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            Result.success(TTSAudioResult.AudioBytes(audioBytes, "audio/mpeg"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableVoices(language: String): List<TTSVoice> {
        return listOf(
            TTSVoice("en-US-Journey-F", "Journey (Conversational Female)", "Female", "Ultra natural conversational female voice", isDefault = true),
            TTSVoice("en-US-Neural2-F", "Neural2 Female (Clear & Smooth)", "Female", "Google Neural2 natural female voice"),
            TTSVoice("hi-IN-Neural2-A", "Hindi Neural2 Female", "Female", "Natural Hindi / Roman Urdu female voice"),
            TTSVoice("ur-PK-Standard-A", "Urdu Pakistan Female", "Female", "Urdu female voice"),
            TTSVoice("en-US-Wavenet-F", "Wavenet Female (Expressive)", "Female", "Google Wavenet female voice")
        )
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is empty"))
        }
        try {
            val payload = JSONObject().apply {
                put("input", JSONObject().put("text", "Hi"))
                put("voice", JSONObject().apply {
                    put("languageCode", "en-US")
                    put("name", "en-US-Neural2-F")
                })
                put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
            }
            val url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("Google Cloud TTS connection verified successfully!")
            } else {
                val err = response.body?.string() ?: ""
                Result.failure(IOException(parseErrorMessage(response.code, err)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveVoiceForLanguage(language: String, requestedVoice: String): Pair<String, String> {
        val lower = language.lowercase()
        return when {
            lower.contains("hindi") -> "hi-IN" to "hi-IN-Neural2-A"
            lower.contains("urdu") || lower.contains("roman") -> {
                // If pure Urdu script, ur-PK-Standard-A; for Roman Urdu or Hindi accent hi-IN is great
                if (lower.contains("roman")) "hi-IN" to "hi-IN-Neural2-A"
                else "ur-PK" to "ur-PK-Standard-A"
            }
            else -> {
                val voice = if (requestedVoice.isNotBlank()) requestedVoice else "en-US-Journey-F"
                "en-US" to voice
            }
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val message = json.optJSONObject("error")?.optString("message", "") ?: body
            "Google Cloud TTS ($code): $message"
        } catch (e: Exception) {
            "Google Cloud TTS error ($code)"
        }
    }
}
