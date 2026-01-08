package com.potero.service.llm

import kotlinx.datetime.Clock

/**
 * Service for cleaning extracted metadata using LLM
 */
class MetadataCleaningService(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {
    /**
     * Clean extracted title using LLM
     * Removes noise like page numbers, watermarks, artifacts from PDF extraction
     */
    suspend fun cleanTitle(
        rawTitle: String,
        context: String? = null,
        paperId: String? = null
    ): Result<String> {
        if (rawTitle.isBlank()) {
            return Result.success(rawTitle)
        }

        // Skip if title looks clean already (no obvious noise)
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
                    // Return original title on failure
                    Result.success(rawTitle)
                }
            )
        } catch (e: Exception) {
            val durationMs = (Clock.System.now() - startTime).inWholeMilliseconds
            llmLogger.log(
                provider = llmService.provider,
                purpose = "title_cleaning",
                inputPrompt = prompt,
                outputResponse = null,
                durationMs = durationMs,
                success = false,
                errorMessage = e.message,
                paperId = paperId,
                paperTitle = rawTitle
            )
            Result.success(rawTitle) // Return original on error
        }
    }

    /**
     * Clean extracted authors using LLM
     */
    suspend fun cleanAuthors(
        rawAuthors: List<String>,
        context: String? = null,
        paperId: String? = null
    ): Result<List<String>> {
        if (rawAuthors.isEmpty()) {
            return Result.success(rawAuthors)
        }

        val prompt = buildAuthorCleaningPrompt(rawAuthors, context)
        val startTime = Clock.System.now()

        return try {
            val result = llmService.chat(prompt)
            val durationMs = (Clock.System.now() - startTime).inWholeMilliseconds

            result.fold(
                onSuccess = { response ->
                    val cleanedAuthors = parseCleanedAuthors(response, rawAuthors)
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "author_cleaning",
                        inputPrompt = prompt,
                        outputResponse = response,
                        durationMs = durationMs,
                        success = true,
                        paperId = paperId,
                        paperTitle = rawAuthors.joinToString(", ")
                    )
                    Result.success(cleanedAuthors)
                },
                onFailure = { error ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "author_cleaning",
                        inputPrompt = prompt,
                        outputResponse = null,
                        durationMs = durationMs,
                        success = false,
                        errorMessage = error.message,
                        paperId = paperId
                    )
                    Result.success(rawAuthors)
                }
            )
        } catch (e: Exception) {
            Result.success(rawAuthors)
        }
    }

    private fun buildTitleCleaningPrompt(rawTitle: String, context: String?): String {
        return """
You are a metadata extraction assistant. Clean the following academic paper title extracted from a PDF.

Raw extracted title: "$rawTitle"
${context?.let { "Context from PDF: $it" } ?: ""}

Tasks:
1. Remove any page numbers, watermarks, or artifacts (e.g., "001", "arXiv:xxx", random numbers)
2. Remove any conference/journal headers that got mixed in
3. Fix any obvious typos or encoding issues
4. Keep the actual paper title intact

Respond with ONLY the cleaned title, nothing else. If the title is already clean, return it as-is.
""".trim()
    }

    private fun buildAuthorCleaningPrompt(rawAuthors: List<String>, context: String?): String {
        return """
You are a metadata extraction assistant. Clean the following author names extracted from a PDF.

Raw extracted authors: ${rawAuthors.joinToString(", ")}
${context?.let { "Context from PDF: $it" } ?: ""}

Tasks:
1. Remove any affiliation numbers or symbols (e.g., "John Smith1,2", "Jane Doe*")
2. Fix name ordering if needed (should be "First Last")
3. Remove any non-author text that got mixed in
4. Remove duplicate names

Respond with ONLY the cleaned author names, one per line. If already clean, return as-is.
""".trim()
    }

    private fun parseCleanedTitle(response: String, fallback: String): String {
        val cleaned = response.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .trim()

        // Sanity check: cleaned title should be reasonable length
        return if (cleaned.length in 5..500 && cleaned.isNotBlank()) {
            cleaned
        } else {
            fallback
        }
    }

    private fun parseCleanedAuthors(response: String, fallback: List<String>): List<String> {
        val lines = response.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length in 2..100 }

        return if (lines.isNotEmpty()) lines else fallback
    }

    /**
     * Heuristic check if title looks clean (no obvious noise)
     */
    private fun looksClean(title: String): Boolean {
        // Check for common noise patterns
        val noisePatterns = listOf(
            Regex("""\s+\d{3,}$"""), // Ends with numbers like "001", "1234"
            Regex("""^\d{3,}\s+"""), // Starts with numbers
            Regex("""\s+v\d+$""", RegexOption.IGNORE_CASE), // Version numbers at end
            Regex("""arXiv:\d+\.\d+""", RegexOption.IGNORE_CASE), // arXiv ID in title
            Regex("""\s+page\s+\d+""", RegexOption.IGNORE_CASE), // Page numbers
        )

        return noisePatterns.none { it.containsMatchIn(title) }
    }
}
