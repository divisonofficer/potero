package com.potero.service.metadata

import com.potero.service.common.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resolver for DBLP (computer science bibliography) API
 *
 * DBLP provides computer science bibliography with:
 * - Computer science publications (journals, conferences, workshops)
 * - Completely free, no API key required
 * - No strict rate limits (but be polite)
 * - Excellent venue information
 * - Author disambiguation
 *
 * @param httpClient HTTP client for making requests
 */
class DBLPResolver(
    private val httpClient: HttpClient
) : MetadataResolver {

    companion object {
        private const val SEARCH_URL = "https://dblp.org/search/publ/api"
        private const val PUB_URL = "https://dblp.org/rec"
        private val rateLimiter = RateLimiter(5) // 5 req/s (be polite)
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    override fun canResolve(identifier: String): Boolean {
        // DBLP can resolve titles, DOIs, DBLP keys
        return identifier.startsWith("10.") || // DOI
                identifier.contains("/") || // DBLP key (e.g., journals/cacm/Knuth74)
                identifier.contains(" ") // Search query
    }

    override suspend fun resolve(identifier: String): Result<ResolvedMetadata> = runCatching {
        val hit = when {
            identifier.startsWith("10.") -> searchByDOI(identifier)
            identifier.contains("/") -> getByKey(identifier)
            else -> searchByTitle(identifier)
        } ?: throw MetadataResolutionException("No results found", identifier)

        hit.toResolvedMetadata()
    }

    /**
     * Search for publications by query
     *
     * @param query Search query (title, author, etc.)
     * @param limit Maximum number of results
     * @return List of search results
     */
    suspend fun search(query: String, limit: Int = 10): List<SearchResult> {
        rateLimiter.throttle()

        val response = withRetry {
            httpClient.get(SEARCH_URL) {
                parameter("q", query)
                parameter("format", "json")
                parameter("h", limit)
            }.body<DBLPSearchResponse>()
        }

        return response.result?.hits?.hit?.mapNotNull { it.toSearchResult() } ?: emptyList()
    }

    /**
     * Search by DOI
     */
    private suspend fun searchByDOI(doi: String): DBLPHit? {
        val results = search(doi, 1)
        return if (results.isNotEmpty()) {
            // Convert SearchResult back to DBLPHit (simplified)
            DBLPHit(
                info = DBLPInfo(
                    title = results.first().title,
                    authors = DBLPAuthors(author = results.first().authors),
                    year = results.first().year,
                    venue = results.first().venue,
                    doi = results.first().doi
                )
            )
        } else {
            null
        }
    }

    /**
     * Search by title
     */
    private suspend fun searchByTitle(title: String): DBLPHit? {
        val results = search(title, 1)
        return if (results.isNotEmpty()) {
            DBLPHit(
                info = DBLPInfo(
                    title = results.first().title,
                    authors = DBLPAuthors(author = results.first().authors),
                    year = results.first().year,
                    venue = results.first().venue,
                    doi = results.first().doi
                )
            )
        } else {
            null
        }
    }

    /**
     * Get publication by DBLP key
     */
    private suspend fun getByKey(key: String): DBLPHit? {
        // For now, use search API (full API would require XML parsing)
        return searchByTitle(key)
    }

    /**
     * Retry logic with exponential backoff
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: Exception("Failed after $MAX_RETRIES retries")
    }
}

// ============================================
// DBLP API Response Models
// ============================================

@Serializable
data class DBLPSearchResponse(
    val result: DBLPResult? = null
)

@Serializable
data class DBLPResult(
    val hits: DBLPHits? = null,
    val time: DBLPTime? = null,
    val completions: DBLPCompletions? = null
)

@Serializable
data class DBLPHits(
    @SerialName("@sent") val sent: Int? = null,
    @SerialName("@total") val total: Int? = null,
    @SerialName("@computed") val computed: Int? = null,
    @SerialName("@first") val first: Int? = null,
    val hit: List<DBLPHit>? = null
)

@Serializable
data class DBLPHit(
    @SerialName("@score") val score: String? = null,
    @SerialName("@id") val id: String? = null,
    val info: DBLPInfo? = null,
    val url: String? = null
) {
    fun toResolvedMetadata(): ResolvedMetadata {
        return ResolvedMetadata(
            title = info?.title ?: "Unknown Title",
            authors = info?.authors?.author?.map { ResolvedAuthor(name = it) } ?: emptyList(),
            abstract = null, // DBLP doesn't provide abstracts
            doi = info?.doi,
            arxivId = null,
            year = info?.year,
            venue = info?.venue,
            venueType = info?.type,
            pdfUrl = info?.ee, // Electronic edition (often PDF)
            citationsCount = null
        )
    }

    fun toSearchResult(): SearchResult {
        return SearchResult(
            id = id ?: "",
            title = info?.title ?: "Unknown Title",
            authors = info?.authors?.author ?: emptyList(),
            year = info?.year,
            venue = info?.venue,
            citationCount = null,
            abstract = null,
            pdfUrl = info?.ee,
            doi = info?.doi,
            arxivId = null,
            source = "dblp"
        )
    }
}

@Serializable
data class DBLPInfo(
    val authors: DBLPAuthors? = null,
    val title: String? = null,
    val venue: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val key: String? = null,
    val doi: String? = null,
    val ee: String? = null, // Electronic edition URL
    val url: String? = null
)

@Serializable
data class DBLPAuthors(
    val author: List<String>? = null
)

@Serializable
data class DBLPTime(
    @SerialName("@unit") val unit: String? = null,
    @SerialName("text") val value: String? = null
)

@Serializable
data class DBLPCompletions(
    @SerialName("@sent") val sent: Int? = null,
    @SerialName("@total") val total: Int? = null,
    val c: List<DBLPCompletion>? = null
)

@Serializable
data class DBLPCompletion(
    @SerialName("@sc") val sc: String? = null,
    @SerialName("@dc") val dc: String? = null,
    @SerialName("@oc") val oc: String? = null,
    @SerialName("@id") val id: String? = null,
    @SerialName("text") val value: String? = null
)
