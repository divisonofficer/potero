package com.potero.server.routes

import com.potero.domain.model.Paper
import com.potero.server.di.ServiceLocator
import com.potero.service.metadata.SearchResult
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String,
    val fields: List<String> = listOf("title", "authors", "abstract"),
    val filters: SearchFilters? = null,
    val sort: String = "relevance",
    val page: Int = 1,
    val limit: Int = 20,
    val highlight: Boolean = true
)

@Serializable
data class SearchFilters(
    val year: YearRange? = null,
    val conference: List<String>? = null,
    val subject: List<String>? = null
)

@Serializable
data class YearRange(
    val min: Int? = null,
    val max: Int? = null
)

@Serializable
data class SearchResultDto(
    val id: String,
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val conference: String?,
    val subject: List<String>,
    val abstract: String?,
    val thumbnailUrl: String?,
    val citations: Int,
    val score: Double,
    val highlights: Map<String, String>? = null
)

@Serializable
data class SearchResponseDto(
    val results: List<SearchResultDto>,
    val pagination: PaginationDto,
    val facets: FacetsDto,
    val queryTime: Long
)

@Serializable
data class PaginationDto(
    val page: Int,
    val limit: Int,
    val totalPages: Int,
    val totalItems: Int
)

@Serializable
data class FacetsDto(
    val conferences: List<FacetItem>,
    val years: List<FacetItem>,
    val subjects: List<FacetItem>
)

@Serializable
data class FacetItem(
    val value: String,
    val count: Int
)

@Serializable
data class SuggestionDto(
    val type: String, // "title", "author", "subject", "query"
    val text: String,
    val paperId: String? = null,
    val count: Int? = null
)

// Extension to convert Paper to SearchResultDto
fun Paper.toSearchResult(score: Double = 1.0, highlights: Map<String, String>? = null): SearchResultDto =
    SearchResultDto(
        id = id,
        title = title,
        authors = authors.map { it.name },
        year = year,
        conference = conference,
        subject = tags.map { it.name },
        abstract = abstract,
        thumbnailUrl = thumbnailPath,
        citations = citationsCount,
        score = score,
        highlights = highlights
    )

/**
 * DTO for online search result (from Semantic Scholar, etc.)
 */
@Serializable
data class OnlineSearchResultDto(
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
    val source: String
)

// Simple in-memory cache for search results
private data class CachedSearch(
    val results: List<OnlineSearchResultDto>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long = 5 * 60 * 1000): Boolean = // 5 minutes TTL
        System.currentTimeMillis() - timestamp > ttlMs
}

private val searchCache = mutableMapOf<String, CachedSearch>()
private var lastSearchTime = 0L
private const val MIN_SEARCH_INTERVAL_MS = 1000L // Minimum 1 second between searches

