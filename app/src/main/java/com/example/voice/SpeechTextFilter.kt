package com.example.voice

import android.util.Log

/**
 * Dedicated speech filtering layer ensuring strict separation between
 * TEXT OUTPUT and VOICE OUTPUT.
 *
 * Guarantees that source code, JSON, XML, Gradle files, stack traces,
 * terminal output, logs, raw AI tool calls, structured action data,
 * and file contents are NEVER sent to the Text-To-Speech (TTS) engine.
 */
object SpeechTextFilter {

    private const val TAG = "SpeechTextFilter"

    // Regular expressions for detecting and stripping non-speech content
    private val MARKDOWN_CODE_BLOCK_REGEX = Regex("""```[\w-]*\s*\n?[\s\S]*?```""", RegexOption.MULTILINE)
    private val INLINE_CODE_REGEX = Regex("""`([^`]+)`""")
    private val JSON_BLOCK_REGEX = Regex("""(\{\s*"[\s\S]*?"\s*:\s*[\s\S]*?\})|(\[\s*\{[\s\S]*?\}\s*\])""", RegexOption.MULTILINE)
    private val XML_HTML_BLOCK_REGEX = Regex("""<([a-zA-Z0-9_-]+)(\s+[^>]*?)?>[\s\S]*?</\1>|<([a-zA-Z0-9_-]+)(\s+[^>]*?)?/>""")
    private val TOOL_CALL_TOKEN_REGEX = Regex("""\[TOOL_CALL:[\s\S]*?\]""")
    private val ACTION_OBSERVED_REGEX = Regex("""(\[OBSERVED ACTION RESULTS[\s\S]*?\])|(OBSERVED RESULT \[[^\]]+\]:[\s\S]*?(?=\n\n|\z))""")
    private val STACK_TRACE_REGEX = Regex("""(?m)^\s*(at\s+[\w$./]+|Caused\s+by:|Exception\s+in\s+thread|[\w.]*Exception:).*$""")
    private val GRADLE_TASK_REGEX = Regex("""(?m)^\s*(>\s*Task\s*:\S+|BUILD\s+SUCCESSFUL|BUILD\s+FAILED|FAILURE:\s*Build).*$""")
    private val TERMINAL_COMMAND_REGEX = Regex("""(?m)^\s*(\$\s+|>\s*|\#\s*|\b(npm|gradle|git|cd|cat|ls|rm|chmod|curl|wget)\b\s+).*$""")
    private val FILE_PATH_LINE_REGEX = Regex("""(?m)^\s*([a-zA-Z]:\\|/)[^\s]+(\.kt|\.java|\.xml|\.gradle|\.json|\.txt|\.md|\.sh|\.py|\.cpp|\.c)\b.*$""")

    // Keywords that identify a line as source code
    private val CODE_LINE_KEYWORDS = setOf(
        "fun ", "val ", "var ", "class ", "interface ", "object ", "package ", "import ",
        "public ", "private ", "protected ", "internal ", "override ", "return ",
        "void ", "int ", "float ", "double ", "boolean ", "String ",
        "def ", "async ", "await ", "const ", "let ",
        "dependencies {", "plugins {", "android {", "defaultConfig {",
        "implementation(", "api(", "testImplementation("
    )

    /**
     * Filters a raw AI response string to extract ONLY clean, spoken natural language.
     *
     * @param rawText Full AI output containing possible code, tools, or explanations.
     * @param language Target language for polite fallbacks if the message was purely code.
     * @return Sanitized spoken text. If the message was purely code, returns a polite verbal summary.
     */
    fun filterForSpeech(rawText: String, language: String = "English"): String {
        if (rawText.isBlank()) return ""

        var workingText = rawText

        // 1. Strip raw tool call syntax & observation logs
        workingText = TOOL_CALL_TOKEN_REGEX.replace(workingText, "")
        workingText = ACTION_OBSERVED_REGEX.replace(workingText, "")

        // 2. Strip multiline code blocks (``` ... ```)
        workingText = MARKDOWN_CODE_BLOCK_REGEX.replace(workingText, "")

        // 3. Strip JSON structures & XML/HTML blocks
        workingText = JSON_BLOCK_REGEX.replace(workingText, "")
        workingText = XML_HTML_BLOCK_REGEX.replace(workingText, "")

        // 4. Strip stack traces, build outputs, and terminal commands
        workingText = STACK_TRACE_REGEX.replace(workingText, "")
        workingText = GRADLE_TASK_REGEX.replace(workingText, "")
        workingText = TERMINAL_COMMAND_REGEX.replace(workingText, "")
        workingText = FILE_PATH_LINE_REGEX.replace(workingText, "")

        // 5. Line-by-line inspection: filter out isolated code lines, curly braces, and imports
        val cleanedLines = mutableListOf<String>()
        val lines = workingText.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Discard lines that look like isolated code syntax
            if (isLikelyCodeLine(trimmed)) {
                continue
            }

            cleanedLines.add(trimmed)
        }

