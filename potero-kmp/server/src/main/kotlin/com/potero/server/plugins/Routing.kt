package com.potero.server.plugins

import com.potero.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "ok", "version" to "0.1.0"))
        }

        // API routes
        route("/api") {
            paperRoutes()
            chatRoutes()
            tagRoutes()
            searchRoutes()
            settingsRoutes()
            uploadRoutes()
            jobRoutes()
            authorRoutes()
            referenceRoutes()
            citationRoutes()
        }

        // LLM routes (separate from /api prefix for clarity)
        llmRoutes()
    }
}
