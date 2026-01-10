package com.potero.service.chat

import com.potero.domain.model.Paper
import com.potero.domain.repository.PaperRepository
import com.potero.service.chat.tools.ToolExecutionContext
import com.potero.service.chat.tools.ToolExecutionDto
import com.potero.service.llm.LLMService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * Chat service with tool calling support.
 *
 * Implements multi-turn tool calling loop:
 * 1. User message → LLM (with system prompt and tools)
 * 2. Parse tool calls from response
 * 3. Execute tools
 * 4. Feed results back to LLM
 * 5. Repeat until no more tool calls (max 5 iterations)
 * 6. Return final response
 */
class ChatService(
    private val llmService: LLMService,
    private val toolRegistry: ToolRegistry,
    private val toolParser: ToolCallParser,
    private val toolExecutor: ToolExecutor,
    private val paperRepository: PaperRepository
) {
    companion object {
        /**
         * Maximum number of tool calling iterations to prevent infinite loops.
         */
        const val MAX_ITERATIONS = 5
    }

    /**
     * Send message with tool calling support.
     *
     * @param sessionId Chat session ID
     * @param message User message
     * @param paperId Paper ID (if in PDF context mode)
     * @param conversationHistory Previous messages in the conversation
     * @return ChatResult with final response and tool executions
     */
    suspend fun sendMessage(
        sessionId: String,
        message: String,
        paperId: String?,
        conversationHistory: List<ChatMessageDto>
    ): Result<ChatResult> = runCatching {
        println("[ChatService] Processing message: $message (paperId: $paperId)")

        val context = ToolExecutionContext(
            paperId = paperId,
            sessionId = sessionId
        )

        // Build initial prompt with system message + history + user message
        val paper = paperId?.let { paperRepository.getById(it).getOrNull() }
        val initialPrompt = buildPromptWithTools(message, paper, conversationHistory)

        // Multi-turn tool calling loop
        var iteration = 0
        var currentPrompt = initialPrompt
        val allToolExecutions = mutableListOf<ToolExecutionResult>()
        var finalResponse = ""

        while (iteration < MAX_ITERATIONS) {
            println("[ChatService] Iteration ${iteration + 1}/$MAX_ITERATIONS")

            // Call LLM
            val llmResponse = llmService.chat(currentPrompt).getOrThrow()
            println("[ChatService] LLM response length: ${llmResponse.length} chars")

            // Parse tool calls
            val parseResult = toolParser.parse(llmResponse)
            println("[ChatService] Found ${parseResult.toolCalls.size} tool calls")

            if (!parseResult.hasToolCalls) {
                // No more tools to call, return final response
                finalResponse = parseResult.text
                println("[ChatService] No tool calls found, returning final response")
                break
            }

            // Check if all tool calls are invalid (parse errors)
            if (parseResult.invalidToolCalls.isNotEmpty() && parseResult.validToolCalls.isEmpty()) {
                // All tool calls failed to parse, return error response
                val errors = parseResult.invalidToolCalls.joinToString(", ") { it.error ?: "Unknown error" }
                println("[ChatService] All tool calls invalid: $errors")
                finalResponse = parseResult.text + "\n\n[Note: I tried to use tools but encountered errors: $errors]"
                break
            }

            // Execute valid tool calls
            val toolResults = mutableListOf<ToolExecutionResult>()
            for (toolCall in parseResult.validToolCalls) {
                println("[ChatService] Executing tool: ${toolCall.name}")
                val result = toolExecutor.execute(toolCall, context)
                toolResults.add(result)
                println("[ChatService] Tool ${toolCall.name}: ${if (result.success) "success" else "failed (${result.error})"}")
            }

            allToolExecutions.addAll(toolResults)

            // Build continuation prompt with tool results
            currentPrompt = ToolCallingPrompts.buildToolResultsPrompt(
                originalMessage = message,
                llmResponse = llmResponse,
                toolResults = toolResults
            )

            iteration++
        }

        // Check if max iterations reached
        if (iteration >= MAX_ITERATIONS) {
            println("[ChatService] Max iterations reached")
            finalResponse += "\n\n[Note: I executed multiple tool calls but need to stop here. Please rephrase your question if you need more information.]"
        }

        ChatResult(
            content = finalResponse,
            toolExecutions = allToolExecutions
        )
    }

    /**
     * Build prompt with tools and conversation history.
     *
     * @param userMessage User's message
     * @param paper Current paper (if any)
     * @param history Conversation history
     * @return Complete prompt string
     */
    private fun buildPromptWithTools(
        userMessage: String,
        paper: Paper?,
        history: List<ChatMessageDto>
    ): String {
        val availableTools = toolRegistry.getAvailableTools(paper != null)

        return buildString {
            // System prompt with tools
            appendLine(ToolCallingPrompts.buildSystemPrompt(availableTools, paper))
            appendLine()

            // Conversation history (last 10 messages)
            val recentHistory = history.takeLast(10)
            if (recentHistory.isNotEmpty()) {
                appendLine("Previous conversation:")
                recentHistory.forEach { msg ->
                    appendLine("${msg.role}: ${msg.content}")
                }
                appendLine()
            }

            // User message
            appendLine("User: $userMessage")
            appendLine()
            appendLine("Assistant:")
        }
    }

    /**
     * Send message with SSE streaming support.
     *
     * Emits events as processing progresses:
     * - ChatStreamEvent.Start: Processing started
     * - ChatStreamEvent.Delta: Incremental content updates
     * - ChatStreamEvent.ToolCall: Tool execution started
     * - ChatStreamEvent.ToolResult: Tool execution completed
     * - ChatStreamEvent.Done: Final response with all tool executions
     * - ChatStreamEvent.Error: Error occurred
     *
     * @param sessionId Chat session ID
     * @param message User message
     * @param paperId Paper ID (if in PDF context mode)
     * @param conversationHistory Previous messages in the conversation
     * @return Flow of ChatStreamEvent
     */
    fun sendMessageStream(
        sessionId: String,
        message: String,
        paperId: String?,
        conversationHistory: List<ChatMessageDto>
    ): Flow<ChatStreamEvent> = flow {
        emit(ChatStreamEvent.Start)

        val context = ToolExecutionContext(
            paperId = paperId,
            sessionId = sessionId
        )

        // Build initial prompt with system message + history + user message
        val paper = paperId?.let { paperRepository.getById(it).getOrNull() }
        val initialPrompt = buildPromptWithTools(message, paper, conversationHistory)

        // Multi-turn tool calling loop
        var iteration = 0
        var currentPrompt = initialPrompt
        val allToolExecutions = mutableListOf<ToolExecutionResult>()
        var finalResponse = ""

        while (iteration < MAX_ITERATIONS) {
            // Call LLM
            val llmResponse = llmService.chat(currentPrompt).getOrThrow()

            // Stream the LLM response incrementally
            emit(ChatStreamEvent.Delta(llmResponse))

            // Parse tool calls
            val parseResult = toolParser.parse(llmResponse)

            if (!parseResult.hasToolCalls) {
                // No more tools to call, return final response
                finalResponse = parseResult.text
                break
            }

            // Check if all tool calls are invalid
            if (parseResult.invalidToolCalls.isNotEmpty() && parseResult.validToolCalls.isEmpty()) {
                val errors = parseResult.invalidToolCalls.joinToString(", ") { it.error ?: "Unknown error" }
                finalResponse = parseResult.text + "\n\n[Note: I tried to use tools but encountered errors: $errors]"
                break
            }

            // Execute valid tool calls
            for (toolCall in parseResult.validToolCalls) {
                emit(ChatStreamEvent.ToolCall(toolCall.name))

                val result = toolExecutor.execute(toolCall, context)
                allToolExecutions.add(result)

                emit(ChatStreamEvent.ToolResult(
                    toolName = result.toolName,
                    success = result.success,
                    error = result.error
                ))
            }

            // Build continuation prompt with tool results
            currentPrompt = ToolCallingPrompts.buildToolResultsPrompt(
                originalMessage = message,
                llmResponse = llmResponse,
                toolResults = allToolExecutions.takeLast(parseResult.validToolCalls.size)
            )

            iteration++
        }

        // Check if max iterations reached
        if (iteration >= MAX_ITERATIONS) {
            finalResponse += "\n\n[Note: I executed multiple tool calls but need to stop here. Please rephrase your question if you need more information.]"
        }

        emit(ChatStreamEvent.Done(
            content = finalResponse,
            toolExecutions = allToolExecutions.map { it.toDto() }
        ))
    }.catch { e ->
        emit(ChatStreamEvent.Error(e.message ?: "Unknown error"))
    }
}

