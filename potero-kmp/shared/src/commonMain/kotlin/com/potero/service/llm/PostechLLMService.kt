package com.potero.service.llm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of LLMService using POSTECH GenAI API
 */
class PostechLLMService(
    private val httpClient: HttpClient,
    private val config: LLMConfig
) : LLMService {

    private var _provider: LLMProvider = config.provider

    override val provider: LLMProvider
        get() = _provider

    override fun setProvider(provider: LLMProvider) {
        _provider = provider
    }

    override suspend fun chat(message: String): Result<String> = runCatching {
        val response = httpClient.post(_provider.endpoint) {
            contentType(ContentType.Application.Json)
            header("X-Api-Key", config.apiKey)
            setBody(LLMRequest(
                message = message,
                stream = false,
                files = emptyList() // MVP: No file upload, using text extraction
            ))
        }

        if (!response.status.isSuccess()) {
            throw LLMException("API request failed with status: ${response.status}")
        }

        response.body<LLMResponse>().message
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
