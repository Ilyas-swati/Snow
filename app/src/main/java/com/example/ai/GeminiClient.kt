package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class AiResponse(
    val spokenText: String,
    val detectedLanguage: String,
    val actionCommand: ActionCommand? = null,
    val modelUsed: String? = null,
    val isQuotaExceeded: Boolean = false
)

sealed class ActionCommand {
    data class OpenApp(val appName: String) : ActionCommand()
    data class WhatsAppMessage(val recipient: String, val message: String) : ActionCommand()
    data class ToggleFlashlight(val enable: Boolean) : ActionCommand()
    data class AdjustVolume(val isUp: Boolean) : ActionCommand()
    object OpenWifiSettings : ActionCommand()
    object OpenBluetoothSettings : ActionCommand()
}

class GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateVoiceResponse(
        prompt: String,
        apiKey: String,
        modelName: String = "gemini-2.5-flash",
        languagePreference: String = "AUTO",
        conversationHistory: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null
    ): AiResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext AiResponse(
                spokenText = "Please set your Gemini API key by tapping the center orb or opening Settings.",
                detectedLanguage = "English"
            )
        }

        try {
            val rootJson = JSONObject()

            // System Instruction
            val systemInstruction = JSONObject()
            val systemParts = JSONArray()
            val systemText = buildString {
                append("You are Snow, an intelligent, empathetic, and witty voice assistant with a warm, natural female voice persona. ")
                append("You are speaking aloud directly to the user through real-time voice synthesis. ")
                append("Crucial guidelines: ")
                append("1. Keep answers concise, spoken-friendly, natural, and conversational (usually 1 to 3 spoken sentences). Avoid bullet points, symbols, or markdown formatting because your words are spoken directly. ")
                append("2. Language Support: You are fluent in English, Hindi (हिन्दी), Urdu (اردو), Roman Urdu (Urdu written in English/Latin letters), and Pashto (پښتو). ")
                append("You deeply understand mixed-language queries, codeswitching, and colloquial speech (e.g., 'Snow kal mujhe 8 baje uthana', 'Snow mujhe batao weather kaisa hai', 'Snow یہ کام کر دو', 'Snow ma sara Pukhto ke khabara kawa'). ")
                when (languagePreference) {
                    "UR" -> append("User selected Urdu: Respond ALWAYS in natural spoken Urdu script (اردو). ")
                    "HI" -> append("User selected Hindi: Respond ALWAYS in natural spoken Hindi script (हिन्दी). ")
                    "ROMAN_UR" -> append("User selected Roman Urdu: Respond ALWAYS in natural conversational Roman Urdu (Urdu written in English/Latin alphabet, e.g., 'Ji bilkul, main abhi check kar ke batati hoon.'). ")
                    "PS" -> append("User selected Pashto: Respond ALWAYS in natural spoken Pashto (پښتو). ")
                    "EN" -> append("User selected English: Respond ALWAYS in clear, warm, friendly English. ")
                    else -> {
                        append("Auto Detect Language: Automatically detect the user's language and respond naturally in the EXACT SAME language. ")
                        append("If the user writes or speaks in Roman Urdu (e.g., 'Snow kal mujhe 8 baje uthana' or 'kaisa hai'), reply naturally in Roman Urdu. ")
                        append("If the user speaks Hindi, reply in Hindi. If Urdu script, reply in Urdu script. If Pashto, reply in Pashto. If English, reply in English. ")
                        append("Do NOT translate the user's request unnecessarily. ")
                    }
                }
                append("3. If the user requests a device action or WhatsApp message, include an action tag at the very end of your reply: ")
                append("[ACTION:OPEN_APP <app_name>] ")
                append("[ACTION:WHATSAPP <recipient_or_number> | <message>] ")
                append("[ACTION:FLASHLIGHT <ON|OFF>] ")
                append("[ACTION:VOLUME <UP|DOWN>] ")
                append("[ACTION:WIFI] ")
                append("[ACTION:BLUETOOTH] ")
                append("Example: 'Sure, opening WhatsApp for you! [ACTION:OPEN_APP whatsapp]'. ")
                append("Always provide a natural polite spoken response before any action tag.")
            }
            systemParts.put(JSONObject().put("text", systemText))
            systemInstruction.put("parts", systemParts)
            rootJson.put("systemInstruction", systemInstruction)

            // Contents (History + Current Prompt)
            val contents = JSONArray()

            // Add last 6 turns for conversational context
            val recent = conversationHistory.takeLast(6)
            for (msg in recent) {
                val role = if (msg.sender.equals("user", ignoreCase = true)) "user" else "model"
                val contentObj = JSONObject()
                contentObj.put("role", role)
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.content))
                contentObj.put("parts", parts)
                contents.put(contentObj)
            }

            // Current user turn
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val currentParts = JSONArray()
            currentParts.put(JSONObject().put("text", prompt))

            // Multimodal image if attached
            if (imageBitmap != null) {
                val base64Data = bitmapToBase64(imageBitmap)
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", base64Data)
                currentParts.put(JSONObject().put("inlineData", inlineData))
            }

            currentTurn.put("parts", currentParts)
            contents.put(currentTurn)

            rootJson.put("contents", contents)

            // Generation config
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.7)
            rootJson.put("generationConfig", genConfig)

            val requestBodyString = rootJson.toString()

            // Candidate models in priority order: first try the requested model, then fallbacks
            val candidateModels = linkedSetOf<String>().apply {
                if (modelName.isNotBlank()) add(modelName)
                add("gemini-2.5-flash")
                add("gemini-flash-latest")
                add("gemini-3.1-flash-lite-preview")
                add("gemini-3.1-pro-preview")
                add("gemini-3.5-flash")
            }.toList()

            var lastStatusCode = 0
            var allQuotaExceeded = true

            for (candidateModel in candidateModels) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$candidateModel:generateContent?key=$apiKey"
                val requestBody = requestBodyString.toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    Log.e("GeminiClient", "Network error for model $candidateModel: ${e.message}")
                    allQuotaExceeded = false
                    continue
                }

                val rawResponse = response.body?.string() ?: ""
                lastStatusCode = response.code

                if (!response.isSuccessful) {
                    Log.w("GeminiClient", "API error for model $candidateModel: ${response.code} - $rawResponse")

                    // Invalid API key
                    if (response.code == 400 && (rawResponse.contains("API_KEY_INVALID") || rawResponse.contains("API key not valid"))) {
                        return@withContext AiResponse(
                            spokenText = "Your Gemini API key appears to be invalid. Please check your API key in Settings.",
                            detectedLanguage = "English"
                        )
                    }

                    // 429 Quota/rate-limit or 503 temporary overload
                    if (response.code == 429 || response.code == 503 || rawResponse.contains("RESOURCE_EXHAUSTED") || rawResponse.contains("QUOTA_EXCEEDED")) {
                        Log.i("GeminiClient", "Model $candidateModel hit quota limit or temporary error (${response.code}). Attempting fallback model...")
                        continue
                    }

                    if (response.code == 404) {
                        Log.w("GeminiClient", "Model $candidateModel not found (404), continuing to next model...")
                        continue
                    }

                    allQuotaExceeded = false
                    continue
                }

                // If response is successful, parse candidates
                allQuotaExceeded = false
                val responseJson = JSONObject(rawResponse)
                val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0)
                val candidateContent = candidate?.optJSONObject("content")
                val parts = candidateContent?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                if (rawText.isNotBlank()) {
                    Log.i("GeminiClient", "Successfully generated response using model: $candidateModel")
                    val parsed = parseAiResponse(rawText)
                    return@withContext parsed.copy(modelUsed = candidateModel)
                }
            }

            if (lastStatusCode == 429 || allQuotaExceeded) {
                AiResponse(
                    spokenText = "Free tier Gemini quota was temporarily reached. Please enter your custom Gemini API key in Settings to continue without interruption.",
                    detectedLanguage = "English",
                    isQuotaExceeded = true
                )
            } else {
                AiResponse(
                    spokenText = "I couldn't process that right now. Please try again shortly.",
                    detectedLanguage = "English"
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Exception in generateVoiceResponse", e)
            AiResponse(
                spokenText = "I couldn't process that right now. Please try again in a moment.",
                detectedLanguage = "English"
            )
        }
    }

    private fun parseAiResponse(rawText: String): AiResponse {
        var cleanText = rawText
        var actionCommand: ActionCommand? = null

        // Parse Action Tags: [ACTION: ...]
        val actionRegex = Regex("\\[ACTION:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val match = actionRegex.find(rawText)

        if (match != null) {
            cleanText = rawText.replace(match.value, "").trim()
            val actionBody = match.groupValues[1].trim()
            val parts = actionBody.split(" ", limit = 2)
            val commandType = parts[0].uppercase()
            val argument = if (parts.size > 1) parts[1].trim() else ""

            actionCommand = when (commandType) {
                "OPEN_APP" -> ActionCommand.OpenApp(argument.ifBlank { "app" })
                "WHATSAPP" -> {
                    val splitArgs = argument.split("|", limit = 2)
                    val recipient = splitArgs.getOrNull(0)?.trim() ?: ""
                    val msg = splitArgs.getOrNull(1)?.trim() ?: ""
                    ActionCommand.WhatsAppMessage(recipient, msg)
                }
                "FLASHLIGHT" -> ActionCommand.ToggleFlashlight(argument.equals("ON", ignoreCase = true))
                "VOLUME" -> ActionCommand.AdjustVolume(argument.equals("UP", ignoreCase = true))
                "WIFI" -> ActionCommand.OpenWifiSettings
                "BLUETOOTH" -> ActionCommand.OpenBluetoothSettings
                else -> null
            }
        }

        // Clean markdown symbols like asterisks or hashtags from spoken text
        cleanText = cleanText.replace(Regex("[*#_`~]"), "").trim()

        val detectedLanguage = detectLanguage(cleanText)

        return AiResponse(
            spokenText = cleanText.ifBlank { "I'm listening, how can I help you?" },
            detectedLanguage = detectedLanguage,
            actionCommand = actionCommand
        )
    }

    private fun detectLanguage(text: String): String {
        // 1. Check Devanagari (Hindi) unicode range: 0x0900..0x097F
        var devanagariCount = 0
        for (char in text) {
            if (char.code in 0x0900..0x097F) {
                devanagariCount++
            }
        }
        if (devanagariCount > 3) {
            return "Hindi"
        }

        // 2. Check for Arabic/Urdu/Pashto unicode ranges
        var urduPashtoCharCount = 0
        for (char in text) {
            val code = char.code
            if (code in 0x0600..0x06FF || code in 0x0750..0x077F) {
                urduPashtoCharCount++
            }
        }
        if (urduPashtoCharCount > 3) {
            // Check specific Pashto characters (e.g., ږ, ښ, ڼ, ۍ, ې, څ, ځ)
            val pashtoChars = setOf('ږ', 'ښ', 'ڼ', 'ۍ', 'ې', 'څ', 'ځ')
            val isPashto = text.any { it in pashtoChars }
            return if (isPashto) "Pashto" else "Urdu"
        }

        // 3. Check for Roman Urdu / Hindi keywords
        val lowerText = text.lowercase()
        val romanUrduKeywords = listOf(
            "mujhe", "mera", "meri", "hum", "hai", "hain", "kaisa", "kaisi", "kaise",
            "karo", "karna", "uthana", "batao", "baje", "kya", "shukriya", "theek",
            "nahi", "nahin", "bohot", "accha", "acha", "suno", "snow", "wala", "wali",
            "aap", "tum", "apka", "aapka", "karen", "raha", "rahi", "rahe", "gaya", "gayi"
        )
        val romanPashtoKeywords = listOf("pukhto", "khabara", "staso", "manana", "kawa", "sara")

        if (romanPashtoKeywords.any { lowerText.contains(it) }) {
            return "Pashto"
        }

        val matchCount = romanUrduKeywords.count { lowerText.contains(it) }
        if (matchCount >= 2 || (matchCount >= 1 && (lowerText.contains("kaisa") || lowerText.contains("karo") || lowerText.contains("uthana") || lowerText.contains("batao")))) {
            return "Roman Urdu"
        }

        return "English"
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
