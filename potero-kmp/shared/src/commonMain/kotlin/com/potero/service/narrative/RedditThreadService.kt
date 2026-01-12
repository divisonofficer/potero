package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.domain.repository.NarrativeRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Service for managing Reddit Thread narratives
 * Provides high-level operations for interactive Reddit-style discussions
 */
class RedditThreadService(
    private val narrativeRepository: NarrativeRepository,
    private val styleRenderingProcessor: StyleRenderingProcessor
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Get Reddit Thread from a narrative
     * Returns null if the narrative is not in REDDIT style or content is invalid
     */
    suspend fun getRedditThread(narrativeId: String): Result<RedditThread?> = runCatching {
        val narrative = narrativeRepository.getById(narrativeId).getOrThrow()
            ?: return@runCatching null

        if (narrative.style != NarrativeStyle.REDDIT) {
            return@runCatching null
        }

        parseRedditThread(narrative.content)
    }

    /**
     * Get Reddit Thread by paper, style, and language
     */
    suspend fun getRedditThreadByPaper(
        paperId: String,
        language: NarrativeLanguage = NarrativeLanguage.KOREAN
    ): Result<Pair<Narrative, RedditThread>?> = runCatching {
        val narrative = narrativeRepository.getByPaperStyleLanguage(
            paperId = paperId,
            style = NarrativeStyle.REDDIT,
            language = language
        ).getOrThrow() ?: return@runCatching null

        val thread = parseRedditThread(narrative.content)
        narrative to thread
    }

    /**
     * Add a user comment to a Reddit Thread and get AI reply
     * Returns the updated narrative with the new thread
     */
    suspend fun addUserComment(
        narrativeId: String,
        userComment: String,
        structural: StructuralUnderstanding,
        parentId: String? = null
    ): Result<Pair<Narrative, RedditThread>> = runCatching {
        // Get current narrative
        val narrative = narrativeRepository.getById(narrativeId).getOrThrow()
            ?: throw IllegalArgumentException("Narrative not found: $narrativeId")

        if (narrative.style != NarrativeStyle.REDDIT) {
            throw IllegalArgumentException("Narrative is not REDDIT style")
        }

        // Parse current thread
        val currentThread = parseRedditThread(narrative.content)

        // Generate AI reply using StyleRenderingProcessor
        val updatedThread = styleRenderingProcessor.handleUserComment(
            currentThread = currentThread,
            userComment = userComment,
            structural = structural,
            language = narrative.language,
            parentId = parentId
        ).getOrThrow()

        // Serialize updated thread
        val updatedContent = json.encodeToString<RedditThread>(updatedThread)

        // Update narrative
        val updatedNarrative = narrative.copy(
            content = updatedContent,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )

        narrativeRepository.update(updatedNarrative).getOrThrow()

        updatedNarrative to updatedThread
    }

    /**
     * Export Reddit Thread to Reddit-formatted Markdown
     */
    suspend fun exportToMarkdown(narrativeId: String): Result<String> = runCatching {
        val narrative = narrativeRepository.getById(narrativeId).getOrThrow()
            ?: throw IllegalArgumentException("Narrative not found: $narrativeId")

        if (narrative.style != NarrativeStyle.REDDIT) {
            throw IllegalArgumentException("Narrative is not REDDIT style")
        }

        val thread = parseRedditThread(narrative.content)
        thread.toRedditMarkdown()
    }

    /**
     * Get plain text summary of a Reddit Thread
     */
    suspend fun getThreadSummary(narrativeId: String): Result<String> = runCatching {
        val narrative = narrativeRepository.getById(narrativeId).getOrThrow()
            ?: throw IllegalArgumentException("Narrative not found: $narrativeId")

        if (narrative.style != NarrativeStyle.REDDIT) {
            throw IllegalArgumentException("Narrative is not REDDIT style")
        }

        val thread = parseRedditThread(narrative.content)
        thread.toPlainTextSummary()
    }

    /**
     * Get all claims referenced in the thread
     */
    suspend fun getThreadClaims(
        narrativeId: String,
        structural: StructuralUnderstanding
    ): Result<List<Claim>> = runCatching {
        val narrative = narrativeRepository.getById(narrativeId).getOrThrow()
            ?: throw IllegalArgumentException("Narrative not found: $narrativeId")

        if (narrative.style != NarrativeStyle.REDDIT) {
            throw IllegalArgumentException("Narrative is not REDDIT style")
        }

        val thread = parseRedditThread(narrative.content)

        // Collect all claim references from OP and comments
        val allClaimRefs = mutableSetOf<Int>()
        allClaimRefs.addAll(thread.originalPost.claimReferences)
        thread.comments.forEach { comment ->
            allClaimRefs.addAll(comment.claimReferences)
        }

        // Return claims in order
        allClaimRefs.sorted().mapNotNull { ref ->
            structural.claims.getOrNull(ref)
        }
    }

    /**
     * Get statistics about the Reddit Thread
     */
    suspend fun getThreadStats(narrativeId: String): Result<RedditThreadStats> = runCatching {
        val narrative = narrativeRepository.getById(narrativeId).getOrThrow()
            ?: throw IllegalArgumentException("Narrative not found: $narrativeId")

        if (narrative.style != NarrativeStyle.REDDIT) {
            throw IllegalArgumentException("Narrative is not REDDIT style")
        }

        val thread = parseRedditThread(narrative.content)

        RedditThreadStats(
            opScore = thread.originalPost.score,
            totalComments = thread.comments.size,
            topLevelComments = thread.comments.count { it.depth == 1 },
            replies = thread.comments.count { it.depth > 1 },
            userComments = thread.comments.count { it.author == "User" },
            aiReplies = thread.comments.count { it.author == "OP" && it.depth > 0 },
            rolesPresent = thread.comments.mapNotNull { it.role }.distinct(),
            totalScore = thread.originalPost.score + thread.comments.sumOf { it.score },
            avgCommentScore = if (thread.comments.isEmpty()) 0 else
                thread.comments.sumOf { it.score } / thread.comments.size
        )
    }

    /**
     * Parse RedditThread from JSON string
     */
    private fun parseRedditThread(jsonContent: String): RedditThread {
        return json.decodeFromString<RedditThread>(jsonContent)
    }
}

/**
 * Statistics about a Reddit Thread
 */
data class RedditThreadStats(
    val opScore: Int,
    val totalComments: Int,
    val topLevelComments: Int,
    val replies: Int,
    val userComments: Int,
    val aiReplies: Int,
    val rolesPresent: List<RedditRole>,
    val totalScore: Int,
    val avgCommentScore: Int
)
