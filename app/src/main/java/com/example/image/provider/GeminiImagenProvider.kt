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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GeminiImagenProvider(
    private val context: Context
) : ImageGenerationProvider {

    override val id: String = SnowPreferences.IMAGE_PROVIDER_GEMINI_IMAGEN
    override val displayName: String = "Google Imagen 3"
    override val defaultModel: String = "imagen-3.0-generate-002"
    override val requiresApiKey: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun supportsImageGeneration(modelName: String, endpointUrl: String, apiKey: String): Boolean {
        return apiKey.isNotBlank()
    }

    override suspend fun generateImage(
        prompt: String,
        modelName: String,
        endpointUrl: String,
        apiKey: String,
        aspectRatio: String
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ImageGenerationResult(
                isSuccess = false,
                errorMessage = "Google Gemini API key is missing. Please enter your API key in Settings.",
                promptUsed = prompt
            )
        }

        val effectiveModel = if (modelName.isNotBlank()) modelName else defaultModel
        val baseUrl = if (endpointUrl.isNotBlank() && endpointUrl.startsWith("http")) {
            endpointUrl
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:predict?key=$apiKey"
        }

        val requestJson = JSONObject().apply {
            val instances = JSONArray().apply {
                put(JSONObject().apply {
                    put("prompt", prompt)
                })
            }
            put("instances", instances)
            put("parameters", JSONObject().apply {
                put("sampleCount", 1)
                put("aspectRatio", if (aspectRatio in listOf("1:1", "3:4", "4:3", "9:16", "16:9")) aspectRatio else "1:1")
                put("outputMimeType", "image/jpeg")
            })
        }

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody).optJSONObject("error")
                    errJson?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "Google Imagen failed: $errorMsg",
                    promptUsed = prompt
                )
            }

            val json = JSONObject(responseBody)
            val predictions = json.optJSONArray("predictions")
            if (predictions == null || predictions.length() == 0) {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "No image returned by Imagen. Response: $responseBody",
                    promptUsed = prompt
                )
            }

            val firstPrediction = predictions.getJSONObject(0)
            val base64Data = firstPrediction.optString("bytesBase64Encoded")
            if (base64Data.isBlank()) {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "Imagen response did not contain image data.",
                    promptUsed = prompt
                )
            }

            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val outputDir = File(context.cacheDir, "snow_generated_images").apply { mkdirs() }
            val outputFile = File(outputDir, "snow_imagen_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { fos ->
                fos.write(imageBytes)
            }

            Log.i("GeminiImagen", "Saved Imagen image to: ${outputFile.absolutePath}")
            ImageGenerationResult(
                isSuccess = true,
                localFilePath = outputFile.absolutePath,
                promptUsed = prompt
            )
        } catch (e: Exception) {
            Log.e("GeminiImagen", "Exception during Imagen generation", e)
            ImageGenerationResult(
                isSuccess = false,
                errorMessage = "Imagen generation error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                promptUsed = prompt
            )
        }
    }
}
