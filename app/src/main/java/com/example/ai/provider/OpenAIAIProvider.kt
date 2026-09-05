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

class OpenAIAIProvider(
    private val endpointUrl: String = "https://api.openai.com/v1/chat/completions"
) : AIProvider {

    override val id: String = "OPENAI"
    override val displayName: String = "OpenAI (GPT-4o / Compatible)"
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
        if (apiKey.isBlank()) {
            return@withContext ProviderTurnResult(
                spokenText = "Please enter your OpenAI API key in Settings.",
                error = "API key missing"
            )
        }

        val effectiveModel = if (modelName.isNotBlank()) modelName else "gpt-4o-mini"

        try {
            val rootJson = JSONObject()
            rootJson.put("model", effectiveModel)

            val messages = JSONArray()

            // System Message
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
                append("When executing a tool, output this JSON line:\n")
                append("""[TOOL_CALL:{"tool":"tool_name","arguments":{"param1":"val1"}}]""")
                append("\nYou can execute multiple tools across steps if needed.")
            }
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", toolsPrompt)
            })

            // History
            for (msg in conversationHistory.takeLast(6)) {
                val role = if (msg.sender.equals("user", ignoreCase = true)) "user" else "assistant"
                messages.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                })
            }

            // Current turn
            val userContent = buildString {
                append(prompt)
                if (!toolOutputsContext.isNullOrBlank()) {
                    append("\n\n[OBSERVED TOOL RESULTS]:\n")
                    append(toolOutputsContext)
                    append("\nNow continue with next tool or state final spoken answer.")
                }
            }

            if (imageBitmap != null) {
                val contentArray = JSONArray()
                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", userContent)
                })
                val base64 = bitmapToBase64(imageBitmap)
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64")
                    })
                })
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            } else {
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            rootJson.put("messages", messages)
            rootJson.put("temperature", 0.7)

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(rootJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val safeError = when (response.code) {
                    401 -> "OpenAI API key is invalid or unauthorized."
                    429 -> "OpenAI rate limit or quota exceeded (429)."
                    500, 503 -> "OpenAI server error (${response.code})."
                    else -> "OpenAI API error ${response.code}"
                }
                return@withContext ProviderTurnResult(
                    spokenText = safeError,
                    isQuotaExceeded = response.code == 429,
                    error = safeError
                )
            }

            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices")
            val messageObj = choices?.optJSONObject(0)?.optJSONObject("message")
            val text = messageObj?.optString("content", "") ?: ""

            val parsed = parseToolCallsAndSpoken(text)
            parsed.copy(modelUsed = effectiveModel)
        } catch (e: Exception) {
            Log.e("OpenAIAIProvider", "Error in OpenAI request", e)
            ProviderTurnResult(
                spokenText = "Failed to connect to OpenAI.",
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
        val effectiveEndpoint = if (customEndpoint.isNotBlank()) customEndpoint else endpointUrl
        val targetModel = if (modelName.isNotBlank()) modelName else "gpt-4o-mini"

        val body = JSONObject().apply {
            put("model", targetModel)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            }
            put("messages", messages)
            put("max_tokens", 5)
        }.toString()

        try {
            val request = Request.Builder()
                .url(effectiveEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val raw = response.body?.string() ?: ""

            if (response.isSuccessful) {
                ConnectionTestResult(true, "Connected successfully ($latency ms)", latency)
            } else {
                val errorMsg = when (response.code) {
                    401 -> "Invalid OpenAI API key"
                    429 -> "Quota exceeded (429)"
                    404 -> "Model or endpoint not found"
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
                Log.w("OpenAIAIProvider", "Failed to parse tool call: ${match.value}")
            }
        }

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
