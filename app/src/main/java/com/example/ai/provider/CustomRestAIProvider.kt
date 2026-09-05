package com.example.ai.provider

import android.graphics.Bitmap
import com.example.agent.AgentTool
import com.example.data.model.ChatMessage

class CustomRestAIProvider(
    private val getEndpointUrl: () -> String
) : AIProvider {

    override val id: String = "CUSTOM_REST"
    override val displayName: String = "Custom REST / Local AI"
    override val requiresApiKey: Boolean = false

    override suspend fun generateAgentTurn(
        prompt: String,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        tools: List<AgentTool>,
        conversationHistory: List<ChatMessage>,
        imageBitmap: Bitmap?,
        toolOutputsContext: String?
    ): ProviderTurnResult {
        val endpoint = getEndpointUrl().trimEnd('/')
        val completionsUrl = if (endpoint.endsWith("/chat/completions")) endpoint else "$endpoint/chat/completions"
        val delegate = OpenAIAIProvider(completionsUrl)
        return delegate.generateAgentTurn(
            prompt, apiKey, modelName, systemInstruction, tools,
            conversationHistory, imageBitmap, toolOutputsContext
        )
    }

    override suspend fun testConnection(
        apiKey: String,
        modelName: String,
        customEndpoint: String
    ): ConnectionTestResult {
        val endpoint = (if (customEndpoint.isNotBlank()) customEndpoint else getEndpointUrl()).trimEnd('/')
        val completionsUrl = if (endpoint.endsWith("/chat/completions")) endpoint else "$endpoint/chat/completions"
        val delegate = OpenAIAIProvider(completionsUrl)
        return delegate.testConnection(apiKey, modelName, completionsUrl)
    }
}
