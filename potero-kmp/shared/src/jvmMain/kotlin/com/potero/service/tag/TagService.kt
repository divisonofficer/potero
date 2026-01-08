package com.potero.service.tag

import com.potero.domain.model.Tag
import com.potero.domain.repository.TagRepository
import com.potero.service.llm.LLMService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Service for intelligent tag management with LLM-based auto-tagging
 */
class TagService(
    private val tagRepository: TagRepository,
    private val llmService: LLMService
) {
    companion object {
        private const val MAX_TAGS = 500 // Trigger merge when exceeding this
        private const val SIMILARITY_THRESHOLD = 0.8

        // Predefined tag colors for auto-generated tags
        private val TAG_COLORS = listOf(
            "#ef4444", // red
            "#f97316", // orange
            "#f59e0b", // amber
            "#eab308", // yellow
            "#84cc16", // lime
            "#22c55e", // green
            "#14b8a6", // teal
            "#06b6d4", // cyan
            "#0ea5e9", // sky
            "#3b82f6", // blue
            "#6366f1", // indigo
            "#8b5cf6", // violet
            "#a855f7", // purple
            "#d946ef", // fuchsia
            "#ec4899", // pink
            "#f43f5e"  // rose
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Extract tags from paper content using LLM
     * @param title Paper title
     * @param abstract Paper abstract (optional)
     * @param fullText Full paper text (optional, first few pages)
     * @return List of extracted tag names
     */
    suspend fun extractTagsFromPaper(
        title: String,
        abstract: String? = null,
        fullText: String? = null
    ): Result<List<String>> = runCatching {
        println("[TagService] extractTagsFromPaper called")
        println("[TagService] Title: $title")
        println("[TagService] Abstract: ${abstract?.take(100)}...")
        println("[TagService] FullText available: ${fullText != null}")

        // Get existing tags for context
        val existingTags = tagRepository.getAll().getOrDefault(emptyList())
        val existingTagNames = existingTags.map { it.name }
        println("[TagService] Existing tags count: ${existingTagNames.size}")

        val prompt = buildExtractionPrompt(title, abstract, fullText, existingTagNames)
        println("[TagService] Prompt built, length: ${prompt.length}")

        println("[TagService] Calling LLM service...")
        val response = llmService.chat(prompt).getOrThrow()
        println("[TagService] LLM response received, length: ${response.length}")
        println("[TagService] LLM response: $response")

        val tags = parseTagResponse(response)
        println("[TagService] Parsed tags: $tags")
        tags
    }

    /**
     * Auto-tag a paper: extract tags, match with existing, create new if needed
     * @return List of Tag objects that were assigned to the paper
     */
    suspend fun autoTagPaper(
        paperId: String,
        title: String,
        abstract: String? = null,
        fullText: String? = null
    ): Result<List<Tag>> = runCatching {
        // 1. Extract tags using LLM
        val extractedTags = extractTagsFromPaper(title, abstract, fullText).getOrThrow()

        if (extractedTags.isEmpty()) {
            return@runCatching emptyList()
        }

        // 2. Get existing tags
        val existingTags = tagRepository.getAll().getOrDefault(emptyList())
        val existingTagMap = existingTags.associateBy { it.name.lowercase() }

        // 3. Match or create tags
        val assignedTags = mutableListOf<Tag>()

        for (extractedTag in extractedTags) {
            val normalizedTag = extractedTag.lowercase().trim()

            // Check for exact match
            val exactMatch = existingTagMap[normalizedTag]
            if (exactMatch != null) {
                assignedTags.add(exactMatch)
                continue
            }

            // Check for similar tag
            val similarTag = findSimilarTag(normalizedTag, existingTags)
            if (similarTag != null) {
                assignedTags.add(similarTag)
                continue
            }

            // Create new tag
            val newTag = Tag(
                id = UUID.randomUUID().toString(),
                name = extractedTag.trim(),
                color = TAG_COLORS.random()
            )
            tagRepository.insert(newTag).getOrThrow()
            assignedTags.add(newTag)
        }

        // 4. Link tags to paper
        val tagQueries = (tagRepository as? com.potero.data.repository.TagRepositoryImpl)
        // Note: Tag linking would be done via PaperTag table

        // 5. Check if we need to merge tags
        val totalTags = tagRepository.getAll().getOrDefault(emptyList()).size
        if (totalTags > MAX_TAGS) {
            // Trigger async merge (not blocking the current operation)
            // This could be done via a background job
        }

        assignedTags
    }

    /**
     * Find similar existing tag using simple string similarity
     */
    private fun findSimilarTag(targetName: String, existingTags: List<Tag>): Tag? {
        for (tag in existingTags) {
            val similarity = calculateSimilarity(targetName, tag.name.lowercase())
            if (similarity >= SIMILARITY_THRESHOLD) {
                return tag
            }
        }
        return null
    }

    /**
     * Simple Jaccard similarity for tag matching
     */
    private fun calculateSimilarity(a: String, b: String): Double {
        val wordsA = a.split(" ", "-", "_").filter { it.isNotBlank() }.toSet()
        val wordsB = b.split(" ", "-", "_").filter { it.isNotBlank() }.toSet()

        if (wordsA.isEmpty() || wordsB.isEmpty()) {
            return if (a == b) 1.0 else 0.0
        }

        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size

        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Merge similar tags to reduce total count
     * Called when tag count exceeds MAX_TAGS
     */
    suspend fun mergeSimilarTags(): Result<Int> = runCatching {
        val tagsWithCounts = tagRepository.getTagsWithCounts().getOrThrow()

        // Sort by paper count (keep tags with more papers)
        val sortedTags = tagsWithCounts.sortedByDescending { it.second }

        val toMerge = mutableListOf<Pair<Tag, Tag>>() // (source, target) - merge source INTO target
        val processed = mutableSetOf<String>()

        for (i in sortedTags.indices) {
            val (tagA, countA) = sortedTags[i]
            if (tagA.id in processed) continue

            for (j in i + 1 until sortedTags.size) {
                val (tagB, countB) = sortedTags[j]
                if (tagB.id in processed) continue

                val similarity = calculateSimilarity(
                    tagA.name.lowercase(),
                    tagB.name.lowercase()
                )

                if (similarity >= 0.7) { // Lower threshold for merging
                    // Merge the less used tag into the more used one
                    toMerge.add(tagB to tagA) // tagB -> tagA (tagA has more papers)
                    processed.add(tagB.id)
                }
            }
            processed.add(tagA.id)
        }

        // If we still have too many after simple merge, use LLM for semantic merge
        if (sortedTags.size - toMerge.size > MAX_TAGS) {
            val llmMerges = suggestMergesWithLLM(
                sortedTags.filter { it.first.id !in toMerge.map { m -> m.first.id } }
            )
            toMerge.addAll(llmMerges)
        }

        // Execute merges
        // This would involve:
        // 1. Update PaperTag to point to target tag
        // 2. Delete source tag

        toMerge.size
    }

    /**
     * Use LLM to suggest which tags should be merged
     */
    private suspend fun suggestMergesWithLLM(
        tagsWithCounts: List<Pair<Tag, Int>>
    ): List<Pair<Tag, Tag>> {
        if (tagsWithCounts.size < 10) return emptyList()

        val tagList = tagsWithCounts.joinToString("\n") {
            "${it.first.name} (${it.second} papers)"
        }

        val prompt = """
            |Analyze these academic paper tags and suggest which ones should be merged because they are semantically similar or one is a subcategory of another.
            |
            |Tags:
            |$tagList
            |
            |Return ONLY a JSON array of merge suggestions. Each suggestion should merge the "from" tag INTO the "to" tag (keep the more general/popular one).
            |
            |Example format:
            |[{"from": "deep learning", "to": "machine learning"}, {"from": "CNN", "to": "neural networks"}]
            |
            |Only suggest merges where the tags are truly semantically related. Return empty array [] if no merges are needed.
        """.trimMargin()

        return try {
            val response = llmService.chat(prompt).getOrThrow()
            val suggestions = json.decodeFromString<List<MergeSuggestion>>(
                response.substringAfter("[").substringBefore("]").let { "[$it]" }
            )

            val tagMap = tagsWithCounts.associate { it.first.name.lowercase() to it.first }

            suggestions.mapNotNull { suggestion ->
                val fromTag = tagMap[suggestion.from.lowercase()]
                val toTag = tagMap[suggestion.to.lowercase()]
                if (fromTag != null && toTag != null && fromTag.id != toTag.id) {
                    fromTag to toTag
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Build prompt for tag extraction
     */
    private fun buildExtractionPrompt(
        title: String,
        abstract: String?,
        fullText: String?,
        existingTags: List<String>
    ): String {
        val existingTagsStr = if (existingTags.isNotEmpty()) {
            """
            |
            |Here are existing tags in the system. Prefer using these if they match:
            |${existingTags.take(100).joinToString(", ")}
            """.trimMargin()
        } else ""

        val contentStr = buildString {
            append("Title: $title")
            if (!abstract.isNullOrBlank()) {
                append("\n\nAbstract: $abstract")
            }
            if (!fullText.isNullOrBlank()) {
                append("\n\nContent excerpt:\n${fullText.take(3000)}")
            }
        }

        return """
            |Analyze this academic paper and extract 3-7 relevant tags/keywords.
            |
            |Paper:
            |$contentStr
            |$existingTagsStr
            |
            |Guidelines:
            |1. Extract tags that represent the main topics, methods, and domains
            |2. Use existing tags when they match
            |3. Keep tags concise (1-3 words each)
            |4. Use lowercase, common academic terminology
            |5. Include both broad categories (e.g., "machine learning") and specific topics (e.g., "transformer")
            |
            |Return ONLY a JSON array of tag strings. Example: ["machine learning", "computer vision", "image classification"]
        """.trimMargin()
    }

    /**
     * Parse LLM response to extract tags
     */
    private fun parseTagResponse(response: String): List<String> {
        return try {
            // Try to parse as JSON array
            val cleanResponse = response
                .substringAfter("[")
                .substringBefore("]")
                .let { "[$it]" }

            json.decodeFromString<List<String>>(cleanResponse)
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length <= 50 }
                .take(7)
        } catch (e: Exception) {
            // Fallback: try to extract quoted strings
            val regex = """"([^"]+)"""".toRegex()
            regex.findAll(response)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() && it.length <= 50 }
                .take(7)
                .toList()
        }
    }

    /**
     * Get tag suggestions for a paper (without auto-assigning)
     */
    suspend fun suggestTags(
        title: String,
        abstract: String? = null
    ): Result<List<TagSuggestion>> = runCatching {
        val extractedTags = extractTagsFromPaper(title, abstract, null).getOrThrow()
        val existingTags = tagRepository.getAll().getOrDefault(emptyList())
        val existingTagMap = existingTags.associateBy { it.name.lowercase() }

        extractedTags.map { tagName ->
            val normalizedName = tagName.lowercase().trim()
            val existingTag = existingTagMap[normalizedName]
                ?: findSimilarTag(normalizedName, existingTags)

            TagSuggestion(
                name = tagName,
                existingTag = existingTag,
                isNew = existingTag == null
            )
        }
    }
}

@Serializable
data class MergeSuggestion(
    val from: String,
    val to: String
)

@Serializable
data class TagSuggestion(
    val name: String,
    val existingTag: Tag?,
    val isNew: Boolean
)
