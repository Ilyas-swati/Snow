package com.example.ai.provider

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.agent.AgentTool
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

class GeminiAIProvider : AIProvider {

    override val id: String = "GEMINI"
    override val displayName: String = "Google Gemini"
    override val requiresApiKey: Boolean = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun generateAgentTurn(
        prompt: String,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        tools: List<AgentTool>,
        conversationHistory: List<ChatMessage>,
        imageBitmap: Bitmap?,
        toolOutputsContext: String?
    ): ProviderTurnResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ProviderTurnResult(
                spokenText = "Please enter your Gemini API key in Settings.",
                error = "API key missing"
            )
        }

        val candidateModels = linkedSetOf<String>().apply {
            if (modelName.isNotBlank()) add(modelName)
            add("gemini-2.5-flash")
            add("gemini-flash-latest")
            add("gemini-3.1-flash-lite-preview")
            add("gemini-3.1-pro-preview")
            add("gemini-3.5-flash")
        }.toList()

        try {
            val rootJson = JSONObject()

            // System Instruction with Tools Guidance
            val toolsPrompt = buildString {
                append(systemInstruction)
                append("\n\nAVAILABLE TOOLS:\n")
                tools.forEach { tool ->
                    append("- Tool: ${tool.name}\n")
                    append("  Description: ${tool.description}\n")
                    if (tool.parameters.isNotEmpty()) {
                        append("  Parameters: " + tool.parameters.joinToString(", ") { "${it.name} (${it.type}): ${it.description}" } + "\n")
                    }
                }
                append("\nTOOL CALLING FORMAT:\n")
                append("When you need to execute a tool, write the call in JSON format on its own line like this:\n")
                append("""[TOOL_CALL:{"tool":"tool_name","arguments":{"param1":"val1"}}]""")
                append("\nYou can execute multiple tools in sequence if the task involves multiple steps. After tool output is provided, give a warm, concise conversational female voice response.")
            }

            val sysObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", toolsPrompt))
            sysObj.put("parts", sysParts)
            rootJson.put("systemInstruction", sysObj)

            val contents = JSONArray()
            // History
            for (msg in conversationHistory.takeLast(6)) {
                val role = if (msg.sender.equals("user", ignoreCase = true)) "user" else "model"
                val contentObj = JSONObject()
                contentObj.put("role", role)
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.content))
                contentObj.put("parts", parts)
                contents.put(contentObj)
            }

            // Current Turn
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val currentParts = JSONArray()

            val effectiveUserPrompt = buildString {
                append(prompt)
                if (!toolOutputsContext.isNullOrBlank()) {
                    append("\n\n[OBSERVED TOOL RESULTS]:\n")
                    append(toolOutputsContext)
                    append("\nNow proceed to next step or provide the final spoken response.")
                }
            }
            currentParts.put(JSONObject().put("text", effectiveUserPrompt))

            if (imageBitmap != null) {
                val base64 = bitmapToBase64(imageBitmap)
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64)
                }
                currentParts.put(JSONObject().put("inlineData", inlineData))
            }
            currentTurn.put("parts", currentParts)
            contents.put(currentTurn)

            rootJson.put("contents", contents)

            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
            }
            rootJson.put("generationConfig", genConfig)

            val requestBodyString = rootJson.toString()
            var lastError: String? = null
            var quotaHit = false

            for (model in candidateModels) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyString.toRequestBody(jsonMediaType))
                    .build()

                val response = try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    lastError = "Network error: ${e.message}"
                    continue
                }

                val raw = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 429 || response.code == 503 || raw.contains("RESOURCE_EXHAUSTED")) {
                        quotaHit = true
                        lastError = "Quota limit reached on $model (${response.code})"
                        continue
                    }
                    if (response.code == 400 && raw.contains("API_KEY_INVALID")) {
                        return@withContext ProviderTurnResult(
                            spokenText = "Your Gemini API key appears invalid. Please verify it in Settings.",
                            error = "Invalid API Key"
                        )
                    }
                    lastError = "Error ${response.code}: $raw"
                    continue
                }

                val responseJson = JSONObject(raw)
                val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0)
                val candidateParts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                val text = candidateParts?.optJSONObject(0)?.optString("text", "") ?: ""

                if (text.isNotBlank()) {
                    val parsed = parseToolCallsAndSpoken(text)
                    return@withContext parsed.copy(modelUsed = model)
                }
            }

            if (quotaHit) {
                ProviderTurnResult(
                    spokenText = "Gemini quota limit was temporarily reached. You can switch to another model or provider in Settings.",
                    isQuotaExceeded = true,
                    error = lastError
                )
            } else {
                ProviderTurnResult(
                    spokenText = "I encountered an issue contacting Gemini. Please check your network or API key in Settings.",
                    error = lastError ?: "Unknown error"
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiAIProvider", "Exception in generateAgentTurn", e)
            ProviderTurnResult(
                spokenText = "An error occurred while processing your request.",
                error = e.message
            )
        }
    }

    override suspend fun testConnection(
        apiKey: String,
        modelName: String,
        customEndpoint: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ConnectionTestResult(false, "API key is empty")
        }

        val startTime = System.currentTimeMillis()
        val targetModel = if (modelName.isNotBlank()) modelName else "gemini-2.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            val contents = JSONArray()
            val turn = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().put("text", "Respond with 1 word: 'Connected'"))
                }
                put("parts", parts)
            }
            contents.put(turn)
            put("contents", contents)
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val raw = response.body?.string() ?: ""

            if (response.isSuccessful) {
                ConnectionTestResult(true, "Connected successfully ($latency ms)", latency)
            } else {
                val errorMsg = when (response.code) {
                    400 -> if (raw.contains("API_KEY_INVALID")) "API key is invalid" else "Bad request (${response.code})"
                    401 -> "Unauthorized - check API key"
                    403 -> "Forbidden - check API permissions"
                    404 -> "Model '$targetModel' not found"
                    429 -> "Quota exceeded (429)"
                    503 -> "Gemini service temporarily unavailable (503)"
                    else -> "HTTP ${response.code}"
                }
                ConnectionTestResult(false, errorMsg, latency)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ConnectionTestResult(false, e.message ?: "Connection failed", latency)
        }
    }

    private fun parseToolCallsAndSpoken(text: String): ProviderTurnResult {
        val toolCalls = mutableListOf<ToolCallRequest>()
        val toolCallRegex = Regex("""\[TOOL_CALL:(\{.*?\})\]""")
        val matches = toolCallRegex.findAll(text)

        for (match in matches) {
            try {
                val jsonStr = match.groupValues[1]
                val obj = JSONObject(jsonStr)
                val toolName = obj.optString("tool", "")
                val argsObj = obj.optJSONObject("arguments")
                val argsMap = mutableMapOf<String, String>()
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = argsObj.optString(k, "")
                    }
                }
                if (toolName.isNotBlank()) {
                    toolCalls.add(ToolCallRequest(toolName, argsMap))
                }
            } catch (e: Exception) {
                Log.w("GeminiAIProvider", "Failed to parse tool call JSON: ${match.value}")
            }
        }

        // Clean spoken text by removing tool call tags
        var cleanSpoken = text.replace(toolCallRegex, "").trim()
        cleanSpoken = cleanSpoken.replace(Regex("""\[ACTION:.*?\]"""), "").trim()

        val detectedLang = detectLanguage(cleanSpoken)
        return ProviderTurnResult(
            spokenText = cleanSpoken,
            toolCalls = toolCalls,
            detectedLanguage = detectedLang
        )
    }

    private fun detectLanguage(text: String): String {
        for (ch in text) {
            val block = Character.UnicodeBlock.of(ch)
            if (block == Character.UnicodeBlock.DEVANAGARI) return "Hindi"
            if (block == Character.UnicodeBlock.ARABIC) return "Urdu"
        }
        return "English"
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
