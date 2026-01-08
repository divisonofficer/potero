package com.potero.server.routes

import com.potero.domain.model.Paper
import com.potero.server.di.ServiceLocator
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

fun Route.searchRoutes() {
    val repository = ServiceLocator.paperRepository

    route("/search") {
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
