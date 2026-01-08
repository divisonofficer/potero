package com.potero.service.search

import com.potero.service.metadata.GoogleScholarScraper
import com.potero.service.metadata.SearchResult
import com.potero.service.metadata.SemanticScholarPaper
import com.potero.service.metadata.SemanticScholarResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified search service with:
 * - Local caching to reduce API calls
 * - Request throttling
 * - Automatic fallback from Semantic Scholar to Google Scholar
 */
class UnifiedSearchService(
    private val semanticScholarResolver: SemanticScholarResolver,
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
     * Search for papers with automatic caching and fallback
     * @param query Search query
     * @param preferredEngine Preferred search engine ("semantic" or "scholar")
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

        // Try preferred engine first
        val result = when (preferredEngine) {
            "scholar" -> searchWithFallback(
                primary = { searchGoogleScholar(query, limit) },
                fallback = { searchSemanticScholar(query, limit) },
                primaryName = "google_scholar",
                fallbackName = "semantic_scholar"
            )
            else -> searchWithFallback(
                primary = { searchSemanticScholar(query, limit) },
                fallback = { searchGoogleScholar(query, limit) },
                primaryName = "semantic_scholar",
                fallbackName = "google_scholar"
            )
        }

        // Cache successful results
        if (result.results.isNotEmpty() && result.error == null) {
            // Convert SearchResult back to SemanticScholarPaper for caching
            // (simplified - in production you'd want a unified cache type)
            val papersForCache = result.results.map { sr ->
                SemanticScholarPaper(
                    paperId = sr.id,
                    title = sr.title,
                    authors = sr.authors.map {
                        com.potero.service.metadata.SemanticScholarAuthor(name = it)
                    },
                    year = sr.year,
                    venue = sr.venue,
                    citationCount = sr.citationCount,
                    abstract = sr.abstract,
                    openAccessPdf = sr.pdfUrl?.let {
                        com.potero.service.metadata.SemanticScholarOpenAccessPdf(url = it)
                    },
                    externalIds = if (sr.doi != null || sr.arxivId != null) {
                        com.potero.service.metadata.SemanticScholarExternalIds(
                            doi = sr.doi,
                            arxivId = sr.arxivId
                        )
                    } else null
                )
            }
            cacheService.cacheSearchResults(query, papersForCache)
        }

        return result
    }

    private suspend fun searchWithFallback(
        primary: suspend () -> List<SearchResult>,
        fallback: suspend () -> List<SearchResult>,
        primaryName: String,
        fallbackName: String
    ): SearchServiceResult {
        // Try primary source
        try {
            val results = primary()
            if (results.isNotEmpty()) {
                return SearchServiceResult(
                    results = results,
                    source = primaryName,
                    fromCache = false
                )
            }
        } catch (e: Exception) {
            // Log and continue to fallback
            println("Primary search ($primaryName) failed: ${e.message}")
        }

        // Try fallback source
        try {
            val results = fallback()
            return SearchServiceResult(
                results = results,
                source = fallbackName,
                fromCache = false,
                usedFallback = true
            )
        } catch (e: Exception) {
            return SearchServiceResult(
                results = emptyList(),
                source = "none",
                fromCache = false,
                error = "All search engines failed: ${e.message}"
            )
        }
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
