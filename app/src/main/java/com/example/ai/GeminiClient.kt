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
    val actionCommand: ActionCommand? = null
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
        modelName: String = "gemini-3.5-flash",
        languagePreference: String = "AUTO",
        conversationHistory: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null
    ): AiResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext AiResponse(
                spokenText = "Please set your Gemini API key by tapping the center orb.",
                detectedLanguage = "English"
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val rootJson = JSONObject()

            // System Instruction
            val systemInstruction = JSONObject()
            val systemParts = JSONArray()
            val systemText = buildString {
                append("You are Snow, an intelligent, empathetic, and witty voice assistant with a warm, natural female voice persona. ")
                append("You are speaking aloud directly to the user through real-time voice synthesis. ")
                append("Crucial guidelines: ")
                append("1. Keep answers concise, spoken-friendly, natural, and conversational (usually 1 to 3 spoken sentences). Avoid bullet points or markdown tables because your words are spoken aloud. ")
                append("2. You are fluent in English, Urdu (اردو), and Pashto (پښتو). ")
                when (languagePreference) {
                    "UR" -> append("Respond ALWAYS in Urdu (اردو) using natural, spoken Urdu script. ")
                    "PS" -> append("Respond ALWAYS in Pashto (پښتو) using natural, spoken Pashto script. ")
                    "EN" -> append("Respond ALWAYS in clear, friendly English. ")
                    else -> append("Match the language of the user's input automatically (if user speaks Urdu, reply in Urdu; if Pashto, reply in Pashto; if English, reply in English). ")
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

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val rawResponse = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API error: ${response.code} - $rawResponse")
                return@withContext AiResponse(
                    spokenText = "Sorry, I encountered an issue connecting to my brain. Please check your network or API key.",
                    detectedLanguage = "English"
                )
            }

            val responseJson = JSONObject(rawResponse)
            val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0)
            val candidateContent = candidate?.optJSONObject("content")
            val parts = candidateContent?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            parseAiResponse(rawText)
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
        // Check for Arabic/Urdu/Pashto unicode ranges
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
        return "English"
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
