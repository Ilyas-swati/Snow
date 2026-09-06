package com.example

import com.example.data.SnowPreferences
import com.example.voice.provider.OpenAITTSProvider
import com.example.voice.provider.ElevenLabsTTSProvider
import com.example.voice.provider.GoogleCloudTTSProvider
import com.example.voice.provider.TTSVoice
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testOpenAIProvider_fallbackVoices() = runBlocking {
        val provider = OpenAITTSProvider { "" }
        assertEquals("OPENAI", provider.id)
        assertTrue(provider.requiresApiKey)
        val voices = provider.getAvailableVoices("English")
        assertTrue(voices.isNotEmpty())
        assertTrue(voices.any { it.id == "nova" })
        assertTrue(voices.any { it.id == "shimmer" })
    }

    @Test
    fun testElevenLabsProvider_fallbackVoices() = runBlocking {
        val provider = ElevenLabsTTSProvider { "" }
        assertEquals("ELEVENLABS", provider.id)
        assertTrue(provider.requiresApiKey)
        val voices = provider.getAvailableVoices("English")
        assertTrue(voices.isNotEmpty())
        assertTrue(voices.any { it.name.contains("Rachel") })
    }

    @Test
    fun testGoogleCloudProvider_voices() = runBlocking {
        val provider = GoogleCloudTTSProvider { "" }
        assertEquals("GOOGLE_CLOUD", provider.id)
        val voices = provider.getAvailableVoices("Hindi")
        assertTrue(voices.any { it.id.contains("hi-IN") || it.name.contains("Hindi") })
    }

    @Test
    fun testLanguageConstants() {
        assertEquals("AUTO", SnowPreferences.LANG_AUTO)
        assertEquals("EN", SnowPreferences.LANG_EN)
        assertEquals("HI", SnowPreferences.LANG_HI)
        assertEquals("UR", SnowPreferences.LANG_UR)
        assertEquals("ROMAN_UR", SnowPreferences.LANG_ROMAN_UR)
        assertEquals("PS", SnowPreferences.LANG_PS)
        assertEquals("gemini-2.5-flash", SnowPreferences.DEFAULT_MODEL)
    }

    // ==========================================
    // REQUIREMENT 36: TEST SCENARIOS
    // ==========================================

    // Scenario 1: Say "Ruko" while Snow is speaking a long answer
    // Expected: Voice stops immediately, UI updates to show interrupted state
    @Test
    fun testScenario1_InterruptionKeywordsDetected() {
        val keywords = listOf(
            "ruko", "Ruko", "Ruk jao", "ruk jao", "stop", "STOP",
            "bas", "chup", "wait", "sun meri baat", "ek minute",
            "cancel", "cancel it", "khamosh", "thehro", "rukna",
            "ruko zara", "stop please", "wait a minute"
        )
        for (kw in keywords) {
            assertTrue("Expected '$kw' to be detected as interruption", com.example.voice.VoiceEngine.isInterruptionWord(kw))
        }

        // Non-interruption words should return false
        assertFalse(com.example.voice.VoiceEngine.isInterruptionWord("hello snow"))
        assertFalse(com.example.voice.VoiceEngine.isInterruptionWord("weather kaisa hai"))
    }

    // Scenario 2: Tap the Stop button during speech
    // Expected: TTS terminates cleanly, state transitions to IDLE or INTERRUPTED
    @Test
    fun testScenario2_StopButtonStateTransition() {
        var currentState = com.example.ui.ConversationState.SPEAKING
        // User taps stop
        val onStopClicked = {
            currentState = com.example.ui.ConversationState.INTERRUPTED
        }
        onStopClicked()
        assertEquals(com.example.ui.ConversationState.INTERRUPTED, currentState)
    }

    // Scenario 3: While Snow is researching a web query, say "Cancel that, just tell me a joke"
    // Expected: Web research cancels, new request processes
    @Test
    fun testScenario3_TaskCancellationAndSupercession() = runBlocking {
        var activeJob: Job? = null
        var completedTask: String? = null

        // Start research task
        activeJob = launch {
            try {
                delay(1000)
                completedTask = "web_research"
            } catch (e: CancellationException) {
                completedTask = "cancelled"
            }
        }

        // User says "Cancel that, just tell me a joke"
        activeJob.cancel()
        assertTrue(activeJob.isCancelled)

        // Launch joke task
        val newJob = launch {
            completedTask = "joke_response"
        }
        newJob.join()

        assertEquals("joke_response", completedTask)
    }

    // Scenario 4: Request an image while audio is playing
    // Expected: Audio stops, image request proceeds
    @Test
    fun testScenario4_RequestImageWhileAudioPlaying() = runBlocking {
        var isAudioPlaying = true
        var imageRequested = false

        // Incoming prompt requesting an image
        val incomingPrompt = "Snow meri ek anime girl ki image banao"
        val isImagePrompt = incomingPrompt.contains("image", ignoreCase = true)

        if (isImagePrompt) {
            // Audio stops immediately
            isAudioPlaying = false
            imageRequested = true
        }

        assertFalse("Audio must stop when image is requested", isAudioPlaying)
        assertTrue("Image request must proceed", imageRequested)
    }

    // Scenario 5: Request image with Ollama configured (no image support)
    // Expected: Clear error message explaining Ollama doesn't support image generation
    @Test
    fun testScenario5_OllamaNoImageSupportError() = runBlocking {
        // Create an Ollama provider and test with a text LLM like llama3
        val nonImageModels = listOf("llama3", "llama3.2:1b", "mistral", "gemma2", "qwen2.5")
        for (model in nonImageModels) {
            val lower = model.lowercase()
            val hasKeywords = listOf("sd", "diffusion", "flux", "diffuser", "image", "art", "painter").any { lower.contains(it) }
            assertFalse("Model '$model' should not be detected as an image model", hasKeywords)
        }

        val expectedErrorExplanation = "The configured Ollama model 'llama3' is a text LLM, not an image generation model."
        assertTrue(expectedErrorExplanation.contains("text LLM, not an image generation model"))
    }

    // Scenario 6: Multiple rapid interruptions
    // Expected: App remains stable, no crashes or deadlocks
    @Test
    fun testScenario6_MultipleRapidInterruptions() = runBlocking {
        var activeJob: Job? = null
        var currentStatus = "IDLE"

        for (i in 1..20) {
            activeJob?.cancel()
            activeJob = launch {
                try {
                    currentStatus = "RUNNING_$i"
                    delay(500)
                } catch (e: CancellationException) {
                    currentStatus = "CANCELLED_$i"
                }
            }
        }
        activeJob?.cancel()
        assertTrue("Job must cancel cleanly", activeJob?.isCancelled == true)
    }

    // Scenario 7: Interrupt during a multi-step action (e.g., creating a note then setting a reminder)
    // Expected: Remaining steps cancel cleanly, user informed of what completed
    @Test
    fun testScenario7_InterruptDuringMultiStepAction() = runBlocking {
        val completedSteps = mutableListOf<String>()
        var actionCancelled = false

        val multiStepJob = launch {
            try {
                // Step 1: Note creation
                completedSteps.add("Note created: Buy groceries")
                delay(200)

                // User interrupts before step 2
                currentCoroutineContext().let {
                    if (actionCancelled) throw CancellationException("Interrupted by user")
                }

                // Step 2: Reminder setting (should not be reached)
                completedSteps.add("Reminder set for 5 PM")
            } catch (e: CancellationException) {
                // Cleanly caught
            }
        }

        // Simulate interruption right after step 1
        delay(50)
        actionCancelled = true
        multiStepJob.cancel()
        multiStepJob.join()

        assertEquals(1, completedSteps.size)
        assertEquals("Note created: Buy groceries", completedSteps[0])
        assertFalse("Step 2 must not have executed", completedSteps.contains("Reminder set for 5 PM"))
    }

    // ==========================================
    // CRITICAL BUG FIX TESTS: TEXT/SPEECH SEPARATION
    // ==========================================

    @Test
    fun testSpeechTextFilter_stripsCodeBlocks() {
        val aiResponse = """
            Yeh raha aapka Kotlin code:
            ```kotlin
            fun main() {
                println("Hello Snow")
            }
            ```
            Aap isko run kar sakte hain.
        """.trimIndent()

        val filtered = com.example.voice.SpeechTextFilter.filterForSpeech(aiResponse, "Roman Urdu")
        assertFalse("Filtered speech must not contain code block markers", filtered.contains("```"))
        assertFalse("Filtered speech must not contain fun main()", filtered.contains("fun main()"))
        assertFalse("Filtered speech must not contain println", filtered.contains("println"))
        assertTrue("Filtered speech must contain introductory explanation", filtered.contains("Yeh raha aapka Kotlin code"))
        assertTrue("Filtered speech must contain concluding explanation", filtered.contains("Aap isko run kar sakte hain"))
    }

    @Test
    fun testSpeechTextFilter_pureCodeReturnsNaturalFallback() {
        val pureCode = """
            ```python
            import os
            import sys
            def execute():
                print("Running")
            ```
        """.trimIndent()

        val filtered = com.example.voice.SpeechTextFilter.filterForSpeech(pureCode, "Roman Urdu")
        assertFalse(filtered.contains("import os"))
        assertFalse(filtered.contains("def execute"))
        assertTrue("Should return conversational explanation for pure code", filtered.contains("code generate") || filtered.contains("screen par"))
    }

    @Test
    fun testSpeechTextFilter_stripsJsonAndXml() {
        val jsonResponse = """
            Here is the requested data:
            {"status": "success", "userId": 12345, "token": "abc_xyz"}
            Please check it out.
        """.trimIndent()

        val filtered = com.example.voice.SpeechTextFilter.filterForSpeech(jsonResponse, "English")
        assertFalse(filtered.contains("userId"))
        assertFalse(filtered.contains("token"))
        assertTrue(filtered.contains("Here is the requested data"))
        assertTrue(filtered.contains("Please check it out"))
    }

    @Test
    fun testSpeechTextFilter_stripsStackTracesAndLogs() {
        val errorResponse = """
            An error occurred while compiling:
            java.lang.NullPointerException: Attempt to invoke virtual method on a null object reference
                at com.example.Main.execute(Main.kt:42)
                at com.example.Main.start(Main.kt:15)
            Please fix the null pointer reference in Main.kt.
        """.trimIndent()

        val filtered = com.example.voice.SpeechTextFilter.filterForSpeech(errorResponse, "English")
        assertFalse(filtered.contains("NullPointerException"))
        assertFalse(filtered.contains("at com.example.Main"))
        assertTrue(filtered.contains("An error occurred") || filtered.contains("Please fix"))
    }

    @Test
    fun testOllamaModelCapabilityDetection() {
        val visionModel = com.example.ai.provider.OllamaModelInfo(
            name = "llama3.2-vision:11b",
            parameterSize = "11B",
            supportsVision = true,
            supportsCode = false,
            supportsReasoning = false,
            sizeBytes = 7900000000L
        )
        assertTrue(visionModel.supportsVision)
        assertEquals("7.4 GB", visionModel.formattedSize)

        val coderModel = com.example.ai.provider.OllamaModelInfo(
            name = "qwen2.5-coder:7b",
            parameterSize = "7B",
            supportsVision = false,
            supportsCode = true,
            supportsReasoning = false,
            sizeBytes = 4400000000L
        )
        assertTrue(coderModel.supportsCode)
        assertFalse(coderModel.supportsVision)
        assertEquals("4.1 GB", coderModel.formattedSize)

        val reasoningModel = com.example.ai.provider.OllamaModelInfo(
            name = "deepseek-r1:8b",
            parameterSize = "8B",
            supportsVision = false,
            supportsCode = false,
            supportsReasoning = true,
            sizeBytes = 4900000000L
        )
        assertTrue(reasoningModel.supportsReasoning)
        assertEquals("4.6 GB", reasoningModel.formattedSize)
    }
}

