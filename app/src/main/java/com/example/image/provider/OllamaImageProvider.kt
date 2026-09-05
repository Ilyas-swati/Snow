package com.example.image.provider

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.SnowPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class OllamaImageProvider(
    private val context: Context
) : ImageGenerationProvider {

    override val id: String = SnowPreferences.IMAGE_PROVIDER_OLLAMA
    override val displayName: String = "Ollama / Local Image Model"
    override val defaultModel: String = "stable-diffusion"
    override val requiresApiKey: Boolean = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val imageModelKeywords = listOf("sd", "diffusion", "flux", "diffuser", "image", "art", "painter")

    override suspend fun supportsImageGeneration(modelName: String, endpointUrl: String, apiKey: String): Boolean {
        val lowerModel = modelName.lowercase()
        return imageModelKeywords.any { lowerModel.contains(it) }
    }

    override suspend fun generateImage(
        prompt: String,
        modelName: String,
        endpointUrl: String,
        apiKey: String,
        aspectRatio: String
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val effectiveModel = if (modelName.isNotBlank()) modelName else defaultModel
        val lowerModel = effectiveModel.lowercase()

        // Detect whether the configured model is a text model or actually an image model
        val isImageModel = imageModelKeywords.any { lowerModel.contains(it) }
        if (!isImageModel) {
            return@withContext ImageGenerationResult(
                isSuccess = false,
                errorMessage = "The configured Ollama model '$effectiveModel' is a text LLM, not an image generation model. To generate images, configure a diffusion model (e.g. 'stable-diffusion') or switch Image Provider to Pollinations (Free / Flux) or Imagen in Settings.",
                promptUsed = prompt
            )
        }

        val baseUrl = if (endpointUrl.isNotBlank() && endpointUrl.startsWith("http")) {
            endpointUrl.trimEnd('/')
        } else {
            "http://10.0.2.2:11434"
        }

        // Try local image diffusion endpoints (Ollama SD bridge or Automatic1111/SD WebUI API)
        val targetUrl = if (baseUrl.endsWith("/sdapi/v1/txt2img") || baseUrl.endsWith("/api/generate")) {
            baseUrl
        } else if (baseUrl.contains(":7860")) {
            "$baseUrl/sdapi/v1/txt2img"
        } else {
            "$baseUrl/api/generate"
        }

        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("model", effectiveModel)
            put("stream", false)
        }

        val request = Request.Builder()
            .url(targetUrl)
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "Ollama image model server error (HTTP ${response.code}): ${response.message}. Ensure your local diffusion model/server is running.",
                    promptUsed = prompt
                )
            }

            val json = JSONObject(responseBody)
            var base64Data = ""

            if (json.has("images")) {
                val images = json.getJSONArray("images")
                if (images.length() > 0) base64Data = images.getString(0)
            } else if (json.has("response")) {
                base64Data = json.getString("response")
            } else if (json.has("image")) {
                base64Data = json.getString("image")
            }

            if (base64Data.isBlank()) {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "Local image server did not return image data. Raw response: ${responseBody.take(200)}",
                    promptUsed = prompt
                )
            }

            val cleanBase64 = base64Data.substringAfter("base64,")
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            val outputDir = File(context.cacheDir, "snow_generated_images").apply { mkdirs() }
            val outputFile = File(outputDir, "snow_ollama_${System.currentTimeMillis()}.png")

            FileOutputStream(outputFile).use { it.write(imageBytes) }

            ImageGenerationResult(
                isSuccess = true,
                localFilePath = outputFile.absolutePath,
                promptUsed = prompt
            )
        } catch (e: Exception) {
            Log.e("OllamaImageProvider", "Call failed to $targetUrl: ${e.message}")
            ImageGenerationResult(
                isSuccess = false,
                errorMessage = "Could not connect to Ollama image endpoint at $targetUrl: ${e.localizedMessage ?: e.javaClass.simpleName}",
                promptUsed = prompt
            )
        }
    }
}
