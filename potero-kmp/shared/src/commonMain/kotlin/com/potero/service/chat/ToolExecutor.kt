package com.potero.service.chat

import com.potero.service.chat.tools.*
import com.potero.service.llm.LLMLogger
import com.potero.service.llm.LLMProvider
import kotlinx.serialization.json.*

/**
 * Executes validated tool calls.
 *
 * Responsibilities:
 * - Validate tool exists
 * - Validate and convert arguments
 * - Check paper context requirement
 * - Execute tool
 * - Log execution
 * - Handle errors
 */
class ToolExecutor(
    private val tools: Map<String, ChatTool>,
    private val llmLogger: LLMLogger
) {
    /**
     * Execute a parsed tool call.
     *
     * @param toolCall Parsed tool call from LLM
     * @param context Execution context (paperId, sessionId)
     * @return ToolExecutionResult with status and data
     */
    suspend fun execute(
        toolCall: ParsedToolCall,
        context: ToolExecutionContext
    ): ToolExecutionResult {
        // Check if tool call is valid (no parse errors)
        if (!toolCall.isValid) {
            return ToolExecutionResult(
                toolName = toolCall.name,
                success = false,
                error = toolCall.error ?: "Invalid tool call"
            )
        }

        // Get tool definition
        val tool = tools[toolCall.name]
        if (tool == null) {
            return ToolExecutionResult(
                toolName = toolCall.name,
                success = false,
                error = "Unknown tool: ${toolCall.name}. Available tools: ${tools.keys.joinToString()}"
            )
        }

        // Check if tool requires paper context
        if (tool.definition.requiresPaper && context.paperId == null) {
            return ToolExecutionResult(
                toolName = toolCall.name,
                success = false,
                error = "This tool requires a paper context. Please open a paper first."
            )
        }

        // Validate and convert arguments
        val validatedArgs = try {
            validateAndConvertArguments(toolCall.arguments, tool.definition.parameters)
        } catch (e: Exception) {
            return ToolExecutionResult(
                toolName = toolCall.name,
                success = false,
                error = "Invalid arguments: ${e.message}"
            )
        }

        // Execute tool
        val startTime = System.currentTimeMillis()
        val result = try {
            tool.execute(validatedArgs, context)
        } catch (e: Exception) {
            println("[ToolExecutor] Tool execution failed: ${toolCall.name} - ${e.message}")
            e.printStackTrace()
            ToolResult(
                success = false,
                error = "Tool execution failed: ${e.message}"
            )
        }
        val duration = System.currentTimeMillis() - startTime

        // Log execution
        llmLogger.log(
            provider = LLMProvider.GPT, // Not applicable for tools, but required
            purpose = "tool_execution",
            inputPrompt = "Tool: ${toolCall.name}, Args: ${toolCall.rawJson}",
            outputResponse = if (result.success) {
                "Success: ${result.data}"
            } else {
                "Error: ${result.error}"
            },
            durationMs = duration,
            success = result.success,
            errorMessage = result.error
        )

        return ToolExecutionResult(
            toolName = toolCall.name,
            success = result.success,
            data = result.data,
            error = result.error,
            metadata = result.metadata,
            durationMs = duration
        )
    }

    /**
     * Validate and convert arguments from JsonElement to proper types.
     *
     * @param args Raw arguments as JsonElement map
     * @param params Parameter definitions
     * @return Validated arguments as Any map
     * @throws IllegalArgumentException if validation fails
     */
    private fun validateAndConvertArguments(
        args: Map<String, JsonElement>,
        params: Map<String, ToolParameter>
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        // Check required parameters
        for ((paramName, param) in params) {
            if (param.required && paramName !in args) {
                throw IllegalArgumentException("Missing required parameter: $paramName")
            }
        }

        // Convert and validate each argument
        for ((paramName, jsonValue) in args) {
            val param = params[paramName]
                ?: continue // Skip unknown parameters (forward compatibility)

            val value = convertArgument(jsonValue, param.type, paramName)
            result[paramName] = value
        }

        // Add default values for missing optional parameters
        for ((paramName, param) in params) {
            if (!param.required && paramName !in result && param.default != null) {
                result[paramName] = param.default
            }
        }

        return result
    }

    /**
     * Convert JsonElement to proper type based on parameter definition.
     *
     * @param jsonValue JSON value
     * @param type Expected type ("string", "number", "boolean", "array")
     * @param paramName Parameter name (for error messages)
     * @return Converted value
     * @throws IllegalArgumentException if conversion fails
     */
    private fun convertArgument(
        jsonValue: JsonElement,
        type: String,
        paramName: String
    ): Any {
        return when (type.lowercase()) {
            "string" -> {
                (jsonValue as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("Parameter '$paramName' must be a string")
            }
            "number" -> {
                val primitive = jsonValue as? JsonPrimitive
                    ?: throw IllegalArgumentException("Parameter '$paramName' must be a number")
                primitive.intOrNull ?: primitive.doubleOrNull
                    ?: throw IllegalArgumentException("Parameter '$paramName' must be a valid number")
            }
            "boolean" -> {
                (jsonValue as? JsonPrimitive)?.booleanOrNull
                    ?: throw IllegalArgumentException("Parameter '$paramName' must be a boolean")
            }
            "array" -> {
                val array = jsonValue as? JsonArray
                    ?: throw IllegalArgumentException("Parameter '$paramName' must be an array")
                array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }
            else -> {
                throw IllegalArgumentException("Unknown parameter type: $type")
            }
        }
    }
}

/**
 * Result of executing a tool.
 *
 * @property toolName Tool that was executed
 * @property success Whether execution succeeded
 * @property data Result data (tool-specific format)
 * @property error Error message if execution failed
 * @property metadata Additional metadata
 * @property durationMs Execution time in milliseconds
 */
data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val durationMs: Long = 0
) {
    /**
     * Convert to DTO for API responses.
     */
    fun toDto(): ToolExecutionDto {
        // Convert data to JsonElement
        val dataJson = if (data != null) {
            try {
                // Handle common data types
                when (data) {
                    is String -> JsonPrimitive(data)
                    is Number -> JsonPrimitive(data)
                    is Boolean -> JsonPrimitive(data)
                    is Map<*, *> -> buildJsonObject {
                        data.forEach { (k, v) ->
                            val key = k.toString()
                            put(key, when (v) {
                                is String -> JsonPrimitive(v)
                                is Number -> JsonPrimitive(v)
                                is Boolean -> JsonPrimitive(v)
                                null -> JsonNull
                                else -> JsonPrimitive(v.toString())
                            })
                        }
                    }
                    is List<*> -> buildJsonArray {
                        data.forEach { item ->
                            add(when (item) {
                                is String -> JsonPrimitive(item)
                                is Number -> JsonPrimitive(item)
                                is Boolean -> JsonPrimitive(item)
                                null -> JsonNull
                                else -> JsonPrimitive(item.toString())
                            })
                        }
                    }
                    else -> JsonPrimitive(data.toString())
                }
            } catch (e: Exception) {
                JsonPrimitive(data.toString())
            }
        } else {
            null
        }

        return ToolExecutionDto(
            toolName = toolName,
            success = success,
            data = dataJson,
            error = error,
            durationMs = durationMs
        )
    }
}
