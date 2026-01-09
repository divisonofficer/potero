package com.potero.service.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Comprehensive paper analysis service
 *
 * Extracts ALL metadata from a paper in a single LLM call:
 * - Cleaned title
 * - Cleaned author names
 * - Tags/keywords
 * - Abstract (if missing)
 * - Year, venue, etc.
 */
class ComprehensivePaperAnalysisService(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Analyze paper and extract all metadata in one LLM call
     */
    suspend fun analyzeComprehensive(
        title: String,
        authors: List<String>,
        abstract: String? = null,
        fullText: String? = null,
        existingTags: List<String> = emptyList()
    ): Result<ComprehensiveAnalysisResult> = runCatching {
        val prompt = buildComprehensivePrompt(title, authors, abstract, fullText, existingTags)

        val startTime = System.currentTimeMillis()
        val llmResult = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        llmResult.fold(
            onSuccess = { response ->
                // Log LLM usage
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "comprehensive_analysis",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true,
                    paperTitle = title
                )

                parseAnalysisResponse(response, title, authors)
            },
            onFailure = { error ->
                // Log failed LLM usage
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "comprehensive_analysis",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message,
                    paperTitle = title
                )
                throw error
            }
        )
    }

    private fun buildComprehensivePrompt(
        title: String,
        authors: List<String>,
        abstract: String?,
        fullText: String?,
        existingTags: List<String>
    ): String = """
Extract ALL metadata from this academic paper by analyzing the paper content directly.

**Paper Content (primary source - extract from HERE):**
${fullText?.let { "Full text excerpt:\n${it.take(3000)}\n\n" } ?: ""}
${abstract?.let { "Abstract section:\n$it\n\n" } ?: ""}

**IMPORTANT:** The metadata below may be INCORRECT. DO NOT copy them directly.
Extract the correct information from the paper content above instead.

Current metadata (possibly wrong):
- Title (may be wrong): $title
- Authors (may be wrong): ${authors.joinToString(", ")}

**Your Task:**
Read the paper content above and extract the CORRECT metadata. Ignore the "possibly wrong" fields.
Respond in **JSON format only**:

{
  "cleanedTitle": "Properly formatted title (remove extra spaces, fix capitalization)",
  "cleanedAuthors": ["Author 1", "Author 2", "..."],
  "tags": ["tag1", "tag2", "tag3", "..."],
  "abstract": "Brief abstract if missing (3-5 sentences)",
  "year": 2024,
  "venue": "Conference or Journal name",
  "researchArea": "Primary research area (e.g., Computer Vision, NLP)",
  "methodology": "Main methodology used",
  "keyContributions": ["contribution1", "contribution2"]
}

**Guidelines:**
1. **cleanedTitle**: Fix formatting issues, proper capitalization, remove artifacts
2. **cleanedAuthors**: Clean author names, fix formatting, remove affiliations
3. **tags**: Extract 5-10 relevant keywords (mix of general and specific)
   - Existing tags in system: ${existingTags.take(20).joinToString(", ")}
   - Reuse existing tags when applicable
4. **abstract**: Only if not provided above - create a brief summary
5. **year**, **venue**: Extract from content if available
6. **researchArea**: High-level field (Computer Vision, NLP, Robotics, etc.)
7. **methodology**: Main approach (Deep Learning, Optimization, Statistical, etc.)
8. **keyContributions**: 2-3 main contributions

**Important**: Respond with ONLY the JSON object, no additional text.
""".trimIndent()

    private fun parseAnalysisResponse(
        response: String,
        originalTitle: String,
        originalAuthors: List<String>
    ): ComprehensiveAnalysisResult {
        return try {
            // Extract JSON from response (handle cases where LLM adds markdown code blocks or HTML comments)
            var jsonText = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Remove HTML comments like <!-- tools: none -->
            jsonText = jsonText.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()

            // Find JSON object boundaries
            val jsonStart = jsonText.indexOf('{')
            val jsonEnd = jsonText.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonText = jsonText.substring(jsonStart, jsonEnd + 1)
            }

            val parsed = json.decodeFromString<ComprehensiveAnalysisJson>(jsonText)

            ComprehensiveAnalysisResult(
                cleanedTitle = parsed.cleanedTitle?.takeIf { it.isNotBlank() } ?: originalTitle,
                cleanedAuthors = parsed.cleanedAuthors?.filter { it.isNotBlank() }?.ifEmpty { originalAuthors } ?: originalAuthors,
                tags = parsed.tags ?: emptyList(),
                abstract = parsed.abstract,
                year = parsed.year,
                venue = parsed.venue,
                researchArea = parsed.researchArea,
                methodology = parsed.methodology,
                keyContributions = parsed.keyContributions ?: emptyList()
            )
        } catch (e: Exception) {
            println("[ComprehensiveAnalysis] Failed to parse LLM response: ${e.message}")
            println("[ComprehensiveAnalysis] Response was: $response")

            // Fallback: return minimal result
            ComprehensiveAnalysisResult(
                cleanedTitle = originalTitle,
                cleanedAuthors = originalAuthors,
                tags = emptyList()
            )
        }
    }
}

@Serializable
private data class ComprehensiveAnalysisJson(
    val cleanedTitle: String? = null,
    val cleanedAuthors: List<String>? = null,
    val tags: List<String>? = null,
    val abstract: String? = null,
    val year: Int? = null,
    val venue: String? = null,
    val researchArea: String? = null,
    val methodology: String? = null,
    val keyContributions: List<String>? = null
)

@Serializable
data class ComprehensiveAnalysisResult(
    val cleanedTitle: String,
    val cleanedAuthors: List<String>,
    val tags: List<String>,
    val abstract: String? = null,
    val year: Int? = null,
    val venue: String? = null,
    val researchArea: String? = null,
    val methodology: String? = null,
    val keyContributions: List<String> = emptyList()
)
