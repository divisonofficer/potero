package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.service.llm.LLMService
import com.potero.service.llm.LLMLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stage 2: Content Recomposition
 *
 * Purpose: Reorganize paper content into a reader-friendly narrative flow.
 * Creates an outline that works across all styles.
 */
class ContentRecompositionProcessor(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Process structural understanding to create narrative outline
     */
    suspend fun process(
        structural: StructuralUnderstanding,
        figures: List<FigureInfo>,
        tables: List<TableInfo> = emptyList(),
        formulas: List<FormulaInfo> = emptyList()
    ): Result<RecomposedContent> = runCatching {
        val prompt = buildRecompositionPrompt(structural, figures, tables, formulas)

        val startTime = System.currentTimeMillis()
        val llmResult = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        llmResult.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_content_recomposition",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true,
                    paperId = structural.paperId
                )

                parseRecomposedContent(response, structural.paperId)
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_content_recomposition",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message,
                    paperId = structural.paperId
                )
                throw error
            }
        )
    }

    private fun buildRecompositionPrompt(
        structural: StructuralUnderstanding,
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        formulas: List<FormulaInfo>
    ): String = """
You are a science communicator. Based on the structural understanding below, create a narrative outline that will be engaging for general readers.

## Paper Understanding
- Main Objective: ${structural.mainObjective}
- Research Question: ${structural.researchQuestion}
- Methodology: ${structural.methodology}
- Key Findings: ${structural.keyFindings.joinToString("; ")}
- Contributions: ${structural.contributions.joinToString("; ")}
- Target Audience: ${structural.targetAudience}
- Prerequisites: ${structural.prerequisites.joinToString(", ")}

## Original Paper Sections
${structural.sections.map { s ->
    "- ${s.title}: ${s.purpose}"
}.joinToString("\n")}

## Available Figures
${if (figures.isEmpty()) "No figures available" else figures.mapIndexed { i, f ->
    "- Figure ${i + 1}: ${f.caption ?: "No caption"}\n  **ID: ${f.id}** (use this exact ID)"
}.joinToString("\n")}

## Available Tables
${if (tables.isEmpty()) "No tables available" else tables.mapIndexed { i, t ->
    "- Table ${i + 1}: ${t.caption ?: "No caption"}\n  **ID: ${t.id}** (use this exact ID)"
}.joinToString("\n")}

## Available Formulas (Key Equations)
${if (formulas.isEmpty()) "No formulas extracted" else formulas.take(10).mapIndexed { i, f ->
    "- Formula ${i + 1}: ${f.label ?: "No label"} - ${f.latex?.take(80) ?: "LaTeX not available"}\n  **ID: ${f.id}** (use this exact ID)"
}.joinToString("\n")}${if (formulas.size > 10) "\n... and ${formulas.size - 10} more formulas" else ""}

## Your Task
Create a narrative outline that:
1. Hooks readers with an interesting angle (Why should they care?)
2. Explains WHY this matters before HOW it works
3. **MUST include 2-4 figures from the available figures list** (select the most relevant ones)
4. **MUST include 1-2 tables if available** (select tables that show key results)
5. **MUST include 2-3 formulas if available** (select only the most essential equations)
6. Builds understanding progressively
7. Uses storytelling techniques (problem-solution, before-after, journey)

**CRITICAL REQUIREMENTS:**
- figureIntegrationPlan: MUST contain at least 2 figure entries (if figures are available)
- tableIntegrationPlan: MUST contain at least 1 table entry (if tables are available)
- formulaIntegrationPlan: MUST contain at least 2 formula entries (if formulas are available)
- Do NOT leave these arrays empty if the content is available above

Respond in JSON:
{
    "narrativeOutline": [
        {
            "order": 1,
            "heading": "Why [Problem] Matters",
            "purposeInNarrative": "Hook readers and establish relevance",
            "sourceFromPaper": "Introduction, motivation",
            "suggestedLength": "short"
        },
        {
            "order": 2,
            "heading": "The Challenge",
            "purposeInNarrative": "Explain the technical problem in accessible terms",
            "sourceFromPaper": "Problem statement, related work",
            "suggestedLength": "medium"
        },
        {
            "order": 3,
            "heading": "The Key Idea",
            "purposeInNarrative": "Present the main insight/innovation",
            "sourceFromPaper": "Method overview",
            "suggestedLength": "long"
        },
        {
            "order": 4,
            "heading": "How It Works",
            "purposeInNarrative": "Explain the technical approach",
            "sourceFromPaper": "Method details",
            "suggestedLength": "medium"
        },
        {
            "order": 5,
            "heading": "Results That Matter",
            "purposeInNarrative": "Show evidence and impact",
            "sourceFromPaper": "Experiments, results",
            "suggestedLength": "medium"
        },
        {
            "order": 6,
            "heading": "What This Means",
            "purposeInNarrative": "Broader implications and future",
            "sourceFromPaper": "Discussion, conclusion",
            "suggestedLength": "short"
        }
    ],
    "figureIntegrationPlan": [
        {
            "figureId": "ACTUAL_FIGURE_ID_FROM_LIST_ABOVE",
            "suggestedSection": 3,
            "narrativeRole": "illustrates the main concept"
        }
    ],
    "tableIntegrationPlan": [
        {
            "tableId": "ACTUAL_TABLE_ID_FROM_LIST_ABOVE",
            "suggestedSection": 5,
            "narrativeRole": "compares results or shows benchmark data"
        }
    ],
    "formulaIntegrationPlan": [
        {
            "formulaId": "ACTUAL_FORMULA_ID_FROM_LIST_ABOVE",
            "suggestedSection": 4,
            "narrativeRole": "shows the key mathematical relationship"
        }
    ],
    "conceptsToExplain": ["Concept1", "Concept2", "Concept3"]
}

**CRITICAL**: Use the EXACT IDs from the "Available Figures/Tables/Formulas" lists above.
Do NOT create your own descriptive IDs like "conceptual_diagram" or "results_comparison".
Copy the actual ID exactly as shown (e.g., "ccdf407a-907c-4b03-af3f-aa9975da0b2c_fig_0").

Guidelines:
- suggestedLength: "short" (100-200 words), "medium" (200-400 words), "long" (400-600 words)
- narrativeRole examples: "introduces the problem", "visualizes the solution", "shows comparison", "demonstrates results"
- For figures: Select 2-3 figures that best support the narrative (not all figures)
- For tables: Include comparison tables or benchmark results that show key findings
- For formulas: Select 2-3 key equations that are essential to understanding the method (not all formulas)
- conceptsToExplain: List technical terms that need explanation for general readers

Respond with ONLY the JSON object, no additional text.
""".trimIndent()

    private fun parseRecomposedContent(response: String, paperId: String): RecomposedContent {
        return try {
            var jsonText = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            jsonText = jsonText.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()

            val jsonStart = jsonText.indexOf('{')
            val jsonEnd = jsonText.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonText = jsonText.substring(jsonStart, jsonEnd + 1)
            }

            val parsed = json.decodeFromString<RecomposedContentJson>(jsonText)

            RecomposedContent(
                paperId = paperId,
                narrativeOutline = parsed.narrativeOutline?.map { s ->
                    NarrativeSection(
                        order = s.order ?: 0,
                        heading = s.heading ?: "Section",
                        purposeInNarrative = s.purposeInNarrative ?: "",
                        sourceFromPaper = s.sourceFromPaper ?: "",
                        suggestedLength = s.suggestedLength ?: "medium"
                    )
                } ?: getDefaultOutline(),
                figureIntegrationPlan = parsed.figureIntegrationPlan?.map { f ->
                    FigurePlacement(
                        figureId = f.figureId ?: "",
                        suggestedSection = f.suggestedSection ?: 1,
                        narrativeRole = f.narrativeRole ?: "supports the narrative"
                    )
                } ?: emptyList(),
                tableIntegrationPlan = parsed.tableIntegrationPlan?.map { t ->
                    TablePlacement(
                        tableId = t.tableId ?: "",
                        suggestedSection = t.suggestedSection ?: 1,
                        narrativeRole = t.narrativeRole ?: "presents data comparison"
                    )
                } ?: emptyList(),
                formulaIntegrationPlan = parsed.formulaIntegrationPlan?.map { formula ->
                    FormulaPlacement(
                        formulaId = formula.formulaId ?: "",
                        suggestedSection = formula.suggestedSection ?: 1,
                        narrativeRole = formula.narrativeRole ?: "demonstrates the key relationship"
                    )
                } ?: emptyList(),
                conceptsToExplain = parsed.conceptsToExplain ?: emptyList()
            )
        } catch (e: Exception) {
            println("[ContentRecomposition] Failed to parse LLM response: ${e.message}")
            println("[ContentRecomposition] Response was: $response")

            // Fallback with default outline
            RecomposedContent(
                paperId = paperId,
                narrativeOutline = getDefaultOutline(),
                figureIntegrationPlan = emptyList(),
                tableIntegrationPlan = emptyList(),
                formulaIntegrationPlan = emptyList(),
                conceptsToExplain = emptyList()
            )
        }
    }

    private fun getDefaultOutline(): List<NarrativeSection> = listOf(
        NarrativeSection(1, "Introduction", "Set up the context", "Introduction", "medium"),
        NarrativeSection(2, "The Problem", "Explain the challenge", "Problem statement", "medium"),
        NarrativeSection(3, "The Solution", "Present the approach", "Method", "long"),
        NarrativeSection(4, "Results", "Show the evidence", "Experiments", "medium"),
        NarrativeSection(5, "Conclusion", "Summarize and look ahead", "Conclusion", "short")
    )
}

@Serializable
private data class RecomposedContentJson(
    val narrativeOutline: List<NarrativeSectionJson>? = null,
    val figureIntegrationPlan: List<FigurePlacementJson>? = null,
    val tableIntegrationPlan: List<TablePlacementJson>? = null,
    val formulaIntegrationPlan: List<FormulaPlacementJson>? = null,
    val conceptsToExplain: List<String>? = null
)

@Serializable
private data class NarrativeSectionJson(
    val order: Int? = null,
    val heading: String? = null,
    val purposeInNarrative: String? = null,
    val sourceFromPaper: String? = null,
    val suggestedLength: String? = null
)

@Serializable
private data class FigurePlacementJson(
    val figureId: String? = null,
    val suggestedSection: Int? = null,
    val narrativeRole: String? = null
)

@Serializable
private data class TablePlacementJson(
    val tableId: String? = null,
    val suggestedSection: Int? = null,
    val narrativeRole: String? = null
)

@Serializable
private data class FormulaPlacementJson(
    val formulaId: String? = null,
    val suggestedSection: Int? = null,
    val narrativeRole: String? = null
)
