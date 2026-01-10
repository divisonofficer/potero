package com.potero.service.grobid

import com.potero.domain.model.GrobidReference
import com.potero.service.llm.LLMLogger
import com.potero.service.llm.LLMService
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * LLM-based reference parser as fallback when GROBID fails
 *
 * Parses References section text using LLM and converts to GrobidReference format.
 * Uses anti-hallucination prompt to ensure quality output.
 */
class LLMReferenceParser(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Parse references text using LLM fallback
     *
     * @param referencesText Full text of References section (from PdfAnalyzer)
     * @param paperId Paper ID for linking
     * @return List of GrobidReference objects with source="llm"
     */
    suspend fun parse(referencesText: String, paperId: String): Result<List<GrobidReference>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val startTime = System.currentTimeMillis()

                println("[LLMReferenceParser] Parsing ${referencesText.length} chars of references")

                // Log first 500 chars of input to debug
                val preview = referencesText.take(500).replace("\n", "\\n")
                println("[LLMReferenceParser] Input preview: $preview")

                // Detect if we should use chunked parsing
                val estimatedRefCount = countReferences(referencesText)
                println("[LLMReferenceParser] Estimated reference count: $estimatedRefCount")

                // Use chunked parsing if many references or text is very long
                // Lower thresholds to prevent LLM truncation
                if (estimatedRefCount > 15 || referencesText.length > 15000) {
                    println("[LLMReferenceParser] Using chunked parsing for better completion (refs=$estimatedRefCount, len=${referencesText.length})")
                    return@runCatching parseInChunks(referencesText, paperId)
                }

                // Truncate if too long (LLM context limit)
                val truncatedText = if (referencesText.length > 50000) {
                    println("[LLMReferenceParser] Warning: Input too long (${referencesText.length} chars), truncating to 50000")
                    referencesText.take(50000)
                } else {
                    referencesText
                }

                // Step 1: Build LLM prompt with anti-hallucination rules
                val prompt = buildPrompt(truncatedText)

                // Step 2: Call LLM service
                val llmResult = llmService.chat(prompt)
                val endTime = System.currentTimeMillis()
                val durationMs = endTime - startTime

                llmResult.fold(
                    onSuccess = { response ->
                        println("[LLMReferenceParser] Received LLM response: ${response.length} chars")
                        println("[LLMReferenceParser] Response preview: ${response.take(200)}")

                        // Log successful LLM usage
                        llmLogger.log(
                            provider = llmService.provider,
                            purpose = "grobid_fallback_references",
                            inputPrompt = prompt,
                            outputResponse = response,
                            durationMs = durationMs,
                            success = true,
                            paperTitle = "Paper ID: $paperId"
                        )

                        // Step 3: Parse JSON response
                        val llmResponse = parseJsonResponse(response)

                        if (llmResponse.references.isEmpty()) {
                            println("[LLMReferenceParser] WARNING: LLM returned empty array")
                            println("[LLMReferenceParser] This usually means the input text format was not recognized")
                            return@runCatching emptyList()
                        }

                        println("[LLMReferenceParser] Parsed ${llmResponse.references.size} references from LLM")

                        // Step 4: Convert to GrobidReference
                        val grobidReferences = llmResponse.references.mapIndexed { index, llmRef ->
                            convertToGrobidReference(llmRef, paperId, index)
                        }

                        println("[LLMReferenceParser] Converted to ${grobidReferences.size} GrobidReferences")

                        grobidReferences
                    },
                    onFailure = { error ->
                        println("[LLMReferenceParser] LLM call failed: ${error.message}")

                        // Log failed LLM usage
                        llmLogger.log(
                            provider = llmService.provider,
                            purpose = "grobid_fallback_references",
                            inputPrompt = prompt,
                            outputResponse = null,
                            durationMs = durationMs,
                            success = false,
                            errorMessage = error.message,
                            paperTitle = "Paper ID: $paperId"
                        )

                        throw error
                    }
                )
            }
        }
    }

    /**
     * Parse references in chunks to avoid LLM truncation.
     * Splits references into smaller batches and processes separately.
     */
    private suspend fun parseInChunks(referencesText: String, paperId: String): List<GrobidReference> {
        println("[LLMReferenceParser] Starting chunked parsing")

        // Split text into reference entries
        val referenceChunks = splitIntoReferenceChunks(referencesText, chunkSize = 20)
        println("[LLMReferenceParser] Split into ${referenceChunks.size} chunks")

        val allReferences = mutableListOf<GrobidReference>()

        for ((chunkIndex, chunk) in referenceChunks.withIndex()) {
            println("[LLMReferenceParser] Processing chunk ${chunkIndex + 1}/${referenceChunks.size}")

            val prompt = buildPrompt(chunk)

            // Retry logic for timeouts
            var retryCount = 0
            val maxRetries = 3
            var success = false

            while (retryCount < maxRetries && !success) {
                if (retryCount > 0) {
                    println("[LLMReferenceParser] Retry attempt ${retryCount}/${maxRetries - 1} for chunk ${chunkIndex + 1}")
                    kotlinx.coroutines.delay(1000L * retryCount) // Exponential backoff: 1s, 2s, 3s
                }

                val llmResult = llmService.chat(prompt)

                llmResult.fold(
                    onSuccess = { response ->
                        val llmResponse = parseJsonResponse(response)
                        println("[LLMReferenceParser] Chunk ${chunkIndex + 1}: parsed ${llmResponse.references.size} references")

                        val grobidReferences = llmResponse.references.mapIndexed { index, llmRef ->
                            convertToGrobidReference(llmRef, paperId, allReferences.size + index)
                        }

                        allReferences.addAll(grobidReferences)
                        success = true
                    },
                    onFailure = { error ->
                        val isTimeout = error.message?.contains("timeout", ignoreCase = true) == true

                        if (isTimeout && retryCount < maxRetries - 1) {
                            println("[LLMReferenceParser] Chunk ${chunkIndex + 1} timeout - will retry")
                            retryCount++
                        } else {
                            println("[LLMReferenceParser] Chunk ${chunkIndex + 1} failed: ${error.message}")
                            if (isTimeout) {
                                println("[LLMReferenceParser] Max retries (${maxRetries}) exceeded for chunk ${chunkIndex + 1}")
                            }
                            // Continue with other chunks
                            success = true // Exit retry loop
                        }
                    }
                )
            }

            // Small delay to avoid rate limiting
            if (chunkIndex < referenceChunks.size - 1) {
                kotlinx.coroutines.delay(500)
            }
        }

        println("[LLMReferenceParser] Chunked parsing complete: ${allReferences.size} total references")
        return allReferences
    }

    /**
     * Count estimated number of references in text.
     * Looks for common reference patterns like [1], [2], (1), (2), etc.
     */
    private fun countReferences(text: String): Int {
        // Pattern: Lines with [N] or (N) where N is a number (1-999)
        // More lenient - doesn't require capital letter after number
        val pattern = Regex("""^\s*\[(\d{1,3})\]""", RegexOption.MULTILINE)
        val matches = pattern.findAll(text).toList()

        // Alternative pattern for (N) style
        val parenPattern = Regex("""^\s*\((\d{1,3})\)""", RegexOption.MULTILINE)
        val parenMatches = parenPattern.findAll(text).toList()

        val totalCount = maxOf(matches.size, parenMatches.size)
        println("[LLMReferenceParser] countReferences: [N] style: ${matches.size}, (N) style: ${parenMatches.size}, using: $totalCount")

        return totalCount
    }

    /**
     * Split references text into chunks.
     * Each chunk contains up to `chunkSize` reference entries.
     */
    private fun splitIntoReferenceChunks(text: String, chunkSize: Int = 20): List<String> {
        val lines = text.lines()
        val chunks = mutableListOf<String>()

        // Pattern to detect reference start: [N] or (N)
        // More lenient - doesn't require capital letter after
        val refStartPattern = Regex("""^\s*[\[\(](\d+)[\]\)]""")

        var currentChunk = mutableListOf<String>()
        var refCount = 0

        for (line in lines) {
            val match = refStartPattern.find(line.trim())

            if (match != null) {
                // New reference detected
                if (refCount >= chunkSize && currentChunk.isNotEmpty()) {
                    // Start new chunk
                    chunks.add(currentChunk.joinToString("\n"))
                    currentChunk = mutableListOf()
                    refCount = 0
                }
                refCount++
            }

            currentChunk.add(line)
        }

        // Add remaining lines
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.joinToString("\n"))
        }

        return chunks
    }

    /**
     * Build LLM prompt with anti-hallucination rules
     * Uses improved template with more explicit instructions
     */
    private fun buildPrompt(referencesText: String): String {
        return """
You are a bibliographic reference parser. Extract ALL academic references from the text below.

## INPUT
The text may contain:
- Page markers like "<<<PAGE 10>>>" (ignore these)
- A "References" or "Bibliography" section
- Numbered entries like [1], [2], (1), (2), etc.

## CRITICAL RULES - MUST FOLLOW
1. **IF THE TEXT IS GARBLED/UNREADABLE** (many control characters, nonsense symbols):
   → Return EMPTY array: {"references":[]}
   → DO NOT invent or make up any data

2. **IF THE TEXT IS READABLE BUT NO REFERENCES FOUND**:
   → Return EMPTY array: {"references":[]}
   → DO NOT use placeholder or example data

3. **IF REFERENCES ARE FOUND**:
   → Parse EVERY SINGLE reference entry (do NOT skip or omit ANY)
   → DO NOT use placeholders like "... (omitted for brevity)" or "// ... more references"
   → DO NOT stop in the middle - COMPLETE THE ENTIRE ARRAY
   → If there are 50+ references, parse ALL of them (not just first few)
   → Extract DOI only if explicitly present in text
   → Use null for unknown fields (do NOT guess or invent)
   → Include complete raw text for each reference

## COMPLETION REQUIREMENT - READ THIS CAREFULLY
**ABSOLUTELY MANDATORY - NO EXCEPTIONS**:
- You MUST parse EVERY SINGLE reference from start to finish
- DO NOT stop after a few references and say "... more references"
- DO NOT truncate the response - complete the entire JSON array
- If there are 10 references, parse ALL 10. If 50, parse ALL 50.
- The JSON must end with: }, ...last entry... ]}  (complete array with closing brackets)
- **NEVER EVER** write explanatory text like "(continuing for all references)" or "(full array would continue...)"

## OUTPUT FORMAT
Return ONLY valid JSON (no markdown, no explanations, no code blocks, NO COMMENTS):
**ABSOLUTELY NO COMMENTS** - Do NOT include // comments or explanatory text in the JSON
**VALID JSON ONLY** - Must be parseable by standard JSON parser

{"references":[
  {
    "refIndex": 1,
    "refLabel": "[1]",
    "raw": "exact reference text from input",
    "authors": [{"family":"LastName","given":"FirstName","raw":"Full name as appears"}],
    "title": "Paper title or null",
    "year": 2020,
    "venue": "Journal/Conference name or null",
    "doi": "10.xxxx/xxxx or null",
    "url": "https://... or null",
    "confidence": 0.8
  }
]}

## TEXT TO PARSE

$referencesText

## YOUR COMPLETE JSON OUTPUT (ALL REFERENCES, NO TRUNCATION)
        """.trimIndent()
    }

    /**
     * Parse JSON response from LLM
     * Handles markdown code blocks, HTML comments, and malformed JSON
     */
    private fun parseJsonResponse(response: String): LLMReferenceResponse {
        // Clean up response: remove HTML comments, and trim
        // IMPORTANT: Don't remove // in middle of lines (breaks URLs like https://...)
        var cleanedResponse = response
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "") // Remove <!-- ... -->
            .replace(Regex("""^\s*//.*$""", RegexOption.MULTILINE), "") // Remove ONLY line-start // comments
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "") // Remove /* */ multi-line comments
            .replace(Regex("""https:\s+//"""), "https://") // Fix broken URLs: "https: //" -> "https://"
            .replace(Regex("""http:\s+//"""), "http://")   // Fix broken URLs: "http: //" -> "http://"
            .trim()

        // Try to parse as-is first
        try {
            return json.decodeFromString<LLMReferenceResponse>(cleanedResponse)
        } catch (e: Exception) {
            println("[LLMReferenceParser] Direct JSON parse failed, trying markdown extraction")
        }

        // Try to extract from markdown code block
        val markdownPattern = Regex("""```json\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val markdownMatch = markdownPattern.find(cleanedResponse)
        if (markdownMatch != null) {
            val jsonContent = markdownMatch.groupValues[1]
            try {
                return json.decodeFromString<LLMReferenceResponse>(jsonContent)
            } catch (e: Exception) {
                println("[LLMReferenceParser] Markdown JSON parse failed: ${e.message}")
            }
        }

        // Try to extract just the JSON object (from { to })
        val jsonPattern = Regex("""\{.*"references".*\}""", RegexOption.DOT_MATCHES_ALL)
        val jsonMatch = jsonPattern.find(cleanedResponse)
        if (jsonMatch != null) {
            val jsonContent = jsonMatch.value
            try {
                return json.decodeFromString<LLMReferenceResponse>(jsonContent)
            } catch (e: Exception) {
                println("[LLMReferenceParser] Extracted JSON parse failed: ${e.message}")
            }
        }

        // Try to fix common JSON issues
        val fixedJson = cleanedResponse
            .replace(Regex(",\\s*}"), "}") // Remove trailing commas before }
            .replace(Regex(",\\s*]"), "]") // Remove trailing commas before ]

        try {
            return json.decodeFromString<LLMReferenceResponse>(fixedJson)
        } catch (e: Exception) {
            println("[LLMReferenceParser] All JSON parsing attempts failed: ${e.message}")
            throw IllegalArgumentException("Failed to parse LLM response as JSON: ${e.message}")
        }
    }

    /**
     * Convert LLM reference to GrobidReference format
     */
    private fun convertToGrobidReference(
        llmRef: LLMReference,
        paperId: String,
        index: Int
    ): GrobidReference {
        // Use refIndex if available, otherwise use array index
        val refNumber = llmRef.refIndex ?: (index + 1)

        // Validate and sanitize DOI
        val validatedDoi = validateDoi(llmRef.doi)

        // Validate year range
        val validatedYear = if (llmRef.year != null && llmRef.year in 1900..2099) {
            llmRef.year
        } else {
            null
        }

        // Concatenate authors
        val authorsString = if (llmRef.authors.isNotEmpty()) {
            llmRef.authors.joinToString(", ") { author ->
                val given = author.given?.trim() ?: ""
                val family = author.family?.trim() ?: ""
                "$given $family".trim().ifEmpty { author.raw }
            }
        } else {
            null
        }

        // Calculate confidence (scale down from LLM's confidence)
        // LLM confidence 0.9 → GrobidReference 0.63 (0.9 * 0.7)
        val scaledConfidence = llmRef.confidence * 0.7

        return GrobidReference(
            id = UUID.randomUUID().toString(),
            paperId = paperId,
            xmlId = "llm-ref-$refNumber", // Synthetic ID for linking
            rawTei = "<!-- source: llm -->\n${llmRef.raw}", // Mark source + store raw text
            authors = authorsString,
            title = llmRef.title?.trim()?.ifEmpty { null },
            venue = llmRef.venue?.trim()?.ifEmpty { null },
            year = validatedYear,
            doi = validatedDoi,
            arxivId = null, // LLM doesn't extract arXiv IDs currently
            pageNum = null, // LLM doesn't have page info
            confidence = scaledConfidence,
            createdAt = Clock.System.now()
        )
    }

    /**
     * Validate DOI format
     * Pattern: 10.xxxx/xxxxx
     */
    private fun validateDoi(doi: String?): String? {
        if (doi.isNullOrBlank()) return null

        val doiPattern = Regex("""^10\.\d{4,9}/[-._;()/:A-Z0-9]+$""", RegexOption.IGNORE_CASE)
        return if (doiPattern.matches(doi.trim())) {
            doi.trim()
        } else {
            println("[LLMReferenceParser] Invalid DOI format: $doi")
            null
        }
    }
}

/**
 * Data classes for LLM JSON response
 */
@Serializable
data class LLMReferenceResponse(
    val references: List<LLMReference>
)

@Serializable
data class LLMReference(
    val refIndex: Int? = null,
    val refLabel: String? = null,
    val raw: String,
    val authors: List<LLMAuthor> = emptyList(),
    val title: String? = null,
    val year: Int? = null,
    val venue: String? = null,
    val doi: String? = null,
    val url: String? = null,
    val confidence: Double = 0.8
)

@Serializable
data class LLMAuthor(
    val family: String? = null,
    val given: String? = null,
    val raw: String
)
