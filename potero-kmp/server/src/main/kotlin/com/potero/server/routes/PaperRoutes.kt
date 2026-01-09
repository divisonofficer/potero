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
import java.io.File
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
    val arxivId: String? = null,
    val pdfUrl: String? = null,  // For auto-download from online search
    val citations: Int? = null   // For preserving citation count from search
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
        citationsCount = citations ?: 0,  // NEW: Preserve citation count from search
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

        // POST /api/papers - Create paper manually (or from online search)
        post {
            val request = call.receive<CreatePaperRequest>()
            var paper = request.toPaper()

            // Insert paper first
            val insertResult = repository.insert(paper)
            val insertedPaper = insertResult.getOrElse {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<PaperDto>(
                        success = false,
                        error = it.message ?: "Failed to create paper"
                    )
                )
                return@post
            }

            // NEW: Auto-download PDF if pdfUrl is provided (from online search)
            if (request.pdfUrl != null) {
                try {
                    println("[Paper Create] Attempting to download PDF from: ${request.pdfUrl}")

                    val pdfDownloadService = ServiceLocator.pdfDownloadService
                    val pdfPath = pdfDownloadService.downloadPdf(
                        paper = insertedPaper,
                        directUrl = request.pdfUrl
                    ).getOrNull()

                    if (pdfPath != null) {
                        // Update paper with PDF path
                        paper = insertedPaper.copy(pdfPath = pdfPath)
                        repository.update(paper)

                        // Generate thumbnail
                        val thumbnailExtractor = ServiceLocator.pdfThumbnailExtractor
                        val thumbnailDir = File(System.getProperty("user.home"), ".potero/thumbnails")
                        thumbnailDir.mkdirs()
                        val thumbnailOutputPath = File(thumbnailDir, "${insertedPaper.id}.jpg").absolutePath

                        val generatedPath = thumbnailExtractor.extractThumbnail(pdfPath, thumbnailOutputPath)
                        if (generatedPath != null) {
                            paper = paper.copy(thumbnailPath = generatedPath)
                            repository.update(paper)
                        }

                        println("[Paper Create] PDF downloaded successfully: $pdfPath")
                    } else {
                        println("[Paper Create] PDF download failed, continuing without PDF")
                    }
                } catch (e: Exception) {
                    println("[Paper Create] PDF download error: ${e.message}")
                    // Continue without PDF - not a critical error
                }
            }

            // Return final paper (with or without PDF)
            call.respond(HttpStatusCode.Created, ApiResponse(data = paper.toDto()))
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

        // GET /api/papers/find/doi - Find paper by DOI (returns null if not found, doesn't create)
        get("/find/doi") {
            val doi = call.request.queryParameters["doi"]
            if (doi.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<PaperDto>(success = false, error = "Missing doi parameter")
                )
                return@get
            }

            val result = repository.getByDoi(doi)
            result.fold(
                onSuccess = { paper ->
                    if (paper != null) {
                        call.respond(ApiResponse(data = paper.toDto()))
                    } else {
                        call.respond(ApiResponse<PaperDto>(data = null))
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(success = false, error = error.message ?: "Search failed")
                    )
                }
            )
        }

        // GET /api/papers/find/arxiv - Find paper by arXiv ID (returns null if not found, doesn't create)
        get("/find/arxiv") {
            val arxivId = call.request.queryParameters["arxivId"]
            if (arxivId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<PaperDto>(success = false, error = "Missing arxivId parameter")
                )
                return@get
            }

            val result = repository.getByArxivId(arxivId)
            result.fold(
                onSuccess = { paper ->
                    if (paper != null) {
                        call.respond(ApiResponse(data = paper.toDto()))
                    } else {
                        call.respond(ApiResponse<PaperDto>(data = null))
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<PaperDto>(success = false, error = error.message ?: "Search failed")
                    )
                }
            )
        }

        // POST /api/papers/{id}/download-pdf - Download PDF from online sources
        post("/{id}/download-pdf") {
            val paperId = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val paper = repository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Map<String, String>>(
                        success = false,
                        error = "Paper not found"
                    )
                )
                return@post
            }

            // Check if PDF already exists
            if (paper.pdfPath != null) {
                call.respond(
                    ApiResponse(data = mapOf(
                        "pdfPath" to paper.pdfPath,
                        "message" to "PDF already exists"
                    ))
                )
                return@post
            }

            // Try to download PDF
            val pdfDownloadService = ServiceLocator.pdfDownloadService
            val pdfPath = pdfDownloadService.downloadPdf(paper).getOrNull()

            if (pdfPath != null) {
                // Update paper with PDF path
                var updatedPaper = paper.copy(pdfPath = pdfPath)
                repository.update(updatedPaper)

                // Generate thumbnail
                val thumbnailExtractor = ServiceLocator.pdfThumbnailExtractor
                val thumbnailDir = File(System.getProperty("user.home"), ".potero/thumbnails")
                thumbnailDir.mkdirs()
                val thumbnailOutputPath = File(thumbnailDir, "$paperId.jpg").absolutePath

                val generatedPath = thumbnailExtractor.extractThumbnail(pdfPath, thumbnailOutputPath)
                if (generatedPath != null) {
                    updatedPaper = updatedPaper.copy(thumbnailPath = generatedPath)
                    repository.update(updatedPaper)
                }

                call.respond(
                    ApiResponse(data = mapOf(
                        "pdfPath" to pdfPath,
                        "message" to "PDF downloaded successfully"
                    ))
                )
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Map<String, String>>(
                        success = false,
                        error = "Could not download PDF from available sources (Semantic Scholar, arXiv, DOI)"
                    )
                )
            }
        }
    }
}
