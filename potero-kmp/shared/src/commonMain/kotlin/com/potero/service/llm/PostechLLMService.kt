package com.potero.service.llm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of LLMService using POSTECH GenAI API
 *
 * @param httpClient HTTP client for API requests
 * @param apiKeyProvider Function to get the current API key (allows dynamic loading from settings)
 * @param providerProvider Function to get the current provider (allows dynamic loading from settings)
 */
class PostechLLMService(
    private val httpClient: HttpClient,
    private val apiKeyProvider: suspend () -> String,
    private val providerProvider: suspend () -> LLMProvider = { LLMProvider.GPT }
) : LLMService {

    // Secondary constructor for backward compatibility
    constructor(
        httpClient: HttpClient,
        config: LLMConfig
    ) : this(httpClient, { config.apiKey }, { config.provider })

    private var _providerOverride: LLMProvider? = null
    private var _lastUsedProvider: LLMProvider = LLMProvider.GPT

    override val provider: LLMProvider
        get() = _lastUsedProvider

    override fun setProvider(provider: LLMProvider) {
        _providerOverride = provider
    }

    override suspend fun chat(message: String): Result<String> = runCatching {
        // Get API key and provider dynamically from settings
        val apiKey = apiKeyProvider()
        val currentProvider = _providerOverride ?: providerProvider()

        // Update last used provider for accurate logging
        _lastUsedProvider = currentProvider

        // Debug logging
        println("[LLM] ========================================")
        println("[LLM] Provider: ${currentProvider.name}")
        println("[LLM] Endpoint: ${currentProvider.endpoint}")
        println("[LLM] API Key configured: ${apiKey.isNotBlank()}")
        println("[LLM] API Key (first 10 chars): ${apiKey.take(10)}...")
        println("[LLM] Message length: ${message.length} chars")
        println("[LLM] Message preview: ${message.take(200)}...")
        println("[LLM] ----------------------------------------")

        if (apiKey.isBlank()) {
            println("[LLM] ERROR: API key is empty!")
            throw LLMException("LLM API key is not configured. Please set it in Settings.")
        }

        val requestBody = LLMRequest(
            message = message,
            stream = false,
            files = emptyList() // MVP: No file upload, using text extraction
        )

        println("[LLM] Sending request to: ${currentProvider.endpoint}")

        val response = httpClient.post(currentProvider.endpoint) {
            contentType(ContentType.Application.Json)
            header("X-Api-Key", apiKey)
            setBody(requestBody)
        }

        println("[LLM] Response status: ${response.status}")

        // Get raw response body first for debugging
        val rawBody = response.body<String>()
        println("[LLM] Raw response: $rawBody")

        if (!response.status.isSuccess()) {
            println("[LLM] ERROR Response: $rawBody")
            throw LLMException("API request failed with status: ${response.status}. Response: $rawBody")
        }

        // Parse the response
        val responseMessage = try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val llmResponse = json.decodeFromString<LLMResponse>(rawBody)
            llmResponse.getContent()
        } catch (e: Exception) {
            println("[LLM] Failed to parse as LLMResponse: ${e.message}")
            // Fallback: use raw body
            rawBody.trim().removeSurrounding("\"")
        }

        if (responseMessage.isBlank()) {
            throw LLMException("LLM returned empty response")
        }

        println("[LLM] Response message length: ${responseMessage.length} chars")
        println("[LLM] Response preview: ${responseMessage.take(200)}...")
        println("[LLM] ========================================")

        responseMessage
    }

    override suspend fun chatWithFiles(message: String, files: List<FileAttachment>): Result<String> = runCatching {
        // Get API key and provider dynamically from settings
        val apiKey = apiKeyProvider()
        val currentProvider = _providerOverride ?: providerProvider()

        // Update last used provider for accurate logging
        _lastUsedProvider = currentProvider

        // Debug logging
        println("[LLM] ========================================")
        println("[LLM] Provider: ${currentProvider.name}")
        println("[LLM] Endpoint: ${currentProvider.endpoint}")
        println("[LLM] API Key configured: ${apiKey.isNotBlank()}")
        println("[LLM] Message length: ${message.length} chars")
        println("[LLM] Files attached: ${files.size}")
        files.forEachIndexed { idx, file ->
            println("[LLM]   File $idx: ${file.name} (${file.id})")
        }
        println("[LLM] ----------------------------------------")

        if (apiKey.isBlank()) {
            println("[LLM] ERROR: API key is empty!")
            throw LLMException("LLM API key is not configured. Please set it in Settings.")
        }

        val requestBody = LLMRequest(
            message = message,
            stream = false,
            files = files  // Now actually passing files!
        )

        println("[LLM] Sending request with ${files.size} file(s) to: ${currentProvider.endpoint}")

        val response = httpClient.post(currentProvider.endpoint) {
            contentType(ContentType.Application.Json)
            header("X-Api-Key", apiKey)
            setBody(requestBody)
        }

        println("[LLM] Response status: ${response.status}")

        // Get raw response body first for debugging
        val rawBody = response.body<String>()
        println("[LLM] Raw response: ${rawBody.take(500)}...")

        if (!response.status.isSuccess()) {
            println("[LLM] ERROR Response: $rawBody")
            throw LLMException("API request failed with status: ${response.status}. Response: $rawBody")
        }

        // Parse the response
        val responseMessage = try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val llmResponse = json.decodeFromString<LLMResponse>(rawBody)
            llmResponse.getContent()
        } catch (e: Exception) {
            println("[LLM] Failed to parse as LLMResponse: ${e.message}")
            // Fallback: use raw body
            rawBody.trim().removeSurrounding("\"")
        }

        if (responseMessage.isBlank()) {
            throw LLMException("LLM returned empty response")
        }

        println("[LLM] Response message length: ${responseMessage.length} chars")
        println("[LLM] ========================================")

        responseMessage
    }

    override fun chatStream(message: String): Flow<String> = flow {
        // For MVP, streaming might not be fully supported
        // Fall back to regular response
        val result = chat(message)
        result.fold(
            onSuccess = { emit(it) },
            onFailure = { throw it }
        )
    }
}

/**
 * Exception for LLM service errors
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
