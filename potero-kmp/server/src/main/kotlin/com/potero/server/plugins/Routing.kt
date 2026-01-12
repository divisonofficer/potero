package com.potero.server.plugins

import com.potero.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

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
            narrativeRoutes()
            researchNoteRoutes()
            relatedWorkRoutes()
            figureRoutes()
            tableRoutes()
        }

        // LLM routes (separate from /api prefix for clarity)
        llmRoutes()

        // Static file serving for Electron frontend
        // Get JAR location and working directory
        val jarLocation = object {}.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it).parentFile }
        val cwd = File(System.getProperty("user.dir"))

        println("[Server] Working directory: ${cwd.absolutePath}")
        println("[Server] JAR location: ${jarLocation?.absolutePath ?: "unknown"}")

        val frontendPaths = mutableListOf<File>()

        // Add paths relative to JAR location (for production)
        jarLocation?.let {
            frontendPaths.add(File(it, "frontend"))
            frontendPaths.add(File(it.parentFile, "frontend"))  // One level up
        }

        // Add paths relative to working directory
        frontendPaths.addAll(listOf(
            File(cwd, "frontend"),
            File(cwd, "../potero-svelte/build"),
            File(cwd, "../../potero-svelte/build"),
            File(cwd, "potero-svelte/build")
        ))

        val frontendDir = frontendPaths.firstOrNull { it.exists() && it.isDirectory }

        if (frontendDir != null) {
            println("[Server] Serving frontend from: ${frontendDir.absolutePath}")

            // Serve static files from frontend directory
            staticFiles("/", frontendDir) {
                default("index.html")
            }
        } else {
            println("[Server] Warning: Frontend directory not found. Checked paths:")
            frontendPaths.forEach { println("  - ${it.absolutePath} (exists: ${it.exists()})") }
        }
    }
}
