package com.example.agent

class ToolRegistry {

    private val tools = mutableMapOf<String, AgentTool>()

    init {
        registerDefaultTools()
    }

    private fun registerDefaultTools() {
        register(
            AgentTool(
                name = "open_app",
                description = "Opens an installed Android application by name (e.g. 'youtube', 'whatsapp', 'chrome', 'camera', 'settings', 'maps', 'spotify').",
                parameters = listOf(
                    ToolParameter("app_name", "string", "Name of the application or package to launch")
                )
            )
        )

        register(
            AgentTool(
                name = "web_search",
                description = "Searches the live internet for fresh information, news, tutorials, recipes, or current facts.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search keywords or question")
                )
            )
        )

        register(
            AgentTool(
                name = "search_contacts",
                description = "Searches user contacts on device to find phone number and contact details by name.",
                parameters = listOf(
                    ToolParameter("name", "string", "Name of the contact to find, e.g. 'Ali'")
                )
            )
        )

        register(
            AgentTool(
                name = "send_whatsapp",
                description = "Prepares and opens WhatsApp to send a chat message to a contact or phone number.",
                parameters = listOf(
                    ToolParameter("recipient", "string", "Name or phone number of the person"),
                    ToolParameter("message", "string", "Message content to type and send")
                ),
                isSensitive = false
            )
        )

        register(
            AgentTool(
                name = "send_sms",
                description = "Prepares SMS message composer for a contact or number.",
                parameters = listOf(
                    ToolParameter("recipient", "string", "Recipient phone number or name"),
                    ToolParameter("message", "string", "Text message body")
                ),
                isSensitive = true
            )
        )

        register(
            AgentTool(
                name = "phone_call",
                description = "Opens the phone dialer with a contact's number ready to call.",
                parameters = listOf(
                    ToolParameter("number", "string", "Phone number or contact name to dial")
                ),
                isSensitive = true
            )
        )

        register(
            AgentTool(
                name = "set_alarm_or_timer",
                description = "Sets an Android alarm or countdown timer. Example: hour 7, minute 0 or 20 minutes timer.",
                parameters = listOf(
                    ToolParameter("title", "string", "Label or title for alarm/timer"),
                    ToolParameter("minutes_or_time", "string", "E.g. '20' for minutes, or '08:00' for alarm time"),
                    ToolParameter("type", "string", "'TIMER' or 'ALARM'")
                )
            )
        )

        register(
            AgentTool(
                name = "create_reminder",
                description = "Creates a persistent reminder with natural language time (e.g. 'tomorrow at 8 AM').",
                parameters = listOf(
                    ToolParameter("title", "string", "What to remind user about, e.g. 'Call Ali'"),
                    ToolParameter("time_description", "string", "When to remind, e.g. 'tomorrow 8:00 AM' or 'in 30 minutes'")
                )
            )
        )

        register(
            AgentTool(
                name = "save_note",
                description = "Saves a smart note into local database.",
                parameters = listOf(
                    ToolParameter("title", "string", "Title of the note"),
                    ToolParameter("content", "string", "Body content of the note")
                )
            )
        )

        register(
            AgentTool(
                name = "search_notes",
                description = "Searches user's saved notes by keyword.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search keywords")
                )
            )
        )

        register(
            AgentTool(
                name = "save_memory",
                description = "Remembers a permanent personal detail or preference about the user (e.g. 'Ali is my brother', 'User prefers dark theme').",
                parameters = listOf(
                    ToolParameter("fact", "string", "Fact or preference to remember")
                )
            )
        )

        register(
            AgentTool(
                name = "recall_memory",
                description = "Retrieves stored memories or answers questions about the user.",
                parameters = listOf(
                    ToolParameter("query", "string", "Keywords or topic to recall")
                )
            )
        )

        register(
            AgentTool(
                name = "forget_memory",
                description = "Deletes stored memories matching a query.",
                parameters = listOf(
                    ToolParameter("query", "string", "What to delete from memory")
                ),
                isSensitive = true
            )
        )

        register(
            AgentTool(
                name = "device_control",
                description = "Controls phone hardware like flashlight, volume, Wi-Fi settings, or Bluetooth settings.",
                parameters = listOf(
                    ToolParameter("feature", "string", "'flashlight', 'volume_up', 'volume_down', 'wifi', 'bluetooth'"),
                    ToolParameter("state", "string", "'ON', 'OFF', or 'TOGGLE'")
                )
            )
        )

        register(
            AgentTool(
                name = "get_device_status",
                description = "Checks device battery level, date, current time, and network status.",
                parameters = emptyList()
            )
        )

        register(
            AgentTool(
                name = "read_notifications",
                description = "Reads recent notifications from messaging apps (e.g. WhatsApp, SMS) via notification listener.",
                parameters = emptyList()
            )
        )

        register(
            AgentTool(
                name = "screen_action",
                description = "Reads text on current screen or clicks UI buttons using Accessibility Service.",
                parameters = listOf(
                    ToolParameter("action", "string", "'READ_SCREEN', 'CLICK_TEXT', 'SCROLL_DOWN', 'BACK'"),
                    ToolParameter("target_text", "string", "Text to click or search for on screen, if applicable")
                )
            )
        )

        register(
            AgentTool(
                name = "file_operation",
                description = "Creates, reads, or lists text files in user documents/app storage.",
                parameters = listOf(
                    ToolParameter("action", "string", "'CREATE', 'READ', 'LIST'"),
                    ToolParameter("filename", "string", "File name, e.g. 'todo.txt'"),
                    ToolParameter("content", "string", "File content when creating", isRequired = false)
                ),
                isSensitive = false
            )
        )

        register(
            AgentTool(
                name = "daily_briefing",
                description = "Synthesizes a complete daily briefing including current date, time, battery, reminders, and notes.",
                parameters = emptyList()
            )
        )
    }

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AgentTool? = tools[name]

    fun getAllTools(): List<AgentTool> = tools.values.toList()
}
