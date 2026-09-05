package com.example.agent

data class PlannedStep(
    val stepNumber: Int,
    val description: String,
    val suggestedTool: String? = null,
    val suggestedArgs: Map<String, String> = emptyMap()
)

data class TaskPlan(
    val originalPrompt: String,
    val isMultiStep: Boolean,
    val steps: List<PlannedStep>
)

class TaskPlanner {

    fun planTask(prompt: String): TaskPlan {
        val lower = prompt.trim().lowercase()
        val steps = mutableListOf<PlannedStep>()

        // Image generation requests
        val isImageGen = (lower.contains("image") || lower.contains("tasweer") || lower.contains("tasveer") || lower.contains("photo") || lower.contains("picture")) &&
                (lower.contains("banao") || lower.contains("bana do") || lower.contains("generate") || lower.contains("create") || lower.contains("draw") || lower.contains("paint")) ||
                lower.startsWith("create an image") || lower.startsWith("generate an image") || lower.startsWith("draw")

        if (isImageGen && !lower.contains("bhejo") && !lower.contains("send") && !lower.contains("share")) {
            val imagePrompt = extractImageGenerationPrompt(prompt)
            steps.add(
                PlannedStep(
                    1,
                    "Generate image: $imagePrompt",
                    suggestedTool = "generate_image",
                    suggestedArgs = mapOf("prompt" to imagePrompt)
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // WhatsApp share image: "WhatsApp kholo aur Ali ko ye image bhejo" / "Ali ko image bhejo"
        if ((lower.contains("image") || lower.contains("tasweer") || lower.contains("photo")) && (lower.contains("bhejo") || lower.contains("send") || lower.contains("share"))) {
            val recipient = extractWhatsAppRecipient(prompt)
            steps.add(
                PlannedStep(
                    1,
                    "Share generated image via WhatsApp to $recipient",
                    suggestedTool = "share_image",
                    suggestedArgs = mapOf("recipient_app" to "com.whatsapp", "caption" to "Sent via Snow AI")
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // Save image to gallery: "Image save karo" / "Gallery mein save kar do"
        if ((lower.contains("image") || lower.contains("photo") || lower.contains("picture")) && (lower.contains("save") || lower.contains("gallery"))) {
            steps.add(
                PlannedStep(
                    1,
                    "Save image to device gallery",
                    suggestedTool = "save_image_to_gallery",
                    suggestedArgs = emptyMap()
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // 1. WhatsApp direct commands:
        // "Ali ko WhatsApp par hello bhejo" / "WhatsApp par Ali ko hi bolo" / "send hello to ali on whatsapp"
        if (lower.contains("whatsapp") && (lower.contains("bhejo") || lower.contains("send") || lower.contains("message") || lower.contains("bolo") || lower.contains("karo"))) {
            val recipient = extractWhatsAppRecipient(prompt)
            val msg = extractWhatsAppMessage(prompt)
            steps.add(
                PlannedStep(
                    1,
                    "Send WhatsApp message to $recipient",
                    suggestedTool = "send_whatsapp",
                    suggestedArgs = mapOf("recipient" to recipient, "message" to msg)
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // 2. Folder creation:
        // "Downloads mein Snow folder banao" / "File Manager mein Snow naam ka folder banao" / "create folder Snow"
        if ((lower.contains("folder") || lower.contains("directory")) && (lower.contains("banao") || lower.contains("create") || lower.contains("make"))) {
            val folderName = extractFolderName(prompt)
            val location = if (lower.contains("document")) "Documents" else "Downloads"
            steps.add(
                PlannedStep(
                    1,
                    "Create folder '$folderName' in $location",
                    suggestedTool = "create_folder",
                    suggestedArgs = mapOf("folder_name" to folderName, "location" to location)
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // 3. File creation:
        // "Snow folder mein test.txt file banao" / "create file test.txt in Snow folder"
        if ((lower.contains("file") || lower.contains(".txt")) && (lower.contains("banao") || lower.contains("create") || lower.contains("write"))) {
            val folderName = extractFolderName(prompt).ifBlank { "Snow" }
            val fileName = extractFileName(prompt)
            steps.add(
                PlannedStep(
                    1,
                    "Create file '$fileName' in folder '$folderName'",
                    suggestedTool = "create_file",
                    suggestedArgs = mapOf("folder_name" to folderName, "file_name" to fileName, "content" to "Created by Snow AI Assistant.")
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // 4. File / Folder open:
        // "Snow folder kholo" / "open Snow folder"
        if ((lower.contains("folder") || lower.contains("file manager")) && (lower.contains("kholo") || lower.contains("open"))) {
            val folderName = extractFolderName(prompt).ifBlank { "Snow" }
            steps.add(
                PlannedStep(
                    1,
                    "Open folder '$folderName'",
                    suggestedTool = "open_folder",
                    suggestedArgs = mapOf("folder_name" to folderName)
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // 5. Screen Reading & Screenshot:
        // "Screen padho" / "screen par kya hai" / "read screen"
        if (lower.contains("screen") && (lower.contains("padho") || lower.contains("read") || lower.contains("kya hai") || lower.contains("what is on"))) {
            steps.add(
                PlannedStep(
                    1,
                    "Read current screen UI hierarchy",
                    suggestedTool = "read_screen"
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        if (lower.contains("screenshot") && (lower.contains("lo") || lower.contains("take") || lower.contains("capture"))) {
            steps.add(
                PlannedStep(
                    1,
                    "Capture screen image",
                    suggestedTool = "take_screenshot"
                )
            )
            return TaskPlan(prompt, isMultiStep = false, steps)
        }

        // 6. Arrow / Chain multi-intent ("->", "phir", "then", "and then")
        val chainDelimiters = listOf("->", " then ", " and then ", " after that ", " aur phir ", " phir ")
        for (delim in chainDelimiters) {
            if (lower.contains(delim)) {
                val parts = prompt.split(Regex(Regex.escape(delim), RegexOption.IGNORE_CASE))
                if (parts.size >= 2) {
                    parts.forEachIndexed { index, part ->
                        steps.add(PlannedStep(index + 1, part.trim()))
                    }
                    return TaskPlan(prompt, isMultiStep = true, steps)
                }
            }
        }

        // Single step default
        return TaskPlan(prompt, isMultiStep = false, listOf(PlannedStep(1, prompt)))
    }

    private fun extractWhatsAppRecipient(prompt: String): String {
        // e.g. "Ali ko WhatsApp par hello bhejo" or "WhatsApp par Ali ko message bhejo"
        val regexKo = Regex("""([A-Z][a-z]+|[a-zA-Z0-9+]+)\s+(?:ko|per|par)\s+(?:whatsapp)""", RegexOption.IGNORE_CASE)
        val matchKo = regexKo.find(prompt)
        if (matchKo != null) return matchKo.groupValues[1].trim()

        val regexPar = Regex("""(?:whatsapp)\s+(?:par|per|on)\s+([A-Z][a-z]+|[a-zA-Z0-9+]+)""", RegexOption.IGNORE_CASE)
        val matchPar = regexPar.find(prompt)
        if (matchPar != null) return matchPar.groupValues[1].trim()

        val regexTo = Regex("""(?:message|to|send)\s+([A-Z][a-z]+|[a-zA-Z0-9+]+)""", RegexOption.IGNORE_CASE)
        val matchTo = regexTo.find(prompt)
        if (matchTo != null) return matchTo.groupValues[1].trim()

        return "Ali"
    }

    private fun extractWhatsAppMessage(prompt: String): String {
        // Look for quoted or explicit message text
        val quoteRegex = Regex("""["'](.*?)["']""")
        val quoteMatch = quoteRegex.find(prompt)
        if (quoteMatch != null) return quoteMatch.groupValues[1]

        val lower = prompt.lowercase()
        return when {
            lower.contains("hello") -> "Hello!"
            lower.contains("hi") -> "Hi!"
            lower.contains("salam") -> "Assalam o Alaikum"
            lower.contains("kaise ho") -> "Kaise ho?"
            prompt.contains("saying", ignoreCase = true) -> prompt.substringAfter("saying").trim()
            prompt.contains("bhejo", ignoreCase = true) -> {
                prompt.substringBefore("bhejo").substringAfter("ko ").substringAfter("par ").trim()
                    .ifBlank { "Hello!" }
            }
            else -> "Hello from Snow AI"
        }
    }

    private fun extractFolderName(prompt: String): String {
        val regex = Regex("""(?i)(?:folder|naam ka folder|directory)\s+(?:called\s+)?([A-Za-z0-9_-]+)""")
        val match = regex.find(prompt)
        if (match != null) return match.groupValues[1]

        val regexBefore = Regex("""(?i)([A-Za-z0-9_-]+)\s+(?:naam ka folder|folder)""")
        val matchBefore = regexBefore.find(prompt)
        if (matchBefore != null) return matchBefore.groupValues[1]

        return "Snow"
    }

    private fun extractFileName(prompt: String): String {
        val regex = Regex("""([A-Za-z0-9_-]+\.[a-zA-Z0-9]+)""")
        val match = regex.find(prompt)
        return match?.groupValues?.getOrNull(1) ?: "test.txt"
    }

    private fun extractImageGenerationPrompt(prompt: String): String {
        var clean = prompt.trim()
        val removePrefixes = listOf(
            "(?i)^(snow\\s+)?(meri\\s+)?(ek\\s+)?",
            "(?i)^(snow\\s+)?(please\\s+)?(create|generate|draw|make|paint)\\s+(an?\\s+)?(image|picture|photo|illustration)\\s+(of\\s+)?",
            "(?i)^(snow\\s+)?(mujhe\\s+)?(ek\\s+)?"
        )
        for (prefix in removePrefixes) {
            clean = clean.replace(Regex(prefix), "").trim()
        }
        val removeSuffixes = listOf(
            "(?i)\\s*(ki\\s+)?(image|tasweer|tasveer|photo|pic|picture)\\s*(banao|bana do|generate karo|generate kar do|create karo|chahiye)\\s*$",
            "(?i)\\s*(image|picture|photo)\\s*(generate|create|draw)\\s*(karo|kar do)?\\s*$",
            "(?i)\\s*(generate|banao|bana do)\\s*$"
        )
        for (suffix in removeSuffixes) {
            clean = clean.replace(Regex(suffix), "").trim()
        }
        return if (clean.length < 3) prompt.trim() else clean
    }
}
