package com.potero.service.llm

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * LLM usage log entry
 */
@Serializable
data class LLMLogEntry(
    val id: String,
    val timestamp: Instant,
    val provider: String,
    val purpose: String, // e.g., "chat", "title_cleaning", "auto_tag", "summarize"
    val inputPrompt: String,
    val inputTokensEstimate: Int, // rough estimate based on character count
    val outputResponse: String?,
    val outputTokensEstimate: Int?,
    val durationMs: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val paperId: String? = null, // associated paper if applicable
    val paperTitle: String? = null
)

/**
 * In-memory LLM usage logger
 * Keeps last N log entries for debugging and monitoring
 */
class LLMLogger(
    private val maxEntries: Int = 100
) {
    private val logs = mutableListOf<LLMLogEntry>()
    private var idCounter = 0L

    @Synchronized
    fun log(
        provider: LLMProvider,
        purpose: String,
        inputPrompt: String,
        outputResponse: String?,
        durationMs: Long,
        success: Boolean,
        errorMessage: String? = null,
        paperId: String? = null,
        paperTitle: String? = null
    ): LLMLogEntry {
        val entry = LLMLogEntry(
            id = "llm_${++idCounter}",
            timestamp = Clock.System.now(),
            provider = provider.displayName,
            purpose = purpose,
            inputPrompt = inputPrompt,
            inputTokensEstimate = estimateTokens(inputPrompt),
            outputResponse = outputResponse,
            outputTokensEstimate = outputResponse?.let { estimateTokens(it) },
            durationMs = durationMs,
            success = success,
            errorMessage = errorMessage,
            paperId = paperId,
            paperTitle = paperTitle
        )

        logs.add(0, entry) // Add to front (most recent first)

        // Trim old entries
        while (logs.size > maxEntries) {
            logs.removeAt(logs.size - 1)
        }

        return entry
    }

    @Synchronized
    fun getLogs(limit: Int = 50): List<LLMLogEntry> {
        return logs.take(limit)
    }

    @Synchronized
    fun getLogsByPurpose(purpose: String, limit: Int = 50): List<LLMLogEntry> {
        return logs.filter { it.purpose == purpose }.take(limit)
    }

    @Synchronized
    fun getStats(): LLMUsageStats {
        val totalCalls = logs.size
        val successfulCalls = logs.count { it.success }
        val totalInputTokens = logs.sumOf { it.inputTokensEstimate }
        val totalOutputTokens = logs.sumOf { it.outputTokensEstimate ?: 0 }
        val avgDurationMs = if (logs.isNotEmpty()) logs.map { it.durationMs }.average().toLong() else 0L
        val byPurpose = logs.groupBy { it.purpose }.mapValues { it.value.size }
        val byProvider = logs.groupBy { it.provider }.mapValues { it.value.size }

        return LLMUsageStats(
            totalCalls = totalCalls,
            successfulCalls = successfulCalls,
            failedCalls = totalCalls - successfulCalls,
            totalInputTokensEstimate = totalInputTokens,
            totalOutputTokensEstimate = totalOutputTokens,
            averageDurationMs = avgDurationMs,
            callsByPurpose = byPurpose,
            callsByProvider = byProvider
        )
    }

    @Synchronized
    fun clear() {
        logs.clear()
    }

    /**
     * Rough token estimate (1 token ≈ 4 characters for English, 1.5 for mixed)
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 3.0).toInt().coerceAtLeast(1)
    }
}

/**
 * Summary statistics for LLM usage
 */
@Serializable
data class LLMUsageStats(
    val totalCalls: Int,
    val successfulCalls: Int,
    val failedCalls: Int,
    val totalInputTokensEstimate: Int,
    val totalOutputTokensEstimate: Int,
    val averageDurationMs: Long,
    val callsByPurpose: Map<String, Int>,
    val callsByProvider: Map<String, Int>
)
