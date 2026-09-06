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

class AnthropicAIProvider : AIProvider {

    override val id: String = "ANTHROPIC"
    override val displayName: String = "Anthropic Claude"
    override val requiresApiKey: Boolean = true

    override fun getCapabilities(modelName: String): Set<ModelCapability> = setOf(
        ModelCapability.TEXT,
        ModelCapability.VISION,
        ModelCapability.TOOL_CALLING,
        ModelCapability.CODE,
        ModelCapability.REASONING
    )

    override suspend fun listAvailableModels(): List<ModelInfo> = listOf(
        ModelInfo("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.TOOL_CALLING, ModelCapability.REASONING, ModelCapability.CODE), "Hybrid reasoning and coding model"),
        ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.TOOL_CALLING, ModelCapability.CODE), "Industry-leading intelligence and vision"),
        ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.TOOL_CALLING, ModelCapability.CODE), "Fast and lightweight model")
    )

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
                spokenText = "Please enter your Anthropic API key in Settings.",
                error = "API key missing"
            )
        }

        val effectiveModel = if (modelName.isNotBlank()) modelName else "claude-3-5-haiku-20241022"

        try {
            val rootJson = JSONObject()
            rootJson.put("model", effectiveModel)
            rootJson.put("max_tokens", 800)

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
                append("When executing a tool, output this JSON line on its own line:\n")
                append("""[TOOL_CALL:{"tool":"tool_name","arguments":{"param1":"val1"}}]""")
            }
            rootJson.put("system", toolsPrompt)

            val messages = JSONArray()
            for (msg in conversationHistory.takeLast(6)) {
                val role = if (msg.sender.equals("user", ignoreCase = true)) "user" else "assistant"
                messages.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                })
            }

            val userContent = buildString {
                append(prompt)
                if (!toolOutputsContext.isNullOrBlank()) {
                    append("\n\n[OBSERVED TOOL RESULTS]:\n")
                    append(toolOutputsContext)
                    append("\nProceed with next step or provide final spoken answer.")
                }
            }

            val currentTurn = JSONObject().apply {
                put("role", "user")
                if (imageBitmap != null) {
                    val contentArr = JSONArray()
                    val base64 = bitmapToBase64(imageBitmap)
                    contentArr.put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", base64)
                        })
                    })
                    contentArr.put(JSONObject().apply {
                        put("type", "text")
                        put("text", userContent)
                    })
                    put("content", contentArr)
                } else {
                    put("content", userContent)
                }
            }
            messages.put(currentTurn)
            rootJson.put("messages", messages)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(rootJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val safeError = when (response.code) {
                    401 -> "Anthropic API key is invalid."
                    429 -> "Anthropic rate limit reached (429)."
                    else -> "Anthropic API error ${response.code}"
                }
                return@withContext ProviderTurnResult(
                    spokenText = safeError,
                    isQuotaExceeded = response.code == 429,
                    error = safeError
                )
            }

            val json = JSONObject(raw)
            val contentArr = json.optJSONArray("content")
            val text = contentArr?.optJSONObject(0)?.optString("text", "") ?: ""

            val parsed = parseToolCallsAndSpoken(text)
            parsed.copy(modelUsed = effectiveModel)
        } catch (e: Exception) {
            Log.e("AnthropicAIProvider", "Anthropic request error", e)
            ProviderTurnResult(spokenText = "Failed to connect to Claude.", error = e.message)
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
        val targetModel = if (modelName.isNotBlank()) modelName else "claude-3-5-haiku-20241022"

        val body = JSONObject().apply {
            put("model", targetModel)
            put("max_tokens", 5)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            }
            put("messages", messages)
        }.toString()

        try {
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                ConnectionTestResult(true, "Connected successfully ($latency ms)", latency)
            } else {
                val errorMsg = when (response.code) {
                    401 -> "Invalid Anthropic API key"
                    429 -> "Anthropic quota/rate limit"
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
                Log.w("AnthropicAIProvider", "Failed to parse tool call: ${match.value}")
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
