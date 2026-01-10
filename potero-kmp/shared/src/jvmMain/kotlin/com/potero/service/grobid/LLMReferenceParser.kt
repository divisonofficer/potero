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
     * Build LLM prompt with anti-hallucination rules
     * Uses improved template with more explicit instructions
     */
    private fun buildPrompt(referencesText: String): String {
        return """
You are a bibliographic reference parser. Extract ALL academic references from the text below.

## INPUT FORMAT
The text may contain:
- Page markers like "<<<PAGE 10>>>" (ignore these)
- Multiple pages of text
- A "References" or "Bibliography" section (usually at the end)
- Numbered entries like [1], [2], (1), (2), etc.

## YOUR TASK
1. Find the References/Bibliography section (if present)
2. Parse EVERY reference entry you can find
3. DO NOT return empty array unless there are truly NO references
4. Output ONLY valid JSON (no markdown, no explanation)

## CRITICAL RULES
- **PARSE EVERY SINGLE REFERENCE** - Do NOT skip or omit any entries
- **DO NOT use placeholders** like "... (중략)" or "... (omitted for brevity)"
- **DO NOT summarize** - Every reference must be included in full
- If there are 50+ references, parse ALL of them (not just a few examples)
- Extract DOI only if explicitly present (format: 10.xxxx/xxxx)
- Use null for unknown fields (do NOT invent data)
- Include complete raw text for each reference
- Preserve reference numbering from original text
- Typical references include: author names, title, venue/journal, year, pages, DOI

## OUTPUT FORMAT
{"references":[{"refIndex":1,"refLabel":"[1]","raw":"Full text of reference...","authors":[{"family":"LastName","given":"FirstName","raw":"Name as appears"}],"title":"Paper Title","year":2020,"venue":"Conference/Journal","doi":"10.xxxx/yyyy","url":"https://...","confidence":0.9}]}

## EXAMPLE
Input text:
```
References
[1] A. Smith and B. Jones. Deep Learning for Vision. CVPR 2020, pp. 1-10. DOI: 10.1109/CVPR.2020.001
[2] C. Lee et al. Natural Language Processing. arXiv:2001.12345, 2021.
```

Output:
{"references":[{"refIndex":1,"refLabel":"[1]","raw":"A. Smith and B. Jones. Deep Learning for Vision. CVPR 2020, pp. 1-10. DOI: 10.1109/CVPR.2020.001","authors":[{"family":"Smith","given":"A.","raw":"A. Smith"},{"family":"Jones","given":"B.","raw":"B. Jones"}],"title":"Deep Learning for Vision","year":2020,"venue":"CVPR","doi":"10.1109/CVPR.2020.001","url":null,"confidence":0.95},{"refIndex":2,"refLabel":"[2]","raw":"C. Lee et al. Natural Language Processing. arXiv:2001.12345, 2021.","authors":[{"family":"Lee","given":"C.","raw":"C. Lee et al."}],"title":"Natural Language Processing","year":2021,"venue":"arXiv","doi":null,"url":null,"confidence":0.85}]}

## TEXT TO PARSE

$referencesText

## OUTPUT
Now output the JSON object with ALL references (no omissions, no placeholders):
        """.trimIndent()
    }

    /**
     * Parse JSON response from LLM
     * Handles markdown code blocks, HTML comments, and malformed JSON
     */
    private fun parseJsonResponse(response: String): LLMReferenceResponse {
        // Clean up response: remove HTML comments and trim
        var cleanedResponse = response
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "") // Remove <!-- ... -->
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
