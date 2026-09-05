package com.example.agent

import android.graphics.Bitmap
import android.util.Log
import com.example.ai.provider.AIProviderManager
import com.example.data.SnowPreferences
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    @Volatile
    private var isCancelled = false

    fun cancelCurrentTask() {
        isCancelled = true
        _agentState.value = _agentState.value.copy(
            isExecuting = false,
            statusText = "Interrupted"
        )
    }

    companion object {
        private const val MAX_AGENT_LOOPS = 5
    }

    suspend fun processUserTurn(
        userPrompt: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        imageBitmap: Bitmap? = null,
        onStatusCallback: (String) -> Unit = {}
    ): FinalAgentResponse = withContext(Dispatchers.IO) {
        isCancelled = false
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) {
            return@withContext FinalAgentResponse("How can I help you?", "English")
        }

        _agentState.value = AgentExecutionState(statusText = "Analyzing…", isExecuting = true)
        onStatusCallback("Analyzing…")

        // 1. Task Planning
        val plan = taskPlanner.planTask(cleanPrompt)
        val totalSteps = if (plan.isMultiStep) plan.steps.size else 1
        _agentState.value = _agentState.value.copy(
            totalSteps = totalSteps,
            statusText = if (plan.isMultiStep) "Executing multi-step action (0/$totalSteps)…" else "Reasoning…"
        )

        // 2. Build Agent System Prompt with Memory Context & Personality
        val memoryContext = memoryManager.getFormattedMemoriesForContext()
        val systemInstruction = buildSystemPrompt(memoryContext, plan)

        val tools = toolRegistry.getAllTools()
        val executedToolsList = mutableListOf<String>()
        val toolOutputsAccumulator = StringBuilder()

        var currentPrompt = cleanPrompt
        var loopCount = 0
        var finalSpokenText = ""
        var detectedLang = "English"

        // 3. Autonomous Agent Loop (Observe -> Plan -> Act -> Verify)
        while (loopCount < MAX_AGENT_LOOPS) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (isCancelled) {
                return@withContext FinalAgentResponse("Action cancelled.", detectedLang)
            }
            loopCount++
            _agentState.value = _agentState.value.copy(
                currentStep = loopCount,
                statusText = if (plan.isMultiStep) "Executing step $loopCount of $totalSteps…" else "Processing…"
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

            // Handle provider error: do not repeatedly loop on network/API failure
            if (turnResult.error != null) {
                if (loopCount == 1 && plan.steps.isNotEmpty() && plan.steps[0].suggestedTool != null) {
                    val step = plan.steps[0]
                    val suggested = step.suggestedTool
                    if (suggested != null) {
                        _agentState.value = _agentState.value.copy(statusText = "Executing: $suggested…")
                        onStatusCallback(_agentState.value.statusText)
                        val result = toolExecutor.executeTool(suggested, step.suggestedArgs)
                        executedToolsList.add(suggested)
                        finalSpokenText = result.userVisibleMessage ?: result.output
                    }
                } else {
                    finalSpokenText = turnResult.spokenText
                }
                break
            }

            // Check if model emitted tool calls
            val toolCallsToExecute = turnResult.toolCalls.toMutableList()

            // Fail-safe: If model emitted no tool calls on loop 1, but task planner identified an unambiguous suggested tool
            if (toolCallsToExecute.isEmpty() && loopCount == 1 && plan.steps.isNotEmpty() && plan.steps[0].suggestedTool != null) {
                val step = plan.steps[0]
                val suggested = step.suggestedTool
                if (suggested != null) {
                    toolCallsToExecute.add(com.example.ai.provider.ToolCallRequest(suggested, step.suggestedArgs))
                }
            }

            if (toolCallsToExecute.isEmpty()) {
                // Agent has formulated final response
                finalSpokenText = turnResult.spokenText
                break
            }

            // Execute requested tools
            for (call in toolCallsToExecute) {
                val tool = toolRegistry.getTool(call.toolName)
                if (tool != null && tool.isSensitive && preferences.requireConfirmationForSensitive) {
                    _agentState.value = _agentState.value.copy(isExecuting = false, statusText = "Awaiting confirmation")
                    return@withContext FinalAgentResponse(
                        spokenText = "This action requires confirmation: ${tool.description}. Proceed?",
                        detectedLanguage = detectedLang,
                        requiresUserConfirmation = true,
                        pendingActionDescription = "${tool.name} with ${call.arguments}"
                    )
                }

                _agentState.value = _agentState.value.copy(
                    statusText = "Action: ${call.toolName}…"
                )
                onStatusCallback(_agentState.value.statusText)

                val result = toolExecutor.executeTool(call.toolName, call.arguments)
                executedToolsList.add(call.toolName)

                toolOutputsAccumulator.append("OBSERVED RESULT [${call.toolName}]:\n")
                toolOutputsAccumulator.append("Success: ").append(result.isSuccess).append("\n")
                toolOutputsAccumulator.append("Output: ").append(result.output).append("\n\n")

                if (result.userVisibleMessage != null) {
                    finalSpokenText = result.userVisibleMessage
                } else if (turnResult.spokenText.isNotBlank()) {
                    finalSpokenText = turnResult.spokenText
                }
            }

            // Provide tool output context for next step
            currentPrompt = "Proceed based on the observed action results and provide the final warm voice response."
        }

        _agentState.value = AgentExecutionState(
            statusText = "Completed.",
            isExecuting = false,
            currentStep = loopCount,
            totalSteps = totalSteps,
            executedTools = executedToolsList
        )
        onStatusCallback("Completed.")

        val summary = if (executedToolsList.isNotEmpty()) {
            "Actions: " + executedToolsList.joinToString(", ")
        } else null

        FinalAgentResponse(
            spokenText = finalSpokenText.ifBlank { "Task completed." },
            detectedLanguage = detectedLang,
            executedToolsSummary = summary
        )
    }

    private fun buildSystemPrompt(memoryContext: String, plan: TaskPlan): String = buildString {
        append("You are ${preferences.assistantName}, an intelligent and empathetic Android personal AI agent. ")
        append("You possess a warm, natural human female voice persona. ")
        append("You are equipped with tools to execute real actions on this Android device: WhatsApp messaging, launching apps, file creation, screen reading, touch gestures, web search, notes, memory, and alarms.\n")
        append("CRITICAL AGENT DIRECTIVES:\n")
        append("1. Multi-Step Execution: When a user asks an actionable command, emit tool calls. Inspect observed outputs and continue until completed.\n")
        append("2. Language Matching: Snow natively speaks and understands English, Urdu (اردو), Roman Urdu (English alphabet), Hindi (हिन्दी), and Pashto (پښتو). Match the user's natural language and script.\n")
        append("3. Spoken Voice Conciseness: Keep final answers conversational, warm, and brief (1 to 3 spoken sentences) as your response is read aloud by TTS.\n")
        append("4. Real World Integrity: Never fake actions. Rely only on observed tool results.\n")

        if (plan.isMultiStep) {
            append("\nCURRENT TASK PLAN:\n")
            plan.steps.forEach { append(" - Step ${it.stepNumber}: ${it.description}\n") }
        }

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
