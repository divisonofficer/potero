package com.potero.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Narrative style types for Paper-to-Narrative Engine
 */
@Serializable
enum class NarrativeStyle(val displayName: String, val description: String) {
    BLOG("Blog", "Technical blog style - in-depth, educational"),
    NEWS("News", "News article style - concise, headline-focused"),
    REDDIT("Reddit", "Reddit style - casual, community-friendly")
}

/**
 * Supported languages for narrative generation
 */
@Serializable
enum class NarrativeLanguage(val code: String, val displayName: String) {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English")
}

/**
 * Figure explanation for narrative
 */
@Serializable
data class FigureExplanation(
    val id: String,
    val narrativeId: String,
    val figureId: String,
    val label: String,
    val originalCaption: String? = null,
    val explanation: String,
    val relevance: String? = null,
    val createdAt: Instant
)

/**
 * Table explanation for narrative
 */
@Serializable
data class TableExplanation(
    val id: String,
    val narrativeId: String,
    val tableId: String,
    val label: String,
    val originalCaption: String? = null,
    val summary: String,
    val keyInsights: String? = null,
    val createdAt: Instant
)

/**
 * Formula explanation for narrative
 */
@Serializable
data class FormulaExplanation(
    val id: String,
    val narrativeId: String,
    val formulaId: String,
    val label: String,
    val latex: String? = null,
    val explanation: String,
    val relevance: String? = null,
    val createdAt: Instant
)

/**
 * Concept explanation for prerequisite knowledge
 */
@Serializable
data class ConceptExplanation(
    val id: String,
    val narrativeId: String,
    val term: String,
    val definition: String,
    val analogy: String? = null,
    val relatedTerms: List<String> = emptyList(),
    val createdAt: Instant
)

/**
 * A generated narrative for a paper
 */
