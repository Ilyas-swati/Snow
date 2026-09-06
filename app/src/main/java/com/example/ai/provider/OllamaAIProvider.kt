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
    val supportsCode: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsTools: Boolean = true,
    val sizeBytes: Long = 0,
    val modifiedAt: String = "",
    val format: String = "",
    val family: String = ""
) {
    val formattedSize: String
        get() = when {
            sizeBytes <= 0 -> ""
            sizeBytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", sizeBytes.toDouble() / (1024 * 1024 * 1024))
            sizeBytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.0f MB", sizeBytes.toDouble() / (1024 * 1024))
            else -> "$sizeBytes B"
        }

    val formattedModified: String
        get() {
            if (modifiedAt.isBlank()) return ""
            return try {
                if (modifiedAt.length >= 10) modifiedAt.substring(0, 10) else modifiedAt
            } catch (e: Exception) {
                ""
            }
        }
}

class OllamaAIProvider(
    private val getBaseUrl: () -> String,
    private val getApiKey: () -> String = { "" },
    private val getTemperature: () -> Float = { 0.7f }
) : AIProvider {

    override val id: String = "OLLAMA"
    override val displayName: String = "Ollama (Local / Remote)"
    override val requiresApiKey: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun getCapabilities(modelName: String): Set<ModelCapability> {
        val lower = modelName.lowercase()
        val caps = mutableSetOf(ModelCapability.TEXT)

        if (lower.contains("vision") || lower.contains("llava") ||
            lower.contains("minicpm-v") || lower.contains("moondream") ||
            lower.contains("bakllava") || lower.contains("qwen2-vl") ||
            lower.contains("mllama") || lower.contains("llama3.2-vision")
        ) {
            caps.add(ModelCapability.VISION)
        }

        if (lower.contains("deepseek-r1") || lower.contains("qwq")) {
            caps.add(ModelCapability.REASONING)
        }

        if (lower.contains("code") || lower.contains("starcoder")) {
            caps.add(ModelCapability.CODE)
        }

        // Native or structured tool calling support
        caps.add(ModelCapability.TOOL_CALLING)

        return caps
    }

    override suspend fun listAvailableModels(): List<ModelInfo> {
        val models = listModels()
        return models.map { m ->
            val caps = mutableSetOf(ModelCapability.TEXT)
            if (m.supportsVision) caps.add(ModelCapability.VISION)
            caps.add(ModelCapability.TOOL_CALLING)
            ModelInfo(
                id = m.name,
                displayName = "${m.name} (${if (m.parameterSize.isNotBlank()) m.parameterSize else "local"})",
                capabilities = caps,
                description = "Installed on Ollama server"
            )
        }
    }


    /**
     * Queries /api/tags from Ollama to retrieve all installed models dynamically.
     * Supports unlimited models (10, 100, 300+) with comprehensive capability detection.
     */
    suspend fun listModels(customUrl: String? = null, customApiKey: String? = null): List<OllamaModelInfo> = withContext(Dispatchers.IO) {
        val baseUrl = (customUrl ?: getBaseUrl()).trim().trimEnd('/')
        val url = "$baseUrl/api/tags"
        try {
            val reqBuilder = Request.Builder().url(url).get()
            val token = if (!customApiKey.isNullOrBlank()) customApiKey else getApiKey()
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
                    if (name.isBlank()) continue

                    val size = mObj.optLong("size", 0)
                    val modifiedAt = mObj.optString("modified_at", "")
                    val details = mObj.optJSONObject("details")
                    val paramSize = details?.optString("parameter_size", "") ?: ""
                    val format = details?.optString("format", "") ?: ""
                    val family = details?.optString("family", "") ?: ""
                    val families = details?.optJSONArray("families")

                    val lowerName = name.lowercase()

                    // Vision capability detection
                    var isVision = lowerName.contains("vision") || lowerName.contains("llava") ||
                            lowerName.contains("minicpm-v") || lowerName.contains("moondream") ||
                            lowerName.contains("bakllava") || lowerName.contains("qwen2-vl") ||
                            lowerName.contains("mllama") || lowerName.contains("llama3.2-vision")
                    if (!isVision && families != null) {
                        for (f in 0 until families.length()) {
                            val fam = families.optString(f, "").lowercase()
                            if (fam.contains("clip") || fam.contains("mllama") || fam.contains("vision")) {
                                isVision = true
                                break
                            }
                        }
                    }

                    // Code capability detection
                    val isCode = lowerName.contains("code") || lowerName.contains("coder") ||
                            lowerName.contains("starcoder") || lowerName.contains("deepseek-coder") ||
                            lowerName.contains("qwen2.5-coder") || lowerName.contains("codellama")

                    // Reasoning capability detection
                    val isReasoning = lowerName.contains("r1") || lowerName.contains("deepseek-r1") ||
                            lowerName.contains("qwq") || lowerName.contains("reasoning")

                    // Tool calling capability detection (all general chat/instruct models, except pure embeddings)
                    val isEmbedding = lowerName.contains("embed") || lowerName.contains("nomic") ||
                            lowerName.contains("bge") || lowerName.contains("minilm")
                    val isTools = !isEmbedding

                    result.add(
                        OllamaModelInfo(
                            name = name,
                            parameterSize = paramSize,
                            supportsVision = isVision,
                            supportsCode = isCode,
                            supportsReasoning = isReasoning,
                            supportsTools = isTools,
                            sizeBytes = size,
                            modifiedAt = modifiedAt,
                            format = format,
                            family = family
                        )
                    )
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
            val supportsVision = getCapabilities(effectiveModel).contains(ModelCapability.VISION)
            if (imageBitmap != null && supportsVision) {
                val imagesArray = JSONArray()
                val stream = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                imagesArray.put(base64)
                userMsgObj.put("images", imagesArray)
            } else if (imageBitmap != null && !supportsVision) {
                userMsgObj.put("content", userContent + "\n\n[Note: Active model '$effectiveModel' is text-only. Visual inspection is not supported by this model.]")
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
        val rawUrl = (if (customEndpoint.isNotBlank()) customEndpoint else getBaseUrl()).trim()
        val baseUrl = rawUrl.trimEnd('/')
        val url = "$baseUrl/api/tags"

        // Android loopback address diagnostic helper
        val isLoopback = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")

        try {
            val reqBuilder = Request.Builder().url(url).get()
            val token = if (apiKey.isNotBlank()) apiKey else getApiKey()
            if (token.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val code = response.code
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val modelsArray = json.optJSONArray("models")
                    val count = modelsArray?.length() ?: 0
                    val discovered = mutableListOf<String>()
                    if (modelsArray != null) {
                        for (i in 0 until modelsArray.length()) {
                            val mName = modelsArray.getJSONObject(i).optString("name", "")
                            if (mName.isNotBlank()) discovered.add(mName)
                        }
                    }

                    ConnectionTestResult(
                        isSuccess = true,
                        message = "Connected to Ollama! Found $count model(s).",
                        latencyMs = latency,
                        reachable = true,
                        httpStatusCode = code,
                        discoveredModels = discovered
                    )
                } else {
                    ConnectionTestResult(
                        isSuccess = false,
                        message = "Ollama server reached but returned HTTP $code.",
                        latencyMs = latency,
                        reachable = true,
                        httpStatusCode = code
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val msg = buildString {
                append("Connection failed: ")
                val err = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                append(err)
                if (isLoopback) {
                    append("\n\nNote: 'localhost' or '127.0.0.1' points to this Android device. If Ollama runs on your computer, use 'http://10.0.2.2:11434' in the emulator or your PC's local Wi-Fi IP (e.g. 'http://192.168.1.50:11434'). Also ensure OLLAMA_HOST=0.0.0.0 is set on the host.")
                }
            }
            ConnectionTestResult(
                isSuccess = false,
                message = msg,
                latencyMs = latency,
                reachable = false,
                httpStatusCode = 0
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
            return provider.listModels(baseUrl, apiKey).map { it.name }
        }

        suspend fun fetchDetailedModels(baseUrl: String, apiKey: String = ""): List<OllamaModelInfo> {
            val provider = OllamaAIProvider({ baseUrl }, { apiKey })
            return provider.listModels(baseUrl, apiKey)
        }
    }
}
