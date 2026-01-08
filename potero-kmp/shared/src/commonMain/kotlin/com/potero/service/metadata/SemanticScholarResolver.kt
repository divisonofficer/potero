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
 */
class SemanticScholarResolver(
    private val httpClient: HttpClient
) : MetadataResolver {

    companion object {
        private const val BASE_URL = "https://api.semanticscholar.org/graph/v1"
        private const val SEARCH_URL = "$BASE_URL/paper/search"
        private const val PAPER_URL = "$BASE_URL/paper"

        // Fields to request from API
        private const val PAPER_FIELDS = "paperId,title,authors,year,venue,citationCount,abstract,externalIds,openAccessPdf,publicationDate"
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
     */
    suspend fun search(query: String, limit: Int = 10): List<SemanticScholarPaper> {
        val response = httpClient.get(SEARCH_URL) {
            parameter("query", query)
            parameter("limit", limit)
            parameter("fields", PAPER_FIELDS)
        }

        if (!response.status.isSuccess()) {
            throw MetadataResolutionException(
                "Semantic Scholar API error: ${response.status}",
                query
            )
        }

        val searchResponse = response.body<SemanticScholarSearchResponse>()
        return searchResponse.data
    }

    /**
     * Get paper by Semantic Scholar paper ID
     */
    suspend fun getByPaperId(paperId: String): SemanticScholarPaper? {
        val response = httpClient.get("$PAPER_URL/$paperId") {
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
            parameter("fields", PAPER_FIELDS)
        }

        if (!response.status.isSuccess()) {
            return null
        }

        return response.body<SemanticScholarPaper>()
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
 * Common search result type for frontend
 */
@Serializable
data class SearchResult(
    val id: String,
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val venue: String?,
    val citationCount: Int?,
    val abstract: String?,
    val pdfUrl: String?,
    val doi: String?,
    val arxivId: String?,
    val source: String // "semantic_scholar", "google_scholar", etc.
)
