package com.potero.service.chat

import com.potero.domain.model.Paper
import com.potero.service.chat.tools.ToolDefinition

/**
 * Generates system prompts for tool-enabled chat.
 *
 * The LLM is instructed to use ```tool``` code blocks to call tools.
 */
object ToolCallingPrompts {
    /**
     * Build system prompt with tool definitions.
     *
     * @param availableTools List of tool definitions to include
     * @param currentPaper Current paper context (if any)
     * @return System prompt string
     */
    fun buildSystemPrompt(
        availableTools: List<ToolDefinition>,
        currentPaper: Paper? = null
    ): String {
        return buildString {
            appendLine("You are a research assistant helping users understand academic papers.")
            appendLine()

            if (availableTools.isNotEmpty()) {
                appendLine("You have access to the following tools:")
                appendLine()

                availableTools.forEach { tool ->
                    appendLine("## ${tool.name}")
                    appendLine(tool.description)
                    appendLine()

                    if (tool.parameters.isNotEmpty()) {
                        appendLine("Parameters:")
                        tool.parameters.forEach { (name, param) ->
                            val required = if (param.required) "(required)" else "(optional)"
                            val defaultInfo = if (param.default != null) " [default: ${param.default}]" else ""
                            appendLine("- $name: ${param.description} $required$defaultInfo")
                        }
                        appendLine()
                    }
                }

                appendLine("To use a tool, respond with a code block:")
                appendLine("```tool")
                appendLine("""
{
  "name": "tool_name",
  "arguments": {
    "param": "value"
  }
}
                """.trimIndent())
                appendLine("```")
                appendLine()
                appendLine("You can call tools multiple times in one response.")
                appendLine("After tool results, you'll receive them and can continue the conversation.")
                appendLine()
            }

            if (currentPaper != null) {
                appendLine("Current paper:")
                appendLine("- Title: ${currentPaper.title}")
                appendLine("- Authors: ${currentPaper.formattedAuthors}")
                currentPaper.year?.let { appendLine("- Year: $it") }
                currentPaper.conference?.let { appendLine("- Conference: $it") }
                appendLine()
            }

            appendLine("Guidelines:")
            appendLine("- Be concise and accurate")
            appendLine("- When answering questions about a paper, use tools to find specific information")
            appendLine("- Always cite specific sections or page numbers when possible")
            appendLine("- If you don't know something, say so rather than guessing")
        }
    }

    /**
     * Build prompt for tool results feedback to LLM.
     *
     * @param originalMessage User's original message
     * @param llmResponse LLM's previous response with tool calls
     * @param toolResults List of tool execution results
     * @return Prompt string for continuation
     */
    fun buildToolResultsPrompt(
        originalMessage: String,
        llmResponse: String,
        toolResults: List<ToolExecutionResult>
    ): String {
        return buildString {
            appendLine("Your previous response:")
            appendLine(llmResponse)
            appendLine()
            appendLine("Tool execution results:")
            toolResults.forEach { result ->
                appendLine("Tool: ${result.toolName}")
                appendLine("Success: ${result.success}")
                if (result.success) {
                    appendLine("Result: ${formatToolResult(result.data)}")
                } else {
                    appendLine("Error: ${result.error}")
                }
                if (result.metadata.isNotEmpty()) {
                    appendLine("Metadata: ${result.metadata}")
                }
                appendLine()
            }
            appendLine("Please continue your response to the user's original question:")
            appendLine("\"$originalMessage\"")
            appendLine()
            appendLine("You can call more tools if needed, or provide your final answer.")
        }
    }

    /**
     * Format tool result data for display in prompt.
     *
     * @param data Tool result data
     * @return Formatted string
     */
    private fun formatToolResult(data: Any?): String {
        if (data == null) return "null"

        return when (data) {
            is String -> data
            is Number -> data.toString()
            is Boolean -> data.toString()
            is Map<*, *> -> {
                data.entries.joinToString(", ", "{", "}") { (k, v) ->
                    "$k: ${formatToolResult(v)}"
                }
            }
            is List<*> -> {
                data.joinToString(", ", "[", "]") { formatToolResult(it) }
            }
            else -> data.toString()
        }
    }

    /**
     * Few-shot examples for teaching LLM how to use tools.
     */
    val FEW_SHOT_EXAMPLES = listOf(
        ChatExample(
            user = "Can you highlight where they discuss the transformer architecture?",
            assistant = """I'll search for and highlight that section.

```tool
{
  "name": "search_current_paper",
  "arguments": {
    "query": "transformer architecture"
  }
}
```"""
        ),
        ChatExample(
            user = "Find related papers about attention mechanisms in my library",
            assistant = """Let me search your library for papers about attention mechanisms.

```tool
{
  "name": "search_library",
  "arguments": {
    "query": "attention mechanism",
    "limit": 5
  }
}
```"""
        ),
        ChatExample(
            user = "Where is the methodology section?",
            assistant = """I'll help you navigate to the methodology section.

```tool
{
  "name": "scroll_to_location",
  "arguments": {
    "section": "methodology"
  }
}
```"""
        )
    )

    /**
     * Build prompt with few-shot examples (for initial training/testing).
     *
     * @param availableTools List of tool definitions
     * @param currentPaper Current paper context
     * @param includeExamples Whether to include few-shot examples
     * @return System prompt with optional examples
     */
    fun buildEnhancedSystemPrompt(
        availableTools: List<ToolDefinition>,
        currentPaper: Paper? = null,
        includeExamples: Boolean = false
    ): String {
        val basePrompt = buildSystemPrompt(availableTools, currentPaper)

        return if (includeExamples && FEW_SHOT_EXAMPLES.isNotEmpty()) {
            buildString {
                append(basePrompt)
                appendLine()
                appendLine("Examples of tool usage:")
                appendLine()
                FEW_SHOT_EXAMPLES.forEach { example ->
                    appendLine("User: ${example.user}")
                    appendLine("Assistant: ${example.assistant}")
                    appendLine()
                }
            }
        } else {
            basePrompt
        }
    }
}

/**
 * Example chat exchange for few-shot learning.
 *
 * @property user User message
 * @property assistant Assistant response (with tool calls)
 */
data class ChatExample(
    val user: String,
    val assistant: String
)
