package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.service.llm.LLMService
import com.potero.service.llm.LLMLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stage 1: Structural Understanding
 *
 * Purpose: Understand the paper's structure, purpose, and key elements.
 * This stage is language-agnostic and creates a foundation for all narratives.
 */
class StructuralUnderstandingProcessor(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Process paper to extract structural understanding
     */
    suspend fun process(
        paper: Paper,
        pdfText: String?,
        figures: List<FigureInfo>
    ): Result<StructuralUnderstanding> = runCatching {
        val prompt = buildStructuralPrompt(paper, pdfText, figures)

        val startTime = System.currentTimeMillis()
        val llmResult = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        llmResult.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_structural_understanding",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true,
                    paperId = paper.id,
                    paperTitle = paper.title
                )

                parseStructuralResponse(response, paper.id)
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_structural_understanding",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message,
                    paperId = paper.id,
                    paperTitle = paper.title
                )
                throw error
            }
        )
    }

    private fun buildStructuralPrompt(
        paper: Paper,
        pdfText: String?,
        figures: List<FigureInfo>
    ): String = """
You are an expert at understanding academic papers. Analyze the following paper and extract its structural understanding.

## Paper Information
- Title: ${paper.title}
- Authors: ${paper.formattedAuthors}
- Year: ${paper.year ?: "Unknown"}
- Venue: ${paper.conference ?: "Unknown"}
- Abstract: ${paper.abstract ?: "Not available"}

## Paper Content (truncated)
${pdfText?.take(8000) ?: "Full text not available. Use abstract and title for analysis."}

## Figures in Paper
${if (figures.isEmpty()) "No figures available" else figures.mapIndexed { i, f ->
    "- ${f.label ?: "Figure ${i + 1}"}: ${f.caption ?: "No caption"}"
}.joinToString("\n")}

## Your Task
Analyze this paper and respond in JSON format:

{
    "mainObjective": "What is the paper trying to achieve? (1-2 sentences)",
    "researchQuestion": "What specific question does this paper answer?",
    "methodology": "What approach/method does the paper use? (2-3 sentences)",
    "keyFindings": ["Finding 1", "Finding 2", "Finding 3"],
    "contributions": ["Contribution 1", "Contribution 2"],
    "sections": [
        {
            "title": "Introduction",
            "purpose": "Sets up the problem and motivation",
            "keyPoints": ["Point 1", "Point 2"]
        }
    ],
    "targetAudience": "Who would benefit from reading this? (e.g., ML researchers, practitioners)",
    "prerequisites": ["Concept 1 that readers should know", "Concept 2"]
}

Important:
- Be comprehensive but concise
- Identify 3-5 key prerequisite concepts that general readers might not know
- Focus on what makes this paper unique/important
- sections should cover main parts: Introduction, Related Work, Method, Experiments, Conclusion

Respond with ONLY the JSON object, no additional text.
""".trimIndent()

    private fun parseStructuralResponse(response: String, paperId: String): StructuralUnderstanding {
        return try {
            var jsonText = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Remove HTML comments
            jsonText = jsonText.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()

            // Find JSON object boundaries
            val jsonStart = jsonText.indexOf('{')
            val jsonEnd = jsonText.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonText = jsonText.substring(jsonStart, jsonEnd + 1)
            }

            val parsed = json.decodeFromString<StructuralUnderstandingJson>(jsonText)

            StructuralUnderstanding(
                paperId = paperId,
                mainObjective = parsed.mainObjective ?: "Unable to determine objective",
                researchQuestion = parsed.researchQuestion ?: "Unable to determine research question",
                methodology = parsed.methodology ?: "Unable to determine methodology",
                keyFindings = parsed.keyFindings ?: emptyList(),
                contributions = parsed.contributions ?: emptyList(),
                sections = parsed.sections?.map { s ->
                    SectionSummary(
                        title = s.title ?: "Unknown",
                        purpose = s.purpose ?: "",
                        keyPoints = s.keyPoints ?: emptyList()
                    )
                } ?: emptyList(),
                targetAudience = parsed.targetAudience ?: "Researchers and practitioners",
                prerequisites = parsed.prerequisites ?: emptyList()
            )
        } catch (e: Exception) {
            println("[StructuralUnderstanding] Failed to parse LLM response: ${e.message}")
            println("[StructuralUnderstanding] Response was: $response")

            // Fallback
            StructuralUnderstanding(
                paperId = paperId,
                mainObjective = "Unable to determine",
                researchQuestion = "Unable to determine",
                methodology = "Unable to determine",
                targetAudience = "Researchers"
            )
        }
    }
}

@Serializable
private data class StructuralUnderstandingJson(
    val mainObjective: String? = null,
    val researchQuestion: String? = null,
    val methodology: String? = null,
    val keyFindings: List<String>? = null,
    val contributions: List<String>? = null,
    val sections: List<SectionJson>? = null,
    val targetAudience: String? = null,
    val prerequisites: List<String>? = null
)

@Serializable
private data class SectionJson(
    val title: String? = null,
    val purpose: String? = null,
    val keyPoints: List<String>? = null
)

/**
 * Simple figure info for processor input
 */
data class FigureInfo(
    val id: String,
    val label: String?,
    val caption: String?
)
