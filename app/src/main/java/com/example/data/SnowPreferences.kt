package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class SnowPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("snow_ai_prefs", Context.MODE_PRIVATE)

    var customApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

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
        get() = prefs.getString(KEY_API_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_API_MODEL, value).apply()

    var languageMode: String
        get() = prefs.getString(KEY_LANGUAGE, LANG_AUTO) ?: LANG_AUTO
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var femaleVoicePitch: Float
        get() = prefs.getFloat(KEY_VOICE_PITCH, 1.18f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_PITCH, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD, value).apply()

    var continuousListening: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS_LISTEN, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS_LISTEN, value).apply()

    var backgroundServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_BG_SERVICE, value).apply()

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val LANG_AUTO = "AUTO"
        const val LANG_EN = "EN"
        const val LANG_UR = "UR"
        const val LANG_PS = "PS"

        private const val KEY_API_KEY = "custom_api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_LANGUAGE = "language_mode"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_CONTINUOUS_LISTEN = "continuous_listening"
        private const val KEY_BG_SERVICE = "bg_service_enabled"
    }
}
