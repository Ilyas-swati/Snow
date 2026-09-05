package com.example.image.provider

/**
 * Result of an image generation operation.
 */
data class ImageGenerationResult(
    val isSuccess: Boolean,
    val imageUrl: String? = null,
    val localFilePath: String? = null,
    val errorMessage: String? = null,
    val promptUsed: String = ""
)

/**
 * Modular interface for AI Image Generation providers.
 */
interface ImageGenerationProvider {
    val id: String
    val displayName: String
    val defaultModel: String
    val requiresApiKey: Boolean

    suspend fun supportsImageGeneration(modelName: String, endpointUrl: String, apiKey: String): Boolean

    suspend fun generateImage(
        prompt: String,
        modelName: String,
        endpointUrl: String,
        apiKey: String,
        aspectRatio: String = "1:1"
    ): ImageGenerationResult
}
