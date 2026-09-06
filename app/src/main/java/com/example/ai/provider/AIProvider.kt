package com.example.ai.provider

import android.graphics.Bitmap
import com.example.agent.AgentTool
import com.example.data.model.ChatMessage

enum class ModelCapability {
    TEXT,
    VISION,
    TOOL_CALLING,
    REASONING,
    CODE,
    EMBEDDING,
    IMAGE_GENERATION
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT),
    val description: String = ""
)

data class ToolCallRequest(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap()
)

data class ProviderTurnResult(
    val spokenText: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val detectedLanguage: String = "English",
    val modelUsed: String = "",
    val isQuotaExceeded: Boolean = false,
    val error: String? = null
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long = 0,
    val reachable: Boolean = false,
    val httpStatusCode: Int = 0,
    val discoveredModels: List<String> = emptyList()
)

interface AIProvider {
    val id: String
    val displayName: String
    val requiresApiKey: Boolean

    fun getCapabilities(modelName: String): Set<ModelCapability> = setOf(ModelCapability.TEXT, ModelCapability.TOOL_CALLING)

    suspend fun listAvailableModels(): List<ModelInfo> = emptyList()

    suspend fun generateAgentTurn(
        prompt: String,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        tools: List<AgentTool>,
        conversationHistory: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null,
        toolOutputsContext: String? = null
    ): ProviderTurnResult

    suspend fun testConnection(
        apiKey: String,
        modelName: String,
        customEndpoint: String = ""
    ): ConnectionTestResult
}

