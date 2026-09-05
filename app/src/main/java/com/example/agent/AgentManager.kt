package com.example.agent

import android.graphics.Bitmap
import android.util.Log
import com.example.ai.provider.AIProviderManager
import com.example.data.SnowPreferences
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AgentExecutionState(
    val statusText: String = "",
    val isExecuting: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val executedTools: List<String> = emptyList()
)

data class FinalAgentResponse(
    val spokenText: String,
    val detectedLanguage: String,
    val executedToolsSummary: String? = null,
    val requiresUserConfirmation: Boolean = false,
    val pendingActionDescription: String? = null
)

class AgentManager(
    private val preferences: SnowPreferences,
    private val aiProviderManager: AIProviderManager,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val taskPlanner: TaskPlanner,
    private val memoryManager: MemoryManager
) {

    private val _agentState = MutableStateFlow(AgentExecutionState())
    val agentState: StateFlow<AgentExecutionState> = _agentState.asStateFlow()

    companion object {
        private const val MAX_AGENT_LOOPS = 5
    }

    suspend fun processUserTurn(
        userPrompt: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null,
        onStatusCallback: (String) -> Unit = {}
    ): FinalAgentResponse = withContext(Dispatchers.IO) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) {
            return@withContext FinalAgentResponse("How can I help you?", "English")
        }

        _agentState.value = AgentExecutionState(statusText = "Planning…", isExecuting = true)
        onStatusCallback("Planning…")

        // 1. Task Planning
        val plan = taskPlanner.planTask(cleanPrompt)
        if (plan.isMultiStep) {
            _agentState.value = _agentState.value.copy(
                totalSteps = plan.steps.size,
                statusText = "Executing multi-step task (0/${plan.steps.size})…"
            )
        }

        // 2. Build Agent System Prompt with Memory Context & Personality
        val memoryContext = memoryManager.getFormattedMemoriesForContext()
        val systemInstruction = buildSystemPrompt(memoryContext)

        val tools = toolRegistry.getAllTools()
        val executedToolsList = mutableListOf<String>()
        val toolOutputsAccumulator = StringBuilder()

        var currentPrompt = cleanPrompt
        var loopCount = 0
        var finalSpokenText = ""
        var detectedLang = "English"

        // 3. Autonomous Agent Loop (Up to MAX_AGENT_LOOPS steps)
        while (loopCount < MAX_AGENT_LOOPS) {
            loopCount++
            _agentState.value = _agentState.value.copy(
                currentStep = loopCount,
                statusText = if (plan.isMultiStep) "Executing step $loopCount of ${plan.steps.size}…" else "Thinking…"
            )
            onStatusCallback(_agentState.value.statusText)

            val turnResult = aiProviderManager.executeAgentTurn(
                prompt = currentPrompt,
                systemInstruction = systemInstruction,
                tools = tools,
                conversationHistory = conversationHistory,
                imageBitmap = imageBitmap,
                toolOutputsContext = if (toolOutputsAccumulator.isNotBlank()) toolOutputsAccumulator.toString() else null,
                onStatusUpdate = onStatusCallback
            )

            detectedLang = turnResult.detectedLanguage

            if (turnResult.toolCalls.isEmpty()) {
                // Agent has formulated final response
                finalSpokenText = turnResult.spokenText
                break
            }

            // Execute requested tools
            for (call in turnResult.toolCalls) {
                val tool = toolRegistry.getTool(call.toolName)
                if (tool != null && tool.isSensitive && preferences.requireConfirmationForSensitive) {
                    _agentState.value = _agentState.value.copy(isExecuting = false, statusText = "Awaiting confirmation")
                    return@withContext FinalAgentResponse(
                        spokenText = "This action requires your confirmation: ${tool.description}. Would you like me to proceed?",
                        detectedLanguage = detectedLang,
                        requiresUserConfirmation = true,
                        pendingActionDescription = "${tool.name} with ${call.arguments}"
                    )
                }

                _agentState.value = _agentState.value.copy(
                    statusText = "Running tool: ${call.toolName}…"
                )
                onStatusCallback(_agentState.value.statusText)

                val result = toolExecutor.executeTool(call.toolName, call.arguments)
                executedToolsList.add(call.toolName)

                toolOutputsAccumulator.append("TOOL [${call.toolName}] RESULT:\n")
                toolOutputsAccumulator.append(result.output).append("\n\n")

                if (turnResult.spokenText.isNotBlank()) {
                    finalSpokenText = turnResult.spokenText
                }
            }

            // Provide tool output context for next step
            currentPrompt = "Proceed based on the observed tool outputs and provide the final warm voice response."
        }

        _agentState.value = AgentExecutionState(
            statusText = "Completed.",
            isExecuting = false,
            currentStep = loopCount,
            totalSteps = plan.steps.size,
            executedTools = executedToolsList
        )
        onStatusCallback("Completed.")

        val summary = if (executedToolsList.isNotEmpty()) {
            "Executed: " + executedToolsList.joinToString(", ")
        } else null

        FinalAgentResponse(
            spokenText = finalSpokenText.ifBlank { "Task completed." },
            detectedLanguage = detectedLang,
            executedToolsSummary = summary
        )
    }

    private fun buildSystemPrompt(memoryContext: String): String = buildString {
        append("You are ${preferences.assistantName}, an intelligent, highly capable, and empathetic personal AI voice agent. ")
        append("You possess a warm, natural, human-like female voice persona. ")
        append("You are equipped with tools to control the user's Android phone, search the web, manage notes, recall memory, check notifications, inspect the screen, and schedule reminders. ")
        append("\nCRITICAL AGENT DIRECTIVES:\n")
        append("1. Multi-Step Execution: When a user asks a complex multi-part request, use tools sequentially. Inspect the tool outputs and proceed until the entire task is resolved.\n")
        append("2. Language Matching & Mixed Speech: Snow natively understands English, Hindi (हिन्दी), Urdu (اردو), Roman Urdu (English alphabet), and Pashto (پښتو). ")
        append("Always answer naturally in the user's spoken language without unnecessary translation. If they speak Roman Urdu, reply in Roman Urdu. If Hindi, in Hindi. If Pashto, in Pashto.\n")
        append("3. Spoken Voice Formatting: Keep answers conversational, natural, and concise (1 to 3 spoken sentences). Avoid markdown stars, lists, or tables as your response is read aloud by TTS.\n")
        append("4. Real World Integrity: Never claim live or internet facts without calling 'web_search'. Never pretend to execute a tool without calling it.\n")
        when (preferences.personality) {
            "PROFESSIONAL" -> append("Tone: Professional, direct, efficient, polite.\n")
            "CONCISE" -> append("Tone: Extremely brief, direct, zero filler.\n")
            "HUMOROUS" -> append("Tone: Witty, playful, charming, warm.\n")
            else -> append("Tone: Warm, empathetic, welcoming, friendly.\n")
        }
        if (memoryContext.isNotBlank()) {
            append("\n$memoryContext\n")
        }
    }
}
