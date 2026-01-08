package com.potero.service.metadata

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resolver for Semantic Scholar API
 * Provides free, stable academic paper search and metadata retrieval
 *
 * API Documentation: https://api.semanticscholar.org/api-docs/graph
 *
 * Note: Without API key, rate limits are stricter (100 requests per 5 minutes).
 * With API key, rate limits are more generous (1 request per second sustained).
 * Get API key from: https://www.semanticscholar.org/product/api
 */
class SemanticScholarResolver(
    private val httpClient: HttpClient,
    private val apiKeyProvider: (suspend () -> String?)? = null
) : MetadataResolver {

    companion object {
        private const val BASE_URL = "https://api.semanticscholar.org/graph/v1"
        private const val SEARCH_URL = "$BASE_URL/paper/search"
        private const val PAPER_URL = "$BASE_URL/paper"
        private const val AUTHOR_URL = "$BASE_URL/author"
        private const val AUTHOR_SEARCH_URL = "$AUTHOR_URL/search"

        // Fields to request from API
        private const val PAPER_FIELDS = "paperId,title,authors,year,venue,citationCount,abstract,externalIds,openAccessPdf,publicationDate"
        private const val AUTHOR_FIELDS = "authorId,name,affiliations,paperCount,citationCount,hIndex,homepage,externalIds"
        private const val AUTHOR_PAPERS_FIELDS = "paperId,title,year,venue,citationCount"

        // API key header name
        private const val API_KEY_HEADER = "x-api-key"
    }

    /**
     * Helper to add API key header if available
     */
    private suspend fun io.ktor.client.request.HttpRequestBuilder.addApiKeyIfAvailable() {
        apiKeyProvider?.invoke()?.takeIf { it.isNotBlank() }?.let { apiKey ->
            header(API_KEY_HEADER, apiKey)
        }
    }

    override fun canResolve(identifier: String): Boolean = identifier.isNotBlank()

    /**
     * Resolve metadata by searching for the title
     */
    override suspend fun resolve(identifier: String): Result<ResolvedMetadata> = runCatching {
        val results = search(identifier, limit = 5)
        if (results.isEmpty()) {
            throw MetadataResolutionException("No results found", identifier)
        }

        // Return the first result as ResolvedMetadata
        results.first().toResolvedMetadata()
    }

    /**
     * Search for papers by query (title, keywords, etc.)
     * Returns a list of results for user selection
     * Includes retry logic for rate limiting (429 errors)
     */
    suspend fun search(query: String, limit: Int = 10): List<SemanticScholarPaper> {
        var lastException: Exception? = null
        var delayMs = 1000L

        repeat(3) { attempt ->
            try {
                val response = httpClient.get(SEARCH_URL) {
                    addApiKeyIfAvailable()
                    parameter("query", query)
                    parameter("limit", limit)
                    parameter("fields", PAPER_FIELDS)
                }

                if (response.status.value == 429) {
                    // Rate limited - wait and retry
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2 // Exponential backoff
                    return@repeat
                }

                if (!response.status.isSuccess()) {
                    throw MetadataResolutionException(
                        "Semantic Scholar API error: ${response.status}",
                        query
                    )
                }

                val searchResponse = response.body<SemanticScholarSearchResponse>()
                return searchResponse.data
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: MetadataResolutionException("Search failed after retries", query)
    }

    /**
     * Get paper by Semantic Scholar paper ID
     */
    suspend fun getByPaperId(paperId: String): SemanticScholarPaper? {
        val response = httpClient.get("$PAPER_URL/$paperId") {
            addApiKeyIfAvailable()
            parameter("fields", PAPER_FIELDS)
        }

        if (!response.status.isSuccess()) {
            return null
        }

        return response.body<SemanticScholarPaper>()
    }

    /**
     * Get paper by DOI
     */
    suspend fun getByDoi(doi: String): SemanticScholarPaper? {
        val response = httpClient.get("$PAPER_URL/DOI:$doi") {
            addApiKeyIfAvailable()
            parameter("fields", PAPER_FIELDS)
        }

        if (!response.status.isSuccess()) {
            return null
        }

        return response.body<SemanticScholarPaper>()
    }

    /**
     * Get paper by arXiv ID
     */
    suspend fun getByArxivId(arxivId: String): SemanticScholarPaper? {
        val response = httpClient.get("$PAPER_URL/ARXIV:$arxivId") {
            addApiKeyIfAvailable()
            parameter("fields", PAPER_FIELDS)
        }

        if (!response.status.isSuccess()) {
            return null
        }

        return response.body<SemanticScholarPaper>()
    }

    // ==================== Author API ====================

    /**
     * Search for authors by name
     * Returns a list of matching authors with their details
     */
    suspend fun searchAuthors(query: String, limit: Int = 10): List<SemanticScholarAuthorDetail> {
        var lastException: Exception? = null
        var delayMs = 1000L

        repeat(3) { attempt ->
            try {
                val response = httpClient.get(AUTHOR_SEARCH_URL) {
                    addApiKeyIfAvailable()
                    parameter("query", query)
                    parameter("limit", limit)
                    parameter("fields", AUTHOR_FIELDS)
                }

                if (response.status.value == 429) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                    return@repeat
                }

                if (!response.status.isSuccess()) {
                    throw MetadataResolutionException(
                        "Semantic Scholar Author API error: ${response.status}",
                        query
                    )
                }

                val searchResponse = response.body<SemanticScholarAuthorSearchResponse>()
                return searchResponse.data
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: MetadataResolutionException("Author search failed after retries", query)
    }

    /**
     * Get author by Semantic Scholar author ID
     * Includes their recent papers
     */
    suspend fun getAuthorById(authorId: String, includePapers: Boolean = true): SemanticScholarAuthorDetail? {
        val fields = if (includePapers) {
            "$AUTHOR_FIELDS,papers.$AUTHOR_PAPERS_FIELDS"
        } else {
            AUTHOR_FIELDS
        }

        var lastException: Exception? = null
        var delayMs = 1000L

        repeat(3) { attempt ->
            try {
                val response = httpClient.get("$AUTHOR_URL/$authorId") {
                    addApiKeyIfAvailable()
                    parameter("fields", fields)
                }

                if (response.status.value == 429) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                    return@repeat
                }

                if (!response.status.isSuccess()) {
                    return null
                }

                return response.body<SemanticScholarAuthorDetail>()
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        return null
    }

    /**
     * Find best matching author for a given name
     * Optionally filter by affiliation hint
     */
    suspend fun findAuthor(name: String, affiliationHint: String? = null): SemanticScholarAuthorDetail? {
        val results = searchAuthors(name, limit = 5)
        if (results.isEmpty()) return null

        // If no affiliation hint, return first result
        if (affiliationHint == null) return results.first()

        // Try to match by affiliation
        val affiliationLower = affiliationHint.lowercase()
        return results.find { author ->
            author.affiliations.any { it.lowercase().contains(affiliationLower) }
        } ?: results.first()
    }
}

/**
 * Semantic Scholar API response for search
 */
@Serializable
data class SemanticScholarSearchResponse(
    val total: Int,
    val data: List<SemanticScholarPaper>
)

/**
 * Paper data from Semantic Scholar
 */
@Serializable
data class SemanticScholarPaper(
    val paperId: String,
    val title: String,
    val authors: List<SemanticScholarAuthor> = emptyList(),
    val year: Int? = null,
    val venue: String? = null,
    val citationCount: Int? = null,
    val abstract: String? = null,
    val externalIds: SemanticScholarExternalIds? = null,
    val openAccessPdf: SemanticScholarOpenAccessPdf? = null,
    val publicationDate: String? = null
) {
    /**
     * Convert to ResolvedMetadata for uniform handling
     */
    fun toResolvedMetadata(): ResolvedMetadata {
        return ResolvedMetadata(
            title = title,
            authors = authors.map { ResolvedAuthor(name = it.name) },
            abstract = abstract,
            doi = externalIds?.doi,
            arxivId = externalIds?.arxivId,
            year = year,
            venue = venue,
            pdfUrl = openAccessPdf?.url,
            citationsCount = citationCount
        )
    }

    /**
     * Convert to SearchResult for frontend display
     */
    fun toSearchResult(): SearchResult {
        return SearchResult(
            id = paperId,
            title = title,
            authors = authors.map { it.name },
            year = year,
            venue = venue,
            citationCount = citationCount,
            abstract = abstract,
            pdfUrl = openAccessPdf?.url,
            doi = externalIds?.doi,
            arxivId = externalIds?.arxivId,
            source = "semantic_scholar"
        )
    }
}

@Serializable
data class SemanticScholarAuthor(
    val authorId: String? = null,
    val name: String
)

/**
 * Detailed author information from Semantic Scholar Author API
 */
@Serializable
data class SemanticScholarAuthorDetail(
    val authorId: String,
    val name: String,
    val affiliations: List<String> = emptyList(),
    val paperCount: Int? = null,
    val citationCount: Int? = null,
    val hIndex: Int? = null,
    val homepage: String? = null,
    val externalIds: SemanticScholarAuthorExternalIds? = null,
    val papers: List<SemanticScholarPaper> = emptyList()
)

@Serializable
data class SemanticScholarAuthorExternalIds(
    @SerialName("ORCID") val orcid: String? = null,
    @SerialName("DBLP") val dblp: List<String>? = null
)

/**
 * Search response for author search
 */
@Serializable
data class SemanticScholarAuthorSearchResponse(
    val total: Int,
    val data: List<SemanticScholarAuthorDetail>
)

@Serializable
data class SemanticScholarExternalIds(
    @SerialName("DOI") val doi: String? = null,
    @SerialName("ArXiv") val arxivId: String? = null,
    @SerialName("PubMed") val pubMedId: String? = null,
    @SerialName("CorpusId") val corpusId: String? = null
)

@Serializable
data class SemanticScholarOpenAccessPdf(
    val url: String? = null,
    val status: String? = null
)

/**
 * Author info for search results
 */
@Serializable
data class SearchResultAuthor(
    val name: String,
    val semanticScholarId: String? = null
) {
    val semanticScholarUrl: String?
        get() = semanticScholarId?.let { "https://www.semanticscholar.org/author/$it" }
}

/**
 * Common search result type for frontend
 */
@Serializable
data class SearchResult(
    val id: String,
    val title: String,
    val authors: List<String>,
    val authorDetails: List<SearchResultAuthor> = emptyList(),
    val year: Int?,
    val venue: String?,
    val citationCount: Int?,
    val abstract: String?,
    val pdfUrl: String?,
    val doi: String?,
    val arxivId: String?,
    val source: String // "semantic_scholar", "google_scholar", etc.
)
