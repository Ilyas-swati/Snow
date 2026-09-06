package com.example.ai.provider

import android.graphics.Bitmap
import android.util.Log
import com.example.agent.AgentTool
import com.example.data.SnowPreferences
import com.example.data.model.ChatMessage

class AIProviderManager(private val preferences: SnowPreferences) {

    val geminiProvider = GeminiAIProvider()
    val ollamaProvider = OllamaAIProvider(
        getBaseUrl = { preferences.ollamaBaseUrl },
        getApiKey = { preferences.ollamaApiKey },
        getTemperature = { preferences.ollamaTemperature }
    )
    val openAiProvider = OpenAIAIProvider()
    val anthropicProvider = AnthropicAIProvider()
    val customRestProvider = CustomRestAIProvider { preferences.customRestEndpoint }

    private val providers = listOf(geminiProvider, ollamaProvider, openAiProvider, anthropicProvider, customRestProvider)

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

    fun getActiveModelCapabilities(): Set<ModelCapability> {
        val primary = getActiveProvider()
        val model = getModelFor(primary)
        return primary.getCapabilities(model)
    }

    fun isVisionSupported(): Boolean {
        return getActiveModelCapabilities().contains(ModelCapability.VISION)
    }

    suspend fun listModelsForProvider(providerId: String): List<ModelInfo> {
        val prov = getProvider(providerId)
        return prov.listAvailableModels()
    }

    suspend fun listActiveProviderModels(): List<ModelInfo> {
        return getActiveProvider().listAvailableModels()
    }

    private fun getApiKeyFor(provider: AIProvider): String {
        return when (provider.id) {
            "GEMINI" -> preferences.effectiveApiKey
            "OLLAMA" -> preferences.ollamaApiKey
            "OPENAI" -> preferences.openAiApiKey
            "ANTHROPIC" -> preferences.anthropicApiKey
            "CUSTOM_REST" -> preferences.customRestApiKey
            else -> ""
        }
    }

    private fun getModelFor(provider: AIProvider): String {
        return when (provider.id) {
            "GEMINI" -> preferences.apiEndpointModel
            "OLLAMA" -> preferences.ollamaModel
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

        // Automatic fail-safe: If primary is not Gemini and is offline / unreachable, and Gemini is available
        if (primary.id != "GEMINI" && preferences.effectiveApiKey.isNotBlank()) {
            Log.w("AIProviderManager", "${primary.displayName} unreachable (${primaryResult.error}). Engaging automatic fallback to Gemini...")
            onStatusUpdate("Failing over to Google Gemini…")
            val geminiResult = geminiProvider.generateAgentTurn(
                prompt = prompt,
                apiKey = preferences.effectiveApiKey,
                modelName = preferences.apiEndpointModel,
                systemInstruction = systemInstruction,
                tools = tools,
                conversationHistory = conversationHistory,
                imageBitmap = imageBitmap,
                toolOutputsContext = toolOutputsContext
            )

            if (geminiResult.error == null) {
                return geminiResult
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
