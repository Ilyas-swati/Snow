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

class OpenAIDalleProvider(
    private val context: Context
) : ImageGenerationProvider {

    override val id: String = SnowPreferences.IMAGE_PROVIDER_OPENAI_DALLE
    override val displayName: String = "OpenAI DALL-E 3"
    override val defaultModel: String = "dall-e-3"
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
                errorMessage = "OpenAI API key is missing. Please enter your API key in Settings.",
                promptUsed = prompt
            )
        }

        val effectiveModel = if (modelName.isNotBlank()) modelName else defaultModel
        val endpoint = if (endpointUrl.isNotBlank() && endpointUrl.startsWith("http")) {
            endpointUrl
        } else {
            "https://api.openai.com/v1/images/generations"
        }

        val size = when (aspectRatio) {
            "16:9" -> "1792x1024"
            "9:16" -> "1024x1792"
            else -> "1024x1024"
        }

        val requestJson = JSONObject().apply {
            put("model", effectiveModel)
            put("prompt", prompt)
            put("n", 1)
            put("size", size)
            put("response_format", "b64_json")
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
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
                    errorMessage = "OpenAI DALL-E error: $errorMsg",
                    promptUsed = prompt
                )
            }

            val json = JSONObject(responseBody)
            val dataArray = json.optJSONArray("data")
            if (dataArray == null || dataArray.length() == 0) {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "No image data received from DALL-E.",
                    promptUsed = prompt
                )
            }

            val item = dataArray.getJSONObject(0)
            val b64 = item.optString("b64_json")
            val url = item.optString("url")

            val outputDir = File(context.cacheDir, "snow_generated_images").apply { mkdirs() }
            val outputFile = File(outputDir, "snow_dalle_${System.currentTimeMillis()}.png")

            if (b64.isNotBlank()) {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                FileOutputStream(outputFile).use { it.write(bytes) }
            } else if (url.isNotBlank()) {
                val imgReq = Request.Builder().url(url).get().build()
                val imgResp = httpClient.newCall(imgReq).execute()
                imgResp.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                }
            } else {
                return@withContext ImageGenerationResult(
                    isSuccess = false,
                    errorMessage = "No valid image payload in DALL-E response.",
                    promptUsed = prompt
                )
            }

            Log.i("OpenAIDalle", "Saved DALL-E image to: ${outputFile.absolutePath}")
            ImageGenerationResult(
                isSuccess = true,
                localFilePath = outputFile.absolutePath,
                promptUsed = prompt
            )
        } catch (e: Exception) {
            Log.e("OpenAIDalle", "Exception during DALL-E generation", e)
            ImageGenerationResult(
                isSuccess = false,
                errorMessage = "DALL-E generation error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                promptUsed = prompt
            )
        }
    }
}
