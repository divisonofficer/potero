package com.potero.service.search

import com.potero.service.metadata.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified search service with:
 * - Hybrid search (primary + supplementary APIs in parallel)
 * - Local caching to reduce API calls
 * - Request throttling
 * - DOI-based deduplication
 */
class UnifiedSearchService(
    private val semanticScholarResolver: SemanticScholarResolver,
    private val openAlexResolver: OpenAlexResolver,
    private val pubMedResolver: PubMedResolver,
    private val dblpResolver: DBLPResolver,
    private val apiStatusProvider: APIStatusProvider,
    private val googleScholarScraper: GoogleScholarScraper,
    private val cacheService: SearchCacheService
) {
    companion object {
        // Minimum time between requests (per source)
        private const val SEMANTIC_SCHOLAR_MIN_INTERVAL_MS = 1000L // 1 second
        private const val GOOGLE_SCHOLAR_MIN_INTERVAL_MS = 5000L  // 5 seconds

        // Track consecutive failures for circuit breaker
        private const val CIRCUIT_BREAKER_THRESHOLD = 3
        private const val CIRCUIT_BREAKER_RESET_MS = 60_000L // 1 minute

        // Hybrid search timeout per API
        private const val API_TIMEOUT_MS = 5000L

        // Source priority for metadata selection when deduplicating
        private val SOURCE_PRIORITY = listOf(
            "semantic_scholar",
            "openalex",
            "pubmed",
            "dblp"
        )
    }

    private val mutex = Mutex()

    // Last request timestamps
    private val lastSemanticScholarRequest = AtomicLong(0)
    private val lastGoogleScholarRequest = AtomicLong(0)

    // Circuit breaker state
    private var semanticScholarFailures = 0
    private var semanticScholarCircuitOpenTime: Long = 0
    private var googleScholarFailures = 0
    private var googleScholarCircuitOpenTime: Long = 0

    /**
     * Hybrid search: Semantic Scholar (primary) + OpenAlex/PubMed/DBLP (supplementary)
     * All APIs run concurrently with 5-second timeout
     * @param query Search query
     * @param preferredEngine Preferred search engine (legacy parameter, ignored)
     * @param limit Maximum results
     * @return List of search results
     */
    suspend fun search(
        query: String,
        preferredEngine: String = "semantic",
        limit: Int = 10
    ): SearchServiceResult {
        // Check cache first
        val cached = cacheService.getSearchResults(query)
        if (cached != null) {
            return SearchServiceResult(
                results = cached.map { it.toSearchResult() }.take(limit),
                source = "cache",
                fromCache = true
            )
        }

        // Check if any API enabled
        val hasEnabledAPI = apiStatusProvider.isSemanticScholarEnabled() ||
                            apiStatusProvider.getEnabledSupplementaryAPIs().isNotEmpty()
        if (!hasEnabledAPI) {
            return SearchServiceResult(
                results = emptyList(),
                source = "none",
                fromCache = false,
                error = "All search APIs disabled. Enable at least one in Settings."
            )
        }

        // Execute hybrid search (primary + supplementary in parallel)
        val (primaryResults, supplementaryResults) = executeHybridSearch(query, limit)

        // Deduplicate by DOI
        val combined = deduplicateResults(primaryResults + supplementaryResults)
        val finalResults = combined.take(limit)

        // Cache successful results
        if (finalResults.isNotEmpty()) {
            val papersForCache = finalResults.map { sr ->
                SemanticScholarPaper(
                    paperId = sr.id,
                    title = sr.title,
                    authors = sr.authors.map { SemanticScholarAuthor(name = it) },
                    year = sr.year,
                    venue = sr.venue,
                    citationCount = sr.citationCount,
                    abstract = sr.abstract,
                    openAccessPdf = sr.pdfUrl?.let { SemanticScholarOpenAccessPdf(url = it) },
                    externalIds = if (sr.doi != null || sr.arxivId != null) {
                        SemanticScholarExternalIds(doi = sr.doi, arxivId = sr.arxivId)
                    } else null
                )
            }
            cacheService.cacheSearchResults(query, papersForCache)
        }

        // Build source list for response
        val sources = mutableListOf<String>()
        if (primaryResults.isNotEmpty()) sources.add("semantic_scholar")
        supplementaryResults.groupBy { it.source }.keys.forEach { sources.add(it) }

        println("[HybridSearch] Query: $query")
        println("[HybridSearch] Primary: ${primaryResults.size}, Supplementary: ${supplementaryResults.size}")
        println("[HybridSearch] After dedup: ${combined.size}, Final: ${finalResults.size}")
        println("[HybridSearch] Sources: ${sources.joinToString(", ")}")

        return SearchServiceResult(
            results = finalResults,
            source = sources.joinToString("+"),
            fromCache = false
        )
    }

    /**
     * Execute primary + supplementary searches in parallel
     * Returns (primary results, supplementary results)
     */
    private suspend fun executeHybridSearch(
        query: String,
        limit: Int
    ): Pair<List<SearchResult>, List<SearchResult>> = coroutineScope {
        // Primary: Semantic Scholar (if enabled)
        val primaryDeferred = async {
            if (apiStatusProvider.isSemanticScholarEnabled()) {
                try {
                    withTimeout(API_TIMEOUT_MS) {
                        searchSemanticScholar(query, limit)
                    }
                } catch (e: Exception) {
                    println("[HybridSearch] Semantic Scholar failed: ${e.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

        // Supplementary: OpenAlex, PubMed, DBLP (parallel)
        val supplementaryDeferred = listOf(
            async {
                if (apiStatusProvider.isOpenAlexEnabled()) {
                    try {
                        withTimeout(API_TIMEOUT_MS) {
                            openAlexResolver.search(query, limit)
                        }
                    } catch (e: Exception) {
                        println("[HybridSearch] OpenAlex failed: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            },
            async {
                if (apiStatusProvider.isPubMedEnabled()) {
                    try {
                        withTimeout(API_TIMEOUT_MS) {
                            pubMedResolver.search(query, limit)
                        }
                    } catch (e: Exception) {
                        println("[HybridSearch] PubMed failed: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            },
            async {
                if (apiStatusProvider.isDBLPEnabled()) {
                    try {
                        withTimeout(API_TIMEOUT_MS) {
                            dblpResolver.search(query, limit)
                        }
                    } catch (e: Exception) {
                        println("[HybridSearch] DBLP failed: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        )

        val primary = primaryDeferred.await()
        val supplementary = supplementaryDeferred.awaitAll().flatten()

        Pair(primary, supplementary)
    }

    /**
     * Deduplicate results by DOI with metadata quality scoring
     *
     * Strategy:
     * - Group by DOI (case-insensitive)
     * - For results without DOI, keep all results (show source badge)
     * - When duplicate DOI found, prefer result with most complete metadata
     * - Break ties by source priority: Semantic Scholar > OpenAlex > PubMed > DBLP
     */
    private fun deduplicateResults(results: List<SearchResult>): List<SearchResult> {
        // Separate results with/without DOI
        val withDoi = results.filter { !it.doi.isNullOrBlank() }
        val withoutDoi = results.filter { it.doi.isNullOrBlank() }

        // Deduplicate by DOI
        val deduplicatedDoi = withDoi
            .groupBy { it.doi!!.lowercase().trim() }
            .map { (_, duplicates) ->
                if (duplicates.size == 1) {
                    duplicates.first()
                } else {
                    // Multiple results with same DOI - pick best one
                    duplicates.maxByOrNull { result ->
                        // Calculate metadata completeness score (0-6)
                        val completeness = listOf(
                            result.abstract != null,
                            result.venue != null,
                            result.year != null,
                            result.citationCount != null,
                            result.pdfUrl != null,
                            result.authors.isNotEmpty()
                        ).count { it }

                        // Get source priority (lower index = higher priority)
                        val sourcePriority = SOURCE_PRIORITY.indexOf(result.source)
                            .takeIf { it >= 0 } ?: 999

                        // Score: completeness (0-6) * 1000 - priority (0-999)
                        // Higher score wins
                        completeness * 1000 - sourcePriority
                    }!!
                }
            }

        // Combine: deduped DOI results + all non-DOI results
        return deduplicatedDoi + withoutDoi
    }

    private suspend fun searchSemanticScholar(query: String, limit: Int): List<SearchResult> {
        // Check circuit breaker
        if (isCircuitOpen(semanticScholarFailures, semanticScholarCircuitOpenTime)) {
            throw RuntimeException("Semantic Scholar circuit breaker is open")
        }

        // Throttle requests
        throttle(lastSemanticScholarRequest, SEMANTIC_SCHOLAR_MIN_INTERVAL_MS)

        return try {
            val results = semanticScholarResolver.search(query, limit)
            resetCircuitBreaker {
                semanticScholarFailures = 0
            }
            results.map { it.toSearchResult() }
        } catch (e: Exception) {
            incrementCircuitBreaker {
                semanticScholarFailures++
                if (semanticScholarFailures >= CIRCUIT_BREAKER_THRESHOLD) {
                    semanticScholarCircuitOpenTime = System.currentTimeMillis()
                }
            }
            throw e
        }
    }

    private suspend fun searchGoogleScholar(query: String, limit: Int): List<SearchResult> {
        // Check circuit breaker
        if (isCircuitOpen(googleScholarFailures, googleScholarCircuitOpenTime)) {
            throw RuntimeException("Google Scholar circuit breaker is open")
        }

        // Throttle requests
        throttle(lastGoogleScholarRequest, GOOGLE_SCHOLAR_MIN_INTERVAL_MS)

        return try {
            val results = googleScholarScraper.search(query, limit).getOrThrow()
            resetCircuitBreaker {
                googleScholarFailures = 0
            }
            results.map { it.toSearchResult() }
        } catch (e: Exception) {
            incrementCircuitBreaker {
                googleScholarFailures++
                if (googleScholarFailures >= CIRCUIT_BREAKER_THRESHOLD) {
                    googleScholarCircuitOpenTime = System.currentTimeMillis()
                }
            }
            throw e
        }
    }

    private fun isCircuitOpen(failures: Int, openTime: Long): Boolean {
        if (failures < CIRCUIT_BREAKER_THRESHOLD) return false

        val elapsed = System.currentTimeMillis() - openTime
        return elapsed < CIRCUIT_BREAKER_RESET_MS
    }

    private suspend fun throttle(lastRequest: AtomicLong, minInterval: Long) {
        val now = System.currentTimeMillis()
        val last = lastRequest.get()
        val elapsed = now - last

        if (elapsed < minInterval) {
            kotlinx.coroutines.delay(minInterval - elapsed)
        }

        lastRequest.set(System.currentTimeMillis())
    }

    private suspend fun resetCircuitBreaker(reset: () -> Unit) = mutex.withLock {
        reset()
    }

    private suspend fun incrementCircuitBreaker(increment: () -> Unit) = mutex.withLock {
        increment()
    }

    /**
     * Get service status and statistics
     */
    suspend fun getStatus(): SearchServiceStatus {
        val cacheStats = cacheService.getStats()

        return SearchServiceStatus(
            cacheEntries = cacheStats.validEntries,
            semanticScholarAvailable = !isCircuitOpen(semanticScholarFailures, semanticScholarCircuitOpenTime),
            googleScholarAvailable = !isCircuitOpen(googleScholarFailures, googleScholarCircuitOpenTime),
            semanticScholarFailures = semanticScholarFailures,
            googleScholarFailures = googleScholarFailures
        )
    }

    /**
     * Clear cache and reset circuit breakers
     */
    suspend fun reset() {
        cacheService.clear()
        mutex.withLock {
            semanticScholarFailures = 0
            semanticScholarCircuitOpenTime = 0
            googleScholarFailures = 0
            googleScholarCircuitOpenTime = 0
        }
    }
}

/**
 * Result from unified search service
 */
data class SearchServiceResult(
    val results: List<SearchResult>,
    val source: String,
    val fromCache: Boolean,
    val usedFallback: Boolean = false,
    val error: String? = null
)

/**
 * Search service status
 */
data class SearchServiceStatus(
    val cacheEntries: Int,
    val semanticScholarAvailable: Boolean,
    val googleScholarAvailable: Boolean,
    val semanticScholarFailures: Int,
    val googleScholarFailures: Int
)
