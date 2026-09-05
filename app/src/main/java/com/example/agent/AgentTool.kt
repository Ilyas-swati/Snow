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
    val userVisibleMessage: String? = null
)
