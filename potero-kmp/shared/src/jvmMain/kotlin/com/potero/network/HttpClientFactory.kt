package com.potero.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Factory for creating HTTP client on JVM platform
 */
object HttpClientFactory {

    private var client: HttpClient? = null

    /**
     * Get or create the HTTP client
     */
    fun getClient(): HttpClient {
        if (client == null) {
            client = createClient()
        }
        return client!!
    }

    private fun createClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }

            defaultRequest {
                // Common headers can be set here
            }
        }
    }

    /**
     * Close the HTTP client
     */
    fun close() {
        client?.close()
        client = null
    }
}
