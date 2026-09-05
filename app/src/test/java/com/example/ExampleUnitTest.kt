package com.example

import com.example.data.SnowPreferences
import com.example.voice.provider.OpenAITTSProvider
import com.example.voice.provider.ElevenLabsTTSProvider
import com.example.voice.provider.GoogleCloudTTSProvider
import com.example.voice.provider.TTSVoice
import kotlinx.coroutines.runBlocking
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
}

