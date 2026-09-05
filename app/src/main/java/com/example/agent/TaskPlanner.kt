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

        // 1. "Open YouTube and search for Python tutorials"
        if (lower.contains("and search") || (lower.contains("open") && lower.contains("search for"))) {
            steps.add(PlannedStep(1, "Open requested application or browser"))
            val query = prompt.substringAfter("search for").substringAfter("search").trim()
            steps.add(PlannedStep(2, "Search for '$query'", "web_search", mapOf("query" to query)))
            return TaskPlan(prompt, isMultiStep = true, steps)
        }

        // 2. "Find Ali's contact and prepare a message..."
        if ((lower.contains("contact") || lower.contains("find")) && (lower.contains("message") || lower.contains("whatsapp") || lower.contains("sms"))) {
            val name = extractName(prompt)
            steps.add(PlannedStep(1, "Look up contact details for $name", "search_contacts", mapOf("name" to name)))
            steps.add(PlannedStep(2, "Prepare message to send", "send_whatsapp", mapOf("recipient" to name, "message" to extractMessage(prompt))))
            return TaskPlan(prompt, isMultiStep = true, steps)
        }

        // 3. Multi-intent connectives ("then", "after that", "aur", "phir")
        val splitDelimiters = listOf(" then ", " and then ", " after that ", " aur phir ", " aur ")
        for (delim in splitDelimiters) {
            if (lower.contains(delim)) {
                val parts = prompt.split(Regex(delim, RegexOption.IGNORE_CASE))
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

    private fun extractName(prompt: String): String {
        val regex = Regex("""(?i)(?:find|contact|message|for|call)\s+([A-Z][a-z]+|Ali|Ahmed|Sara|Hamza|Usman)""")
        val match = regex.find(prompt)
        return match?.groupValues?.getOrNull(1) ?: "Contact"
    }

    private fun extractMessage(prompt: String): String {
        return when {
            prompt.contains("saying", ignoreCase = true) -> prompt.substringAfter("saying").trim()
            prompt.contains("that", ignoreCase = true) -> prompt.substringAfter("that").trim()
            else -> "I will call you later."
        }
    }
}
