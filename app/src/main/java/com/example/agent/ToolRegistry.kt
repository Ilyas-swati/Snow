package com.example.agent

class ToolRegistry {

    private val tools = mutableMapOf<String, AgentTool>()

    init {
        registerDefaultTools()
    }

    private fun registerDefaultTools() {
        // 1. OPEN_APP
        register(
            AgentTool(
                name = "open_app",
                description = "Opens an installed Android application by name (e.g. 'whatsapp', 'youtube', 'chrome', 'camera', 'settings', 'calculator').",
                parameters = listOf(
                    ToolParameter("app_name", "string", "Name of the app to launch (e.g. 'whatsapp', 'youtube')")
                )
            )
        )

        // 2. SEND_WHATSAPP / SEND_MESSAGE
        register(
            AgentTool(
                name = "send_whatsapp",
                description = "Automates sending a WhatsApp message to a recipient contact name or number. Resolves contact, opens conversation, types message, clicks send, and verifies transmission.",
                parameters = listOf(
                    ToolParameter("recipient", "string", "Contact name or phone number (e.g. 'Ali', 'Sara', '03001234567')"),
                    ToolParameter("message", "string", "The message text to send")
                )
            )
        )

        // 3. READ_SCREEN / SCREEN UNDERSTANDING
        register(
            AgentTool(
                name = "read_screen",
                description = "Inspects the active screen UI hierarchy using Accessibility Service, extracting all visible text, interactive buttons, and input fields.",
                parameters = emptyList()
            )
        )

        // 4. TAKE_SCREENSHOT
        register(
            AgentTool(
                name = "take_screenshot",
                description = "Captures a live screenshot of the current Android screen using Accessibility screenshot API.",
                parameters = emptyList()
            )
        )

        // 5. CLICK / TAP
        val clickTool = AgentTool(
            name = "click_element",
            description = "Clicks a button, tab, contact, or UI element matching text, contentDescription, or view ID on the current screen.",
            parameters = listOf(
                ToolParameter("target", "string", "Visible text, description, or view ID of the element to click")
            )
        )
        register(clickTool)
        register(clickTool.copy(name = "click"))

        // 6. LONG_CLICK
        val longClickTool = AgentTool(
            name = "long_click_element",
            description = "Performs a long-press touch on an element matching text or description on screen.",
            parameters = listOf(
                ToolParameter("target", "string", "Visible text or description of the element to long click")
            )
        )
        register(longClickTool)
        register(longClickTool.copy(name = "long_click"))

        // 7. TYPE_TEXT
        register(
            AgentTool(
                name = "type_text",
                description = "Types text into the currently active or detected editable text field on screen using accessibility input.",
                parameters = listOf(
                    ToolParameter("text", "string", "The text to type into the field"),
                    ToolParameter("clear_first", "string", "'true' to clear existing text before typing, else 'false'", isRequired = false)
                )
            )
        )

        // 8. CLEAR_TEXT
        register(
            AgentTool(
                name = "clear_text",
                description = "Clears text from the active text field on screen.",
                parameters = emptyList()
            )
        )

        // 9. SCROLL
        val scrollTool = AgentTool(
            name = "scroll_screen",
            description = "Scrolls the current screen up or down.",
            parameters = listOf(
                ToolParameter("direction", "string", "'DOWN' to scroll down (forward) or 'UP' to scroll up (backward)")
            )
        )
        register(scrollTool)
        register(AgentTool("scroll_up", "Scrolls screen up (backward).", emptyList()))
        register(AgentTool("scroll_down", "Scrolls screen down (forward).", emptyList()))

        // 10. BACK & HOME
        val backTool = AgentTool("press_back", "Triggers the system Back button action.", emptyList())
        register(backTool)
        register(backTool.copy(name = "back"))

        val homeTool = AgentTool("press_home", "Triggers the system Home button action to go to home screen.", emptyList())
        register(homeTool)
        register(homeTool.copy(name = "home"))

        // FIND_TEXT & FIND_ELEMENT
        register(
            AgentTool(
                name = "find_text",
                description = "Finds whether specific text is visible on the current screen.",
                parameters = listOf(ToolParameter("text", "string", "Text to search for on screen"))
            )
        )
        register(
            AgentTool(
                name = "find_element",
                description = "Finds a UI element matching text, description, or id on screen.",
                parameters = listOf(ToolParameter("target", "string", "Element name, description, or view ID"))
            )
        )


        // 11. CONTACT SELECTION / RESOLUTION
        register(
            AgentTool(
                name = "select_contact",
                description = "Searches phone contacts by name. Disambiguates if multiple matches are found.",
                parameters = listOf(
                    ToolParameter("name", "string", "Contact name to look up, e.g. 'Ali'")
                )
            )
        )

        // 12. CREATE_FOLDER
        register(
            AgentTool(
                name = "create_folder",
                description = "Creates a folder in Downloads, Documents, or app storage and verifies its existence.",
                parameters = listOf(
                    ToolParameter("folder_name", "string", "Name of the folder to create (e.g. 'Snow')"),
                    ToolParameter("location", "string", "Parent location: 'Downloads' or 'Documents'", isRequired = false)
                )
            )
        )

        // 13. CREATE_FILE
        register(
            AgentTool(
                name = "create_file",
                description = "Creates a text file inside a folder with specified content and verifies creation.",
                parameters = listOf(
                    ToolParameter("folder_name", "string", "Target folder name (e.g. 'Snow')"),
                    ToolParameter("file_name", "string", "Name of file (e.g. 'test.txt')"),
                    ToolParameter("content", "string", "Content to write into the file", isRequired = false)
                )
            )
        )

        // 14. OPEN_FILE / OPEN_FOLDER
        register(
            AgentTool(
                name = "open_folder",
                description = "Opens the specified folder in the system file manager.",
                parameters = listOf(
                    ToolParameter("folder_name", "string", "Folder name to open")
                )
            )
        )
        register(
            AgentTool(
                name = "open_file",
                description = "Opens a file using system viewer or editor.",
                parameters = listOf(
                    ToolParameter("file_name", "string", "File name to open")
                )
            )
        )

        // 15. SHARE_FILE
        register(
            AgentTool(
                name = "share_file",
                description = "Shares a file with other apps using the Android share sheet.",
                parameters = listOf(
                    ToolParameter("file_name", "string", "File name to share")
                )
            )
        )

        // 16. WAIT / SLEEP
        val waitTool = AgentTool(
            name = "wait_action",
            description = "Pauses execution for a specified duration in milliseconds to allow UI to render.",
            parameters = listOf(
                ToolParameter("duration_ms", "string", "Milliseconds to wait (e.g. '1000')")
            )
        )
        register(waitTool)
        register(waitTool.copy(name = "wait"))

        // 17. VERIFY_ACTION
        val verifyTool = AgentTool(
            name = "verify_action",
            description = "Verifies that an expected UI text, element, or app package is now present on the screen.",
            parameters = listOf(
                ToolParameter("expected_text_or_pkg", "string", "Text or package expected on screen")
            )
        )
        register(verifyTool)
        register(verifyTool.copy(name = "verify"))

        // SEND_MESSAGE (Generic)
        register(
            AgentTool(
                name = "send_message",
                description = "Sends a message to a recipient via messaging apps (e.g. WhatsApp or SMS).",
                parameters = listOf(
                    ToolParameter("recipient", "string", "Contact name or number"),
                    ToolParameter("message", "string", "Message content"),
                    ToolParameter("platform", "string", "Platform: 'whatsapp' or 'sms'", isRequired = false)
                )
            )
        )


        // 18. WEB SEARCH
        register(
            AgentTool(
                name = "web_search",
                description = "Searches the live internet for fresh facts, news, recipes, tutorials, or current info.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search terms or question")
                )
            )
        )

        // 19. PHONE CALL & SMS
        register(
            AgentTool(
                name = "phone_call",
                description = "Opens dialer ready to call a contact or phone number.",
                parameters = listOf(
                    ToolParameter("number", "string", "Phone number or contact name")
                ),
                isSensitive = true
            )
        )

        register(
            AgentTool(
                name = "send_sms",
                description = "Prepares SMS composer with recipient and message body.",
                parameters = listOf(
                    ToolParameter("recipient", "string", "Recipient phone number or name"),
                    ToolParameter("message", "string", "Message body")
                ),
                isSensitive = true
            )
        )

        // 20. DEVICE HARDWARE CONTROL
        register(
            AgentTool(
                name = "device_control",
                description = "Controls phone hardware: flashlight, volume, Wi-Fi settings, Bluetooth settings.",
                parameters = listOf(
                    ToolParameter("feature", "string", "'flashlight', 'volume_up', 'volume_down', 'wifi', 'bluetooth'"),
                    ToolParameter("state", "string", "'ON', 'OFF', or 'TOGGLE'")
                )
            )
        )

        // 21. DEVICE STATUS
        register(
            AgentTool(
                name = "get_device_status",
                description = "Returns device battery level, date, current time, and network connection.",
                parameters = emptyList()
            )
        )

        // 22. ALARM & REMINDER
        register(
            AgentTool(
                name = "set_alarm_or_timer",
                description = "Sets an alarm or countdown timer.",
                parameters = listOf(
                    ToolParameter("title", "string", "Title or reason"),
                    ToolParameter("minutes_or_time", "string", "Minutes count (e.g. '15') or time (e.g. '08:30')"),
                    ToolParameter("type", "string", "'TIMER' or 'ALARM'")
                )
            )
        )

        // 23. NOTES & MEMORY
        register(
            AgentTool(
                name = "save_note",
                description = "Saves a smart note into local database.",
                parameters = listOf(
                    ToolParameter("title", "string", "Title of note"),
                    ToolParameter("content", "string", "Content of note")
                )
            )
        )

        register(
            AgentTool(
                name = "search_notes",
                description = "Searches saved notes by keywords.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search query")
                )
            )
        )

        register(
            AgentTool(
                name = "save_memory",
                description = "Remembers a permanent personal detail or user preference.",
                parameters = listOf(
                    ToolParameter("fact", "string", "Fact to remember")
                )
            )
        )

        register(
            AgentTool(
                name = "recall_memory",
                description = "Retrieves stored facts about user.",
                parameters = listOf(
                    ToolParameter("query", "string", "Topic or question to recall")
                )
            )
        )

        // 24. IMAGE GENERATION & SHARING
        register(
            AgentTool(
                name = "generate_image",
                description = "Generates an image from a detailed visual prompt using the configured image generation provider (Pollinations, Imagen 3, DALL-E, or Ollama).",
                parameters = listOf(
                    ToolParameter("prompt", "string", "Detailed descriptive visual prompt for the image generation engine"),
                    ToolParameter("aspect_ratio", "string", "Aspect ratio: '1:1', '16:9', '9:16', '4:3', or '3:4'", isRequired = false)
                )
            )
        )

        register(
            AgentTool(
                name = "save_image_to_gallery",
                description = "Saves a generated image to the Android system gallery / photos library.",
                parameters = listOf(
                    ToolParameter("file_path", "string", "Local file path of the image to save", isRequired = false)
                )
            )
        )

        register(
            AgentTool(
                name = "share_image",
                description = "Shares an image with another application such as WhatsApp or opens the Android share sheet.",
                parameters = listOf(
                    ToolParameter("file_path", "string", "Local file path of the image to share", isRequired = false),
                    ToolParameter("recipient_app", "string", "Optional target app package name (e.g. 'com.whatsapp')", isRequired = false),
                    ToolParameter("caption", "string", "Optional caption message to accompany the image", isRequired = false)
                )
            )
        )
    }

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AgentTool? = tools[name]

    fun getAllTools(): List<AgentTool> = tools.values.toList()
}
