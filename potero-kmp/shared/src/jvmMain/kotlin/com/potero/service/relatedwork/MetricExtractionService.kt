package com.potero.service.relatedwork

import com.potero.domain.model.ColumnDataType
import com.potero.domain.model.Paper
import com.potero.domain.repository.PaperRepository
import com.potero.service.llm.LLMService
import com.potero.service.pdf.PreprocessedPdfProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for extracting specific metric values from papers using LLM.
 * Uses PreprocessedPdfProvider for efficient text access.
 */
class MetricExtractionService(
    private val paperRepository: PaperRepository,
    private val preprocessedPdfProvider: PreprocessedPdfProvider,
    private val llmService: LLMService
) {

    companion object {
        private const val MAX_TEXT_LENGTH = 15000 // LLM context window limit
        private const val MAX_PAGES_FOR_EXTRACTION = 5 // Extract from first 5 pages if needed
    }

    /**
     * Extract a specific metric value from a paper.
     *
     * @param paperId Paper ID
     * @param metricName Name of the metric to extract
     * @param metricDescription Description/context for the metric
     * @param dataType Expected data type of the metric
     * @return Extracted metric with confidence score
     */
    suspend fun extractMetric(
        paperId: String,
        metricName: String,
        metricDescription: String?,
        dataType: ColumnDataType
    ): Result<ExtractedMetric> = withContext(Dispatchers.IO) {
        runCatching {
            val paper = paperRepository.getById(paperId).getOrNull()
                ?: throw IllegalArgumentException("Paper not found: $paperId")

            // Get paper text (try preprocessed first, fallback to abstract)
            val paperText = getPaperText(paper)

            // Build extraction prompt
            val prompt = buildExtractionPrompt(
                paperTitle = paper.title,
                paperText = paperText,
                metricName = metricName,
                metricDescription = metricDescription,
                dataType = dataType
            )

            // Call LLM
            val response = llmService.chat(prompt).getOrThrow()

            // Parse and validate response
            val value = parseExtractionResponse(response, dataType)
            val confidence = estimateConfidence(response, dataType, value)

            ExtractedMetric(
                value = value,
                confidence = confidence,
                source = if (paperText.length > (paper.abstract?.length ?: 0) + 100) "pdf_text" else "abstract"
            )
        }
    }

    /**
     * Get paper text for extraction.
     * Priority: Preprocessed PDF > Abstract
     */
    private suspend fun getPaperText(paper: Paper): String {
        // Try preprocessed PDF first
        if (paper.pdfPath != null) {
            // Check if preprocessing is available
            val isPreprocessed = preprocessedPdfProvider.isPreprocessed(paper.id)

            if (isPreprocessed) {
                // Get first few pages (usually sufficient for metadata/methods)
                val pdfText = preprocessedPdfProvider.getFirstPages(paper.id, MAX_PAGES_FOR_EXTRACTION)
                    .getOrNull()

                if (pdfText != null && pdfText.length > 500) {
                    // Truncate if too long
                    return if (pdfText.length > MAX_TEXT_LENGTH) {
                        pdfText.take(MAX_TEXT_LENGTH) + "\n\n[... truncated ...]"
                    } else {
                        pdfText
                    }
                }
            }
        }

        // Fallback to abstract (if available)
        val abstract = paper.abstract
        if (!abstract.isNullOrBlank()) {
            return abstract
        }

        // Last resort: just the title
        return paper.title
    }

    /**
     * Build LLM prompt for metric extraction.
     * Includes anti-hallucination rules.
     */
    private fun buildExtractionPrompt(
        paperTitle: String,
        paperText: String,
        metricName: String,
        metricDescription: String?,
        dataType: ColumnDataType
    ): String {
        val dataTypeInstruction = when (dataType) {
            ColumnDataType.TEXT -> "a concise text summary (1-2 sentences maximum)"
            ColumnDataType.NUMBER -> "a single numeric value (just the number, no units or text)"
            ColumnDataType.BOOLEAN -> "either 'true' or 'false'"
            ColumnDataType.LIST -> "a comma-separated list (e.g., \"item1, item2, item3\")"
            ColumnDataType.DATE -> "a year (YYYY) or date (YYYY-MM-DD)"
            ColumnDataType.CITATION_COUNT -> "the number of citations mentioned (just the number)"
        }

        return """
Extract the following information from this research paper:

Paper Title: $paperTitle

Metric to extract: $metricName
${metricDescription?.let { "Description: $it" } ?: ""}

Paper content:
$paperText

CRITICAL RULES:
1. Return ONLY the extracted value, nothing else
2. If the information is not found or unclear, return exactly "N/A"
3. Do NOT make up or hallucinate information
4. Do NOT include explanations or commentary
5. Format: $dataTypeInstruction

Your response (value only):
        """.trimIndent()
    }

    /**
     * Parse LLM response based on expected data type.
     */
    private fun parseExtractionResponse(response: String, dataType: ColumnDataType): String {
        val trimmed = response.trim()

        // Handle N/A responses
        if (trimmed.equals("N/A", ignoreCase = true) ||
            trimmed.equals("not available", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true)
        ) {
            return "N/A"
        }

        return when (dataType) {
            ColumnDataType.NUMBER -> {
                // Extract first number found
                val numberRegex = Regex("-?\\d+\\.?\\d*")
                numberRegex.find(trimmed)?.value ?: "N/A"
            }

            ColumnDataType.BOOLEAN -> {
                when {
                    trimmed.contains("true", ignoreCase = true) -> "true"
                    trimmed.contains("false", ignoreCase = true) -> "false"
                    trimmed.contains("yes", ignoreCase = true) -> "true"
                    trimmed.contains("no", ignoreCase = true) -> "false"
                    else -> "N/A"
                }
            }

            ColumnDataType.DATE -> {
                // Extract year (4 digits)
                val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
                yearRegex.find(trimmed)?.value ?: "N/A"
            }

            ColumnDataType.LIST -> {
                // Clean up list formatting
                trimmed.replace(Regex("\\s*,\\s*"), ", ")
            }

            ColumnDataType.TEXT, ColumnDataType.CITATION_COUNT -> {
                // Return as-is, but limit length for TEXT
                if (dataType == ColumnDataType.TEXT && trimmed.length > 200) {
                    trimmed.take(200) + "..."
                } else {
                    trimmed
                }
            }
        }
    }

    /**
     * Estimate confidence in extracted value.
     * Based on response characteristics and data type.
     */
    private fun estimateConfidence(response: String, dataType: ColumnDataType, parsedValue: String): Double {
        val trimmed = response.trim()

        return when {
            // N/A responses have 0 confidence
            parsedValue == "N/A" -> 0.0

            // Responses with uncertainty keywords have low confidence
            trimmed.contains("unclear", ignoreCase = true) ||
                    trimmed.contains("not mentioned", ignoreCase = true) ||
                    trimmed.contains("possibly", ignoreCase = true) ||
                    trimmed.contains("maybe", ignoreCase = true) -> 0.3

            // Responses with hedging language have medium confidence
            trimmed.contains("approximately", ignoreCase = true) ||
                    trimmed.contains("roughly", ignoreCase = true) ||
                    trimmed.contains("about", ignoreCase = true) -> 0.6

            // Number and boolean types are easier to extract reliably
            dataType == ColumnDataType.NUMBER || dataType == ColumnDataType.BOOLEAN -> 0.85

            // Everything else has good confidence
            else -> 0.8
        }
    }
}

/**
 * Result of metric extraction
 */
data class ExtractedMetric(
    val value: String,
    val confidence: Double,
    val source: String  // "pdf_text" or "abstract"
)
