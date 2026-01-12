package com.potero.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = ErrorDetail(
                        code = "BAD_REQUEST",
                        message = cause.message ?: "Invalid request"
                    )
                )
            )
        }

        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = ErrorDetail(
                        code = "NOT_FOUND",
                        message = cause.message ?: "Resource not found"
                    )
                )
            )
        }

        exception<Exception> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = ErrorDetail(
                        code = "INTERNAL_ERROR",
                        message = cause.message ?: "An unexpected error occurred"
                    )
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            // Only return JSON error for API routes
            // For other routes, let the static file handler deal with it
            val path = call.request.local.uri
            if (path.startsWith("/api/") || path.startsWith("/llm/") || path == "/health") {
                call.respond(
                    status,
                    ErrorResponse(
                        error = ErrorDetail(
                            code = "NOT_FOUND",
                            message = "The requested resource was not found"
                        )
                    )
                )
            }
            // For non-API routes, don't respond here - let static files handle it
        }
    }
}
