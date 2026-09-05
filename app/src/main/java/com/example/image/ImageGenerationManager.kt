package com.example.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.SnowPreferences
import com.example.image.provider.CustomRestImageProvider
import com.example.image.provider.GeminiImagenProvider
import com.example.image.provider.ImageGenerationProvider
import com.example.image.provider.ImageGenerationResult
import com.example.image.provider.OllamaImageProvider
import com.example.image.provider.OpenAIDalleProvider
import com.example.image.provider.PollinationsImageProvider
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ImageGenerationManager(
    private val context: Context,
    private val preferences: SnowPreferences
) {

    private val providers = mapOf<String, ImageGenerationProvider>(
        SnowPreferences.IMAGE_PROVIDER_POLLINATIONS to PollinationsImageProvider(context),
        SnowPreferences.IMAGE_PROVIDER_GEMINI_IMAGEN to GeminiImagenProvider(context),
        SnowPreferences.IMAGE_PROVIDER_OPENAI_DALLE to OpenAIDalleProvider(context),
        SnowPreferences.IMAGE_PROVIDER_OLLAMA to OllamaImageProvider(context),
        SnowPreferences.IMAGE_PROVIDER_CUSTOM_REST to CustomRestImageProvider(context)
    )

    var currentActiveJob: Job? = null

    fun getAvailableProviders(): List<ImageGenerationProvider> = providers.values.toList()

    fun getActiveProvider(): ImageGenerationProvider {
        val selectedId = preferences.imageGenerationProvider
        return providers[selectedId] ?: providers[SnowPreferences.IMAGE_PROVIDER_POLLINATIONS]!!
    }

    /**
     * Determines whether the user input is an explicit image generation request.
     */
    fun isImageGenerationRequest(text: String): Boolean {
        val t = text.lowercase().trim()
        val keywords = listOf(
            "image bana", "image generate", "tasweer bana", "tasveer bana",
            "photo bana", "pic bana", "picture bana", "chitra bana",
            "generate image", "create image", "create an image", "generate an image",
            "draw an image", "draw a picture", "make an image", "make a picture",
            "draw me", "paint a", "generate a picture", "illustration of"
        )
        return keywords.any { t.contains(it) }
    }

    /**
     * Cleans up conversational request fluff to leave a crisp image prompt.
     */
    fun extractImagePrompt(rawText: String): String {
        var clean = rawText.trim()
        val removePatterns = listOf(
            "(?i)^(snow\\s+)?(meri\\s+)?ek\\s+",
            "(?i)^(snow\\s+)?(please\\s+)?(create|generate|draw|make|paint)\\s+(an?\\s+)?(image|picture|photo|illustration)\\s+(of\\s+)?",
            "(?i)^(snow\\s+)?(mujhe\\s+)?(ek\\s+)?",
            "(?i)\\s*(ki\\s+)?(image|tasweer|tasveer|photo|pic|picture)\\s*(banao|bana do|generate karo|generate kar do|create karo|chahiye)\\s*$",
            "(?i)\\s*(image|picture|photo)\\s*(generate|create|draw)\\s*(karo|kar do)?\\s*$"
        )
        for (pattern in removePatterns) {
            clean = clean.replace(Regex(pattern), "").trim()
        }
        return if (clean.length < 3) rawText.trim() else clean
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1"
    ): ImageGenerationResult {
        val provider = getActiveProvider()
        val model = preferences.imageGenerationModel
        val endpoint = preferences.imageGenerationEndpoint
        val apiKey = when (provider.id) {
            SnowPreferences.IMAGE_PROVIDER_GEMINI_IMAGEN -> {
                preferences.imageGenerationApiKey.ifBlank { preferences.effectiveApiKey }
            }
            SnowPreferences.IMAGE_PROVIDER_OPENAI_DALLE -> {
                preferences.imageGenerationApiKey.ifBlank { preferences.openAiApiKey }
            }
            SnowPreferences.IMAGE_PROVIDER_OLLAMA -> {
                preferences.imageGenerationApiKey.ifBlank { preferences.ollamaApiKey }
            }
            else -> preferences.imageGenerationApiKey
        }

        Log.i("ImageGenManager", "Executing image generation with provider ${provider.displayName}, model: $model, prompt: $prompt")
        return provider.generateImage(
            prompt = prompt,
            modelName = model,
            endpointUrl = endpoint,
            apiKey = apiKey,
            aspectRatio = aspectRatio
        )
    }

    /**
     * Saves a generated image file to the device's public MediaStore Pictures gallery.
     */
    fun saveImageToGallery(localFilePath: String): Boolean {
        try {
            val sourceFile = File(localFilePath)
            if (!sourceFile.exists()) return false

            val filename = "Snow_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Snow AI")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

            resolver.openOutputStream(imageUri).use { out ->
                if (out == null) return false
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            Log.i("ImageGenManager", "Saved image to gallery at $imageUri")
            return true
        } catch (e: Exception) {
            Log.e("ImageGenManager", "Error saving image to gallery", e)
            return false
        }
    }

    /**
     * Shares image via standard Android Intent or targets specific app (e.g. WhatsApp).
     */
    fun shareImage(localFilePath: String, caption: String = "Created with Snow AI", targetPackage: String? = null): Boolean {
        try {
            val file = File(localFilePath)
            if (!file.exists()) return false

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!targetPackage.isNullOrBlank()) {
                    setPackage(targetPackage)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (targetPackage.isNullOrBlank()) {
                val chooser = Intent.createChooser(shareIntent, "Share Image").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } else {
                context.startActivity(shareIntent)
            }
            return true
        } catch (e: Exception) {
            Log.e("ImageGenManager", "Error sharing image", e)
            return false
        }
    }
}
