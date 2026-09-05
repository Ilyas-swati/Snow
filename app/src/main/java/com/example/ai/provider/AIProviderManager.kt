package com.example.ai.provider

import android.graphics.Bitmap
import android.util.Log
import com.example.agent.AgentTool
import com.example.data.SnowPreferences
import com.example.data.model.ChatMessage

class AIProviderManager(private val preferences: SnowPreferences) {

    val geminiProvider = GeminiAIProvider()
    val openAiProvider = OpenAIAIProvider()
    val anthropicProvider = AnthropicAIProvider()
    val customRestProvider = CustomRestAIProvider { preferences.customRestEndpoint }

    private val providers = listOf(geminiProvider, openAiProvider, anthropicProvider, customRestProvider)

    fun getProvider(id: String): AIProvider {
        return providers.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: geminiProvider
    }

    fun getActiveProvider(): AIProvider {
        return getProvider(preferences.activeAiProvider)
    }

    fun getFallbackProvider(): AIProvider? {
        val fallbackId = preferences.fallbackAiProvider
        if (fallbackId == SnowPreferences.PROVIDER_NONE || fallbackId == preferences.activeAiProvider) {
            return null
        }
        return getProvider(fallbackId)
    }

    private fun getApiKeyFor(provider: AIProvider): String {
        return when (provider.id) {
            "GEMINI" -> preferences.effectiveApiKey
            "OPENAI" -> preferences.openAiApiKey
            "ANTHROPIC" -> preferences.anthropicApiKey
            "CUSTOM_REST" -> preferences.customRestApiKey
            else -> ""
        }
    }

    private fun getModelFor(provider: AIProvider): String {
        return when (provider.id) {
            "GEMINI" -> preferences.apiEndpointModel
            "OPENAI" -> preferences.openAiModel
            "ANTHROPIC" -> preferences.anthropicModel
            "CUSTOM_REST" -> preferences.customRestModel
            else -> ""
        }
    }

    suspend fun executeAgentTurn(
        prompt: String,
        systemInstruction: String,
        tools: List<AgentTool>,
        conversationHistory: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null,
        toolOutputsContext: String? = null,
        onStatusUpdate: (String) -> Unit = {}
    ): ProviderTurnResult {
        val primary = getActiveProvider()
        val primaryKey = getApiKeyFor(primary)
        val primaryModel = getModelFor(primary)

        onStatusUpdate("Contacting ${primary.displayName}…")
        val primaryResult = primary.generateAgentTurn(
            prompt = prompt,
            apiKey = primaryKey,
            modelName = primaryModel,
            systemInstruction = systemInstruction,
            tools = tools,
            conversationHistory = conversationHistory,
            imageBitmap = imageBitmap,
            toolOutputsContext = toolOutputsContext
        )

        // If primary succeeded or user did not configure fallback, return
        if (primaryResult.error == null && !primaryResult.isQuotaExceeded) {
            return primaryResult
        }

        // Try Fallback Provider if configured
        val fallback = getFallbackProvider()
        if (fallback != null) {
            val fallbackKey = getApiKeyFor(fallback)
            val fallbackModel = getModelFor(fallback)
            Log.w("AIProviderManager", "Primary provider ${primary.displayName} failed (${primaryResult.error}). Engaging fallback ${fallback.displayName}...")
            onStatusUpdate("Failing over to ${fallback.displayName}…")

            val fallbackResult = fallback.generateAgentTurn(
                prompt = prompt,
                apiKey = fallbackKey,
                modelName = fallbackModel,
                systemInstruction = systemInstruction,
                tools = tools,
                conversationHistory = conversationHistory,
                imageBitmap = imageBitmap,
                toolOutputsContext = toolOutputsContext
            )

            if (fallbackResult.error == null) {
                return fallbackResult
            }
        }

        return primaryResult
    }

    suspend fun testConnection(providerId: String): ConnectionTestResult {
        val provider = getProvider(providerId)
        val key = getApiKeyFor(provider)
        val model = getModelFor(provider)
        val customEndpoint = if (provider.id == "CUSTOM_REST") preferences.customRestEndpoint else ""
        return provider.testConnection(key, model, customEndpoint)
    }
}
