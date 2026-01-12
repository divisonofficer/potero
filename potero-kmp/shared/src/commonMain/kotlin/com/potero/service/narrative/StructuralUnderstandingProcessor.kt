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
        figures: List<FigureInfo>,
        tables: List<TableInfo> = emptyList(),
        formulas: List<FormulaInfo> = emptyList()
    ): Result<StructuralUnderstanding> = runCatching {
        val prompt = buildStructuralPrompt(paper, pdfText, figures, tables, formulas)

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
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        formulas: List<FormulaInfo>
    ): String = """
You are an expert at understanding academic papers. Analyze the following paper and extract its structural understanding.

## Paper Information
- Title: ${paper.title}
- Authors: ${paper.formattedAuthors}
- Year: ${paper.year ?: "Unknown"}
- Venue: ${paper.conference ?: "Unknown"}
- Abstract: ${paper.abstract ?: "Not available"}

## Paper Content (representative sections)
${if (pdfText != null) {
    SectionSampler.extractRepresentativeSections(pdfText)
} else {
    "Full text not available. Use abstract and title for analysis."
}}

## Figures in Paper
${if (figures.isEmpty()) "No figures available" else figures.mapIndexed { i, f ->
    "- ${f.label ?: "Figure ${i + 1}"}: ${f.caption ?: "No caption"}"
}.joinToString("\n")}

## Tables in Paper
${if (tables.isEmpty()) "No tables available" else tables.mapIndexed { i, t ->
    "- ${t.label ?: "Table ${i + 1}"}: ${t.caption ?: "No caption"}"
}.joinToString("\n")}

## Formulas in Paper
${if (formulas.isEmpty()) "No formulas extracted" else formulas.take(5).mapIndexed { i, f ->
    "- ${f.label ?: "Formula ${i + 1}"}: ${f.latex?.take(100) ?: "LaTeX not available"}"
}.joinToString("\n")}${if (formulas.size > 5) "\n... and ${formulas.size - 5} more formulas" else ""}

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
    "prerequisites": ["Concept 1 that readers should know", "Concept 2"],
    "claims": [
        {
            "statement": "The paper's main claim in one sentence",
            "type": "PROBLEM_MOTIVATION | TECHNICAL_CONTRIBUTION | EMPIRICAL_RESULT | THEORETICAL_RESULT | LIMITATION",
            "evidence": {
                "type": "TEXT_QUOTE | FIGURE | TABLE | FORMULA | SECTION",
                "snippet": "50-200 char quote from paper supporting this claim",
                "figureId": "${figures.getOrNull(0)?.id ?: "fig_id"}",
                "tableId": "${tables.getOrNull(0)?.id ?: "tbl_id"}",
                "formulaId": null,
                "sectionReference": "Section 3.2"
            },
            "confidence": "HIGH | MEDIUM | LOW"
        }
    ],
    "limitations": ["Limitation 1", "Limitation 2", "Limitation 3"],
    "relatedPapers": []
}

## Claims Extraction (8-12 items)
Extract key claims from the paper:
- statement: One sentence summarizing the claim
- type:
  * PROBLEM_MOTIVATION: Why this problem matters
  * TECHNICAL_CONTRIBUTION: New method/technique introduced
  * EMPIRICAL_RESULT: Experimental findings
  * THEORETICAL_RESULT: Theoretical analysis/proof
  * LIMITATION: Known weaknesses
- evidence:
  * type: Most relevant evidence type
  * snippet: Direct quote (50-200 chars) from paper
  * figureId/tableId/formulaId: Use exact IDs from lists above if referenced
  * sectionReference: "Section X.Y" or "Introduction", etc.
- confidence:
  * HIGH: Explicitly stated with strong evidence
  * MEDIUM: Implied or partially supported
  * LOW: Inferred

Available Figure IDs: ${figures.take(5).map { it.id }.joinToString(", ")}
Available Table IDs: ${tables.take(5).map { it.id }.joinToString(", ")}
Available Formula IDs: ${formulas.take(5).map { it.id }.joinToString(", ")}

## Limitations (3-5 items)
What are the weaknesses, open questions, or limitations mentioned?

## Related Papers
Leave empty for now (will be extracted separately).

Important:
- Be comprehensive but concise
- Extract 8-12 claims covering all aspects (problem, method, results, limitations)
- Every claim MUST have evidence with a snippet
- Use exact IDs from the lists above
- If no figure/table/formula is referenced, set those fields to null
- Snippet length: 50-200 characters
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
                prerequisites = parsed.prerequisites ?: emptyList(),
                claims = parsed.claims?.map { c ->
                    Claim(
                        statement = c.statement ?: "",
                        type = parseClaimType(c.type),
                        evidence = c.evidence?.let { e ->
                            Evidence(
                                type = parseEvidenceType(e.type),
                                snippet = e.snippet ?: "",
                                figureId = e.figureId,
                                tableId = e.tableId,
                                formulaId = e.formulaId,
                                sectionReference = e.sectionReference
                            )
                        } ?: Evidence(
                            type = EvidenceType.TEXT_QUOTE,
                            snippet = ""
                        ),
                        confidence = parseClaimConfidence(c.confidence)
                    )
                } ?: emptyList(),
                limitations = parsed.limitations ?: emptyList(),
                relatedPapers = parsed.relatedPapers?.map { rp ->
                    RelatedPaper(
                        grobidRefId = rp.grobidRefId ?: "",
                        title = rp.title ?: "",
                        authors = rp.authors ?: "",
                        year = rp.year,
                        relationship = rp.relationship ?: ""
                    )
                } ?: emptyList()
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

    private fun parseClaimType(typeStr: String?): ClaimType {
        return when (typeStr?.uppercase()) {
            "PROBLEM_MOTIVATION" -> ClaimType.PROBLEM_MOTIVATION
            "TECHNICAL_CONTRIBUTION" -> ClaimType.TECHNICAL_CONTRIBUTION
            "EMPIRICAL_RESULT" -> ClaimType.EMPIRICAL_RESULT
            "THEORETICAL_RESULT" -> ClaimType.THEORETICAL_RESULT
            "LIMITATION" -> ClaimType.LIMITATION
            else -> ClaimType.TECHNICAL_CONTRIBUTION
        }
    }

    private fun parseEvidenceType(typeStr: String?): EvidenceType {
        return when (typeStr?.uppercase()) {
            "TEXT_QUOTE" -> EvidenceType.TEXT_QUOTE
            "FIGURE" -> EvidenceType.FIGURE
            "TABLE" -> EvidenceType.TABLE
            "FORMULA" -> EvidenceType.FORMULA
            "SECTION" -> EvidenceType.SECTION
            else -> EvidenceType.TEXT_QUOTE
        }
    }

    private fun parseClaimConfidence(confidenceStr: String?): ClaimConfidence {
        return when (confidenceStr?.uppercase()) {
            "HIGH" -> ClaimConfidence.HIGH
            "MEDIUM" -> ClaimConfidence.MEDIUM
            "LOW" -> ClaimConfidence.LOW
            else -> ClaimConfidence.MEDIUM
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
    val prerequisites: List<String>? = null,
    val claims: List<ClaimJson>? = null,
    val limitations: List<String>? = null,
    val relatedPapers: List<RelatedPaperJson>? = null
)

@Serializable
private data class SectionJson(
    val title: String? = null,
    val purpose: String? = null,
    val keyPoints: List<String>? = null
)

@Serializable
private data class ClaimJson(
    val statement: String? = null,
    val type: String? = null,
    val evidence: EvidenceJson? = null,
    val confidence: String? = null
)

@Serializable
private data class EvidenceJson(
    val type: String? = null,
    val snippet: String? = null,
    val figureId: String? = null,
    val tableId: String? = null,
    val formulaId: String? = null,
    val sectionReference: String? = null
)

@Serializable
private data class RelatedPaperJson(
    val grobidRefId: String? = null,
    val title: String? = null,
    val authors: String? = null,
    val year: Int? = null,
    val relationship: String? = null
)

/**
 * Simple figure info for processor input
 */
data class FigureInfo(
    val id: String,
    val label: String?,
    val caption: String?
)

/**
 * Simple formula info for processor input
 */
data class FormulaInfo(
    val id: String,
    val label: String?,
    val latex: String?,
    val pageNum: Int
)

/**
 * Simple table info for processor input
 */
data class TableInfo(
    val id: String,
    val label: String?,
    val caption: String?,
    val pageNum: Int
)
