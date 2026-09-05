package com.example.vision

import android.graphics.Bitmap

data class VisionAnalysisResult(
    val description: String,
    val detectedText: String? = null,
    val identifiedObjects: List<String> = emptyList(),
    val error: String? = null
)

interface VisionProvider {
    val id: String
    val name: String
    suspend fun analyzeImage(
        bitmap: Bitmap,
        userPrompt: String = "Describe what you see in detail."
    ): VisionAnalysisResult
}
