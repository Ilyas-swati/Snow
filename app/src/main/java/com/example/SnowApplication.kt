package com.example

import android.app.Application
import com.example.agent.AgentManager
import com.example.agent.MemoryManager
import com.example.agent.NotesManager
import com.example.agent.TaskPlanner
import com.example.agent.ToolExecutor
import com.example.agent.ToolRegistry
import com.example.ai.GeminiClient
import com.example.ai.provider.AIProviderManager
import com.example.data.AppDatabase
import com.example.data.SnowPreferences
import com.example.device.DeviceCommander
import com.example.permissions.PermissionManager
import com.example.search.SearchManager
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

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var searchManager: SearchManager
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var notesManager: NotesManager
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var toolExecutor: ToolExecutor
        private set

    lateinit var taskPlanner: TaskPlanner
        private set

    lateinit var aiProviderManager: AIProviderManager
        private set

    lateinit var agentManager: AgentManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        preferences = SnowPreferences(this)
        geminiClient = GeminiClient()
        deviceCommander = DeviceCommander(this)

        permissionManager = PermissionManager(this)
        searchManager = SearchManager(preferences)
        memoryManager = MemoryManager(database.memoryDao())
        notesManager = NotesManager(database.noteDao())
        toolRegistry = ToolRegistry()
        toolExecutor = ToolExecutor(
            context = this,
            deviceCommander = deviceCommander,
            searchManager = searchManager,
            memoryManager = memoryManager,
            notesManager = notesManager,
            reminderDao = database.reminderDao(),
            permissionManager = permissionManager
        )
        taskPlanner = TaskPlanner()
        aiProviderManager = AIProviderManager(preferences)
        agentManager = AgentManager(
            preferences = preferences,
            aiProviderManager = aiProviderManager,
            toolRegistry = toolRegistry,
            toolExecutor = toolExecutor,
            taskPlanner = taskPlanner,
            memoryManager = memoryManager
        )

        SnowVoiceService.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: SnowApplication
            private set
    }
}
