package com.potero.service.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Parses tool calls from LLM responses using ```tool``` markdown code blocks.
 *
 * POSTECH GenAI API doesn't support native function calling, so we use a custom
 * implementation where the LLM returns tool calls in markdown code blocks with
 * the 'tool' language identifier.
 *
 * Example format:
 * ```tool
 * {
 *   "name": "highlight_text",
 *   "arguments": {
 *     "text": "transformer architecture",
 *     "context": "optional surrounding text"
 *   }
 * }
 * ```
 */
class ToolCallParser {
    companion object {
        /**
         * Regex to extract ```tool blocks from LLM response.
         * Matches: ```tool\n...\n```
         */
        private val TOOL_BLOCK_REGEX = Regex(
            pattern = "```tool\\s*\\n([\\s\\S]*?)\\n```",
            option = RegexOption.MULTILINE
        )

        /**
         * JSON configuration for parsing tool calls.
         * Ignores unknown keys for forward compatibility.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * Parse tool calls from LLM response.
     *
     * @param llmResponse The raw response from the LLM
     * @return ToolCallParseResult containing extracted tool calls and cleaned text
     */
    fun parse(llmResponse: String): ToolCallParseResult {
        val toolCalls = mutableListOf<ParsedToolCall>()
        val matches = TOOL_BLOCK_REGEX.findAll(llmResponse)

        for (match in matches) {
            val jsonContent = match.groupValues[1].trim()
            try {
                // Parse JSON content
                val rawToolCall = json.decodeFromString<RawToolCall>(jsonContent)

                toolCalls.add(
                    ParsedToolCall(
                        name = rawToolCall.name,
                        arguments = rawToolCall.arguments,
                        rawJson = jsonContent,
                        error = null
                    )
                )
            } catch (e: Exception) {
                // Malformed tool call - include in errors
                toolCalls.add(
                    ParsedToolCall(
                        name = "invalid",
                        arguments = emptyMap(),
                        rawJson = jsonContent,
                        error = "Failed to parse tool call: ${e.message}"
                    )
                )
            }
        }

        // Remove tool blocks from text
        var cleanText = TOOL_BLOCK_REGEX.replace(llmResponse, "")
            .trim()

        // Remove HTML comments (e.g., <!-- tools: none -->)
        cleanText = cleanText.replace(Regex("<!--[\\s\\S]*?-->"), "")
            .trim()

        // Clean up extra newlines
        cleanText = cleanText.replace(Regex("\\n{3,}"), "\n\n")

        return ToolCallParseResult(
            toolCalls = toolCalls,
            text = cleanText,
            hasToolCalls = toolCalls.isNotEmpty()
        )
    }
}

/**
 * Raw tool call structure as returned by the LLM.
 */
@Serializable
data class RawToolCall(
    val name: String,
    val arguments: Map<String, JsonElement>
)

/**
 * Parsed tool call with validation status.
 *
 * @property name Tool name (or "invalid" if parsing failed)
 * @property arguments Tool arguments as JsonElement map
 * @property rawJson Original JSON string for debugging
 * @property error Error message if parsing failed, null otherwise
 */
data class ParsedToolCall(
    val name: String,
    val arguments: Map<String, JsonElement>,
    val rawJson: String,
    val error: String? = null
) {
    /**
     * Check if this tool call is valid (no parsing errors).
     */
    val isValid: Boolean get() = error == null && name != "invalid"

    /**
     * Get a string argument value.
     */
    fun getStringArg(key: String): String? {
        val element = arguments[key] ?: return null
        return when (element) {
            is JsonPrimitive -> element.contentOrNull
            else -> null
        }
    }

    /**
     * Get an integer argument value.
     */
    fun getIntArg(key: String): Int? {
        val element = arguments[key] ?: return null
        return when (element) {
            is JsonPrimitive -> element.intOrNull
            else -> null
        }
    }

    /**
     * Get a boolean argument value.
     */
    fun getBooleanArg(key: String): Boolean? {
        val element = arguments[key] ?: return null
        return when (element) {
            is JsonPrimitive -> element.booleanOrNull
            else -> null
        }
    }

    /**
     * Get a double argument value.
     */
    fun getDoubleArg(key: String): Double? {
        val element = arguments[key] ?: return null
        return when (element) {
            is JsonPrimitive -> element.doubleOrNull
            else -> null
        }
    }

    /**
     * Get an array argument value as list of strings.
     */
    fun getStringArrayArg(key: String): List<String>? {
        val element = arguments[key] ?: return null
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> null
        }
    }
}

/**
 * Result of parsing tool calls from LLM response.
 *
 * @property toolCalls List of parsed tool calls (may include invalid ones)
 * @property text LLM response text with tool blocks removed
 * @property hasToolCalls True if at least one tool call was found
 */
data class ToolCallParseResult(
    val toolCalls: List<ParsedToolCall>,
    val text: String,
    val hasToolCalls: Boolean
) {
    /**
     * Get only valid tool calls (exclude parse errors).
     */
    val validToolCalls: List<ParsedToolCall>
        get() = toolCalls.filter { it.isValid }

    /**
     * Get only invalid tool calls (parse errors).
     */
    val invalidToolCalls: List<ParsedToolCall>
        get() = toolCalls.filter { !it.isValid }

    /**
     * Check if all tool calls are valid.
     */
    val allValid: Boolean
        get() = toolCalls.isNotEmpty() && toolCalls.all { it.isValid }
}
