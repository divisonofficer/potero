package com.potero.service.metadata

import com.potero.service.common.RateLimiter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resolver for OpenAlex API
 *
 * OpenAlex provides comprehensive scholarly metadata with:
 * - 200M+ works across all disciplines
 * - Free access, no API key required (but recommended for higher rate limits)
 * - 100,000 requests/day
 * - Open Access PDF links
 *
 * Rate limits:
 * - Without polite pool: ~100k requests/day
 * - With polite pool (email in User-Agent): Higher sustained throughput
 * - Max 10 requests/second recommended
 *
 * @param httpClient HTTP client for making requests
 * @param apiKeyProvider Optional provider for API key (increases rate limits)
 */
class OpenAlexResolver(
    private val httpClient: HttpClient,
    private val apiKeyProvider: (suspend () -> String?)? = null
) : MetadataResolver {

    companion object {
        private const val BASE_URL = "https://api.openalex.org"
        private const val USER_AGENT = "Potero/1.0 (mailto:potero@postech.ac.kr)"
        private val rateLimiter = RateLimiter(10) // 10 req/s
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    override fun canResolve(identifier: String): Boolean {
        // OpenAlex can resolve DOIs, OpenAlex IDs, and search queries
        return identifier.startsWith("10.") || // DOI
                identifier.startsWith("W") || // OpenAlex Work ID
                identifier.contains(" ") // Search query
    }

    override suspend fun resolve(identifier: String): Result<ResolvedMetadata> = runCatching {
        val work = when {
            identifier.startsWith("10.") -> getByDOI(identifier)
            identifier.startsWith("W") -> getById(identifier)
            else -> searchSingle(identifier)
        } ?: throw MetadataResolutionException("No results found", identifier)

        work.toResolvedMetadata()
    }

    /**
     * Search for papers by query string
     *
     * @param query Search query (title, author, etc.)
     * @param limit Maximum number of results
     * @return List of search results
     */
    suspend fun search(query: String, limit: Int = 10): List<SearchResult> {
        rateLimiter.throttle()

        val response = withRetry {
            httpClient.get("$BASE_URL/works") {
                header("User-Agent", USER_AGENT)
                apiKeyProvider?.invoke()?.let { key ->
                    if (key.isNotBlank()) {
                        header("Authorization", "Bearer $key")
                    }
                }
                parameter("search", query)
                parameter("per-page", limit)
            }.body<OpenAlexSearchResponse>()
        }

        return response.results.map { it.toSearchResult() }
    }

    /**
     * Get a single paper by DOI
     */
    private suspend fun getByDOI(doi: String): OpenAlexWork? {
        rateLimiter.throttle()

        return withRetry {
            try {
                httpClient.get("$BASE_URL/works/https://doi.org/$doi") {
                    header("User-Agent", USER_AGENT)
                    apiKeyProvider?.invoke()?.let { key ->
                        if (key.isNotBlank()) {
                            header("Authorization", "Bearer $key")
                        }
                    }
                }.body<OpenAlexWork>()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get a single paper by OpenAlex ID
     */
    private suspend fun getById(id: String): OpenAlexWork? {
        rateLimiter.throttle()

        return withRetry {
            try {
                httpClient.get("$BASE_URL/works/$id") {
                    header("User-Agent", USER_AGENT)
                    apiKeyProvider?.invoke()?.let { key ->
                        if (key.isNotBlank()) {
                            header("Authorization", "Bearer $key")
                        }
                    }
                }.body<OpenAlexWork>()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Search and return the first result
     */
    private suspend fun searchSingle(query: String): OpenAlexWork? {
        val results = search(query, 1)
        return if (results.isNotEmpty()) {
            // Get full details for the first result
            getById(results.first().id)
        } else {
            null
        }
    }

    /**
     * Retry logic with exponential backoff for rate limiting
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
// OpenAlex API Response Models
// ============================================

@Serializable
data class OpenAlexSearchResponse(
    val results: List<OpenAlexWork> = emptyList(),
    val meta: OpenAlexMeta
)

@Serializable
data class OpenAlexMeta(
    val count: Int,
    @SerialName("per_page") val perPage: Int,
    val page: Int
)

@Serializable
data class OpenAlexWork(
    val id: String,
    val doi: String? = null,
    val title: String? = null,
    @SerialName("publication_year") val publicationYear: Int? = null,
    @SerialName("publication_date") val publicationDate: String? = null,
    @SerialName("primary_location") val primaryLocation: OpenAlexLocation? = null,
    val authorships: List<OpenAlexAuthorship> = emptyList(),
    @SerialName("cited_by_count") val citedByCount: Int? = null,
    @SerialName("biblio") val biblio: OpenAlexBiblio? = null,
    @SerialName("abstract_inverted_index") val abstractInvertedIndex: Map<String, List<Int>>? = null,
    @SerialName("open_access") val openAccess: OpenAlexOpenAccess? = null,
    @SerialName("ids") val ids: OpenAlexIds? = null
) {
    fun toResolvedMetadata(): ResolvedMetadata {
        val abstract = abstractInvertedIndex?.let { invertedIndex ->
            reconstructAbstract(invertedIndex)
        }

        return ResolvedMetadata(
            title = title ?: "Unknown Title",
            authors = authorships.mapNotNull { it.author?.displayName }.map { name ->
                ResolvedAuthor(name = name)
            },
            abstract = abstract,
            doi = doi,
            arxivId = ids?.arxiv,
            year = publicationYear,
            venue = primaryLocation?.source?.displayName,
            venueType = primaryLocation?.source?.type,
            pdfUrl = openAccess?.oaUrl,
            citationsCount = citedByCount
        )
    }

    fun toSearchResult(): SearchResult {
        return SearchResult(
            id = id.substringAfterLast("/"),
            title = title ?: "Unknown Title",
            authors = authorships.mapNotNull { it.author?.displayName },
            year = publicationYear,
            venue = primaryLocation?.source?.displayName,
            citationCount = citedByCount,
            abstract = null, // Not included in search results
            pdfUrl = openAccess?.oaUrl,
            doi = doi,
            arxivId = ids?.arxiv,
            source = "openalex"
        )
    }

    /**
     * Reconstruct abstract from inverted index
     *
     * OpenAlex stores abstracts as inverted indexes for space efficiency.
     * Example: {"hello": [0], "world": [1]} -> "hello world"
     */
    private fun reconstructAbstract(invertedIndex: Map<String, List<Int>>): String {
        val wordPositions = mutableMapOf<Int, String>()
        invertedIndex.forEach { (word, positions) ->
            positions.forEach { pos ->
                wordPositions[pos] = word
            }
        }
        return wordPositions.toSortedMap().values.joinToString(" ")
    }
}

@Serializable
data class OpenAlexLocation(
    val source: OpenAlexSource? = null,
    @SerialName("pdf_url") val pdfUrl: String? = null,
    val version: String? = null
)

@Serializable
data class OpenAlexSource(
    @SerialName("display_name") val displayName: String? = null,
    val type: String? = null,
    @SerialName("issn_l") val issnL: String? = null,
    val issn: List<String>? = null
)

@Serializable
data class OpenAlexAuthorship(
    val author: OpenAlexAuthor? = null,
    @SerialName("author_position") val authorPosition: String? = null,
    val institutions: List<OpenAlexInstitution> = emptyList()
)

@Serializable
data class OpenAlexAuthor(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val orcid: String? = null
)

@Serializable
data class OpenAlexInstitution(
    val id: String,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class OpenAlexBiblio(
    val volume: String? = null,
    val issue: String? = null,
    @SerialName("first_page") val firstPage: String? = null,
    @SerialName("last_page") val lastPage: String? = null
)

@Serializable
data class OpenAlexOpenAccess(
    @SerialName("is_oa") val isOa: Boolean,
    @SerialName("oa_status") val oaStatus: String? = null,
    @SerialName("oa_url") val oaUrl: String? = null,
    @SerialName("any_repository_has_fulltext") val anyRepositoryHasFulltext: Boolean = false
)

@Serializable
data class OpenAlexIds(
    val openalex: String,
    val doi: String? = null,
    val mag: String? = null,
    val pmid: String? = null,
    val pmcid: String? = null,
    @SerialName("arxiv") val arxiv: String? = null
)
