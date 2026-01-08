package com.potero.server

import com.potero.server.di.ServiceLocator
import com.potero.server.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    // Initialize services
    ServiceLocator.init()

    // Add shutdown hook to clean up resources
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down server...")
        ServiceLocator.shutdown()
    })

    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",  // Bind to all interfaces (required for WSL)
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureStatusPages()
    configureRouting()
}
