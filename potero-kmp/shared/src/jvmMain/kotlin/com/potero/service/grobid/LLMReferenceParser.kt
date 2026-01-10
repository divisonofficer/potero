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
                println("[LLMReferenceParser] Parsing ${referencesText.length} chars of references")

                // Step 1: Build LLM prompt with anti-hallucination rules
                val prompt = buildPrompt(referencesText)

                // Step 2: Call LLM service
                val response = llmService.chat(prompt).getOrElse { error ->
                    println("[LLMReferenceParser] LLM call failed: ${error.message}")
                    throw error
                }

                println("[LLMReferenceParser] Received LLM response: ${response.length} chars")

                // Step 3: Parse JSON response
                val llmResponse = parseJsonResponse(response)

                if (llmResponse.references.isEmpty()) {
                    println("[LLMReferenceParser] LLM returned no references")
                    return@runCatching emptyList()
                }

                println("[LLMReferenceParser] Parsed ${llmResponse.references.size} references from LLM")

                // Step 4: Convert to GrobidReference
                val grobidReferences = llmResponse.references.mapIndexed { index, llmRef ->
                    convertToGrobidReference(llmRef, paperId, index)
                }

                println("[LLMReferenceParser] Converted to ${grobidReferences.size} GrobidReferences")

                grobidReferences
            }
        }
    }

    /**
     * Build LLM prompt with anti-hallucination rules
     * Uses improved template with more explicit instructions
     */
    private fun buildPrompt(referencesText: String): String {
        return """
You are a reference parser for academic papers. Your task is to extract bibliographic references from the provided text.

**CRITICAL INSTRUCTIONS:**
1. Output ONLY valid JSON, no markdown, no explanation, no code blocks
2. Look for entries that start with numbers like [1], [2], (1), (2), or similar patterns
3. Each reference typically includes: authors, title, venue/journal, year, and optional DOI
4. Parse ALL references you can find - do NOT return empty array unless text truly has no references
5. Do not invent anything - if unsure, use null for that field
6. Extract DOI only if explicitly present (pattern: 10.xxxx/xxxx)
7. Always include the complete raw text for each reference

**Output Schema:**
{
  "references": [
    {
      "refIndex": <number or null>,
      "refLabel": "<[1] or (1) or null>",
      "raw": "<complete raw text of this reference entry>",
      "authors": [{"family": "<last name>", "given": "<first name>", "raw": "<author name as-is>"}],
      "title": "<paper title or null>",
      "year": <4-digit year or null>,
      "venue": "<conference/journal name or null>",
      "doi": "<10.xxxx/xxxx or null>",
      "url": "<http(s)://... or null>",
      "confidence": <0.0-1.0, your confidence in this parse>
    }
  ]
}

**Example Input:**
[1] Smith, J. and Doe, A. Deep Learning for Computer Vision. In CVPR 2020, pp. 123-456. DOI: 10.1109/CVPR.2020.00123
[2] Johnson et al. Natural Language Processing: A Survey. arXiv:2012.12345, 2020.

**Example Output:**
{"references":[{"refIndex":1,"refLabel":"[1]","raw":"Smith, J. and Doe, A. Deep Learning for Computer Vision. In CVPR 2020, pp. 123-456. DOI: 10.1109/CVPR.2020.00123","authors":[{"family":"Smith","given":"J.","raw":"Smith, J."},{"family":"Doe","given":"A.","raw":"Doe, A."}],"title":"Deep Learning for Computer Vision","year":2020,"venue":"CVPR","doi":"10.1109/CVPR.2020.00123","url":null,"confidence":0.95},{"refIndex":2,"refLabel":"[2]","raw":"Johnson et al. Natural Language Processing: A Survey. arXiv:2012.12345, 2020.","authors":[{"family":"Johnson","given":null,"raw":"Johnson et al."}],"title":"Natural Language Processing: A Survey","year":2020,"venue":"arXiv","doi":null,"url":null,"confidence":0.85}]}

**Now parse the following text:**

$referencesText

**Remember:** Output ONLY the JSON object, starting with { and ending with }. No other text.
        """.trimIndent()
    }

    /**
     * Parse JSON response from LLM
     * Handles markdown code blocks and malformed JSON
     */
    private fun parseJsonResponse(response: String): LLMReferenceResponse {
        // Try to parse as-is first
        try {
            return json.decodeFromString<LLMReferenceResponse>(response)
        } catch (e: Exception) {
            println("[LLMReferenceParser] Direct JSON parse failed, trying markdown extraction")
        }

        // Try to extract from markdown code block
        val markdownPattern = Regex("""```json\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val markdownMatch = markdownPattern.find(response)
        if (markdownMatch != null) {
            val jsonContent = markdownMatch.groupValues[1]
            try {
                return json.decodeFromString<LLMReferenceResponse>(jsonContent)
            } catch (e: Exception) {
                println("[LLMReferenceParser] Markdown JSON parse failed: ${e.message}")
            }
        }

        // Try to fix common JSON issues
        val fixedJson = response
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
