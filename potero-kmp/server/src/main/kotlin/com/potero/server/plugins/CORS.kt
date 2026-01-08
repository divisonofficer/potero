package com.potero.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        // Allow requests from Svelte dev server and Electron
        allowHost("localhost:5173")
        allowHost("localhost:3000")
        allowHost("127.0.0.1:5173")
        allowHost("127.0.0.1:3000")

        // Allow any host for development (WSL IP access from Windows)
        anyHost()

        // Note: allowCredentials cannot be used with anyHost()
        // allowCredentials = true

        // Allow common headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Api-Key")

        // Allow common methods
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)

        // Expose headers that client might need
        exposeHeader(HttpHeaders.ContentLength)
    }
}
