package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.service.llm.LLMService
import com.potero.service.llm.LLMLogger
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Stage 4: Style Rendering
 *
 * Purpose: Generate the final narrative in a specific style and language.
 * This is the most expensive stage (runs once per style/language combo).
 */
class StyleRenderingProcessor(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val editorialRewriter = EditorialRewriter(llmService)

    /**
     * Render the final narrative
     */
    suspend fun render(
        paper: Paper,
        structural: StructuralUnderstanding,
        recomposed: RecomposedContent,
        concepts: List<ConceptExplanation>,
        figures: List<FigureInfo>,
        tables: List<TableInfo> = emptyList(),
        formulas: List<FormulaInfo> = emptyList(),
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Result<Narrative> = runCatching {
        val narrativeId = UUID.randomUUID().toString()

        // Generate main content (REDDIT style uses special thread generation)
        val content = if (style == NarrativeStyle.REDDIT) {
            // Generate Reddit Thread
            val redditThread = generateRedditThread(
                paper = paper,
                structural = structural,
                recomposed = recomposed,
                figures = figures,
                tables = tables,
                formulas = formulas,
                language = language
            )

            // Serialize to JSON
            json.encodeToString<RedditThread>(redditThread)
        } else {
            // Pass A: Generate draft content
            val contentPrompt = buildContentPrompt(paper, structural, recomposed, concepts, figures, tables, formulas, style, language)
            val startTime = System.currentTimeMillis()
            val contentResult = llmService.chat(contentPrompt)
            val endTime = System.currentTimeMillis()

            val draftContent = contentResult.fold(
                onSuccess = { response ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "narrative_render_draft_${style.name.lowercase()}_${language.code}",
                        inputPrompt = contentPrompt,
                        outputResponse = response,
                        durationMs = endTime - startTime,
                        success = true,
                        paperId = paper.id,
                        paperTitle = paper.title
                    )
                    response
                },
                onFailure = { error ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "narrative_render_draft_${style.name.lowercase()}_${language.code}",
                        inputPrompt = contentPrompt,
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

            // Pass B: Apply editorial improvements
            val editorialStartTime = System.currentTimeMillis()
            val editedContent = editorialRewriter.applyEditorialPass(
                draftContent = draftContent,
                outline = recomposed.narrativeOutline,
                style = style,
                language = language
            )
            val editorialEndTime = System.currentTimeMillis()

            editedContent.fold(
                onSuccess = { edited: String ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "narrative_editorial_${style.name.lowercase()}_${language.code}",
                        inputPrompt = "Editorial pass on ${draftContent.length} chars",
                        outputResponse = edited,
                        durationMs = editorialEndTime - editorialStartTime,
                        success = true,
                        paperId = paper.id,
                        paperTitle = paper.title
                    )
                    edited
                },
                onFailure = { error: Throwable ->
                    llmLogger.log(
                        provider = llmService.provider,
                        purpose = "narrative_editorial_${style.name.lowercase()}_${language.code}",
                        inputPrompt = "Editorial pass",
                        outputResponse = null,
                        durationMs = editorialEndTime - editorialStartTime,
                        success = false,
                        errorMessage = error.message,
                        paperId = paper.id,
                        paperTitle = paper.title
                    )
                    // Fallback to draft if editorial fails
                    println("[StyleRendering] Editorial pass failed, using draft: ${error.message}")
                    draftContent
                }
            )
        }

        // Generate figure explanations
        val figureExplanations = if (figures.isNotEmpty()) {
            generateFigureExplanations(
                figures = figures,
                placements = recomposed.figureIntegrationPlan,
                style = style,
                language = language,
                narrativeId = narrativeId
            )
        } else {
            emptyList()
        }

        // Generate table explanations
        val tableExplanations = if (tables.isNotEmpty() && recomposed.tableIntegrationPlan.isNotEmpty()) {
            generateTableExplanations(
                tables = tables,
                placements = recomposed.tableIntegrationPlan,
                style = style,
                language = language,
                narrativeId = narrativeId
            )
        } else {
            emptyList()
        }

        // Generate formula explanations
        val formulaExplanations = if (formulas.isNotEmpty() && recomposed.formulaIntegrationPlan.isNotEmpty()) {
            generateFormulaExplanations(
                formulas = formulas,
                placements = recomposed.formulaIntegrationPlan,
                style = style,
                language = language,
                narrativeId = narrativeId
            )
        } else {
            emptyList()
        }

        // Generate title and summary from the content
        val (title, summary) = generateTitleAndSummary(content, paper.title, style, language)

        val now = Clock.System.now()

        Narrative(
            id = narrativeId,
            paperId = paper.id,
            style = style,
            language = language,
            title = title,
            content = content,
            summary = summary,
            figureExplanations = figureExplanations,
            tableExplanations = tableExplanations,
            formulaExplanations = formulaExplanations,
            conceptExplanations = concepts.map { it.copy(narrativeId = narrativeId) },
            estimatedReadTime = estimateReadTime(content),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun buildContentPrompt(
        paper: Paper,
        structural: StructuralUnderstanding,
        recomposed: RecomposedContent,
        concepts: List<ConceptExplanation>,
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        formulas: List<FormulaInfo>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val styleGuidelines = getStyleGuidelines(style)
        val stylePersona = getStylePersona(style)
        val languageInstruction = getLanguageInstruction(language)

        // Build figure integration section - only show figures in integration plan
        val figureSection = if (figures.isNotEmpty() && recomposed.figureIntegrationPlan.isNotEmpty()) {
            val plannedFigures = recomposed.figureIntegrationPlan.mapNotNull { placement ->
                val fig = figures.find { it.id == placement.figureId }
                fig?.let { Triple(it, placement, figures.indexOf(it) + 1) }
            }

            if (plannedFigures.isNotEmpty()) {
                val figureList = plannedFigures.joinToString("\n") { (fig, placement, index) ->
                    """
- **${fig.label ?: "Figure $index"}** (ID: ${fig.id})
  - Caption: ${fig.caption ?: "No caption"}
  - Suggested Section: ${placement.suggestedSection}
  - Role: ${placement.narrativeRole}
  - Markdown: ![${fig.label ?: "Figure $index"}: ${fig.caption?.take(80) ?: "Visual"}](/api/figures/${fig.id}/image)
""".trim()
                }
                """
## Selected Figures for Integration (${plannedFigures.size} of ${figures.size} available)
$figureList

IMPORTANT: Include these figures at their suggested sections.
- Review each caption carefully and place the figure where it best supports your narrative
- Distribute figures across multiple sections (avoid clustering all figures in one place)
- Add 1-2 sentences after each figure explaining what it shows and why it matters
- You may skip a figure if it doesn't fit naturally into the narrative flow
"""
            } else {
                ""
            }
        } else {
            ""
        }

        // Build table integration section - only show tables in integration plan
        val tableSection = if (tables.isNotEmpty() && recomposed.tableIntegrationPlan.isNotEmpty()) {
            val plannedTables = recomposed.tableIntegrationPlan.mapNotNull { placement ->
                val table = tables.find { it.id == placement.tableId }
                table?.let { Triple(it, placement, tables.indexOf(it) + 1) }
            }

            if (plannedTables.isNotEmpty()) {
                val tableList = plannedTables.joinToString("\n") { (table, placement, index) ->
                    """
- **${table.label ?: "Table $index"}** (ID: ${table.id})
  - Caption: ${table.caption ?: "No caption"}
  - Suggested Section: ${placement.suggestedSection}
  - Role: ${placement.narrativeRole}
  - Markdown: ![${table.label ?: "Table $index"}: ${table.caption?.take(80) ?: "Data"}](/api/tables/${table.id}/image)
""".trim()
                }
                """
## Selected Tables for Integration (${plannedTables.size} of ${tables.size} available)
$tableList

IMPORTANT: Include these tables at their suggested sections.
- Review each caption and place the table where it best supports your narrative
- Add 1-2 sentences after each table explaining the key insights and what the data shows
- You may skip a table if it doesn't add value to the narrative flow
"""
            } else {
                ""
            }
        } else {
            ""
        }

        // Build formula integration section
        val formulaSection = if (formulas.isNotEmpty() && recomposed.formulaIntegrationPlan.isNotEmpty()) {
            val formulaList = recomposed.formulaIntegrationPlan.mapNotNull { placement ->
                val formula = formulas.find { it.id == placement.formulaId }
                formula?.let {
                    """
- **${it.label ?: "Key Equation"}**: ${it.latex?.take(100) ?: "LaTeX not available"}
  - Best placed: Section ${placement.suggestedSection}
  - Role: ${placement.narrativeRole}
""".trim()
                }
            }.joinToString("\n")
            """
## Key Formulas (Include in narrative)
$formulaList

For formulas: Use LaTeX syntax wrapped in ${"$$"}...${"$$"} for display mode or ${"$"}...${"$"} for inline.
Example: ${"$$"}E = mc^2${"$$"}
Provide context before and explanation after each formula.
"""
        } else {
            ""
        }

        return """
$languageInstruction

You are a $stylePersona. Write a narrative about this academic paper.

## Paper Information
- Title: ${paper.title}
- Authors: ${paper.formattedAuthors}
- Year: ${paper.year ?: "Unknown"}
- Venue: ${paper.conference ?: "Unknown"}

## Paper Understanding
- Main Objective: ${structural.mainObjective}
- Research Question: ${structural.researchQuestion}
- Methodology: ${structural.methodology}
- Key Findings: ${structural.keyFindings.joinToString("; ")}
- Contributions: ${structural.contributions.joinToString("; ")}

## Narrative Outline to Follow
${recomposed.narrativeOutline.map { section ->
    """
### Section ${section.order}: ${section.heading}
- Purpose: ${section.purposeInNarrative}
- Source: ${section.sourceFromPaper}
- Length: ${section.suggestedLength}
""".trim()
}.joinToString("\n\n")}

$figureSection

$tableSection

$formulaSection

## Concept Definitions to Include
${if (concepts.isEmpty()) "No specific concepts to explain" else concepts.map {
    "${it.term}: ${it.definition}${it.analogy?.let { a -> " ($a)" } ?: ""}"
}.joinToString("\n")}

## Style Guidelines
$styleGuidelines

## Output Format
Write in Markdown format with:
- A compelling opening hook
- Clear section headings (##)
- Inline explanations for technical terms when first mentioned
${if (recomposed.figureIntegrationPlan.isNotEmpty()) """- **CRITICAL**: Use the EXACT markdown syntax from the figure list above for each figure
  Copy the markdown line exactly as shown (including the full ID)
  Place each figure at its suggested section""" else ""}
${if (recomposed.tableIntegrationPlan.isNotEmpty()) """- **CRITICAL**: Use the EXACT markdown syntax from the table list above for each table
  Copy the markdown line exactly as shown (including the full ID)
  Add insights after each table""" else ""}
${if (recomposed.formulaIntegrationPlan.isNotEmpty()) "- Include the selected key formulas with explanations" else ""}
- A memorable conclusion

**Avoid These Generic Phrases:**
- "This paper makes an important contribution"
- "The results are impressive" or "The results are significant"
- "This is a significant improvement" (without specific numbers)
- "The method is novel" (without explaining how)
- "Future work should explore..." (without specific directions)

**Instead, use specific evidence:**
- "This paper improves X from 85% to 92.7% (Table 1)"
- "The method achieves SOTA on benchmark Y (Section 4.2)"
- "Unlike previous work, this approach reduces latency by 40%"

${getLanguageReminder(language)}

**IMPORTANT**: When including figures or tables, copy the markdown line EXACTLY from the lists above.
Do NOT use placeholder IDs like "abc-123-def" or "xyz-456-abc".
Use the actual IDs provided in the figure/table lists.

## Self-Check Before Submitting

For each major section, verify:
- [ ] At least 2 paper-specific details mentioned (methods, results, specific innovations)
- [ ] At least 1 evidence reference (Figure/Table/Section citation)
- [ ] All technical terms defined inline when first mentioned
- [ ] No vague claims without supporting numbers or evidence

If any section fails these checks, revise it to include specific details and evidence.

Begin writing the narrative now:
""".trimIndent()
    }

    // Keep the old signature for backward compatibility during migration
    @Deprecated("Use the version with formulas parameter")
    private fun buildContentPrompt(
        paper: Paper,
        structural: StructuralUnderstanding,
        recomposed: RecomposedContent,
        concepts: List<ConceptExplanation>,
        figures: List<FigureInfo>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val styleGuidelines = getStyleGuidelines(style)
        val stylePersona = getStylePersona(style)
        val languageInstruction = getLanguageInstruction(language)

        // Build figure integration section
        val figureSection = if (figures.isNotEmpty()) {
            val figureList = figures.mapIndexed { index, fig ->
                val placement = recomposed.figureIntegrationPlan.find { it.figureId == fig.id }
                """
- **${fig.label ?: "Figure ${index + 1}"}** (ID: ${fig.id}): ${fig.caption ?: "No caption"}
  - Best placed: Section ${placement?.suggestedSection ?: "any"}
  - Role: ${placement?.narrativeRole ?: "Supports the main argument"}
  - Markdown: ![${fig.label ?: "Figure ${index + 1}"}: ${fig.caption?.take(80) ?: "Visual"}](/api/figures/${fig.id}/image)
""".trim()
            }.joinToString("\n")
            """
## Available Figures (MUST include these in your narrative)
$figureList

IMPORTANT: You MUST include ALL ${figures.size} figures as markdown images at appropriate points.
Use the exact markdown syntax provided above for each figure.
Add 1-2 sentences after each figure explaining what it shows and why it matters.
"""
        } else {
            ""
        }

        return """
$languageInstruction

You are a $stylePersona. Write a narrative about this academic paper.

## Paper Information
- Title: ${paper.title}
- Authors: ${paper.formattedAuthors}
- Year: ${paper.year ?: "Unknown"}
- Venue: ${paper.conference ?: "Unknown"}

## Paper Understanding
- Main Objective: ${structural.mainObjective}
- Research Question: ${structural.researchQuestion}
- Methodology: ${structural.methodology}
- Key Findings: ${structural.keyFindings.joinToString("; ")}
- Contributions: ${structural.contributions.joinToString("; ")}

## Narrative Outline to Follow
${recomposed.narrativeOutline.map { section ->
    """
### Section ${section.order}: ${section.heading}
- Purpose: ${section.purposeInNarrative}
- Source: ${section.sourceFromPaper}
- Length: ${section.suggestedLength}
""".trim()
}.joinToString("\n\n")}

$figureSection

## Concept Definitions to Include
${if (concepts.isEmpty()) "No specific concepts to explain" else concepts.map {
    "${it.term}: ${it.definition}${it.analogy?.let { a -> " ($a)" } ?: ""}"
}.joinToString("\n")}

## Style Guidelines
$styleGuidelines

## Output Format
Write in Markdown format with:
- A compelling opening hook
- Clear section headings (##)
- Inline explanations for technical terms when first mentioned
${if (figures.isNotEmpty()) """- **CRITICAL**: Include ALL ${figures.size} figures as markdown images at appropriate points:
  Format: ![Figure label: Caption text](/api/figures/FIGURE_ID/image)
  Example: ![Figure 1: Network architecture](/api/figures/abc-123-def/image)
  Place figures where they support the narrative best""" else ""}
- A memorable conclusion

${getLanguageReminder(language)}

Begin writing the narrative now:
""".trimIndent()
    }

    private fun getStyleGuidelines(style: NarrativeStyle): String = when (style) {
        NarrativeStyle.BLOG -> """
- Tone: Educational, enthusiastic, thorough
- Structure: Long-form with detailed explanations
- Voice: First person ("I explored this paper and here's what I found...")
- Include: Technical details where helpful, your analysis and insights
- Audience: Technical professionals wanting depth
- Length: 1500-2500 words
- Features: Use code-like formatting for technical terms, bullet points for lists
""".trimIndent()

        NarrativeStyle.NEWS -> """
- Tone: Professional, objective, concise
- Structure: Inverted pyramid (most important first)
- Voice: Third person, journalistic ("Researchers have developed...")
- Include: Key findings upfront, real-world implications, expert quotes style
- Audience: General tech-literate readers
- Length: 500-800 words
- Features: Strong headline-worthy opening, clear paragraph breaks
""".trimIndent()

        NarrativeStyle.REDDIT -> """
- Tone: Casual, conversational, community-friendly
- Structure: TL;DR first, then engaging explanation
- Voice: Second person/casual first ("So I just read this paper and...")
- Include: ELI5 explanations, "why should you care" angle, relatable comparisons
- Audience: Curious non-experts, fellow enthusiasts
- Length: 800-1200 words
- Features: Start with **TL;DR**, use bold for emphasis, rhetorical questions
""".trimIndent()
    }

    private fun getStylePersona(style: NarrativeStyle): String = when (style) {
        NarrativeStyle.BLOG -> "technical blogger who loves explaining complex topics in depth"
        NarrativeStyle.NEWS -> "tech journalist at a major publication like Wired or MIT Technology Review"
        NarrativeStyle.REDDIT -> "helpful Redditor who just discovered something interesting and wants to share"
    }

    private fun getLanguageInstruction(language: NarrativeLanguage): String = when (language) {
        NarrativeLanguage.KOREAN -> "다음 학술 논문에 대한 글을 한국어로 작성하세요. 모든 내용을 자연스러운 한국어로 작성해야 합니다."
        NarrativeLanguage.ENGLISH -> "Write a narrative about the following academic paper in English."
    }

    private fun getLanguageReminder(language: NarrativeLanguage): String = when (language) {
        NarrativeLanguage.KOREAN -> """
Important: Write EVERYTHING in Korean (한국어).
- All headings, content, and explanations must be in Korean
- Technical terms can remain in English but should be accompanied by Korean explanation
- Use natural Korean expressions and sentence structures
"""
        NarrativeLanguage.ENGLISH -> "Write everything in clear, accessible English."
    }

    private suspend fun generateFigureExplanations(
        figures: List<FigureInfo>,
        placements: List<FigurePlacement>,
        style: NarrativeStyle,
        language: NarrativeLanguage,
        narrativeId: String
    ): List<FigureExplanation> {
        val prompt = buildFigurePrompt(figures, placements, style, language)

        val result = llmService.chat(prompt)
        val response = result.getOrNull() ?: return emptyList()

        return parseFigureExplanations(response, figures, narrativeId)
    }

    private fun buildFigurePrompt(
        figures: List<FigureInfo>,
        placements: List<FigurePlacement>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val languageNote = if (language == NarrativeLanguage.KOREAN)
            "Write all explanations in Korean (한국어)."
        else
            "Write all explanations in English."

        return """
$languageNote

Explain these figures for a ${getStylePersona(style)}:

## Figures
${figures.mapIndexed { i, f ->
    val placement = placements.find { it.figureId == f.id }
    """
Figure ${i + 1} (id: ${f.id}):
- Label: ${f.label ?: "Figure ${i + 1}"}
- Original Caption: ${f.caption ?: "No caption available"}
- Role in Narrative: ${placement?.narrativeRole ?: "Supports the main argument"}
""".trim()
}.joinToString("\n\n")}

## Your Task
For each figure, provide a ${style.name.lowercase()}-style explanation.

Respond in JSON:
{
    "figures": [
        {
            "figureId": "fig_id",
            "explanation": "2-3 sentence explanation appropriate for the style",
            "relevance": "Why this figure matters for understanding"
        }
    ]
}

Respond with ONLY the JSON object.
""".trimIndent()
    }

    private fun parseFigureExplanations(
        response: String,
        figures: List<FigureInfo>,
        narrativeId: String
    ): List<FigureExplanation> {
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

            val parsed = json.decodeFromString<FiguresResponseJson>(jsonText)
            val now = Clock.System.now()

            parsed.figures?.mapNotNull { f ->
                val figureInfo = figures.find { it.id == f.figureId } ?: return@mapNotNull null
                FigureExplanation(
                    id = UUID.randomUUID().toString(),
                    narrativeId = narrativeId,
                    figureId = f.figureId ?: "",
                    label = figureInfo.label ?: "Figure",
                    originalCaption = figureInfo.caption,
                    explanation = f.explanation ?: "Explanation not available",
                    relevance = f.relevance,
                    createdAt = now
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("[StyleRendering] Failed to parse figure explanations: ${e.message}")
            emptyList()
        }
    }

    private suspend fun generateTableExplanations(
        tables: List<TableInfo>,
        placements: List<TablePlacement>,
        style: NarrativeStyle,
        language: NarrativeLanguage,
        narrativeId: String
    ): List<TableExplanation> {
        val prompt = buildTablePrompt(tables, placements, style, language)

        val result = llmService.chat(prompt)
        val response = result.getOrNull() ?: return emptyList()

        return parseTableExplanations(response, tables, narrativeId)
    }

    private fun buildTablePrompt(
        tables: List<TableInfo>,
        placements: List<TablePlacement>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val languageNote = if (language == NarrativeLanguage.KOREAN)
            "Write all explanations in Korean (한국어)."
        else
            "Write all explanations in English."

        return """
$languageNote

Explain these tables for a ${getStylePersona(style)}:

## Tables
${tables.mapIndexed { i, t ->
    val placement = placements.find { it.tableId == t.id }
    """
Table ${i + 1} (id: ${t.id}):
- Label: ${t.label ?: "Table ${i + 1}"}
- Original Caption: ${t.caption ?: "No caption available"}
- Role in Narrative: ${placement?.narrativeRole ?: "Presents data comparison"}
""".trim()
}.joinToString("\n\n")}

## Your Task
For each table, provide a ${style.name.lowercase()}-style explanation.

Respond in JSON:
{
    "tables": [
        {
            "tableId": "table_id",
            "summary": "2-3 sentence summary of what the table shows",
            "keyInsights": "The most important insights or patterns from the data"
        }
    ]
}

Respond with ONLY the JSON object.
""".trimIndent()
    }

    private fun parseTableExplanations(
        response: String,
        tables: List<TableInfo>,
        narrativeId: String
    ): List<TableExplanation> {
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

            val parsed = json.decodeFromString<TablesResponseJson>(jsonText)
            val now = Clock.System.now()

            parsed.tables?.mapNotNull { t ->
                val tableInfo = tables.find { it.id == t.tableId } ?: return@mapNotNull null
                TableExplanation(
                    id = UUID.randomUUID().toString(),
                    narrativeId = narrativeId,
                    tableId = t.tableId ?: "",
                    label = tableInfo.label ?: "Table",
                    originalCaption = tableInfo.caption,
                    summary = t.summary ?: "Summary not available",
                    keyInsights = t.keyInsights,
                    createdAt = now
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("[StyleRendering] Failed to parse table explanations: ${e.message}")
            emptyList()
        }
    }

    private suspend fun generateFormulaExplanations(
        formulas: List<FormulaInfo>,
        placements: List<FormulaPlacement>,
        style: NarrativeStyle,
        language: NarrativeLanguage,
        narrativeId: String
    ): List<FormulaExplanation> {
        val prompt = buildFormulaPrompt(formulas, placements, style, language)

        val result = llmService.chat(prompt)
        val response = result.getOrNull() ?: return emptyList()

        return parseFormulaExplanations(response, formulas, narrativeId)
    }

    private fun buildFormulaPrompt(
        formulas: List<FormulaInfo>,
        placements: List<FormulaPlacement>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val languageNote = if (language == NarrativeLanguage.KOREAN)
            "Write all explanations in Korean (한국어)."
        else
            "Write all explanations in English."

        return """
$languageNote

Explain these formulas for a ${getStylePersona(style)}:

## Formulas
${formulas.mapIndexed { i, f ->
    val placement = placements.find { it.formulaId == f.id }
    """
Formula ${i + 1} (id: ${f.id}):
- Label: ${f.label ?: "Equation ${i + 1}"}
- LaTeX: ${f.latex ?: "LaTeX not available"}
- Role in Narrative: ${placement?.narrativeRole ?: "Shows key mathematical relationship"}
""".trim()
}.joinToString("\n\n")}

## Your Task
For each formula, provide a ${style.name.lowercase()}-style explanation that:
1. Explains what the formula represents in plain language
2. Describes what each variable/term means
3. Explains why this formula is important for the research

Respond in JSON:
{
    "formulas": [
        {
            "formulaId": "formula_id",
            "explanation": "2-3 sentence plain-language explanation of what the formula means",
            "relevance": "Why this formula is important for understanding the research"
        }
    ]
}

Respond with ONLY the JSON object.
""".trimIndent()
    }

    private fun parseFormulaExplanations(
        response: String,
        formulas: List<FormulaInfo>,
        narrativeId: String
    ): List<FormulaExplanation> {
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

            val parsed = json.decodeFromString<FormulasResponseJson>(jsonText)
            val now = Clock.System.now()

            parsed.formulas?.mapNotNull { f ->
                val formulaInfo = formulas.find { it.id == f.formulaId } ?: return@mapNotNull null
                FormulaExplanation(
                    id = UUID.randomUUID().toString(),
                    narrativeId = narrativeId,
                    formulaId = f.formulaId ?: "",
                    label = formulaInfo.label ?: "Equation",
                    latex = formulaInfo.latex,
                    explanation = f.explanation ?: "Explanation not available",
                    relevance = f.relevance,
                    createdAt = now
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("[StyleRendering] Failed to parse formula explanations: ${e.message}")
            emptyList()
        }
    }

    private suspend fun generateTitleAndSummary(
        content: String,
        originalTitle: String,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Pair<String, String> {
        val languageNote = if (language == NarrativeLanguage.KOREAN)
            "Respond in Korean (한국어)."
        else
            "Respond in English."

        val prompt = """
$languageNote

Based on the following ${style.name.lowercase()}-style narrative about a paper titled "$originalTitle", generate:
1. A catchy title appropriate for the ${style.name.lowercase()} style
2. A 1-2 sentence summary/hook

Narrative excerpt (first 1000 chars):
${content.take(1000)}

Respond in JSON:
{
    "title": "Engaging title for the narrative",
    "summary": "Brief 1-2 sentence summary that hooks readers"
}

Style guidelines for title:
- BLOG: Informative, may include "How" or "What"
- NEWS: Headline style, action-focused
- REDDIT: Casual, may use "TIL" or questions

Respond with ONLY the JSON object.
""".trimIndent()

        val result = llmService.chat(prompt)
        return result.fold(
            onSuccess = { response ->
                try {
                    var jsonText = response
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val jsonStart = jsonText.indexOf('{')
                    val jsonEnd = jsonText.lastIndexOf('}')
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        jsonText = jsonText.substring(jsonStart, jsonEnd + 1)
                    }

                    val parsed = json.decodeFromString<TitleSummaryJson>(jsonText)
                    Pair(
                        parsed.title ?: getDefaultTitle(originalTitle, style, language),
                        parsed.summary ?: getDefaultSummary(originalTitle, language)
                    )
                } catch (e: Exception) {
                    Pair(
                        getDefaultTitle(originalTitle, style, language),
                        getDefaultSummary(originalTitle, language)
                    )
                }
            },
            onFailure = {
                Pair(
                    getDefaultTitle(originalTitle, style, language),
                    getDefaultSummary(originalTitle, language)
                )
            }
        )
    }

    private fun getDefaultTitle(
        originalTitle: String,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val prefix = when (style) {
            NarrativeStyle.BLOG -> if (language == NarrativeLanguage.KOREAN) "깊이 있게 살펴보기: " else "Deep Dive: "
            NarrativeStyle.NEWS -> if (language == NarrativeLanguage.KOREAN) "연구 동향: " else "Research Update: "
            NarrativeStyle.REDDIT -> if (language == NarrativeLanguage.KOREAN) "이 논문이 흥미로운 이유: " else "Why this paper matters: "
        }
        return "$prefix$originalTitle"
    }

    private fun getDefaultSummary(originalTitle: String, language: NarrativeLanguage): String {
        return if (language == NarrativeLanguage.KOREAN) {
            "\"$originalTitle\" 논문에 대한 분석입니다."
        } else {
            "An analysis of the paper \"$originalTitle\"."
        }
    }

    private fun estimateReadTime(content: String): Int {
        // Average reading speed: ~200 words per minute for technical content
        val wordCount = content.split(Regex("\\s+")).size
        return maxOf(1, (wordCount / 200.0).toInt())
    }

    // =============================================================================
    // Reddit Thread Generation
    // =============================================================================

    /**
     * Generate a Reddit Thread for the paper
     */
    private suspend fun generateRedditThread(
        paper: Paper,
        structural: StructuralUnderstanding,
        recomposed: RecomposedContent,
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        formulas: List<FormulaInfo>,
        language: NarrativeLanguage
    ): RedditThread {
        // Generate OP (Original Post)
        val op = generateOP(paper, structural, figures, tables, formulas, language)

        // Generate top-level comments (one per role)
        val roles = RedditRole.entries
        val topLevelComments = roles.mapIndexed { index, role ->
            generateComment(
                role = role,
                structural = structural,
                figures = figures,
                tables = tables,
                language = language,
                parentId = null,
                depth = 1,
                order = index
            )
        }

        // Generate replies from OP
        val replies = topLevelComments.mapIndexed { index, comment ->
            generateReply(
                parentComment = comment,
                structural = structural,
                language = language,
                depth = 2,
                order = index
            )
        }

        return RedditThread(
            originalPost = op,
            comments = topLevelComments + replies
        )
    }

    /**
     * Generate the Original Post
     */
    private suspend fun generateOP(
        paper: Paper,
        structural: StructuralUnderstanding,
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        formulas: List<FormulaInfo>,
        language: NarrativeLanguage
    ): RedditPost {
        val languageInstruction = if (language == NarrativeLanguage.KOREAN) {
            "Write in Korean (한국어). Use casual, friendly Korean."
        } else {
            "Write in English. Use casual, conversational tone."
        }

        val prompt = """
$languageInstruction

You are writing a Reddit post (OP) about this research paper.

## Paper Information
- Title: ${paper.title}
- Authors: ${paper.formattedAuthors}
- Venue: ${paper.conference ?: "Unknown"}
- Year: ${paper.year ?: "Unknown"}

## Paper Understanding
- Main Objective: ${structural.mainObjective}
- Research Question: ${structural.researchQuestion}
- Methodology: ${structural.methodology}
- Key Findings: ${structural.keyFindings.joinToString("; ")}
- Contributions: ${structural.contributions.joinToString("; ")}

## Claims (for reference in your post)
${structural.claims.mapIndexed { i, claim ->
    "[Claim #$i] ${claim.statement} (${claim.type}, confidence: ${claim.confidence})"
}.joinToString("\n")}

## Available Figures
${figures.take(5).mapIndexed { i, f ->
    "- ${f.label ?: "Figure ${i + 1}"} (ID: ${f.id}): ${f.caption ?: "No caption"}"
}.joinToString("\n")}

## Available Tables
${tables.take(5).mapIndexed { i, t ->
    "- ${t.label ?: "Table ${i + 1}"} (ID: ${t.id}): ${t.caption ?: "No caption"}"
}.joinToString("\n")}

## Your Task
Write a Reddit OP with this structure:

**[Catchy Title, max 200 chars]**

**TL;DR** (3 bullet points)
- Key finding 1 [Claim #X]
- Key finding 2 [Claim #Y]
- Why it matters [Claim #Z]

**Introduction** (2-3 sentences)
Hook the reader. Why should they care about this problem?

**The Approach** (3-4 sentences)
ELI5 explanation of the method. Keep it simple!

**Results** (3-5 bullet points)
- Result 1 [Claim #A]
  ![Figure label](/api/figures/FIGURE_ID/image)
- Result 2 [Claim #B]
  ![Table label](/api/tables/TABLE_ID/image)

**Why This Matters** (2-3 sentences)
Real-world implications.

**Limitations** (2-3 bullet points)
${structural.limitations.take(3).map { "- $it" }.joinToString("\n")}

**Related Work** (if available)
${if (structural.relatedPapers.isNotEmpty()) {
    structural.relatedPapers.take(3).map { "- ${it.title} (${it.year ?: "N/A"}): ${it.relationship}" }.joinToString("\n")
} else {
    "(No related papers listed)"
}}

## Rules
1. Use [Claim #X] notation to reference claims (0-indexed)
2. Use markdown images for figures/tables: ![Label](/api/figures/{id}/image)
3. Casual, conversational tone - like you're explaining to a friend
4. Use **bold** for emphasis
5. Max 1200 words
6. Be enthusiastic but honest about limitations

Respond with ONLY the markdown text of the post. No JSON, no code blocks.
""".trimIndent()

        val startTime = System.currentTimeMillis()
        val result = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        return result.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_op_generation",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true,
                    paperId = paper.id,
                    paperTitle = paper.title
                )

                val claimRefs = extractClaimReferences(response)
                val score = calculateOPScore(structural.claims, claimRefs)

                RedditPost(
                    id = "op_${UUID.randomUUID()}",
                    parentId = null,
                    role = null,
                    author = "OP",
                    content = response.trim(),
                    claimReferences = claimRefs,
                    depth = 0,
                    order = 0,
                    score = score
                )
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_op_generation",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message,
                    paperId = paper.id,
                    paperTitle = paper.title
                )

                // Fallback OP
                RedditPost(
                    id = "op_${UUID.randomUUID()}",
                    parentId = null,
                    role = null,
                    author = "OP",
                    content = "**${paper.title}**\n\nTL;DR: ${structural.mainObjective}",
                    claimReferences = emptyList(),
                    depth = 0,
                    order = 0,
                    score = 500
                )
            }
        )
    }

    /**
     * Generate a role-based comment
     */
    private suspend fun generateComment(
        role: RedditRole,
        structural: StructuralUnderstanding,
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        language: NarrativeLanguage,
        parentId: String?,
        depth: Int,
        order: Int
    ): RedditPost {
        val languageInstruction = if (language == NarrativeLanguage.KOREAN) {
            "Write in Korean (한국어). Use casual Reddit comment style."
        } else {
            "Write in English. Use casual Reddit comment style."
        }

        val prompt = buildCommentPrompt(role, structural, figures, tables, languageInstruction)

        val startTime = System.currentTimeMillis()
        val result = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        return result.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_comment_${role.name.lowercase()}",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true
                )

                val claimRefs = extractClaimReferences(response)
                val score = calculateCommentScore(role, structural.claims, claimRefs)

                RedditPost(
                    id = "c_${role.name.lowercase()}_${UUID.randomUUID()}",
                    parentId = parentId,
                    role = role,
                    author = "${role.name.lowercase()}_user",
                    content = response.trim(),
                    claimReferences = claimRefs,
                    depth = depth,
                    order = order,
                    score = score
                )
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_comment_${role.name.lowercase()}",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message
                )

                // Fallback comment
                RedditPost(
                    id = "c_${role.name.lowercase()}_${UUID.randomUUID()}",
                    parentId = parentId,
                    role = role,
                    author = "${role.name.lowercase()}_user",
                    content = "Interesting paper! Looking forward to reading more about this.",
                    claimReferences = emptyList(),
                    depth = depth,
                    order = order,
                    score = 50
                )
            }
        )
    }

    /**
     * Build role-specific comment prompt
     */
    private fun buildCommentPrompt(
        role: RedditRole,
        structural: StructuralUnderstanding,
        figures: List<FigureInfo>,
        tables: List<TableInfo>,
        languageInstruction: String
    ): String {
        val claimsList = structural.claims.mapIndexed { i, claim ->
            "[Claim #$i] ${claim.statement} (${claim.type}, confidence: ${claim.confidence})"
        }.joinToString("\n")

        return when (role) {
            RedditRole.SKEPTIC -> """
$languageInstruction

You are a skeptical researcher commenting on this paper.

## Available Claims
$claimsList

## Your Task
Challenge 1-2 specific claims by asking for more evidence or questioning methodology.
- Reference [Claim #X] in your comment
- Be constructive, not rude
- Ask specific questions about experimental setup, baselines, or fairness

Write 2-3 sentences. Casual but intelligent tone.
Respond with ONLY the comment text, no JSON.
""".trimIndent()

            RedditRole.IMPLEMENTER -> """
$languageInstruction

You want to reproduce this work.

## Available Claims
$claimsList

## Your Task
Ask practical implementation questions:
- Code availability?
- Hyperparameters?
- Compute requirements?
- Training time?
- Dataset details?

Reference [Claim #X] if relevant.
Write 2-3 sentences.
Respond with ONLY the comment text, no JSON.
""".trimIndent()

            RedditRole.REVIEWER_2 -> """
$languageInstruction

You are an academic reviewer providing constructive critique.

## Available Claims
$claimsList

## Your Task
Point out potential weaknesses:
- Missing ablations?
- Statistical significance?
- Limited datasets?
- Related work gaps?
- Generalization concerns?

Reference [Claim #X].
Write 3-4 sentences. Professional but critical tone.
Respond with ONLY the comment text, no JSON.
""".trimIndent()

            RedditRole.ELI5 -> """
$languageInstruction

You don't understand technical jargon and want simpler explanations.

## Paper Summary
- Objective: ${structural.mainObjective}
- Methodology: ${structural.methodology}

## Your Task
Ask for simpler explanations of complex concepts:
- "What does [technical term] mean?"
- "Can you explain [method] without equations?"
- "ELI5: how does this actually work?"

Write 1-2 friendly, curious questions.
Respond with ONLY the comment text, no JSON.
""".trimIndent()

            RedditRole.RELATED_PAPER -> """
$languageInstruction

You know the research landscape well.

## Related Papers
${if (structural.relatedPapers.isNotEmpty()) {
    structural.relatedPapers.take(3).map { "- ${it.title} (${it.year ?: "N/A"})" }.joinToString("\n")
} else {
    "(No related papers listed)"
}}

## Your Task
Mention 2-3 related works and how they compare:
- "This reminds me of [Paper X] from [Year]"
- "How does this differ from [approach]?"
- "You should also check out [Paper Y]"

Write 3-4 sentences.
Respond with ONLY the comment text, no JSON.
""".trimIndent()

            RedditRole.COMPARATIVE_CRITIC -> """
$languageInstruction

You've read similar papers and want to compare.

## Available Claims
$claimsList

## Your Task
Compare with related approaches:
- "I read [Paper X] which does [similar thing]"
- "What makes this better than [baseline]?"
- "The improvement seems marginal compared to [method]"

Reference [Claim #X] when comparing results.
Write 2-3 sentences. Slightly provocative but fair.
Respond with ONLY the comment text, no JSON.
""".trimIndent()

            RedditRole.ALTERNATIVE_VIEW -> """
$languageInstruction

You present an alternative perspective.

## Available Claims
$claimsList

## Your Task
Challenge an assumption or propose alternative interpretation:
- "What if we approached this differently by [alternative]?"
- "I disagree with [assumption] because..."
- "Another way to interpret [result] is..."

Reference [Claim #X].
Write 2-3 sentences. Respectful disagreement.
Respond with ONLY the comment text, no JSON.
""".trimIndent()
        }
    }

    /**
     * Generate OP's reply to a comment
     */
    private suspend fun generateReply(
        parentComment: RedditPost,
        structural: StructuralUnderstanding,
        language: NarrativeLanguage,
        depth: Int,
        order: Int
    ): RedditPost {
        val languageInstruction = if (language == NarrativeLanguage.KOREAN) {
            "Write in Korean (한국어). Respond as the paper's advocate."
        } else {
            "Write in English. Respond as the paper's advocate."
        }

        val claimsList = structural.claims.mapIndexed { i, claim ->
            "[Claim #$i] ${claim.statement}\nEvidence: ${claim.evidence.snippet}"
        }.joinToString("\n\n")

        val prompt = """
$languageInstruction

You are responding to a comment as the OP (original poster).

## Original Comment
Role: ${parentComment.role}
Author: ${parentComment.author}
Content: ${parentComment.content}

## Available Claims with Evidence
$claimsList

## Your Task
Answer the comment using claim evidence:
- Be helpful and factual
- Reference [Claim #X] to support your answer
- If challenged, defend with evidence or acknowledge limitation
- Keep it friendly and informative

Write 2-3 sentences.
Respond with ONLY the reply text, no JSON.
""".trimIndent()

        val startTime = System.currentTimeMillis()
        val result = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        return result.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_reply_op",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true
                )

                val claimRefs = extractClaimReferences(response)

                RedditPost(
                    id = "r_op_${UUID.randomUUID()}",
                    parentId = parentComment.id,
                    role = null,
                    author = "OP",
                    content = response.trim(),
                    claimReferences = claimRefs,
                    depth = depth,
                    order = order,
                    score = (parentComment.score * 0.6).toInt().coerceAtLeast(30)
                )
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_reply_op",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message
                )

                // Fallback reply
                RedditPost(
                    id = "r_op_${UUID.randomUUID()}",
                    parentId = parentComment.id,
                    role = null,
                    author = "OP",
                    content = "Thanks for your comment! That's a great point to consider.",
                    claimReferences = emptyList(),
                    depth = depth,
                    order = order,
                    score = 30
                )
            }
        )
    }

    /**
     * Extract [Claim #X] references from text
     */
    private fun extractClaimReferences(text: String): List<Int> {
        val regex = Regex("""\[Claim #(\d+)\]""")
        return regex.findAll(text)
            .map { it.groupValues[1].toInt() }
            .distinct()
            .toList()
    }

    /**
     * Calculate OP score based on claims referenced
     */
    private fun calculateOPScore(claims: List<Claim>, claimRefs: List<Int>): Int {
        val baseScore = 500
        val avgConfidence = claimRefs.mapNotNull { ref ->
            claims.getOrNull(ref)?.confidence
        }.map { conf ->
            when (conf) {
                ClaimConfidence.HIGH -> 1.0
                ClaimConfidence.MEDIUM -> 0.7
                ClaimConfidence.LOW -> 0.4
            }
        }.average().takeIf { !it.isNaN() } ?: 0.7

        return (baseScore + (avgConfidence * 500)).toInt()
    }

    /**
     * Calculate comment score based on role and claims
     */
    private fun calculateCommentScore(role: RedditRole, claims: List<Claim>, claimRefs: List<Int>): Int {
        val baseScore = when (role) {
            RedditRole.SKEPTIC -> 120
            RedditRole.IMPLEMENTER -> 180
            RedditRole.REVIEWER_2 -> 80
            RedditRole.ELI5 -> 150
            RedditRole.RELATED_PAPER -> 100
            RedditRole.COMPARATIVE_CRITIC -> 90
            RedditRole.ALTERNATIVE_VIEW -> 70
        }

        // Adjust based on claim confidence
        val avgConfidence = claimRefs.mapNotNull { ref ->
            claims.getOrNull(ref)?.confidence
        }.map { conf ->
            when (conf) {
                ClaimConfidence.HIGH -> 1.0
                ClaimConfidence.MEDIUM -> 0.8
                ClaimConfidence.LOW -> 0.5
            }
        }.average().takeIf { !it.isNaN() } ?: 0.7

        // Limitation claims get lower scores (controversial)
        val hasLimitationClaim = claimRefs.any { ref ->
            claims.getOrNull(ref)?.type == ClaimType.LIMITATION
        }

        val multiplier = if (hasLimitationClaim) 0.7 else 1.0

        return (baseScore * avgConfidence * multiplier).toInt()
    }

    // =============================================================================
    // Interactive User Comments
    // =============================================================================

    /**
     * Generate AI reply to a user's comment
     * Returns the updated RedditThread with the user comment and AI reply added
     */
    suspend fun handleUserComment(
        currentThread: RedditThread,
        userComment: String,
        structural: StructuralUnderstanding,
        language: NarrativeLanguage,
        parentId: String? = null
    ): Result<RedditThread> = runCatching {
        // Create user's comment post
        val userPost = RedditPost(
            id = "u_${UUID.randomUUID()}",
            parentId = parentId,
            role = null,
            author = "User",
            content = userComment,
            claimReferences = extractClaimReferences(userComment),
            depth = if (parentId == null) 1 else 2,
            order = currentThread.comments.count { it.parentId == parentId },
            score = 1
        )

        // Generate AI reply to user's comment
        val aiReply = generateUserReply(
            userComment = userPost,
            structural = structural,
            language = language
        )

        // Add both user comment and AI reply to thread
        val updatedComments = currentThread.comments + userPost + aiReply

        RedditThread(
            originalPost = currentThread.originalPost,
            comments = updatedComments
        )
    }

    /**
     * Generate OP's reply to a user comment
     */
    private suspend fun generateUserReply(
        userComment: RedditPost,
        structural: StructuralUnderstanding,
        language: NarrativeLanguage
    ): RedditPost {
        val languageInstruction = if (language == NarrativeLanguage.KOREAN) {
            "Write in Korean (한국어). Respond as the paper's advocate."
        } else {
            "Write in English. Respond as the paper's advocate."
        }

        val claimsList = structural.claims.mapIndexed { i, claim ->
            "[Claim #$i] ${claim.statement}\nEvidence: ${claim.evidence.snippet}"
        }.joinToString("\n\n")

        val prompt = """
$languageInstruction

You are responding to a user's comment as the OP (original poster) who wrote about this research paper.

## User's Comment
${userComment.content}

## Available Claims with Evidence
$claimsList

## Your Task
Answer the user's comment using claim evidence:
- Be helpful, friendly, and informative
- Reference [Claim #X] to support your answer with evidence from the paper
- If the user asks a question, answer it directly
- If the user challenges something, defend with evidence or acknowledge if it's a valid limitation
- Keep the conversational tone of Reddit

Write 2-4 sentences.
Respond with ONLY the reply text, no JSON, no code blocks.
""".trimIndent()

        val startTime = System.currentTimeMillis()
        val result = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        return result.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_user_reply",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true
                )

                val claimRefs = extractClaimReferences(response)

                RedditPost(
                    id = "r_user_${UUID.randomUUID()}",
                    parentId = userComment.id,
                    role = null,
                    author = "OP",
                    content = response.trim(),
                    claimReferences = claimRefs,
                    depth = userComment.depth + 1,
                    order = 0,
                    score = 50
                )
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "reddit_user_reply",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message
                )

                // Fallback reply
                RedditPost(
                    id = "r_user_${UUID.randomUUID()}",
                    parentId = userComment.id,
                    role = null,
                    author = "OP",
                    content = "Thanks for your comment! That's an interesting point.",
                    claimReferences = emptyList(),
                    depth = userComment.depth + 1,
                    order = 0,
                    score = 30
                )
            }
        )
    }
}

@Serializable
private data class FiguresResponseJson(
    val figures: List<FigureJson>? = null
)

@Serializable
private data class FigureJson(
    val figureId: String? = null,
    val explanation: String? = null,
    val relevance: String? = null
)

@Serializable
private data class FormulasResponseJson(
    val formulas: List<FormulaJson>? = null
)

@Serializable
private data class FormulaJson(
    val formulaId: String? = null,
    val explanation: String? = null,
    val relevance: String? = null
)

@Serializable
private data class TablesResponseJson(
    val tables: List<TableJson>? = null
)

@Serializable
private data class TableJson(
    val tableId: String? = null,
    val summary: String? = null,
    val keyInsights: String? = null
)

@Serializable
private data class TitleSummaryJson(
    val title: String? = null,
    val summary: String? = null
)
