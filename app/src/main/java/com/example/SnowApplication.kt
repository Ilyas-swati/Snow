package com.example

import android.app.Application
import com.example.ai.GeminiClient
import com.example.data.AppDatabase
import com.example.data.SnowPreferences
import com.example.device.DeviceCommander
import com.example.service.SnowVoiceService

class SnowApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: SnowPreferences
        private set

    lateinit var geminiClient: GeminiClient
        private set

    lateinit var deviceCommander: DeviceCommander
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        preferences = SnowPreferences(this)
        geminiClient = GeminiClient()
        deviceCommander = DeviceCommander(this)

        SnowVoiceService.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: SnowApplication
            private set
    }
}
