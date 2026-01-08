package com.potero.server.routes

import com.potero.domain.model.Author
import com.potero.domain.model.Paper
import com.potero.domain.model.Tag
import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val error: String? = null
)

@Serializable
data class PaperDto(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val conference: String? = null,
    val subject: List<String> = emptyList(),
    val abstract: String? = null,
    val pdfUrl: String? = null,
    val thumbnailUrl: String? = null,
    val citations: Int = 0,
    val doi: String? = null,
    val arxivId: String? = null,
    val hasBlogView: Boolean = false
)

@Serializable
data class CreatePaperRequest(
    val title: String,
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val conference: String? = null,
    val subject: List<String> = emptyList(),
    val abstract: String? = null,
    val doi: String? = null,
    val arxivId: String? = null
)

@Serializable
data class ImportByDoiRequest(
    val doi: String
)

@Serializable
data class ImportByArxivRequest(
    val arxivId: String
)

// Extension to convert domain Paper to DTO
fun Paper.toDto(): PaperDto = PaperDto(
    id = id,
    title = title,
    authors = authors.map { it.name },
    year = year,
    conference = conference,
    subject = tags.map { it.name },
    abstract = abstract,
    pdfUrl = pdfPath,
    thumbnailUrl = thumbnailPath,
    citations = citationsCount,
    doi = doi,
    arxivId = arxivId,
    hasBlogView = hasBlogView
)

// Extension to create domain Paper from request
fun CreatePaperRequest.toPaper(): Paper {
    val now = Clock.System.now()
    val paperId = UUID.randomUUID().toString()

    return Paper(
        id = paperId,
        title = title,
        abstract = abstract,
        doi = doi,
        arxivId = arxivId,
        year = year,
        conference = conference,
        authors = authors.mapIndexed { index, name ->
            Author(
                id = UUID.randomUUID().toString(),
                name = name,
                order = index
            )
        },
        tags = subject.map { tagName ->
            Tag(
                id = UUID.randomUUID().toString(),
                name = tagName
            )
        },
        createdAt = now,
        updatedAt = now
    )
}