@Serializable
data class Narrative(
    val id: String,
    val paperId: String,
    val style: NarrativeStyle,
    val language: NarrativeLanguage,
    val title: String,
    val content: String,
    val summary: String,
    val figureExplanations: List<FigureExplanation> = emptyList(),
    val tableExplanations: List<TableExplanation> = emptyList(),
    val formulaExplanations: List<FormulaExplanation> = emptyList(),
    val conceptExplanations: List<ConceptExplanation> = emptyList(),
    val estimatedReadTime: Int = 5,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Intermediate result from structural understanding stage (Stage 1)
 */
@Serializable
data class StructuralUnderstanding(
    val paperId: String,
    val mainObjective: String,
    val researchQuestion: String,
    val methodology: String,
    val keyFindings: List<String> = emptyList(),
    val contributions: List<String> = emptyList(),
    val sections: List<SectionSummary> = emptyList(),
    val targetAudience: String,
    val prerequisites: List<String> = emptyList(),

    // Reddit Thread를 위한 추가 필드
    val claims: List<Claim> = emptyList(),
    val limitations: List<String> = emptyList(),
    val relatedPapers: List<RelatedPaper> = emptyList()
)

@Serializable
data class SectionSummary(
    val title: String,
    val purpose: String,
    val keyPoints: List<String> = emptyList()
)

/**
 * Intermediate result from content recomposition stage (Stage 2)
 */
@Serializable
data class RecomposedContent(
    val paperId: String,
    val narrativeOutline: List<NarrativeSection> = emptyList(),
    val figureIntegrationPlan: List<FigurePlacement> = emptyList(),
    val tableIntegrationPlan: List<TablePlacement> = emptyList(),
    val formulaIntegrationPlan: List<FormulaPlacement> = emptyList(),
    val conceptsToExplain: List<String> = emptyList()
)

@Serializable
data class NarrativeSection(
    val order: Int,
    val heading: String,
    val purposeInNarrative: String,
    val sourceFromPaper: String,
    val suggestedLength: String
)

@Serializable
data class FigurePlacement(
    val figureId: String,
    val suggestedSection: Int,
    val narrativeRole: String
)

@Serializable
data class TablePlacement(
    val tableId: String,
    val suggestedSection: Int,
    val narrativeRole: String
)

@Serializable
data class FormulaPlacement(
    val formulaId: String,
    val suggestedSection: Int,
    val narrativeRole: String
)

/**
 * Generation request for narrative
 *
 * Default behavior: Generate BLOG style in Korean only (1 narrative)
 * To generate multiple: Specify desired styles/languages explicitly
 */
@Serializable
data class NarrativeGenerationRequest(
    val paperId: String,
    val styles: List<NarrativeStyle> = listOf(NarrativeStyle.BLOG),  // Default: BLOG only
    val languages: List<NarrativeLanguage> = listOf(NarrativeLanguage.KOREAN),  // Default: Korean only
    val includeFigureExplanations: Boolean = true,
    val includeConceptExplanations: Boolean = true,
    val regenerate: Boolean = false
)

/**
 * Generation progress tracking
 */
@Serializable
data class NarrativeGenerationProgress(
    val paperId: String,
    val totalNarratives: Int,
    val completedNarratives: Int,
    val currentStage: String,
    val currentStyle: NarrativeStyle? = null,
    val currentLanguage: NarrativeLanguage? = null
)

/**
 * Narrative cache entry for intermediate results
 */
@Serializable
data class NarrativeCache(
    val id: String,
    val paperId: String,
    val stage: String,
    val data: String,
    val createdAt: Long,
    val expiresAt: Long
)

// =============================================================================
// Reddit Thread Models
// =============================================================================

/**
 * Claim extracted from paper for Reddit Thread generation
 */
@Serializable
data class Claim(
    val statement: String,
    val type: ClaimType,
    val evidence: Evidence,
    val confidence: ClaimConfidence
)

@Serializable
enum class ClaimType {
    PROBLEM_MOTIVATION,
    TECHNICAL_CONTRIBUTION,
    EMPIRICAL_RESULT,
    THEORETICAL_RESULT,
    LIMITATION
}

@Serializable
enum class ClaimConfidence {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Evidence supporting a claim
 */
@Serializable
data class Evidence(
    val type: EvidenceType,
    val snippet: String,
    val figureId: String? = null,
    val tableId: String? = null,
    val formulaId: String? = null,
    val sectionReference: String? = null
)

@Serializable
enum class EvidenceType {
    TEXT_QUOTE,
    FIGURE,
    TABLE,
    FORMULA,
    SECTION
}

/**
 * Related paper from GROBID references
 */
@Serializable
data class RelatedPaper(
    val grobidRefId: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val relationship: String
)

/**
 * Reddit Thread structure (stored as JSON in Narrative.content)
 */
@Serializable
data class RedditThread(
    val originalPost: RedditPost,
    val comments: List<RedditPost>
)

/**
 * Reddit post (OP or comment)
 */
@Serializable
data class RedditPost(
    val id: String,
    val parentId: String? = null,
    val role: RedditRole? = null,
    val author: String,
    val content: String,
    val claimReferences: List<Int> = emptyList(),
    val depth: Int = 0,
    val order: Int = 0,
    val score: Int = 0
)

@Serializable
enum class RedditRole {
    SKEPTIC,
    IMPLEMENTER,
    REVIEWER_2,
    ELI5,
    RELATED_PAPER,
    COMPARATIVE_CRITIC,
    ALTERNATIVE_VIEW
}

// =============================================================================
// Extension Functions
// =============================================================================

/**
 * Convert RedditThread to Reddit-formatted Markdown
 * Can be copied and pasted directly to Reddit
 */
fun RedditThread.toRedditMarkdown(): String {
    val sb = StringBuilder()

    // OP (Original Post)
    sb.appendLine("# ${extractTitle()}")
    sb.appendLine()
    sb.appendLine(originalPost.content)
    sb.appendLine()
    sb.appendLine("*Posted by u/${originalPost.author} | ${originalPost.score} points*")
    sb.appendLine()
    sb.appendLine("---")
    sb.appendLine()

    // Comments sorted by depth and order
    val sortedComments = comments.sortedWith(compareBy({ it.depth }, { it.order }))

    // Group comments by parent
    val topLevelComments = sortedComments.filter { it.depth == 1 }
    val replies = sortedComments.filter { it.depth > 1 }.groupBy { it.parentId }

    topLevelComments.forEach { comment ->
        // Top-level comment
        sb.appendLine("## Comment by u/${comment.author} ${comment.role?.let { "[$it]" } ?: ""} | ${comment.score} points")
        sb.appendLine()
        sb.appendLine(comment.content)
        sb.appendLine()

        // Replies to this comment
        replies[comment.id]?.forEach { reply ->
            sb.appendLine("> **Reply by u/${reply.author}**")
            sb.appendLine(">")
            reply.content.lines().forEach { line ->
                sb.appendLine("> $line")
            }
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine()
    }

    return sb.toString()
}

/**
 * Extract title from OP content (first line starting with **)
 */
private fun RedditThread.extractTitle(): String {
    val titlePattern = Regex("""\*\*(.+?)\*\*""")
    val match = titlePattern.find(originalPost.content)
    return match?.groupValues?.get(1) ?: "Reddit Thread"
}

/**
 * Get a plain text summary of the Reddit Thread
 */
fun RedditThread.toPlainTextSummary(): String {
    val title = extractTitle()
    val commentCount = comments.count { it.depth == 1 }
    val replyCount = comments.count { it.depth > 1 }

    return """
Reddit Thread: $title
OP Score: ${originalPost.score}
Comments: $commentCount
Replies: $replyCount
Roles: ${comments.mapNotNull { it.role }.distinct().joinToString(", ")}
    """.trimIndent()
}
