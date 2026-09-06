package com.example.agent

data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean"
    val description: String,
    val isRequired: Boolean = true
)

data class AgentTool(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val isSensitive: Boolean = false
)

data class ToolExecutionResult(
    val toolName: String,
    val isSuccess: Boolean,
    val output: String,
    val userVisibleMessage: String? = null,
    val verified: Boolean = false,
    val error: String? = null,
    val details: String = "",
    val screenChanged: Boolean = false,
    val verificationResult: String = ""
)


enum class AndroidActionType {
    OPEN_APP,
    CLICK,
    LONG_CLICK,
    TYPE_TEXT,
    CLEAR_TEXT,
    SCROLL,
    BACK,
    HOME,
    TAKE_SCREENSHOT,
    READ_SCREEN,
    FIND_TEXT,
    FIND_ELEMENT,
    SEND_MESSAGE,
    CREATE_FOLDER,
    CREATE_FILE,
    SELECT_CONTACT,
    OPEN_FILE,
    SHARE_FILE,
    PRESS_ENTER,
    WAIT,
    VERIFY_ACTION
}

data class StructuredAction(
    val action: AndroidActionType,
    val target: String? = null,
    val text: String? = null,
    val parentLocation: String? = null,
    val name: String? = null,
    val durationMs: Long? = null,
    val expectedOutcome: String? = null
)

data class ActionLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val targetOrDetails: String,
    val isSuccess: Boolean,
    val verificationSummary: String
)