fun Route.paperRoutes() {
    val repository = ServiceLocator.paperRepository

    route("/papers") {
        // GET /api/papers - List all papers
        get {
            val result = repository.getAll()
            result.fold(
                onSuccess = { papers ->
                    call.respond(ApiResponse(data = papers.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<PaperDto>>(
                            success = false,
                            error = error.message ?: "Failed to fetch papers"
                        )
                    )
                }
            )
        }

        // POST /api/papers - Create paper manually
        post {
            val request = call.receive<CreatePaperRequest>()
            val paper = request.toPaper()

            val result = repository.insert(paper)
            result.fold(
                onSuccess = { insertedPaper ->
                    call.respond(HttpStatusCode.Created, ApiResponse(data = insertedPaper.toDto()))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(
                            success = false,
                            error = error.message ?: "Failed to create paper"
                        )
                    )
                }
            )
        }

        // POST /api/papers/import/doi - Import by DOI
        post("/import/doi") {
            val request = call.receive<ImportByDoiRequest>()

            // Check if paper already exists
            val existing = repository.getByDoi(request.doi).getOrNull()
            if (existing != null) {
                call.respond(ApiResponse(data = existing.toDto()))
                return@post
            }

            // Resolve metadata from DOI
            val metadata = ServiceLocator.resolveMetadata(request.doi)
            if (metadata == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<PaperDto>(
                        success = false,
                        error = "Could not resolve DOI: ${request.doi}"
                    )
                )
                return@post
            }

            // Create paper from resolved metadata
            val now = Clock.System.now()
            val paper = Paper(
                id = UUID.randomUUID().toString(),
                title = metadata.title,
                abstract = metadata.abstract,
                doi = metadata.doi ?: request.doi,
                arxivId = metadata.arxivId,
                year = metadata.year,
                conference = metadata.venue,
                citationsCount = metadata.citationsCount ?: 0,
                pdfPath = metadata.pdfUrl,
                authors = metadata.authors.mapIndexed { index, author ->
                    Author(
                        id = UUID.randomUUID().toString(),
                        name = author.name,
                        affiliation = author.affiliation,
                        order = index
                    )
                },
                tags = metadata.keywords.map { keyword ->
                    Tag(
                        id = UUID.randomUUID().toString(),
                        name = keyword
                    )
                },
                createdAt = now,
                updatedAt = now
            )

            val result = repository.insert(paper)
            result.fold(
                onSuccess = { insertedPaper ->
                    call.respond(HttpStatusCode.Created, ApiResponse(data = insertedPaper.toDto()))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(
                            success = false,
                            error = error.message ?: "Failed to import paper"
                        )
                    )
                }
            )
        }

        // POST /api/papers/import/arxiv - Import by arXiv ID
        post("/import/arxiv") {
            val request = call.receive<ImportByArxivRequest>()

            // Check if paper already exists
            val existing = repository.getByArxivId(request.arxivId).getOrNull()
            if (existing != null) {
                call.respond(ApiResponse(data = existing.toDto()))
                return@post
            }

            // Resolve metadata from arXiv
            val metadata = ServiceLocator.resolveMetadata(request.arxivId)
            if (metadata == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<PaperDto>(
                        success = false,
                        error = "Could not resolve arXiv ID: ${request.arxivId}"
                    )
                )
                return@post
            }

            // Create paper from resolved metadata
            val now = Clock.System.now()
            val paper = Paper(
                id = UUID.randomUUID().toString(),
                title = metadata.title,
                abstract = metadata.abstract,
                doi = metadata.doi,
                arxivId = metadata.arxivId ?: request.arxivId,
                year = metadata.year,
                conference = metadata.venue,
                citationsCount = metadata.citationsCount ?: 0,
                pdfPath = metadata.pdfUrl,
                authors = metadata.authors.mapIndexed { index, author ->
                    Author(
                        id = UUID.randomUUID().toString(),
                        name = author.name,
                        affiliation = author.affiliation,
                        order = index
                    )
                },
                tags = metadata.keywords.map { keyword ->
                    Tag(
                        id = UUID.randomUUID().toString(),
                        name = keyword
                    )
                },
                createdAt = now,
                updatedAt = now
            )

            val result = repository.insert(paper)
            result.fold(
                onSuccess = { insertedPaper ->
                    call.respond(HttpStatusCode.Created, ApiResponse(data = insertedPaper.toDto()))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(
                            success = false,
                            error = error.message ?: "Failed to import paper"
                        )
                    )
                }
            )
        }

        // GET /api/papers/{id} - Get paper by ID
        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = repository.getById(id)
            result.fold(
                onSuccess = { paper ->
                    if (paper != null) {
                        call.respond(ApiResponse(data = paper.toDto()))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<PaperDto>(
                                success = false,
                                error = "Paper not found: $id"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(
                            success = false,
                            error = error.message ?: "Failed to fetch paper"
                        )
                    )
                }
            )
        }

        // PATCH /api/papers/{id} - Update paper
        patch("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val request = call.receive<CreatePaperRequest>()

            // Get existing paper
            val existing = repository.getById(id).getOrNull()
            if (existing == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<PaperDto>(
                        success = false,
                        error = "Paper not found: $id"
                    )
                )
                return@patch
            }

            // Update paper
            val updatedPaper = existing.copy(
                title = request.title,
                abstract = request.abstract,
                doi = request.doi,
                arxivId = request.arxivId,
                year = request.year,
                conference = request.conference,
                authors = request.authors.mapIndexed { index, name ->
                    Author(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        order = index
                    )
                },
                tags = request.subject.map { tagName ->
                    Tag(
                        id = UUID.randomUUID().toString(),
                        name = tagName
                    )
                },
                updatedAt = Clock.System.now()
            )

            val result = repository.update(updatedPaper)
            result.fold(
                onSuccess = { paper ->
                    call.respond(ApiResponse(data = paper.toDto()))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(
                            success = false,
                            error = error.message ?: "Failed to update paper"
                        )
                    )
                }
            )
        }

        // DELETE /api/papers/{id} - Delete paper
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = repository.delete(id)
            result.fold(
                onSuccess = {
                    call.respond(ApiResponse(data = mapOf("deletedId" to id)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, String>>(
                            success = false,
                            error = error.message ?: "Failed to delete paper"
                        )
                    )
                }
            )
        }
    }
}