        var result = cleanedLines.joinToString(" ").trim()

        // 6. Clean inline code markers (`code`)
        result = INLINE_CODE_REGEX.replace(result) { matchResult ->
            val inner = matchResult.groupValues[1]
            // If inner has code-like syntax, drop it; otherwise keep plain word without backticks
            if (isLikelyCodeLine(inner) || inner.contains("(") || inner.contains("=") || inner.contains(";")) {
                ""
            } else {
                inner
            }
        }

        // 7. Strip leftover markdown decoration symbols (#, *, _, ~, >, etc.)
        result = result.replace(Regex("""[#*~_>`]"""), " ")
        result = result.replace(Regex("""\s+"""), " ").trim()

        // 8. If result is empty because the entire message was code/files/JSON:
        // Provide a graceful, natural-language verbal notification.
        if (result.isBlank()) {
            val hasCode = rawText.contains("```") || rawText.contains("fun ") || rawText.contains("class ")
            val hasJson = rawText.contains("{") && rawText.contains("}")
            val hasFile = rawText.contains("import ") || rawText.contains("package ")

            if (hasCode || hasJson || hasFile) {
                return getCodeFallbackSpeech(language)
            }
            return ""
        }

        // 9. Limit length of spoken response to prevent overly verbose reading
        if (result.length > 500) {
            // Cut at sentence boundary
            val sentenceEnd = result.indexOfAny(charArrayOf('.', '!', '?', '۔', '।'), 300)
            if (sentenceEnd != -1 && sentenceEnd < 550) {
                result = result.substring(0, sentenceEnd + 1)
            } else {
                result = result.substring(0, 400).trimEnd() + "…"
            }
        }

        try {
            Log.d(TAG, "Speech filter sanitized length from ${rawText.length} to ${result.length} characters")
        } catch (_: Throwable) {
            // Ignored in unit test environments where android.util.Log is not mocked
        }
        return result
    }

    /**
     * Checks if a single line of text is likely source code or structured syntax.
     */
    private fun isLikelyCodeLine(line: String): Boolean {
        if (line.isEmpty()) return false

        // Check standalone delimiters
        if (line == "{" || line == "}" || line == "};" || line == "];" || line == "(" || line == ")") {
            return true
        }

        // Check common programming keywords
        for (kw in CODE_LINE_KEYWORDS) {
            if (line.startsWith(kw)) return true
        }

        // Semicolon terminated statements or assignment lines
        if (line.endsWith(";") && (line.contains("=") || line.contains("("))) {
            return true
        }

        // Imports / packages
        if (line.startsWith("import ") || line.startsWith("package ") || line.startsWith("#include")) {
            return true
        }

        // JSON properties e.g. "key": "value"
        if (line.startsWith("\"") && line.contains("\":")) {
            return true
        }

        return false
    }

    /**
     * Provides a polite, warm spoken response when the AI output consists purely of code.
     */
    private fun getCodeFallbackSpeech(language: String): String {
        return when (language.trim()) {
            "Urdu", "UR" -> "کوڈ اسکرین پر تیار ہے۔"
            "Hindi", "HI" -> "कोड स्क्रीन पर तैयार है।"
            "Roman Urdu", "ROMAN_UR" -> "Code screen par ready hai."
            "Pashto", "PS" -> "کوډ په سکرین کې چمتو دی."
            else -> "I've placed the code for you on screen."
        }
    }
}
