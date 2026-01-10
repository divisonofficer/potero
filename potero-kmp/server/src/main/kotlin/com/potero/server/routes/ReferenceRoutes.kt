package com.potero.server.routes

import com.potero.domain.model.Reference
import com.potero.server.di.ServiceLocator
import com.potero.service.pdf.PdfAnalyzer
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/**
 * DTO for Reference
 */
@Serializable
data class ReferenceDto(
    val id: String,
    val paperId: String,
    val number: Int,
    val rawText: String,
    val authors: String?,
    val title: String?,
    val venue: String?,
    val year: Int?,
    val doi: String?,
    val pageNum: Int,
    val searchQuery: String
)

/**
 * Response for references list
 */
@Serializable
data class ReferencesResponse(
    val paperId: String,
    val referencesStartPage: Int?,
    val references: List<ReferenceDto>,
    val totalCount: Int
)

/**
 * Convert domain Reference to DTO
 */
fun Reference.toDto(): ReferenceDto = ReferenceDto(
    id = id,
    paperId = paperId,
    number = number,
    rawText = rawText,
    authors = authors,
    title = title,
    venue = venue,
    year = year,
    doi = doi,
    pageNum = pageNum,
    searchQuery = searchQuery
)

/**
 * DTO for GrobidReference
 */
@Serializable
data class GrobidReferenceDto(
    val id: String,
    val paperId: String,
    val xmlId: String,
    val authors: String?,
    val title: String?,
    val venue: String?,
    val year: Int?,
    val doi: String?,
    val arxivId: String?,
    val pageNum: Int?,
    val confidence: Double
)

/**
 * Convert domain GrobidReference to DTO
 */
fun com.potero.domain.model.GrobidReference.toDto(): GrobidReferenceDto = GrobidReferenceDto(
    id = id,
    paperId = paperId,
    xmlId = xmlId,
    authors = authors,
    title = title,
    venue = venue,
    year = year,
    doi = doi,
    arxivId = arxivId,
    pageNum = pageNum,
    confidence = confidence
)

/**
 * Routes for Reference operations
 */
fun Route.referenceRoutes() {
    val referenceRepository = ServiceLocator.referenceRepository
    val paperRepository = ServiceLocator.paperRepository
    val grobidRepository = ServiceLocator.grobidRepository

    // GET /api/papers/{paperId}/grobid-references - Get GROBID-extracted references
    route("/papers/{paperId}/grobid-references") {
        get {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = grobidRepository.getReferencesByPaperId(paperId)
            result.fold(
                onSuccess = { references ->
                    call.respond(ApiResponse(data = references.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<GrobidReferenceDto>>(
                            success = false,
                            error = error.message ?: "Failed to get GROBID references"
                        )
                    )
                }
            )
        }
    }

    route("/papers/{paperId}/references") {
        // GET /api/papers/{paperId}/references - Get all references for a paper
        get {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = referenceRepository.getByPaperId(paperId)
            result.fold(
                onSuccess = { references ->
                    call.respond(
                        ApiResponse(
                            data = ReferencesResponse(
                                paperId = paperId,
                                referencesStartPage = references.firstOrNull()?.pageNum,
                                references = references.map { it.toDto() },
                                totalCount = references.size
                            )
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ReferencesResponse>(
                            success = false,
                            error = error.message ?: "Failed to get references"
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/references/{number} - Get a specific reference by number
        get("/{number}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")
            val number = call.parameters["number"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid reference number")

            val result = referenceRepository.getByPaperIdAndNumber(paperId, number)
            result.fold(
                onSuccess = { reference ->
                    if (reference != null) {
                        call.respond(ApiResponse(data = reference.toDto()))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<ReferenceDto>(
                                success = false,
                                error = "Reference $number not found"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ReferenceDto>(
                            success = false,
                            error = error.message ?: "Failed to get reference"
                        )
                    )
                }
            )
        }

        // POST /api/papers/{paperId}/references/analyze - Analyze and extract references from PDF
        post("/analyze") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            // Get paper to find PDF path
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ReferencesResponse>(
                        success = false,
                        error = "Paper not found: $paperId"
                    )
                )
                return@post
            }

            val pdfPath = paper.pdfPath
            if (pdfPath.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ReferencesResponse>(
                        success = false,
                        error = "Paper does not have a PDF file"
                    )
                )
                return@post
            }

            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ReferencesResponse>(
                        success = false,
                        error = "PDF file not found: $pdfPath"
                    )
                )
                return@post
            }

            try {
                // Analyze PDF for references
                val analyzer = PdfAnalyzer(pdfPath)
                val referencesResult = analyzer.analyzeReferences()

                // Delete existing references for this paper
                referenceRepository.deleteByPaperId(paperId)

                // Convert and save new references
                val now = Clock.System.now()
                val references = referencesResult.references.map { parsed ->
                    Reference(
                        id = UUID.randomUUID().toString(),
                        paperId = paperId,
                        number = parsed.number,
                        rawText = parsed.rawText,
                        authors = parsed.authors,
                        title = parsed.title,
                        venue = parsed.venue,
                        year = parsed.year,
                        doi = parsed.doi,
                        pageNum = parsed.pageNum,
                        createdAt = now
                    )
                }

                if (references.isNotEmpty()) {
                    referenceRepository.insertAll(references)
                }

                println("[References] Analyzed paper $paperId: found ${references.size} references starting at page ${referencesResult.startPage}")

                call.respond(
                    ApiResponse(
                        data = ReferencesResponse(
                            paperId = paperId,
                            referencesStartPage = referencesResult.startPage,
                            references = references.map { it.toDto() },
                            totalCount = references.size
                        )
                    )
                )
            } catch (e: Exception) {
                println("[References] Analysis failed for paper $paperId: ${e.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<ReferencesResponse>(
                        success = false,
                        error = "Failed to analyze references: ${e.message}"
                    )
                )
            }
        }

        // DELETE /api/papers/{paperId}/references - Delete all references for a paper
        delete {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = referenceRepository.deleteByPaperId(paperId)
            result.fold(
                onSuccess = {
                    call.respond(ApiResponse(data = mapOf("deleted" to true)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, Boolean>>(
                            success = false,
                            error = error.message ?: "Failed to delete references"
                        )
                    )
                }
            )
        }
    }

    // Search references across all papers
    route("/references") {
        // GET /api/references/search?q=query&type=title|authors
        get("/search") {
            val query = call.request.queryParameters["q"] ?: ""
            val type = call.request.queryParameters["type"] ?: "title"

            if (query.isBlank()) {
                call.respond(ApiResponse(data = emptyList<ReferenceDto>()))
                return@get
            }

            val result = when (type) {
                "authors" -> referenceRepository.searchByAuthors(query)
                else -> referenceRepository.searchByTitle(query)
            }

            result.fold(
                onSuccess = { references ->
                    call.respond(ApiResponse(data = references.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<ReferenceDto>>(
                            success = false,
                            error = error.message ?: "Search failed"
                        )
                    )
                }
            )
        }
    }
}