/**
 * Events emitted during chat streaming.
 */
@Serializable
sealed class ChatStreamEvent {
    /**
     * Processing started.
     */
    @Serializable
    data object Start : ChatStreamEvent()

    /**
     * Incremental content update.
     */
    @Serializable
    data class Delta(val content: String) : ChatStreamEvent()

    /**
     * Tool execution started.
     */
    @Serializable
    data class ToolCall(val toolName: String) : ChatStreamEvent()

    /**
     * Tool execution completed.
     */
    @Serializable
    data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val error: String? = null
    ) : ChatStreamEvent()

    /**
     * Final response with all tool executions.
     */
    @Serializable
    data class Done(
        val content: String,
        val toolExecutions: List<ToolExecutionDto>
    ) : ChatStreamEvent()

    /**
     * Error occurred.
     */
    @Serializable
    data class Error(val message: String) : ChatStreamEvent()
}

/**
 * Result of chat processing.
 *
 * @property content Final response text
 * @property toolExecutions List of tool executions that occurred
 * @property error Error message if processing failed
 */
data class ChatResult(
    val content: String,
    val toolExecutions: List<ToolExecutionResult> = emptyList(),
    val error: String? = null
)

/**
 * Chat message DTO for conversation history.
 *
 * @property id Message ID
 * @property sessionId Session ID
 * @property role Message role ("user" or "assistant")
 * @property content Message content
 * @property model Model name (optional)
 * @property createdAt Timestamp
 */
data class ChatMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val model: String? = null,
    val createdAt: Long
)
