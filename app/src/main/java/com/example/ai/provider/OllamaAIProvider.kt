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

data class OllamaModelInfo(
    val name: String,
    val parameterSize: String = "",
    val supportsVision: Boolean = false,
    val sizeBytes: Long = 0
)

class OllamaAIProvider(
    private val getBaseUrl: () -> String,
    private val getApiKey: () -> String = { "" },
    private val getTemperature: () -> Float = { 0.7f }
) : AIProvider {

    override val id: String = "OLLAMA"
    override val displayName: String = "Ollama (Local / Remote)"
    override val requiresApiKey: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Queries /api/tags from Ollama to retrieve all installed models.
     */
    suspend fun listModels(customUrl: String? = null): List<OllamaModelInfo> = withContext(Dispatchers.IO) {
        val baseUrl = (customUrl ?: getBaseUrl()).trim().trimEnd('/')
        val url = "$baseUrl/api/tags"
        try {
            val reqBuilder = Request.Builder().url(url).get()
            val token = getApiKey()
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("OllamaProvider", "listModels failed: HTTP ${response.code}")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: JSONArray()
                val result = mutableListOf<OllamaModelInfo>()

                for (i in 0 until modelsArray.length()) {
                    val mObj = modelsArray.getJSONObject(i)
                    val name = mObj.optString("name", "")
                    val size = mObj.optLong("size", 0)
                    val details = mObj.optJSONObject("details")
                    val paramSize = details?.optString("parameter_size", "") ?: ""
                    val families = details?.optJSONArray("families")

                    var isVision = false
                    val lowerName = name.lowercase()
                    if (lowerName.contains("vision") || lowerName.contains("llava") ||
                        lowerName.contains("minicpm-v") || lowerName.contains("moondream") ||
                        lowerName.contains("bakllava") || lowerName.contains("qwen2-vl") ||
                        lowerName.contains("mllama")
                    ) {
                        isVision = true
                    }
                    if (families != null) {
                        for (f in 0 until families.length()) {
                            val fam = families.optString(f, "").lowercase()
                            if (fam.contains("clip") || fam.contains("mllama") || fam.contains("vision")) {
                                isVision = true
                            }
                        }
                    }

                    if (name.isNotBlank()) {
                        result.add(OllamaModelInfo(name, paramSize, isVision, size))
                    }
                }
                result
            }
        } catch (e: Exception) {
            Log.e("OllamaProvider", "Error listing Ollama models from $url", e)
            emptyList()
        }
    }

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
        val baseUrl = getBaseUrl().trim().trimEnd('/')
        val effectiveModel = if (modelName.isNotBlank()) modelName else "llama3.2"
        val chatUrl = "$baseUrl/api/chat"

        try {
            val root = JSONObject()
            root.put("model", effectiveModel)
            root.put("stream", false)

            val options = JSONObject()
            options.put("temperature", getTemperature().toDouble())
            root.put("options", options)

            val messages = JSONArray()

            // System prompt with tool definitions
            val systemContent = buildString {
                append(systemInstruction)
                append("\n\nAVAILABLE AGENT TOOLS & ACTIONS:\n")
                tools.forEach { tool ->
                    append("- Tool: ${tool.name}\n")
                    append("  Description: ${tool.description}\n")
                    if (tool.parameters.isNotEmpty()) {
                        append("  Parameters: " + tool.parameters.joinToString(", ") { "${it.name} (${it.type}): ${it.description}" } + "\n")
                    }
                }
                append("\nSTRUCTURED TOOL INVOCATION FORMAT:\n")
                append("When you need to execute an Android action or tool, output this exact token on its own line:\n")
                append("""[TOOL_CALL:{"tool":"tool_name","arguments":{"param_key":"param_value"}}]""")
                append("\nYou can execute multiple tools sequentially. If no tool is needed, respond with a friendly, natural spoken explanation.")
            }

            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemContent)
            })

            // Conversation history (last 8 messages)
            for (msg in conversationHistory.takeLast(8)) {
                val role = if (msg.sender.equals("user", ignoreCase = true)) "user" else "assistant"
                messages.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                })
            }

            // Current turn user message
            val userContent = buildString {
                append(prompt)
                if (!toolOutputsContext.isNullOrBlank()) {
                    append("\n\n[OBSERVED ACTION RESULTS / SCREEN DATA]:\n")
                    append(toolOutputsContext)
                    append("\nNow continue with the next step or deliver the final natural response.")
                }
            }

            val userMsgObj = JSONObject()
            userMsgObj.put("role", "user")
            userMsgObj.put("content", userContent)

            // Optional image if supported
            if (imageBitmap != null) {
                val imagesArray = JSONArray()
                val stream = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                imagesArray.put(base64)
                userMsgObj.put("images", imagesArray)
            }
            messages.put(userMsgObj)

            root.put("messages", messages)

            val reqBuilder = Request.Builder()
                .url(chatUrl)
                .post(root.toString().toRequestBody(jsonMediaType))

            val token = if (apiKey.isNotBlank()) apiKey else getApiKey()
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext ProviderTurnResult(
                    spokenText = "Ollama connection error (HTTP ${response.code}). Check if server is running at $baseUrl.",
                    modelUsed = effectiveModel,
                    error = "HTTP ${response.code}: $responseBody"
                )
            }

            val resJson = JSONObject(responseBody)
            val msgObj = resJson.optJSONObject("message")
            val rawContent = msgObj?.optString("content", "") ?: ""

            // Parse tool calls
            val toolCalls = parseToolCalls(rawContent)
            val cleanSpokenText = cleanToolCallText(rawContent)

            val detectedLang = detectLanguage(prompt + " " + cleanSpokenText)

            ProviderTurnResult(
                spokenText = cleanSpokenText.ifBlank { "Action in progress…" },
                toolCalls = toolCalls,
                detectedLanguage = detectedLang,
                modelUsed = effectiveModel
            )
        } catch (e: Exception) {
            Log.w("OllamaProvider", "Call failed to $chatUrl: ${e.javaClass.simpleName} - ${e.message}")
            ProviderTurnResult(
                spokenText = "Could not reach Ollama at $baseUrl. Ensure Ollama is running and accessible.",
                modelUsed = effectiveModel,
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override suspend fun testConnection(
        apiKey: String,
        modelName: String,
        customEndpoint: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val baseUrl = (if (customEndpoint.isNotBlank()) customEndpoint else getBaseUrl()).trim().trimEnd('/')
        val url = "$baseUrl/api/tags"

        try {
            val reqBuilder = Request.Builder().url(url).get()
            val token = if (apiKey.isNotBlank()) apiKey else getApiKey()
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val count = json.optJSONArray("models")?.length() ?: 0
                    ConnectionTestResult(
                        isSuccess = true,
                        message = "Connected to Ollama! Found $count model(s).",
                        latencyMs = latency
                    )
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        message = "Ollama returned HTTP ${response.code}",
                        latencyMs = latency
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Failed to connect: ${e.localizedMessage ?: e.message}",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun parseToolCalls(content: String): List<ToolCallRequest> {
        val results = mutableListOf<ToolCallRequest>()
        val regex = Regex("""\[TOOL_CALL:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(content)

        for (match in matches) {
            try {
                val jsonStr = match.groupValues[1]
                val obj = JSONObject(jsonStr)
                val toolName = obj.optString("tool", "")
                val argsObj = obj.optJSONObject("arguments")
                val argsMap = mutableMapOf<String, String>()
                argsObj?.keys()?.forEach { k ->
                    argsMap[k] = argsObj.optString(k, "")
                }
                if (toolName.isNotBlank()) {
                    results.add(ToolCallRequest(toolName, argsMap))
                }
            } catch (e: Exception) {
                Log.w("OllamaProvider", "Failed to parse tool call: ${match.value}", e)
            }
        }
        return results
    }

    private fun cleanToolCallText(content: String): String {
        return content.replace(Regex("""\[TOOL_CALL:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    private fun detectLanguage(text: String): String {
        val hasUrduChars = text.any { it in '\u0600'..'\u06FF' }
        val hasHindiChars = text.any { it in '\u0900'..'\u097F' }
        return when {
            hasUrduChars -> "Urdu"
            hasHindiChars -> "Hindi"
            else -> "English"
        }
    }

    companion object {
        suspend fun fetchInstalledModels(baseUrl: String, apiKey: String = ""): List<String> {
            val provider = OllamaAIProvider({ baseUrl }, { apiKey })
            return provider.listModels().map { it.name }
        }
    }
}
