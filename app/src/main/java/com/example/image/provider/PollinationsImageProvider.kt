package com.example.image.provider

import android.content.Context
import android.util.Log
import com.example.data.SnowPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PollinationsImageProvider(
    private val context: Context
) : ImageGenerationProvider {

    override val id: String = SnowPreferences.IMAGE_PROVIDER_POLLINATIONS
    override val displayName: String = "Pollinations AI (Free / Flux)"
    override val defaultModel: String = "flux"
    override val requiresApiKey: Boolean = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun supportsImageGeneration(modelName: String, endpointUrl: String, apiKey: String): Boolean {
        return true
    }

    override suspend fun generateImage(
        prompt: String,
        modelName: String,
        endpointUrl: String,
        apiKey: String,
        aspectRatio: String
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val (width, height) = when (aspectRatio) {
            "16:9" -> 1280 to 720
            "9:16" -> 720 to 1280
            "4:3" -> 1024 to 768
            "3:4" -> 768 to 1024
            else -> 1024 to 1024
        }

        val effectiveModel = if (modelName.isNotBlank()) modelName else defaultModel
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        val baseUrl = if (endpointUrl.isNotBlank() && endpointUrl.startsWith("http")) {
            endpointUrl.trimEnd('/')
        } else {
            "https://image.pollinations.ai/prompt"
        }

        val seed = (System.currentTimeMillis() % 100000).toInt()
        val requestUrl = "$baseUrl/$encodedPrompt?model=$effectiveModel&width=$width&height=$height&seed=$seed&nologo=true"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        val call = httpClient.newCall(request)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "Pollinations image generation failed (HTTP ${response.code}): ${response.message}",
                    promptUsed = prompt
                )
            }

            val body = response.body
                ?: return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "Empty response received from image generator",
                    promptUsed = prompt
                )

            // Save image stream to app internal cache
            val outputDir = File(context.cacheDir, "snow_generated_images").apply { mkdirs() }
            val outputFile = File(outputDir, "snow_img_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { fos ->
                body.byteStream().use { input ->
                    input.copyTo(fos)
                }
            }

            Log.i("PollinationsImage", "Image successfully generated and saved to: ${outputFile.absolutePath}")
            ImageGenerationResult(
                isSuccess = true,
                imageUrl = requestUrl,
                localFilePath = outputFile.absolutePath,
                promptUsed = prompt
            )
        } catch (e: Exception) {
            Log.e("PollinationsImage", "Error generating image: ${e.message}", e)
            ImageGenerationResult(
                isSuccess = false,
                errorMessage = "Failed to generate image: ${e.localizedMessage ?: e.javaClass.simpleName}",
                promptUsed = prompt
            )
        }
    }
}
