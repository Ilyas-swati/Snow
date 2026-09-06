package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.security.KeystoreSecretManager

class SnowPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("snow_ai_prefs", Context.MODE_PRIVATE)
    private val secretManager = KeystoreSecretManager(context)

    // Assistant Identity & Personality
    var assistantName: String
        get() = prefs.getString(KEY_ASSISTANT_NAME, "Snow") ?: "Snow"
        set(value) = prefs.edit().putString(KEY_ASSISTANT_NAME, value.trim()).apply()

    var wakePhrase: String
        get() = prefs.getString(KEY_WAKE_PHRASE, "Hey Snow") ?: "Hey Snow"
        set(value) = prefs.edit().putString(KEY_WAKE_PHRASE, value.trim()).apply()

    var personality: String
        get() = prefs.getString(KEY_PERSONALITY, "FRIENDLY") ?: "FRIENDLY"
        set(value) = prefs.edit().putString(KEY_PERSONALITY, value).apply()

    var responseLength: String
        get() = prefs.getString(KEY_RESPONSE_LENGTH, "CONCISE") ?: "CONCISE"
        set(value) = prefs.edit().putString(KEY_RESPONSE_LENGTH, value).apply()

    // Primary & Fallback AI Providers
    var activeAiProvider: String
        get() = prefs.getString(KEY_ACTIVE_AI_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        set(value) = prefs.edit().putString(KEY_ACTIVE_AI_PROVIDER, value).apply()

    var fallbackAiProvider: String
        get() = prefs.getString(KEY_FALLBACK_AI_PROVIDER, PROVIDER_NONE) ?: PROVIDER_NONE
        set(value) = prefs.edit().putString(KEY_FALLBACK_AI_PROVIDER, value).apply()

    // Gemini
    var customApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_API_KEY, value.trim())
            prefs.edit().putString(KEY_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    val effectiveApiKey: String
        get() {
            val custom = customApiKey
            if (custom.isNotBlank()) return custom
            return try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        }

    var apiEndpointModel: String
        get() {
            val saved = prefs.getString(KEY_API_MODEL, null)
            return if (saved.isNullOrBlank() || saved == "gemini-3.5-flash") {
                DEFAULT_MODEL
            } else {
                saved
            }
        }
        set(value) = prefs.edit().putString(KEY_API_MODEL, value).apply()

    // OpenAI AI Provider
    var openAiApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_OPENAI_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_OPENAI_API_KEY, value.trim())
            prefs.edit().putString(KEY_OPENAI_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    var openAiModel: String
        get() = prefs.getString(KEY_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString(KEY_OPENAI_MODEL, value.trim()).apply()

    // Anthropic AI Provider
    var anthropicApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_ANTHROPIC_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_ANTHROPIC_API_KEY, value.trim())
            prefs.edit().putString(KEY_ANTHROPIC_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    var anthropicModel: String
        get() = prefs.getString(KEY_ANTHROPIC_MODEL, "claude-3-5-haiku-20241022") ?: "claude-3-5-haiku-20241022"
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_MODEL, value.trim()).apply()

    // Ollama AI Provider
    var ollamaBaseUrl: String
        get() = prefs.getString(KEY_OLLAMA_BASE_URL, "http://10.0.2.2:11434") ?: "http://10.0.2.2:11434"
        set(value) = prefs.edit().putString(KEY_OLLAMA_BASE_URL, value.trim()).apply()

    var ollamaApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_OLLAMA_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_OLLAMA_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_OLLAMA_API_KEY, value.trim())
            prefs.edit().putString(KEY_OLLAMA_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    var ollamaModel: String
        get() = prefs.getString(KEY_OLLAMA_MODEL, "llama3.2") ?: "llama3.2"
        set(value) = prefs.edit().putString(KEY_OLLAMA_MODEL, value.trim()).apply()

    var ollamaVisionModel: String
        get() = prefs.getString(KEY_OLLAMA_VISION_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OLLAMA_VISION_MODEL, value.trim()).apply()

    var ollamaTemperature: Float
        get() = prefs.getFloat(KEY_OLLAMA_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_OLLAMA_TEMPERATURE, value).apply()

    var speakTypedResponses: String
        get() = prefs.getString(KEY_SPEAK_TYPED_RESPONSES, SPEAK_TYPED_VOICE_ONLY) ?: SPEAK_TYPED_VOICE_ONLY
        set(value) = prefs.edit().putString(KEY_SPEAK_TYPED_RESPONSES, value).apply()

    // Image Generation Provider (Req 29)
    var imageGenProvider: String
        get() = prefs.getString(KEY_IMAGE_PROVIDER, IMAGE_PROVIDER_POLLINATIONS) ?: IMAGE_PROVIDER_POLLINATIONS
        set(value) = prefs.edit().putString(KEY_IMAGE_PROVIDER, value).apply()

    var imageGenModel: String
        get() = prefs.getString(KEY_IMAGE_MODEL, "flux") ?: "flux"
        set(value) = prefs.edit().putString(KEY_IMAGE_MODEL, value.trim()).apply()

    var imageGenEndpoint: String
        get() = prefs.getString(KEY_IMAGE_ENDPOINT, "https://image.pollinations.ai/prompt") ?: "https://image.pollinations.ai/prompt"
        set(value) = prefs.edit().putString(KEY_IMAGE_ENDPOINT, value.trim()).apply()

    var imageGenApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_IMAGE_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_IMAGE_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_IMAGE_API_KEY, value.trim())
            prefs.edit().putString(KEY_IMAGE_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    // Custom REST Provider (Groq / Ollama / OpenAI-compatible / Local)
    var customRestEndpoint: String
        get() = prefs.getString(KEY_CUSTOM_REST_ENDPOINT, "https://api.groq.com/openai/v1") ?: "https://api.groq.com/openai/v1"
        set(value) = prefs.edit().putString(KEY_CUSTOM_REST_ENDPOINT, value.trim()).apply()

    var customRestApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_CUSTOM_REST_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_CUSTOM_REST_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_CUSTOM_REST_API_KEY, value.trim())
            prefs.edit().putString(KEY_CUSTOM_REST_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    var customRestModel: String
        get() = prefs.getString(KEY_CUSTOM_REST_MODEL, "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
        set(value) = prefs.edit().putString(KEY_CUSTOM_REST_MODEL, value.trim()).apply()

    // Web Search
    var searchProvider: String
        get() = prefs.getString(KEY_SEARCH_PROVIDER, SEARCH_PROVIDER_DUCKDUCKGO) ?: SEARCH_PROVIDER_DUCKDUCKGO
        set(value) = prefs.edit().putString(KEY_SEARCH_PROVIDER, value).apply()

    var searchApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_SEARCH_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_SEARCH_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_SEARCH_API_KEY, value.trim())
            prefs.edit().putString(KEY_SEARCH_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    // Language Mode
    var languageMode: String
        get() = prefs.getString(KEY_LANGUAGE, LANG_AUTO) ?: LANG_AUTO
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    // TTS & Voice
    var ttsProvider: String
        get() = prefs.getString(KEY_TTS_PROVIDER, TTS_PROVIDER_SYSTEM) ?: TTS_PROVIDER_SYSTEM
        set(value) = prefs.edit().putString(KEY_TTS_PROVIDER, value).apply()

    var elevenLabsApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_ELEVENLABS_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_ELEVENLABS_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_ELEVENLABS_API_KEY, value.trim())
            prefs.edit().putString(KEY_ELEVENLABS_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    var googleCloudApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_GOOGLE_CLOUD_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_GOOGLE_CLOUD_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_GOOGLE_CLOUD_API_KEY, value.trim())
            prefs.edit().putString(KEY_GOOGLE_CLOUD_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    var selectedVoiceId: String
        get() = prefs.getString(KEY_SELECTED_VOICE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_VOICE_ID, value).apply()

    var autoSpeakEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    var interruptWhileSpeaking: Boolean
        get() = prefs.getBoolean(KEY_INTERRUPT_SPEAKING, true)
        set(value) = prefs.edit().putBoolean(KEY_INTERRUPT_SPEAKING, value).apply()

    var fallbackVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_FALLBACK_VOICE, true)
        set(value) = prefs.edit().putBoolean(KEY_FALLBACK_VOICE, value).apply()

    var femaleVoicePitch: Float
        get() = prefs.getFloat(KEY_VOICE_PITCH, 1.18f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_PITCH, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    // Agent Controls & Safety
    var requireConfirmationForSensitive: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_CONFIRMATION, value).apply()

    var proactiveAssistance: Boolean
        get() = prefs.getBoolean(KEY_PROACTIVE_ASSIST, true)
        set(value) = prefs.edit().putBoolean(KEY_PROACTIVE_ASSIST, value).apply()

    var screenControlEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_CONTROL, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_CONTROL, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD, value).apply()

    var continuousListening: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS_LISTEN, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS_LISTEN, value).apply()

    var backgroundServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_BG_SERVICE, value).apply()

    // Image Generation Provider Settings
    var imageGenerationProvider: String
        get() = prefs.getString(KEY_IMAGE_PROVIDER, IMAGE_PROVIDER_POLLINATIONS) ?: IMAGE_PROVIDER_POLLINATIONS
        set(value) = prefs.edit().putString(KEY_IMAGE_PROVIDER, value).apply()

    var imageGenerationModel: String
        get() = prefs.getString(KEY_IMAGE_MODEL, "flux") ?: "flux"
        set(value) = prefs.edit().putString(KEY_IMAGE_MODEL, value.trim()).apply()

    var imageGenerationEndpoint: String
        get() = prefs.getString(KEY_IMAGE_ENDPOINT, "https://image.pollinations.ai/prompt") ?: "https://image.pollinations.ai/prompt"
        set(value) = prefs.edit().putString(KEY_IMAGE_ENDPOINT, value.trim()).apply()

    var imageGenerationApiKey: String
        get() {
            val secure = secretManager.getSecret(KEY_IMAGE_API_KEY)
            if (secure.isNotBlank()) return secure
            return prefs.getString(KEY_IMAGE_API_KEY, "") ?: ""
        }
        set(value) {
            secretManager.storeSecret(KEY_IMAGE_API_KEY, value.trim())
            prefs.edit().putString(KEY_IMAGE_API_KEY, if (value.isBlank()) "" else "SECURE").apply()
        }

    fun getApiKeyForProvider(providerId: String): String {
        return when (providerId) {
            TTS_PROVIDER_OPENAI -> openAiApiKey
            TTS_PROVIDER_ELEVENLABS -> elevenLabsApiKey
            TTS_PROVIDER_GOOGLE_CLOUD -> googleCloudApiKey
            else -> ""
        }
    }

    fun setApiKeyForProvider(providerId: String, key: String) {
        when (providerId) {
            TTS_PROVIDER_OPENAI -> openAiApiKey = key
            TTS_PROVIDER_ELEVENLABS -> elevenLabsApiKey = key
            TTS_PROVIDER_GOOGLE_CLOUD -> googleCloudApiKey = key
        }
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        secretManager.clearAllSecrets()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val LANG_AUTO = "AUTO"
        const val LANG_EN = "EN"
        const val LANG_HI = "HI"
        const val LANG_UR = "UR"
        const val LANG_ROMAN_UR = "ROMAN_UR"
        const val LANG_PS = "PS"

        const val PROVIDER_GEMINI = "GEMINI"
        const val PROVIDER_OLLAMA = "OLLAMA"
        const val PROVIDER_OPENAI = "OPENAI"
        const val PROVIDER_ANTHROPIC = "ANTHROPIC"
        const val PROVIDER_CUSTOM_REST = "CUSTOM_REST"
        const val PROVIDER_NONE = "NONE"

        const val SPEAK_TYPED_ALWAYS = "ALWAYS"
        const val SPEAK_TYPED_VOICE_ONLY = "VOICE_ONLY"
        const val SPEAK_TYPED_NEVER = "NEVER"

        const val SEARCH_PROVIDER_DUCKDUCKGO = "DUCKDUCKGO"
        const val SEARCH_PROVIDER_SERPER = "SERPER"
        const val SEARCH_PROVIDER_NONE = "NONE"

        const val TTS_PROVIDER_SYSTEM = "SYSTEM"
        const val TTS_PROVIDER_OPENAI = "OPENAI"
        const val TTS_PROVIDER_ELEVENLABS = "ELEVENLABS"
        const val TTS_PROVIDER_GOOGLE_CLOUD = "GOOGLE_CLOUD"

        // Image Generation Providers
        const val IMAGE_PROVIDER_POLLINATIONS = "POLLINATIONS"
        const val IMAGE_PROVIDER_GEMINI_IMAGEN = "GEMINI_IMAGEN"
        const val IMAGE_PROVIDER_OPENAI_DALLE = "OPENAI_DALLE"
        const val IMAGE_PROVIDER_OLLAMA = "OLLAMA"
        const val IMAGE_PROVIDER_CUSTOM_REST = "CUSTOM_REST"

        const val IMG_PROVIDER_POLLINATIONS = IMAGE_PROVIDER_POLLINATIONS
        const val IMG_PROVIDER_OPENAI = IMAGE_PROVIDER_OPENAI_DALLE
        const val IMG_PROVIDER_OLLAMA = IMAGE_PROVIDER_OLLAMA
        const val IMG_PROVIDER_CUSTOM = IMAGE_PROVIDER_CUSTOM_REST

        private const val KEY_ASSISTANT_NAME = "assistant_name"
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val KEY_PERSONALITY = "assistant_personality"
        private const val KEY_RESPONSE_LENGTH = "response_length"
        private const val KEY_ACTIVE_AI_PROVIDER = "active_ai_provider"
        private const val KEY_FALLBACK_AI_PROVIDER = "fallback_ai_provider"
        private const val KEY_API_KEY = "custom_api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_ANTHROPIC_MODEL = "anthropic_model"
        private const val KEY_OLLAMA_BASE_URL = "ollama_base_url"
        private const val KEY_OLLAMA_API_KEY = "ollama_api_key"
        private const val KEY_OLLAMA_MODEL = "ollama_model"
        private const val KEY_OLLAMA_VISION_MODEL = "ollama_vision_model"
        private const val KEY_OLLAMA_TEMPERATURE = "ollama_temperature"
        private const val KEY_SPEAK_TYPED_RESPONSES = "speak_typed_responses"
        private const val KEY_IMAGE_PROVIDER = "image_generation_provider"
        private const val KEY_IMAGE_MODEL = "image_generation_model"
        private const val KEY_IMAGE_ENDPOINT = "image_generation_endpoint"
        private const val KEY_IMAGE_API_KEY = "image_generation_api_key"
        private const val KEY_CUSTOM_REST_ENDPOINT = "custom_rest_endpoint"
        private const val KEY_CUSTOM_REST_API_KEY = "custom_rest_api_key"
        private const val KEY_CUSTOM_REST_MODEL = "custom_rest_model"
        private const val KEY_SEARCH_PROVIDER = "search_provider"
        private const val KEY_SEARCH_API_KEY = "search_api_key"
        private const val KEY_LANGUAGE = "language_mode"
        private const val KEY_TTS_PROVIDER = "tts_provider"
        private const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
        private const val KEY_GOOGLE_CLOUD_API_KEY = "google_cloud_api_key"
        private const val KEY_SELECTED_VOICE_ID = "selected_voice_id"
        private const val KEY_AUTO_SPEAK = "auto_speak_enabled"
        private const val KEY_INTERRUPT_SPEAKING = "interrupt_speaking"
        private const val KEY_FALLBACK_VOICE = "fallback_voice_enabled"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_CONTINUOUS_LISTEN = "continuous_listening"
        private const val KEY_BG_SERVICE = "bg_service_enabled"
        private const val KEY_REQUIRE_CONFIRMATION = "require_confirmation"
        private const val KEY_PROACTIVE_ASSIST = "proactive_assistance"
        private const val KEY_SCREEN_CONTROL = "screen_control_enabled"
    }
}
