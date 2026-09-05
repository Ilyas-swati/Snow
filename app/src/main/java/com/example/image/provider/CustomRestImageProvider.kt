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

class CustomRestImageProvider(
    private val context: Context
) : ImageGenerationProvider {

    override val id: String = SnowPreferences.IMAGE_PROVIDER_CUSTOM_REST
    override val displayName: String = "Custom REST Image Server"
    override val defaultModel: String = "custom"
    override val requiresApiKey: Boolean = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun supportsImageGeneration(modelName: String, endpointUrl: String, apiKey: String): Boolean {
        return endpointUrl.isNotBlank() && endpointUrl.startsWith("http")
    }

    override suspend fun generateImage(
        prompt: String,
        modelName: String,
        endpointUrl: String,
        apiKey: String,
        aspectRatio: String
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        if (endpointUrl.isBlank() || !endpointUrl.startsWith("http")) {
            return@withContext ImageGenerationResult(
                isSuccess = false,
                errorMessage = "Custom image endpoint URL is missing or invalid. Please configure it in Settings.",
                promptUsed = prompt
            )
        }

        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("model", modelName.ifBlank { defaultModel })
            put("aspect_ratio", aspectRatio)
        }

        val request = Request.Builder()
            .url(endpointUrl)
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
                    errorMessage = "Custom REST endpoint returned HTTP ${response.code}: ${response.message}",
                    promptUsed = prompt
                )
            }

            // Try parsing json for base64 or url
            var base64Data: String? = null
            var directUrl: String? = null

            try {
                val json = JSONObject(responseBody)
                if (json.has("image_url")) directUrl = json.getString("image_url")
                else if (json.has("url")) directUrl = json.getString("url")
                else if (json.has("b64_json")) base64Data = json.getString("b64_json")
                else if (json.has("image")) base64Data = json.getString("image")
            } catch (e: Exception) {
                // responseBody might be direct raw image bytes if content type is image/*
            }

            val outputDir = File(context.cacheDir, "snow_generated_images").apply { mkdirs() }
            val outputFile = File(outputDir, "snow_custom_${System.currentTimeMillis()}.png")

            if (!base64Data.isNullOrBlank()) {
                val cleanB64 = base64Data.substringAfter("base64,")
                val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                FileOutputStream(outputFile).use { it.write(bytes) }
            } else if (!directUrl.isNullOrBlank()) {
                val imgReq = Request.Builder().url(directUrl).get().build()
                val imgResp = httpClient.newCall(imgReq).execute()
                imgResp.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                }
            } else {
                // If it's a binary response
                FileOutputStream(outputFile).use { it.write(responseBody.toByteArray()) }
            }

            ImageGenerationResult(
                isSuccess = true,
                localFilePath = outputFile.absolutePath,
                promptUsed = prompt
            )
        } catch (e: Exception) {
            Log.e("CustomRestImage", "Error calling custom image provider: ${e.message}")
            ImageGenerationResult(
                isSuccess = false,
                errorMessage = "Custom image API error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                promptUsed = prompt
            )
        }
    }
}
