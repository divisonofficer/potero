package com.potero.service.chat.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Interface for chat tools that can be invoked by the LLM.
 *
 * Each tool should:
 * - Define its metadata (name, description, parameters)
 * - Implement the execution logic
 * - Handle errors gracefully
 */
interface ChatTool {
    /**
     * Tool definition (metadata) for LLM prompt generation.
     */
    val definition: ToolDefinition

    /**
     * Execute the tool with given arguments and context.
     *
     * @param arguments Tool arguments as raw map
     * @param context Execution context (paperId, sessionId, etc.)
     * @return ToolResult with execution status and data
     */
    suspend fun execute(
        arguments: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult
}

/**
 * Tool definition (metadata) used for generating system prompts.
 *
 * @property name Unique tool identifier (e.g., "highlight_text")
 * @property description What the tool does (shown to LLM)
 * @property parameters Parameter definitions with types and descriptions
 * @property requiresPaper Whether tool requires a paper context (paperId)
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>,
    val requiresPaper: Boolean = false
)

/**
 * Parameter definition for a tool.
 *
 * @property type Parameter type: "string", "number", "boolean", "array"
 * @property description What the parameter is used for
 * @property required Whether parameter is required
 * @property default Default value if not provided
 */
data class ToolParameter(
    val type: String, // "string", "number", "boolean", "array"
    val description: String,
    val required: Boolean = true,
    val default: Any? = null
)

/**
 * Context information for tool execution.
 *
 * @property paperId Current paper ID (if in PDF context mode)
 * @property sessionId Chat session ID
 * @property userId User ID (optional, for future multi-user support)
 */
data class ToolExecutionContext(
    val paperId: String?,
    val sessionId: String,
    val userId: String? = null
)

/**
 * Result of tool execution.
 *
 * @property success Whether execution succeeded
 * @property data Result data (tool-specific format)
 * @property error Error message if execution failed
 * @property metadata Additional metadata (e.g., execution time, page numbers)
 */
data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Create a successful result.
         */
        fun success(data: Any? = null, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return ToolResult(
                success = true,
                data = data,
                error = null,
                metadata = metadata
            )
        }

        /**
         * Create a failure result.
         */
        fun failure(error: String, metadata: Map<String, Any> = emptyMap()): ToolResult {
            return ToolResult(
                success = false,
                data = null,
                error = error,
                metadata = metadata
            )
        }
    }
}

/**
 * Serializable version of tool execution result (for API responses).
 */
@Serializable
data class ToolExecutionDto(
    val toolName: String,
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null,
    val durationMs: Long = 0
)

/**
 * Exception for tool execution errors.
 */
class ToolExecutionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
