package com.potero.service.relatedwork

import com.potero.domain.model.*
import com.potero.domain.repository.PaperRepository
import com.potero.domain.repository.GrobidRepository
import com.potero.domain.repository.RelatedWorkRepository
import com.potero.service.metadata.SemanticScholarResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Service for finding and managing related work relationships
 */
class RelatedWorkService(
    private val relatedWorkRepository: RelatedWorkRepository,
    private val paperRepository: PaperRepository,
    private val grobidRepository: GrobidRepository,
    private val semanticScholarResolver: SemanticScholarResolver
) {

    /**
     * Find and rank related papers for a source paper.
     * Uses multiple strategies and caches results.
     *
     * @param sourcePaperId Source paper ID
     * @param limit Maximum number of results
     * @param forceRefresh Force fresh search (ignore cache)
     * @return List of related paper candidates with relevance scores
     */
    suspend fun findRelatedPapers(
        sourcePaperId: String,
        limit: Int = 20,
        forceRefresh: Boolean = false
    ): Result<List<RelatedPaperCandidate>> = runCatching {

        // Check cache first
        if (!forceRefresh) {
            val cached = relatedWorkRepository.getTopRelated(sourcePaperId, limit).getOrNull()
            if (!cached.isNullOrEmpty()) {
                return@runCatching cached.map { it.toCandidate() }
            }
        }

        val sourcePaper = paperRepository.getById(sourcePaperId).getOrNull()
            ?: throw IllegalArgumentException("Source paper not found: $sourcePaperId")

        val candidates = mutableListOf<RelatedPaperCandidate>()

        // Strategy 1: Semantic Scholar recommendations
        try {
            candidates.addAll(fetchSemanticScholarRecommendations(sourcePaper))
        } catch (e: Exception) {
            println("[RelatedWorkService] S2 recommendations failed: ${e.message}")
        }

        // Strategy 2: Internal reference analysis (papers cited by this paper)
        try {
            candidates.addAll(analyzeInternalReferences(sourcePaper))
        } catch (e: Exception) {
            println("[RelatedWorkService] Internal reference analysis failed: ${e.message}")
        }

        // Strategy 3: Papers in local library with shared authors/tags
        try {
            candidates.addAll(findSimilarInLibrary(sourcePaper))
        } catch (e: Exception) {
            println("[RelatedWorkService] Library similarity search failed: ${e.message}")
        }

        // Deduplicate and rank
        val ranked = deduplicateAndRank(candidates).take(limit)

        // Cache results to database
        saveCandidatesToDatabase(sourcePaper.id, ranked)

        ranked
    }

    /**
     * Fetch recommendations from Semantic Scholar API
     */
    private suspend fun fetchSemanticScholarRecommendations(paper: Paper): List<RelatedPaperCandidate> {
        // Semantic Scholar Recommendations API: GET /paper/{paperId}/recommendations
        // Note: This API endpoint returns papers similar to the given paper
        // We'll use the paper search as a proxy for now, as recommendations API may need special access

        val query = paper.title
        val searchResults = runCatching {
            semanticScholarResolver.search(query, limit = 20)
        }.getOrNull() ?: return emptyList()

        // Get Semantic Scholar ID for filtering (check if paper has a DOI and get S2 paper)
        val sourcePaperS2Id = paper.doi?.let { doi ->
            runCatching { semanticScholarResolver.getByDoi(doi) }.getOrNull()?.paperId
        }

        return searchResults
            .filter { it.paperId != sourcePaperS2Id } // Exclude source paper
            .map { s2Paper ->
                RelatedPaperCandidate(
                    paperId = s2Paper.paperId,
                    title = s2Paper.title,
                    authors = s2Paper.authors.map { it.name },
                    year = s2Paper.year,
                    doi = s2Paper.externalIds?.doi,
                    abstract = s2Paper.abstract,
                    citationsCount = s2Paper.citationCount ?: 0,
                    relationshipType = RelationshipType.SEMANTIC_SIMILAR,
                    relevanceScore = calculateSemanticSimilarityScore(paper, s2Paper),
                    source = RelationshipSource.SEMANTIC_SCHOLAR,
                    reasoning = "Semantically similar based on Semantic Scholar recommendations"
                )
            }
    }

    /**
     * Analyze paper's references and find which are in local library
     */
    private suspend fun analyzeInternalReferences(paper: Paper): List<RelatedPaperCandidate> {
        // Get GROBID-extracted references
        val references = grobidRepository.getReferencesByPaperId(paper.id).getOrNull()
            ?: return emptyList()

        val candidates = mutableListOf<RelatedPaperCandidate>()

        // Check if any references match papers in our library
        for (ref in references) {
            // Try to match by DOI first
            if (ref.doi != null) {
                val matchedPaper = paperRepository.getByDoi(ref.doi).getOrNull()
                if (matchedPaper != null) {
                    candidates.add(
                        RelatedPaperCandidate(
                            paperId = matchedPaper.id,
                            title = matchedPaper.title,
                            authors = matchedPaper.authors.map { it.name },
                            year = matchedPaper.year,
                            doi = matchedPaper.doi,
                            abstract = matchedPaper.abstract,
                            citationsCount = matchedPaper.citationsCount,
                            relationshipType = RelationshipType.REFERENCE,
                            relevanceScore = 0.9, // High score - directly cited
                            source = RelationshipSource.INTERNAL_CITATIONS,
                            reasoning = "Cited by source paper"
                        )
                    )
                }
            }

            // TODO: Could also try fuzzy title matching for references without DOI
        }

        return candidates
    }

    /**
     * Find similar papers in local library (by authors or tags)
     */
    private suspend fun findSimilarInLibrary(paper: Paper): List<RelatedPaperCandidate> = coroutineScope {
        val candidates = mutableListOf<RelatedPaperCandidate>()

        // Strategy 1: Papers by same authors
        val authorNames = paper.authors.map { it.name }
        if (authorNames.isNotEmpty()) {
            val allPapers = paperRepository.getAll().getOrNull() ?: emptyList()

            allPapers
                .filter { it.id != paper.id } // Exclude source paper
                .filter { otherPaper ->
                    // Check for author overlap
                    val otherAuthors = otherPaper.authors.map { it.name }
                    authorNames.any { author -> otherAuthors.contains(author) }
                }
                .forEach { matchedPaper ->
                    val sharedAuthors = authorNames.intersect(matchedPaper.authors.map { it.name }.toSet())
                    candidates.add(
                        RelatedPaperCandidate(
                            paperId = matchedPaper.id,
                            title = matchedPaper.title,
                            authors = matchedPaper.authors.map { it.name },
                            year = matchedPaper.year,
                            doi = matchedPaper.doi,
                            abstract = matchedPaper.abstract,
                            citationsCount = matchedPaper.citationsCount,
                            relationshipType = RelationshipType.AUTHOR_OVERLAP,
                            relevanceScore = calculateAuthorOverlapScore(sharedAuthors.size, authorNames.size),
                            source = RelationshipSource.INTERNAL_LIBRARY,
                            reasoning = "Shares ${sharedAuthors.size} author(s): ${sharedAuthors.joinToString()}"
                        )
                    )
                }
        }

        // Strategy 2: Papers with shared tags
        val tagNames = paper.tags.map { it.name }
        if (tagNames.isNotEmpty()) {
            val allPapers = paperRepository.getAll().getOrNull() ?: emptyList()

            allPapers
                .filter { it.id != paper.id }
                .filter { otherPaper ->
                    val otherTags = otherPaper.tags.map { it.name }
                    tagNames.any { tag -> otherTags.contains(tag) }
                }
                .forEach { matchedPaper ->
                    val sharedTags = tagNames.intersect(matchedPaper.tags.map { it.name }.toSet())

                    // Only add if not already added by author overlap
                    if (candidates.none { it.paperId == matchedPaper.id }) {
                        candidates.add(
                            RelatedPaperCandidate(
                                paperId = matchedPaper.id,
                                title = matchedPaper.title,
                                authors = matchedPaper.authors.map { it.name },
                                year = matchedPaper.year,
                                doi = matchedPaper.doi,
                                abstract = matchedPaper.abstract,
                                citationsCount = matchedPaper.citationsCount,
                                relationshipType = RelationshipType.TOPIC_SIMILAR,
                                relevanceScore = calculateTagOverlapScore(sharedTags.size, tagNames.size),
                                source = RelationshipSource.INTERNAL_LIBRARY,
                                reasoning = "Shares ${sharedTags.size} tag(s): ${sharedTags.joinToString()}"
                            )
                        )
                    }
                }
        }

        candidates
    }

    /**
     * Deduplicate candidates and rank by relevance
     */
    private fun deduplicateAndRank(candidates: List<RelatedPaperCandidate>): List<RelatedPaperCandidate> {
        // Group by paperId (or DOI if available) and keep highest scoring
        val grouped = candidates
            .groupBy { it.doi ?: it.paperId }
            .mapValues { (_, group) ->
                group.maxByOrNull { it.relevanceScore }!!
            }
            .values

        // Sort by relevance score, then citation count, then recency
        return grouped.sortedWith(
            compareByDescending<RelatedPaperCandidate> { it.relevanceScore }
                .thenByDescending { it.citationsCount }
                .thenByDescending { it.year ?: 0 }
        )
    }

    /**
     * Save candidate results to database for caching
     */
    private suspend fun saveCandidatesToDatabase(sourcePaperId: String, candidates: List<RelatedPaperCandidate>) {
        val now = Clock.System.now()

        candidates.forEach { candidate ->
            // First, ensure the related paper exists in our database
            // If it doesn't, we could create a stub entry (optional)
            val existingPaper = paperRepository.getById(candidate.paperId).getOrNull()
                ?: if (candidate.doi != null) {
                    paperRepository.getByDoi(candidate.doi).getOrNull()
                } else null

            val relatedPaperId = existingPaper?.id ?: candidate.paperId

            val relatedWork = RelatedWork(
                id = UUID.randomUUID().toString(),
                sourcePaperId = sourcePaperId,
                relatedPaperId = relatedPaperId,
                relationshipType = candidate.relationshipType,
                relevanceScore = candidate.relevanceScore,
                source = candidate.source,
                reasoning = candidate.reasoning,
                createdAt = now,
                updatedAt = now
            )

            relatedWorkRepository.insert(relatedWork)
        }
    }

    /**
     * Calculate semantic similarity score from Semantic Scholar result
     * Higher position in results = higher score
     */
    private fun calculateSemanticSimilarityScore(sourcePaper: Paper, s2Paper: com.potero.service.metadata.SemanticScholarPaper): Double {
        // Base score starts at 0.7 for S2 recommendations
        var score = 0.7

        // Boost if same conference
        if (sourcePaper.conference != null && sourcePaper.conference == s2Paper.venue) {
            score += 0.1
        }

        // Boost for recent papers (within 2 years)
        val sourcePaperYear = sourcePaper.year ?: 0
        val s2PaperYear = s2Paper.year ?: 0
        if (s2PaperYear in (sourcePaperYear - 2)..(sourcePaperYear + 2)) {
            score += 0.1
        }

        // Boost for highly cited papers (normalized)
        val citationBoost = minOf(0.1, (s2Paper.citationCount ?: 0) / 1000.0)
        score += citationBoost

        return minOf(1.0, score)
    }

    /**
     * Calculate score based on author overlap
     */
    private fun calculateAuthorOverlapScore(sharedCount: Int, totalCount: Int): Double {
        val ratio = sharedCount.toDouble() / totalCount.toDouble()
        return minOf(0.95, 0.6 + (ratio * 0.35))
    }

    /**
     * Calculate score based on tag overlap
     */
    private fun calculateTagOverlapScore(sharedCount: Int, totalCount: Int): Double {
        val ratio = sharedCount.toDouble() / totalCount.toDouble()
        return minOf(0.85, 0.5 + (ratio * 0.35))
    }

    /**
     * Get cached related papers
     */
    suspend fun getRelatedPapers(sourcePaperId: String, limit: Int): Result<List<RelatedWork>> {
        return relatedWorkRepository.getTopRelated(sourcePaperId, limit)
    }

    /**
     * Convert RelatedWork to candidate format
     */
    private suspend fun RelatedWork.toCandidate(): RelatedPaperCandidate {
        val paper = paperRepository.getById(this.relatedPaperId).getOrNull()

        return RelatedPaperCandidate(
            paperId = this.relatedPaperId,
            title = paper?.title ?: "Unknown",
            authors = paper?.authors?.map { it.name } ?: emptyList(),
            year = paper?.year,
            doi = paper?.doi,
            abstract = paper?.abstract,
            citationsCount = paper?.citationsCount ?: 0,
            relationshipType = this.relationshipType,
            relevanceScore = this.relevanceScore,
            source = this.source,
            reasoning = this.reasoning
        )
    }
}

/**
 * Related paper candidate with metadata
 */
data class RelatedPaperCandidate(
    val paperId: String,
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val doi: String?,
    val abstract: String?,
    val citationsCount: Int,
    val relationshipType: RelationshipType,
    val relevanceScore: Double,
    val source: RelationshipSource,
    val reasoning: String?
)
