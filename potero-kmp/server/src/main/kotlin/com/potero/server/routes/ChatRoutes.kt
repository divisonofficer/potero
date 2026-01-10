package com.potero.server.routes

import com.potero.server.di.ServiceLocator
import com.potero.service.genai.GenAIFileResponse
import com.potero.service.chat.tools.ToolExecutionDto
import com.potero.service.chat.ChatStreamEvent
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ChatRequest(
    val sessionId: String? = null,
    val paperId: String? = null,
    val message: String,
    val includeFullText: Boolean = false,
    val files: List<ChatFileAttachment> = emptyList()
)

@Serializable
data class ChatDoneResponse(
    val messageId: String,
    val content: String,
    val toolExecutions: List<ToolExecutionDto>
)

@Serializable
data class ChatFileAttachment(
    val id: String,
    val name: String,
    val url: String
)

@Serializable
data class ChatResponseDto(
    val messageId: String,
    val sessionId: String,
    val role: String = "assistant",
    val content: String,
    val model: String? = null,
    val toolExecutions: List<ToolExecutionDto> = emptyList()  // NEW: Tool calling support
)

@Serializable
data class ChatSessionDto(
    val id: String,
    val paperId: String? = null,
    val title: String,
    val messageCount: Int = 0,
    val lastMessage: String? = null
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val model: String? = null,
    val createdAt: Long
)

// In-memory storage for chat sessions and messages (would be replaced with DB in production)
private val chatSessions = ConcurrentHashMap<String, ChatSessionDto>()
private val chatMessages = ConcurrentHashMap<String, MutableList<ChatMessageDto>>()

