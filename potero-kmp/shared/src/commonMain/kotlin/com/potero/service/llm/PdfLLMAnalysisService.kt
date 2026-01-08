package com.potero.service.llm

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Service for analyzing PDF content using LLM
 * - Extract clean title from noisy PDF metadata
 * - Extract abstract from PDF text
 * - Translate abstract to Korean
 */
class PdfLLMAnalysisService(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Comprehensive PDF analysis using LLM
     * Extracts title, abstract, and Korean translation from PDF text
     */
    suspend fun analyzePdf(
        pdfText: String,
        rawTitle: String? = null,
        paperId: String? = null
    ): Result<PdfLLMAnalysisResult> {
        // Limit text to first ~4000 chars to avoid token limits
        val truncatedText = pdfText.take(4000)

        val prompt = buildAnalysisPrompt(truncatedText, rawTitle)
        val startTime = Clock.System.now()

        return try {
            val result = llmService.chat(prompt)
            val durationMs = (Clock.System.now() - startTime).inWholeMilliseconds

            result.fold(
                onSuccess = { response ->
                    val analysisResult = parseAnalysisResponse(response, rawTitle)
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "pdf_analysis",
                        inputPrompt = prompt,
                        outputResponse = response,
                        durationMs = durationMs,
                        success = true,
                        paperId = paperId,
                        paperTitle = rawTitle
                    )
                    Result.success(analysisResult)
                },
                onFailure = { error ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "pdf_analysis",
                        inputPrompt = prompt,
                        outputResponse = null,
                        durationMs = durationMs,
                        success = false,
                        errorMessage = error.message,
                        paperId = paperId,
                        paperTitle = rawTitle
                    )
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            val durationMs = (Clock.System.now() - startTime).inWholeMilliseconds
            llmLogger.log(
                provider = llmService.provider,
                purpose = "pdf_analysis",
                inputPrompt = prompt,
                outputResponse = null,
                durationMs = durationMs,
                success = false,
                errorMessage = e.message,
                paperId = paperId,
                paperTitle = rawTitle
            )
            Result.failure(e)
        }
    }

    /**
     * Clean title only (for quick cleanup without full analysis)
     */
    suspend fun cleanTitle(
        rawTitle: String,
        context: String? = null,
        paperId: String? = null
    ): Result<String> {
        if (rawTitle.isBlank()) {
            return Result.success(rawTitle)
        }

        // Check if title already looks clean
        if (looksClean(rawTitle)) {
            return Result.success(rawTitle)
        }

        val prompt = buildTitleCleaningPrompt(rawTitle, context)
        val startTime = Clock.System.now()

        return try {
            val result = llmService.chat(prompt)
            val durationMs = (Clock.System.now() - startTime).inWholeMilliseconds

            result.fold(
                onSuccess = { response ->
                    val cleanedTitle = parseCleanedTitle(response, rawTitle)
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "title_cleaning",
                        inputPrompt = prompt,
                        outputResponse = response,
                        durationMs = durationMs,
                        success = true,
                        paperId = paperId,
                        paperTitle = rawTitle
                    )
                    Result.success(cleanedTitle)
                },
                onFailure = { error ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "title_cleaning",
                        inputPrompt = prompt,
                        outputResponse = null,
                        durationMs = durationMs,
                        success = false,
                        errorMessage = error.message,
                        paperId = paperId,
                        paperTitle = rawTitle
                    )
                    Result.success(rawTitle) // Return original on failure
                }
            )
        } catch (e: Exception) {
            Result.success(rawTitle)
        }
    }

    /**
     * Translate abstract to Korean
     */
    suspend fun translateToKorean(
        text: String,
        paperId: String? = null
    ): Result<String> {
        if (text.isBlank()) {
            return Result.success(text)
        }

        val prompt = buildTranslationPrompt(text)
        val startTime = Clock.System.now()

        return try {
            val result = llmService.chat(prompt)
            val durationMs = (Clock.System.now() - startTime).inWholeMilliseconds

            result.fold(
                onSuccess = { response ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "abstract_translation",
                        inputPrompt = prompt,
                        outputResponse = response,
                        durationMs = durationMs,
                        success = true,
                        paperId = paperId
                    )
                    Result.success(response.trim())
                },
                onFailure = { error ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "abstract_translation",
                        inputPrompt = prompt,
                        outputResponse = null,
                        durationMs = durationMs,
                        success = false,
                        errorMessage = error.message,
                        paperId = paperId
                    )
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildAnalysisPrompt(pdfText: String, rawTitle: String?): String {
        return """
You are an academic paper metadata extraction assistant. Analyze the following text from a PDF and extract information.

${rawTitle?.let { "Raw title from PDF metadata: \"$it\"" } ?: ""}

PDF text (first pages):
---
$pdfText
---

Extract the following and respond in JSON format:
{
    "title": "Clean paper title (remove page numbers, watermarks, artifacts like '001', 'v1', etc.)",
    "authors": ["Author Name 1", "Author Name 2"],
    "abstract": "The paper's abstract in English",
    "abstractKorean": "논문 초록의 한국어 번역"
}

Important:
- For title: Remove any page numbers, version numbers, or artifacts (e.g., "001", "v2", random numbers at the end)
- For authors: Extract actual author names, removing affiliation numbers
- For abstract: Extract the abstract section if present, or summarize the paper briefly if not found
- For abstractKorean: Provide a Korean translation of the abstract

Respond with ONLY the JSON object, no additional text.
""".trim()
    }

    private fun buildTitleCleaningPrompt(rawTitle: String, context: String?): String {
        return """
You are a metadata extraction assistant. Clean the following academic paper title extracted from a PDF.

Raw extracted title: "$rawTitle"
${context?.let { "Context from PDF: $it" } ?: ""}

Tasks:
1. Remove any page numbers, watermarks, or artifacts (e.g., "001", "1234", random numbers at the end)
2. Remove any conference/journal headers that got mixed in
3. Remove version numbers like "v1", "v2" at the end
4. Fix any obvious typos or encoding issues
5. Keep the actual paper title intact

Respond with ONLY the cleaned title, nothing else. If the title is already clean, return it as-is.
""".trim()
    }

    private fun buildTranslationPrompt(text: String): String {
        return """
Translate the following academic abstract to Korean. Maintain academic tone and terminology.

English abstract:
$text

Respond with ONLY the Korean translation, nothing else.
""".trim()
    }

    private fun parseAnalysisResponse(response: String, fallbackTitle: String?): PdfLLMAnalysisResult {
        return try {
            // Try to extract JSON from response
            val jsonStr = extractJson(response)
            val parsed = json.decodeFromString<PdfLLMAnalysisJson>(jsonStr)

            PdfLLMAnalysisResult(
                title = parsed.title?.takeIf { it.isNotBlank() && it.length in 5..500 },
                authors = parsed.authors?.filter { it.isNotBlank() } ?: emptyList(),
                abstract = parsed.abstract?.takeIf { it.isNotBlank() },
                abstractKorean = parsed.abstractKorean?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            // Fallback: try to extract title only from response
            PdfLLMAnalysisResult(
                title = parseCleanedTitle(response, fallbackTitle ?: ""),
                authors = emptyList(),
                abstract = null,
                abstractKorean = null
            )
        }
    }

    private fun extractJson(response: String): String {
        // Find JSON object in response
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')

        return if (start >= 0 && end > start) {
            response.substring(start, end + 1)
        } else {
            response
        }
    }

    private fun parseCleanedTitle(response: String, fallback: String): String {
        val cleaned = response.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .trim()

        return if (cleaned.length in 5..500 && cleaned.isNotBlank()) {
            cleaned
        } else {
            fallback
        }
    }

    private fun looksClean(title: String): Boolean {
        val noisePatterns = listOf(
            Regex("""\s+\d{3,}$"""),           // Ends with numbers like "001", "1234"
            Regex("""^\d{3,}\s+"""),            // Starts with numbers
            Regex("""\s+v\d+$""", RegexOption.IGNORE_CASE), // Version numbers at end
            Regex("""arXiv:\d+\.\d+""", RegexOption.IGNORE_CASE), // arXiv ID in title
            Regex("""\s+page\s+\d+""", RegexOption.IGNORE_CASE), // Page numbers
        )

        return noisePatterns.none { it.containsMatchIn(title) }
    }
}

/**
 * Result of LLM-based PDF analysis
 */
data class PdfLLMAnalysisResult(
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val abstract: String? = null,
    val abstractKorean: String? = null
)

@Serializable
private data class PdfLLMAnalysisJson(
    val title: String? = null,
    val authors: List<String>? = null,
    val abstract: String? = null,
    val abstractKorean: String? = null
)
