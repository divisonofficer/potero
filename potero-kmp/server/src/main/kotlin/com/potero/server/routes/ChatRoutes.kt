package com.potero.server.routes

import com.potero.server.di.ServiceLocator
import com.potero.service.genai.GenAIFileResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
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
    val model: String? = null
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
    val llmService = ServiceLocator.llmService
    val paperRepository = ServiceLocator.paperRepository

    route("/chat") {
        // POST /api/chat/message - Send a chat message
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

            // Build prompt with paper context if available
            val prompt = if (request.paperId != null) {
                val paper = paperRepository.getById(request.paperId).getOrNull()
                if (paper != null) {
                    buildString {
                        append("Context about the paper:\n")
                        append("Title: ${paper.title}\n")
                        append("Authors: ${paper.formattedAuthors}\n")
                        paper.year?.let { append("Year: $it\n") }
                        paper.conference?.let { append("Conference: $it\n") }
                        paper.abstract?.let { append("Abstract: $it\n") }
                        append("\nUser question: ${request.message}")
                    }
                } else {
                    request.message
                }
            } else {
                request.message
            }

            // Call LLM service (with files if provided)
            val llmResult = if (request.files.isNotEmpty()) {
                // Convert ChatFileAttachment to FileAttachment for LLM service
                val fileAttachments = request.files.map { file ->
                    com.potero.service.llm.FileAttachment(
                        id = file.id,
                        name = file.name,
                        url = file.url
                    )
                }
                llmService.chatWithFiles(prompt, fileAttachments)
            } else {
                llmService.chat(prompt)
            }

            llmResult.fold(
                onSuccess = { responseContent ->
                    // Store assistant message
                    val assistantMessageId = UUID.randomUUID().toString()
                    val assistantMessage = ChatMessageDto(
                        id = assistantMessageId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = responseContent,
                        model = "gpt", // Would be dynamic based on provider
                        createdAt = System.currentTimeMillis()
                    )
                    chatMessages.getOrPut(sessionId) { mutableListOf() }.add(assistantMessage)

                    // Update session
                    val currentSession = chatSessions[sessionId]!!
                    chatSessions[sessionId] = currentSession.copy(
                        messageCount = chatMessages[sessionId]?.size ?: 0,
                        lastMessage = responseContent.take(100)
                    )

                    val response = ChatResponseDto(
                        messageId = assistantMessageId,
                        sessionId = sessionId,
                        content = responseContent,
                        model = "gpt"
                    )

                    call.respond(ApiResponse(data = response))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ChatResponseDto>(
                            success = false,
                            error = error.message ?: "LLM request failed"
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