fun Route.chatRoutes() {
    val chatService = ServiceLocator.chatService  // NEW: Use ChatService with tool calling
    val llmLogger = ServiceLocator.llmLogger
    val paperRepository = ServiceLocator.paperRepository

    route("/chat") {
        // SSE /api/chat/stream - Send a chat message with SSE streaming
        sse("/stream") {
            try {
                // Parse query parameters (sent as JSON in the SSE request body would be complex,
                // so we use query params for simple data)
                val sessionIdParam = call.request.queryParameters["sessionId"]
                val paperIdParam = call.request.queryParameters["paperId"]
                val messageParam = call.request.queryParameters["message"]
                    ?: throw IllegalArgumentException("Missing message parameter")

                val sessionId = sessionIdParam ?: UUID.randomUUID().toString()

                // Create or update session
                if (!chatSessions.containsKey(sessionId)) {
                    chatSessions[sessionId] = ChatSessionDto(
                        id = sessionId,
                        paperId = paperIdParam,
                        title = messageParam.take(50) + if (messageParam.length > 50) "..." else "",
                        messageCount = 0
                    )
                    chatMessages[sessionId] = mutableListOf()
                }

                // Store user message
                val userMessageId = UUID.randomUUID().toString()
                val userMessage = ChatMessageDto(
                    id = userMessageId,
                    sessionId = sessionId,
                    role = "user",
                    content = messageParam,
                    createdAt = System.currentTimeMillis()
                )
                chatMessages.getOrPut(sessionId) { mutableListOf() }.add(userMessage)

                // Get conversation history
                val history = chatMessages[sessionId]?.takeLast(20)?.map { msg ->
                    com.potero.service.chat.ChatMessageDto(
                        id = msg.id,
                        sessionId = msg.sessionId,
                        role = msg.role,
                        content = msg.content,
                        model = msg.model,
                        createdAt = msg.createdAt
                    )
                } ?: emptyList()

                // Send session ID first
                send(
                    data = Json.encodeToString(mapOf("sessionId" to sessionId)),
                    event = "session"
                )

                // Stream chat events
                chatService.sendMessageStream(
                    sessionId = sessionId,
                    message = messageParam,
                    paperId = paperIdParam,
                    conversationHistory = history
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Start -> {
                            send(
                                data = "{}",
                                event = "start"
                            )
                        }
                        is ChatStreamEvent.Delta -> {
                            send(
                                data = Json.encodeToString(mapOf("content" to event.content)),
                                event = "delta"
                            )
                        }
                        is ChatStreamEvent.ToolCall -> {
                            send(
                                data = Json.encodeToString(mapOf("toolName" to event.toolName)),
                                event = "tool_call"
                            )
                        }
                        is ChatStreamEvent.ToolResult -> {
                            send(
                                data = Json.encodeToString(mapOf(
                                    "toolName" to event.toolName,
                                    "success" to event.success,
                                    "error" to event.error
                                )),
                                event = "tool_result"
                            )
                        }
                        is ChatStreamEvent.Done -> {
                            // Store assistant message
                            val assistantMessageId = UUID.randomUUID().toString()
                            val assistantMessage = ChatMessageDto(
                                id = assistantMessageId,
                                sessionId = sessionId,
                                role = "assistant",
                                content = event.content,
                                model = ServiceLocator.llmService.provider.name.lowercase(),
                                createdAt = System.currentTimeMillis()
                            )
                            chatMessages.getOrPut(sessionId) { mutableListOf() }.add(assistantMessage)

                            // Update session
                            val currentSession = chatSessions[sessionId]!!
                            chatSessions[sessionId] = currentSession.copy(
                                messageCount = chatMessages[sessionId]?.size ?: 0,
                                lastMessage = event.content.take(100)
                            )

                            send(
                                data = Json.encodeToString(ChatDoneResponse(
                                    messageId = assistantMessageId,
                                    content = event.content,
                                    toolExecutions = event.toolExecutions
                                )),
                                event = "done"
                            )
                        }
                        is ChatStreamEvent.Error -> {
                            send(
                                data = Json.encodeToString(mapOf("message" to event.message)),
                                event = "error"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                println("[ChatRoutes] SSE error: ${e.message}")
                e.printStackTrace()
                send(
                    data = Json.encodeToString(mapOf("message" to (e.message ?: "Unknown error"))),
                    event = "error"
                )
            }
        }

        // POST /api/chat/message - Send a chat message with tool calling support
        post("/message") {
            val request = call.receive<ChatRequest>()

            val sessionId = request.sessionId ?: UUID.randomUUID().toString()

            // Create or update session
            if (!chatSessions.containsKey(sessionId)) {
                chatSessions[sessionId] = ChatSessionDto(
                    id = sessionId,
                    paperId = request.paperId,
                    title = request.message.take(50) + if (request.message.length > 50) "..." else "",
                    messageCount = 0
                )
                chatMessages[sessionId] = mutableListOf()
            }

            // Store user message
            val userMessageId = UUID.randomUUID().toString()
            val userMessage = ChatMessageDto(
                id = userMessageId,
                sessionId = sessionId,
                role = "user",
                content = request.message,
                createdAt = System.currentTimeMillis()
            )
            chatMessages.getOrPut(sessionId) { mutableListOf() }.add(userMessage)

            // Get conversation history and convert to ChatService format
            val history = chatMessages[sessionId]?.takeLast(20)?.map { msg ->
                com.potero.service.chat.ChatMessageDto(
                    id = msg.id,
                    sessionId = msg.sessionId,
                    role = msg.role,
                    content = msg.content,
                    model = msg.model,
                    createdAt = msg.createdAt
                )
            } ?: emptyList()

            // Call ChatService with tool calling support
            val chatResult = chatService.sendMessage(
                sessionId = sessionId,
                message = request.message,
                paperId = request.paperId,
                conversationHistory = history
            )

            chatResult.fold(
                onSuccess = { result ->
                    println("[ChatRoutes] Chat succeeded with ${result.toolExecutions.size} tool executions")

                    // Note: LLM logging is already done inside ChatService

                    // Store assistant message
                    val assistantMessageId = UUID.randomUUID().toString()
                    val assistantMessage = ChatMessageDto(
                        id = assistantMessageId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = result.content,
                        model = ServiceLocator.llmService.provider.name.lowercase(),
                        createdAt = System.currentTimeMillis()
                    )
                    chatMessages.getOrPut(sessionId) { mutableListOf() }.add(assistantMessage)

                    // Update session
                    val currentSession = chatSessions[sessionId]!!
                    chatSessions[sessionId] = currentSession.copy(
                        messageCount = chatMessages[sessionId]?.size ?: 0,
                        lastMessage = result.content.take(100)
                    )

                    // Convert tool executions to DTOs
                    val toolExecutionDtos = result.toolExecutions.map { it.toDto() }

                    val response = ChatResponseDto(
                        messageId = assistantMessageId,
                        sessionId = sessionId,
                        content = result.content,
                        model = ServiceLocator.llmService.provider.name.lowercase(),
                        toolExecutions = toolExecutionDtos  // NEW: Include tool executions
                    )

                    call.respond(ApiResponse(data = response))
                },
                onFailure = { error ->
                    println("[ChatRoutes] Chat failed: ${error.message}")
                    error.printStackTrace()

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ChatResponseDto>(
                            success = false,
                            error = error.message ?: "Chat service failed"
                        )
                    )
                }
            )
        }

        // GET /api/chat/sessions - List chat sessions
        get("/sessions") {
            val paperId = call.request.queryParameters["paperId"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

            val sessions = chatSessions.values
                .filter { session -> paperId == null || session.paperId == paperId }
                .sortedByDescending { chatMessages[it.id]?.lastOrNull()?.createdAt ?: 0L }
                .take(limit)

            call.respond(ApiResponse(data = sessions))
        }

        // GET /api/chat/sessions/{id}/messages - Get chat history
        get("/sessions/{id}/messages") {
            val sessionId = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing session ID")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

            val messages = chatMessages[sessionId]
                ?.takeLast(limit)
                ?.map { msg ->
                    ChatResponseDto(
                        messageId = msg.id,
                        sessionId = msg.sessionId,
                        role = msg.role,
                        content = msg.content,
                        model = msg.model
                    )
                }
                ?: emptyList()

            call.respond(ApiResponse(data = messages))
        }

        // POST /api/chat/sessions - Create new session
        post("/sessions") {
            @Serializable
            data class CreateSessionRequest(
                val paperId: String? = null,
                val title: String
            )

            val request = call.receive<CreateSessionRequest>()

            val sessionId = UUID.randomUUID().toString()
            val session = ChatSessionDto(
                id = sessionId,
                paperId = request.paperId,
                title = request.title,
                messageCount = 0
            )

            chatSessions[sessionId] = session
            chatMessages[sessionId] = mutableListOf()

            call.respond(HttpStatusCode.Created, ApiResponse(data = session))
        }

        // DELETE /api/chat/sessions/{id} - Delete session
        delete("/sessions/{id}") {
            val sessionId = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing session ID")

            chatSessions.remove(sessionId)
            chatMessages.remove(sessionId)

            call.respond(ApiResponse(data = mapOf("deletedSessionId" to sessionId)))
        }

        // POST /api/chat/upload - Upload file to GenAI for chat attachment
        post("/upload") {
            val genAIService = ServiceLocator.genAIFileUploadService

            val multipart = call.receiveMultipart()

            var fileName: String? = null
            var fileBytes: ByteArray? = null
            var contentType: String = "application/octet-stream"

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: "file"
                        contentType = part.contentType?.toString() ?: "application/octet-stream"
                        fileBytes = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileName == null || fileBytes == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ChatUploadResponse>(
                        success = false,
                        error = "No file uploaded"
                    )
                )
                return@post
            }

            // Upload file to GenAI using SSO token
            val result = genAIService.uploadFile(fileName!!, fileBytes!!, contentType)

            result.fold(
                onSuccess = { response: com.potero.service.genai.GenAIFileResponse ->
                    // Convert GenAI response to ChatFileAttachment
                    val attachments = response.files.map { file ->
                        ChatFileAttachment(
                            id = file.id,
                            name = file.name,
                            url = genAIService.buildFileUrl(file.id)
                        )
                    }
                    call.respond(ApiResponse(data = ChatUploadResponse(files = attachments)))
                },
                onFailure = { error: Throwable ->
                    println("[Chat] File upload failed: ${error.message}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ChatUploadResponse>(
                            success = false,
                            error = error.message ?: "File upload failed"
                        )
                    )
                }
            )
        }
    }
}

@Serializable
data class ChatUploadResponse(
    val files: List<ChatFileAttachment>
)
