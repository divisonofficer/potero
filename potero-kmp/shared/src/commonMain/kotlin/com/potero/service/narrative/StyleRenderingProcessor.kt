package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.service.llm.LLMService
import com.potero.service.llm.LLMLogger
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
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

    /**
     * Render the final narrative
     */
    suspend fun render(
        paper: Paper,
        structural: StructuralUnderstanding,
        recomposed: RecomposedContent,
        concepts: List<ConceptExplanation>,
        figures: List<FigureInfo>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Result<Narrative> = runCatching {
        val narrativeId = UUID.randomUUID().toString()

        // Generate main content
        val contentPrompt = buildContentPrompt(paper, structural, recomposed, concepts, figures, style, language)
        val startTime = System.currentTimeMillis()
        val contentResult = llmService.chat(contentPrompt)
        val endTime = System.currentTimeMillis()

        val content = contentResult.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_render_${style.name.lowercase()}_${language.code}",
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
                    purpose = "narrative_render_${style.name.lowercase()}_${language.code}",
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
- **${fig.label ?: "Figure ${index + 1}"}**: ${fig.caption ?: "No caption"}
  - Best placed: ${placement?.suggestedSection ?: "Where relevant"}
  - Role: ${placement?.narrativeRole ?: "Supports the main argument"}
""".trim()
            }.joinToString("\n")
            """
## Available Figures (MUST reference these in your narrative)
$figureList

IMPORTANT: You MUST integrate ALL figures naturally into your narrative.
For each figure, write "[See ${if (language == NarrativeLanguage.KOREAN) "Figure" else "Figure"} X: brief description]" at appropriate points.
Explain what each figure shows and why it matters to readers.
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
${if (figures.isNotEmpty()) "- **CRITICAL**: Reference ALL ${figures.size} figures at appropriate points using [See Figure X: description] format" else ""}
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
private data class TitleSummaryJson(
    val title: String? = null,
    val summary: String? = null
)
