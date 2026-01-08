package com.potero.server.routes

import com.potero.server.di.ServiceLocator
import com.potero.service.metadata.SemanticScholarAuthorDetail
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Author profile response combining data from multiple sources
 */
@Serializable
data class AuthorProfileResponse(
    val name: String,
    val affiliations: List<String> = emptyList(),
    val paperCount: Int = 0,
    val citationCount: Int = 0,
    val hIndex: Int? = null,
    val i10Index: Int? = null, // Not available from Semantic Scholar
    val homepage: String? = null,
    val orcid: String? = null,
    val semanticScholarId: String? = null,
    val googleScholarUrl: String? = null,
    val dblpUrl: String? = null,
    val semanticScholarUrl: String? = null
)

/**
 * Author search result
 */
@Serializable
data class AuthorSearchResult(
    val id: String,
    val name: String,
    val affiliations: List<String> = emptyList(),
    val paperCount: Int? = null,
    val citationCount: Int? = null,
    val hIndex: Int? = null
)

fun Route.authorRoutes() {
    val semanticScholarResolver = ServiceLocator.semanticScholarResolver

    route("/authors") {
        // GET /api/authors/search?q=name - Search for authors by name
        get("/search") {
            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<List<AuthorSearchResult>>(
                        success = false,
                        error = "Missing query parameter 'q'"
                    )
                )
                return@get
            }

            try {
                val authors = semanticScholarResolver.searchAuthors(query, limit = 10)
                val results = authors.map { author ->
                    AuthorSearchResult(
                        id = author.authorId,
                        name = author.name,
                        affiliations = author.affiliations,
                        paperCount = author.paperCount,
                        citationCount = author.citationCount,
                        hIndex = author.hIndex
                    )
                }
                call.respond(ApiResponse(data = results))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<AuthorSearchResult>>(
                        success = false,
                        error = "Failed to search authors: ${e.message}"
                    )
                )
            }
        }

        // GET /api/authors/lookup?name=... - Lookup author profile by name
        get("/lookup") {
            val name = call.request.queryParameters["name"]
            val affiliation = call.request.queryParameters["affiliation"]

            if (name.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<AuthorProfileResponse>(
                        success = false,
                        error = "Missing query parameter 'name'"
                    )
                )
                return@get
            }

            try {
                val author = semanticScholarResolver.findAuthor(name, affiliation)
                if (author == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<AuthorProfileResponse>(
                            success = false,
                            error = "Author not found: $name"
                        )
                    )
                    return@get
                }

                val profile = author.toProfileResponse()
                call.respond(ApiResponse(data = profile))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<AuthorProfileResponse>(
                        success = false,
                        error = "Failed to lookup author: ${e.message}"
                    )
                )
            }
        }

        // GET /api/authors/{id} - Get author by Semantic Scholar ID
        get("/{id}") {
            val authorId = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing author ID")

            try {
                val author = semanticScholarResolver.getAuthorById(authorId, includePapers = true)
                if (author == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<AuthorProfileResponse>(
                            success = false,
                            error = "Author not found: $authorId"
                        )
                    )
                    return@get
                }

                val profile = author.toProfileResponse()
                call.respond(ApiResponse(data = profile))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<AuthorProfileResponse>(
                        success = false,
                        error = "Failed to get author: ${e.message}"
                    )
                )
            }
        }
    }
}

/**
 * Convert Semantic Scholar author detail to profile response
 */
private fun SemanticScholarAuthorDetail.toProfileResponse(): AuthorProfileResponse {
    // Build external URLs
    val googleScholarUrl = "https://scholar.google.com/scholar?q=author:\"${name.replace(" ", "+")}\""
    val semanticScholarUrl = "https://www.semanticscholar.org/author/$authorId"
    val dblpUrl = externalIds?.dblp?.firstOrNull()?.let { dblpId ->
        "https://dblp.org/pid/$dblpId.html"
    }

    return AuthorProfileResponse(
        name = name,
        affiliations = affiliations,
        paperCount = paperCount ?: 0,
        citationCount = citationCount ?: 0,
        hIndex = hIndex,
        i10Index = null, // Semantic Scholar doesn't provide i10-index
        homepage = homepage,
        orcid = externalIds?.orcid,
        semanticScholarId = authorId,
        googleScholarUrl = googleScholarUrl,
        dblpUrl = dblpUrl,
        semanticScholarUrl = semanticScholarUrl
    )
}
