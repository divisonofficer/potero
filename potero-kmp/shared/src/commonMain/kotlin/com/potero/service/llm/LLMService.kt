package com.potero.service.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LLM Provider endpoints for POSTECH GenAI API (OpenAI Responses API compatible)
 * Base URL format: https://genai.postech.ac.kr/agent/api/a{idx}
 * Path: /openai/responses
 */
enum class LLMProvider(val endpoint: String, val displayName: String) {
    GPT("https://genai.postech.ac.kr/agent/api/a11/gpt/responses", "GPT"),
    GEMINI("https://genai.postech.ac.kr/agent/api/a2/gemini", "Gemini"),
    CLAUDE("https://genai.postech.ac.kr/agent/api/a3/claude", "Claude")
}

/**
 * Request body — OpenAI Responses API format
 */
@Serializable
data class LLMRequest(
    val model: String = "default",
    val input: String
)

/**
 * File attachment (not yet used — kept for future SSO-based upload)
 */
@Serializable
data class FileAttachment(
    val id: String,
    val name: String,
    val url: String
)

/**
 * Response from POSTECH GenAI API (OpenAI Responses API format)
 * output[].content[].text is the primary field.
 * Legacy "replies"/"message" fields kept as fallback.
 */
@Serializable
data class LLMResponse(
    val output: List<OutputItem>? = null,
    // Legacy fields
    val replies: String? = null,
    val message: String? = null
) {
    fun getContent(): String {
        // OpenAI Responses API: output[0].content[0].text
        val fromOutput = output
            ?.firstOrNull()
            ?.content
            ?.firstOrNull()
            ?.resolvedText
            ?.takeIf { it.isNotBlank() }

        val raw = fromOutput ?: replies ?: message ?: ""

        // Handle doubly-escaped JSON string
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            try {
                kotlinx.serialization.json.Json.decodeFromString<String>(raw)
            } catch (e: Exception) {
                raw.removeSurrounding("\"")
            }
        } else {
            raw
        }
    }
}

@Serializable
data class OutputItem(
    val type: String? = null,
    val content: List<ContentItem>? = null
)

@Serializable
data class ContentItem(
    val type: String? = null,
    val text: String? = null,
    @SerialName("output_text") val outputText: String? = null
) {
    val resolvedText: String? get() = text ?: outputText
}

/**
 * LLM Service interface for chat operations
 */
interface LLMService {
    suspend fun chat(message: String): Result<String>
    suspend fun chatWithFiles(message: String, files: List<FileAttachment>): Result<String>
    fun chatStream(message: String): Flow<String>
    val provider: LLMProvider
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
