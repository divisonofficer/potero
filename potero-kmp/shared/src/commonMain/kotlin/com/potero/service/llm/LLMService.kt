package com.potero.service.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * LLM Provider endpoints for POSTECH GenAI API
 */
enum class LLMProvider(val endpoint: String, val displayName: String) {
    GPT("https://genai.postech.ac.kr/agent/api/a1/got", "GPT"),
    GEMINI("https://genai.postech.ac.kr/agent/api/a2/gemini", "Gemini"),
    CLAUDE("https://genai.postech.ac.kr/agent/api/a3/claude", "Claude")
}

/**
 * Request body for POSTECH GenAI API
 */
@Serializable
data class LLMRequest(
    val message: String,
    val stream: Boolean = false,
    val files: List<FileAttachment> = emptyList()
)

/**
 * File attachment for future SSO-based file upload
 * (Not used in MVP - using text extraction instead)
 */
@Serializable
data class FileAttachment(
    val id: String,
    val name: String,
    val url: String
)

/**
 * Response from POSTECH GenAI API
 */
@Serializable
data class LLMResponse(
    val message: String
)

/**
 * LLM Service interface for chat operations
 */
interface LLMService {
    /**
     * Send a chat message and receive a response
     */
    suspend fun chat(message: String): Result<String>

    /**
     * Send a chat message with streaming response
     * (If supported by the API)
     */
    fun chatStream(message: String): Flow<String>

    /**
     * Get the current provider
     */
    val provider: LLMProvider

    /**
     * Change the provider
     */
    fun setProvider(provider: LLMProvider)
}

/**
 * Configuration for LLM service
 */
data class LLMConfig(
    val apiKey: String,
    val provider: LLMProvider = LLMProvider.GPT,
    val maxRetries: Int = 3,
    val timeoutMs: Long = 60_000
)
