package com.potero.server.routes

import com.potero.server.di.ServiceLocator
import com.potero.service.llm.LLMLogEntry
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * DTO for LLM log entries (simplified for frontend)
 */
@Serializable
data class LLMLogDto(
    val id: String,
    val timestamp: String,
    val provider: String,
    val purpose: String,
    val inputPromptPreview: String, // First 200 chars
    val inputTokensEstimate: Int,
    val outputResponsePreview: String?, // First 200 chars
    val outputTokensEstimate: Int?,
    val durationMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val paperId: String?,
    val paperTitle: String?
)

fun LLMLogEntry.toDto(): LLMLogDto = LLMLogDto(
    id = id,
    timestamp = timestamp.toString(),
    provider = provider,
    purpose = purpose,
    inputPromptPreview = inputPrompt.take(200) + if (inputPrompt.length > 200) "..." else "",
    inputTokensEstimate = inputTokensEstimate,
    outputResponsePreview = outputResponse?.take(200)?.let { it + if ((outputResponse?.length ?: 0) > 200) "..." else "" },
    outputTokensEstimate = outputTokensEstimate,
    durationMs = durationMs,
    success = success,
    errorMessage = errorMessage,
    paperId = paperId,
    paperTitle = paperTitle
)

/**
 * Full log entry DTO for detailed view
 */
@Serializable
data class LLMLogDetailDto(
    val id: String,
    val timestamp: String,
    val provider: String,
    val purpose: String,
    val inputPrompt: String,
    val inputTokensEstimate: Int,
    val outputResponse: String?,
    val outputTokensEstimate: Int?,
    val durationMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val paperId: String?,
    val paperTitle: String?
)

fun LLMLogEntry.toDetailDto(): LLMLogDetailDto = LLMLogDetailDto(
    id = id,
    timestamp = timestamp.toString(),
    provider = provider,
    purpose = purpose,
    inputPrompt = inputPrompt,
    inputTokensEstimate = inputTokensEstimate,
    outputResponse = outputResponse,
    outputTokensEstimate = outputTokensEstimate,
    durationMs = durationMs,
    success = success,
    errorMessage = errorMessage,
    paperId = paperId,
    paperTitle = paperTitle
)

fun Route.llmRoutes() {
    val llmLogger = ServiceLocator.llmLogger
    val llmService = ServiceLocator.llmService

    route("/api/llm") {
        /**
         * GET /api/llm/logs - Get recent LLM usage logs
         */
        get("/logs") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val purpose = call.request.queryParameters["purpose"]

            val logs = if (purpose != null) {
                llmLogger.getLogsByPurpose(purpose, limit)
            } else {
                llmLogger.getLogs(limit)
            }

            call.respond(ApiResponse(data = logs.map { it.toDto() }))
        }

        /**
         * GET /api/llm/logs/{id} - Get detailed log entry
         */
        get("/logs/{id}") {
            val logId = call.parameters["id"]
                ?: return@get call.respond(ApiResponse<LLMLogDetailDto>(success = false, error = "Log ID required"))

            val logs = llmLogger.getLogs(100)
            val log = logs.find { it.id == logId }

            if (log != null) {
                call.respond(ApiResponse(data = log.toDetailDto()))
            } else {
                call.respond(ApiResponse<LLMLogDetailDto>(success = false, error = "Log not found"))
            }
        }

        /**
         * GET /api/llm/stats - Get LLM usage statistics
         */
        get("/stats") {
            val stats = llmLogger.getStats()
            call.respond(ApiResponse(data = stats))
        }

        /**
         * GET /api/llm/status - Check LLM service status
         */
        get("/status") {
            val settingsRepo = ServiceLocator.settingsRepository
            val apiKey = settingsRepo.get("llmApiKey").getOrNull()
            val apiKeySet = !apiKey.isNullOrBlank()
            val provider = llmService.provider

            call.respond(ApiResponse(data = LLMStatusDto(
                configured = apiKeySet,
                provider = provider.displayName,
                endpoint = provider.endpoint
            )))
        }

        /**
         * DELETE /api/llm/logs - Clear all logs
         */
        delete("/logs") {
            llmLogger.clear()
            call.respond(ApiResponse(data = mapOf("cleared" to true)))
        }
    }
}

@Serializable
data class LLMStatusDto(
    val configured: Boolean,
    val provider: String,
    val endpoint: String
)