fun Route.searchRoutes() {
    val repository = ServiceLocator.paperRepository
    val unifiedSearchService = ServiceLocator.unifiedSearchService

    route("/search") {
        // GET /api/search/online - Online paper search (with automatic fallback)
        get("/online") {
            val query = call.request.queryParameters["q"] ?: ""
            val engine = call.request.queryParameters["engine"] ?: "semantic"

            println("[Search] Online search request: query='$query', engine='$engine'")

            if (query.isBlank()) {
                call.respond(ApiResponse(data = emptyList<OnlineSearchResultDto>()))
                return@get
            }

            try {
                // Use UnifiedSearchService which handles caching, throttling, and fallback
                val result = unifiedSearchService.search(
                    query = query,
                    preferredEngine = engine,
                    limit = 10
                )

                val dtos: List<OnlineSearchResultDto> = result.results.map { searchResult ->
                    OnlineSearchResultDto(
                        id = searchResult.id,
                        title = searchResult.title,
                        authors = searchResult.authors,
                        year = searchResult.year,
                        venue = searchResult.venue,
                        citationCount = searchResult.citationCount,
                        abstract = searchResult.abstract,
                        pdfUrl = searchResult.pdfUrl,
                        doi = searchResult.doi,
                        arxivId = searchResult.arxivId,
                        source = searchResult.source
                    )
                }

                println("[Search] Found ${dtos.size} results from ${result.source}" +
                    (if (result.fromCache) " (cached)" else "") +
                    (if (result.usedFallback) " (used fallback)" else ""))

                if (result.error != null) {
                    call.respond(ApiResponse<List<OnlineSearchResultDto>>(
                        success = false,
                        error = result.error
                    ))
                } else {
                    call.respond(ApiResponse<List<OnlineSearchResultDto>>(data = dtos))
                }
            } catch (e: Exception) {
                println("[Search] Search failed: ${e.message}")
                call.respond(ApiResponse<List<OnlineSearchResultDto>>(
                    success = false,
                    error = "Search failed: ${e.message}. Please try again later."
                ))
            }
        }

        // GET /api/search/status - Get search service status
        get("/status") {
            try {
                val status = unifiedSearchService.getStatus()
                call.respond(ApiResponse(data = mapOf(
                    "cacheEntries" to status.cacheEntries,
                    "semanticScholarAvailable" to status.semanticScholarAvailable,
                    "googleScholarAvailable" to status.googleScholarAvailable,
                    "semanticScholarFailures" to status.semanticScholarFailures,
                    "googleScholarFailures" to status.googleScholarFailures
                )))
            } catch (e: Exception) {
                call.respond(ApiResponse<Map<String, Any>>(
                    success = false,
                    error = e.message ?: "Failed to get status"
                ))
            }
        }

        // POST /api/search - Full-text search
        post {
            val request = call.receive<SearchRequest>()
            val startTime = System.currentTimeMillis()

            // Search by title (simple implementation for now)
            val searchResult = repository.searchByTitle(request.query)

            searchResult.fold(
                onSuccess = { papers ->
                    // Apply filters
                    var filteredPapers = papers

                    request.filters?.let { filters ->
                        // Filter by year range
                        filters.year?.let { yearRange ->
                            filteredPapers = filteredPapers.filter { paper ->
                                paper.year?.let { year ->
                                    (yearRange.min == null || year >= yearRange.min) &&
                                    (yearRange.max == null || year <= yearRange.max)
                                } ?: true
                            }
                        }

                        // Filter by conference
                        filters.conference?.let { conferences ->
                            if (conferences.isNotEmpty()) {
                                filteredPapers = filteredPapers.filter { paper ->
                                    paper.conference?.let { it in conferences } ?: false
                                }
                            }
                        }

                        // Filter by subject/tags
                        filters.subject?.let { subjects ->
                            if (subjects.isNotEmpty()) {
                                filteredPapers = filteredPapers.filter { paper ->
                                    paper.tags.any { it.name in subjects }
                                }
                            }
                        }
                    }

                    // Pagination
                    val offset = (request.page - 1) * request.limit
                    val paginatedPapers = filteredPapers.drop(offset).take(request.limit)
                    val totalPages = (filteredPapers.size + request.limit - 1) / request.limit

                    // Calculate facets from all papers
                    val allPapers = repository.getAll().getOrDefault(emptyList())
                    val conferences = allPapers
                        .mapNotNull { it.conference }
                        .groupingBy { it }
                        .eachCount()
                        .map { FacetItem(it.key, it.value) }
                        .sortedByDescending { it.count }
                        .take(10)

                    val years = allPapers
                        .mapNotNull { it.year }
                        .groupingBy { it.toString() }
                        .eachCount()
                        .map { FacetItem(it.key, it.value) }
                        .sortedByDescending { it.value }
                        .take(10)

                    val subjects = allPapers
                        .flatMap { it.tags }
                        .groupingBy { it.name }
                        .eachCount()
                        .map { FacetItem(it.key, it.value) }
                        .sortedByDescending { it.count }
                        .take(10)

                    val queryTime = System.currentTimeMillis() - startTime

                    // Create search results with highlighting
                    val results = paginatedPapers.map { paper ->
                        val highlights: Map<String, String>? = if (request.highlight) {
                            val highlightMap = mutableMapOf<String, String>()
                            val query = request.query.lowercase()
                            if (paper.title.lowercase().contains(query)) {
                                highlightMap["title"] = paper.title.replace(
                                    Regex("(?i)${Regex.escape(request.query)}"),
                                    "<mark>$0</mark>"
                                )
                            }
                            val abstractText = paper.abstract
                            if (abstractText != null && abstractText.lowercase().contains(query)) {
                                highlightMap["abstract"] = abstractText.replace(
                                    Regex("(?i)${Regex.escape(request.query)}"),
                                    "<mark>$0</mark>"
                                )
                            }
                            highlightMap.takeIf { it.isNotEmpty() }
                        } else null

                        paper.toSearchResult(
                            score = 1.0, // Simple scoring for now
                            highlights = highlights
                        )
                    }

                    val response = SearchResponseDto(
                        results = results,
                        pagination = PaginationDto(
                            page = request.page,
                            limit = request.limit,
                            totalPages = totalPages,
                            totalItems = filteredPapers.size
                        ),
                        facets = FacetsDto(
                            conferences = conferences,
                            years = years,
                            subjects = subjects
                        ),
                        queryTime = queryTime
                    )

                    call.respond(ApiResponse(data = response))
                },
                onFailure = { error ->
                    call.respond(ApiResponse<SearchResponseDto>(
                        success = false,
                        error = error.message ?: "Search failed"
                    ))
                }
            )
        }

        // GET /api/search/suggest - Autocomplete suggestions
        get("/suggest") {
            val query = call.request.queryParameters["q"] ?: ""
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10

            if (query.isBlank()) {
                call.respond(ApiResponse(data = emptyList<SuggestionDto>()))
                return@get
            }

            val allPapers = repository.getAll().getOrDefault(emptyList())
            val queryLower = query.lowercase()

            val suggestions = mutableListOf<SuggestionDto>()

            // Title suggestions
            allPapers
                .filter { it.title.lowercase().contains(queryLower) }
                .take(3)
                .forEach { paper ->
                    suggestions.add(SuggestionDto(
                        type = "title",
                        text = paper.title,
                        paperId = paper.id
                    ))
                }

            // Author suggestions
            val authorMatches = allPapers
                .flatMap { it.authors }
                .filter { it.name.lowercase().contains(queryLower) }
                .groupingBy { it.name }
                .eachCount()

            authorMatches.entries
                .sortedByDescending { it.value }
                .take(3)
                .forEach { (name, count) ->
                    suggestions.add(SuggestionDto(
                        type = "author",
                        text = name,
                        count = count
                    ))
                }

            // Subject/tag suggestions
            val tagMatches = allPapers
                .flatMap { it.tags }
                .filter { it.name.lowercase().contains(queryLower) }
                .groupingBy { it.name }
                .eachCount()

            tagMatches.entries
                .sortedByDescending { it.value }
                .take(3)
                .forEach { (name, count) ->
                    suggestions.add(SuggestionDto(
                        type = "subject",
                        text = name,
                        count = count
                    ))
                }

            call.respond(ApiResponse(data = suggestions.take(limit)))
        }
    }
}
