package com.potero.server.routes

import com.potero.domain.model.*
import com.potero.server.di.ServiceLocator
import com.potero.service.pdf.CitationExtractor
import com.potero.service.pdf.CitationLinker
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/**
 * DTO for CitationSpan
 */
@Serializable
data class CitationSpanDto(
    val id: String,
    val paperId: String,
    val pageNum: Int,
    val bbox: BoundingBoxDto,
    val rawText: String,
    val style: String,
    val provenance: String,
    val confidence: Double,
    val destPage: Int?,
    val destY: Double?,
    val linkedRefIds: List<String> = emptyList(),
    val linkedReferences: List<LinkedReferenceDto> = emptyList()  // NEW: Full reference info
)

/**
 * DTO for linked reference with full information
 */
@Serializable
data class LinkedReferenceDto(
    val id: String,
    val number: Int,
    val authors: String?,
    val title: String?,
    val venue: String?,
    val year: Int?,
    val doi: String?,
    val searchQuery: String,      // For search engine queries
    val linkMethod: String,        // How it was linked (e.g., "grobid_target", "numeric")
    val linkConfidence: Double     // Confidence of the link
)

/**
 * DTO for BoundingBox
 */
@Serializable
data class BoundingBoxDto(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double
)

/**
 * DTO for CitationLink
 */
@Serializable
data class CitationLinkDto(
    val id: String,
    val citationSpanId: String,
    val referenceId: String,
    val linkMethod: String,
    val confidence: Double
)

/**
 * Response for citation extraction
 */
@Serializable
data class CitationExtractionResponse(
    val paperId: String,
    val spans: List<CitationSpanDto>,
    val links: List<CitationLinkDto>,
    val stats: CitationStatsDto
)

/**
 * Statistics for citation extraction
 */
@Serializable
data class CitationStatsDto(
    val totalSpans: Int,
    val annotationSpans: Int,
    val patternSpans: Int,
    val linkedCount: Int,
    val avgConfidence: Double
)

/**
 * Convert domain CitationSpan to DTO
 */
fun CitationSpan.toDto(
    linkedRefIds: List<String> = emptyList(),
    linkedReferences: List<LinkedReferenceDto> = emptyList()
): CitationSpanDto = CitationSpanDto(
    id = id,
    paperId = paperId,
    pageNum = pageNum,
    bbox = BoundingBoxDto(bbox.x1, bbox.y1, bbox.x2, bbox.y2),
    rawText = rawText,
    style = style.name.lowercase(),
    provenance = provenance.name.lowercase(),
    confidence = confidence,
    destPage = destPage,
    destY = destY,
    linkedRefIds = linkedRefIds,
    linkedReferences = linkedReferences  // NEW
)

/**
 * Convert domain CitationLink to DTO
 */
fun CitationLink.toDto(): CitationLinkDto = CitationLinkDto(
    id = id,
    citationSpanId = citationSpanId,
    referenceId = referenceId,
    linkMethod = linkMethod,
    confidence = confidence
)

/**
 * Routes for Citation operations
 */
fun Route.citationRoutes() {
    val citationRepository = ServiceLocator.citationRepository
    val referenceRepository = ServiceLocator.referenceRepository
    val paperRepository = ServiceLocator.paperRepository

    route("/papers/{paperId}/citations") {
        // GET /api/papers/{paperId}/citations - Get all citation spans for a paper
        get {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val spansResult = citationRepository.getSpansByPaperId(paperId)
            spansResult.fold(
                onSuccess = { spans ->
                    // Fetch all references for paper once (avoid N+1)
                    val allReferences = referenceRepository.getByPaperId(paperId)
                        .getOrDefault(emptyList())
                    val refMap = allReferences.associateBy { it.id }

                    // Build DTOs with full reference info
                    val spansWithLinks = spans.map { span ->
                        val links = citationRepository.getLinksBySpanId(span.id)
                            .getOrDefault(emptyList())

                        val linkedRefs = links.mapNotNull { link ->
                            refMap[link.referenceId]?.let { ref ->
                                LinkedReferenceDto(
                                    id = ref.id,
                                    number = ref.number,
                                    authors = ref.authors,
                                    title = ref.title,
                                    venue = ref.venue,
                                    year = ref.year,
                                    doi = ref.doi,
                                    searchQuery = ref.searchQuery,
                                    linkMethod = link.linkMethod,
                                    linkConfidence = link.confidence
                                )
                            }
                        }

                        span.toDto(
                            linkedRefIds = links.map { it.referenceId },
                            linkedReferences = linkedRefs
                        )
                    }
                    call.respond(ApiResponse(data = spansWithLinks))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<CitationSpanDto>>(
                            success = false,
                            error = error.message ?: "Failed to get citations"
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/citations/page/{pageNum} - Get citations for a specific page
        get("/page/{pageNum}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")
            val pageNum = call.parameters["pageNum"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid page number")

            val spansResult = citationRepository.getSpansByPage(paperId, pageNum)
            spansResult.fold(
                onSuccess = { spans ->
                    val spansWithLinks = spans.map { span ->
                        val links = citationRepository.getLinksBySpanId(span.id).getOrDefault(emptyList())
                        span.toDto(links.map { it.referenceId })
                    }
                    call.respond(ApiResponse(data = spansWithLinks))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<CitationSpanDto>>(
                            success = false,
                            error = error.message ?: "Failed to get citations for page"
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/citations/{spanId} - Get a specific citation span
        get("/{spanId}") {
            val spanId = call.parameters["spanId"]
                ?: throw IllegalArgumentException("Missing span ID")

            val spanResult = citationRepository.getSpanById(spanId)
            spanResult.fold(
                onSuccess = { span ->
                    if (span != null) {
                        val links = citationRepository.getLinksBySpanId(span.id).getOrDefault(emptyList())
                        call.respond(ApiResponse(data = span.toDto(links.map { it.referenceId })))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<CitationSpanDto>(
                                success = false,
                                error = "Citation span not found"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<CitationSpanDto>(
                            success = false,
                            error = error.message ?: "Failed to get citation span"
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/citations/{spanId}/references - Get linked references for a citation
        get("/{spanId}/references") {
            val spanId = call.parameters["spanId"]
                ?: throw IllegalArgumentException("Missing span ID")

            val refsResult = citationRepository.getReferencesForSpan(spanId)
            refsResult.fold(
                onSuccess = { refs ->
                    call.respond(ApiResponse(data = refs.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<ReferenceDto>>(
                            success = false,
                            error = error.message ?: "Failed to get linked references"
                        )
                    )
                }
            )
        }

        // POST /api/papers/{paperId}/citations/extract - Extract citations from PDF
        post("/extract") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            // Get paper to find PDF path
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<CitationExtractionResponse>(
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
                    ApiResponse<CitationExtractionResponse>(
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
                    ApiResponse<CitationExtractionResponse>(
                        success = false,
                        error = "PDF file not found: $pdfPath"
                    )
                )
                return@post
            }

            try {
                // Get existing references for linking
                val references = referenceRepository.getByPaperId(paperId).getOrDefault(emptyList())
                val referencesStartPage = references.minOfOrNull { it.pageNum }

                // Get GROBID data if available (optional - for improved linking)
                val grobidRepository = ServiceLocator.grobidRepository
                val grobidCitations = grobidRepository.getCitationSpansByPaperId(paperId)
                    .getOrDefault(emptyList())
                val grobidReferences = grobidRepository.getReferencesByPaperId(paperId)
                    .getOrDefault(emptyList())

                if (grobidCitations.isNotEmpty()) {
                    println("[Citations] Using GROBID data: ${grobidCitations.size} citations, ${grobidReferences.size} references")
                } else {
                    println("[Citations] No GROBID data available, using PDF-only extraction")
                }

                // Extract citations from PDF
                val extractor = CitationExtractor(pdfPath)
                val extractionResult = extractor.extract()

                // Link citations to references (with GROBID enhancement if available)
                val linker = CitationLinker()
                val linkResults = linker.link(
                    extractionResult.spans,
                    references,
                    referencesStartPage,
                    grobidCitations = grobidCitations,  // NEW: Use GROBID data
                    grobidReferences = grobidReferences  // NEW: Use GROBID data
                )

                // Delete existing citation data for this paper
                citationRepository.deleteSpansByPaperId(paperId)

                // Convert and save citation spans
                val now = Clock.System.now()
                val citationSpans = extractionResult.spans.map { raw ->
                    CitationSpan(
                        id = UUID.randomUUID().toString(),
                        paperId = paperId,
                        pageNum = raw.pageNum,
                        bbox = BoundingBox(
                            x1 = raw.bbox.x1,
                            y1 = raw.bbox.y1,
                            x2 = raw.bbox.x2,
                            y2 = raw.bbox.y2
                        ),
                        rawText = raw.rawText,
                        style = when (raw.style) {
                            "numeric" -> CitationStyle.NUMERIC
                            "author_year" -> CitationStyle.AUTHOR_YEAR
                            else -> CitationStyle.UNKNOWN
                        },
                        provenance = if (raw.provenance == "annotation")
                            CitationProvenance.ANNOTATION
                        else
                            CitationProvenance.PATTERN,
                        confidence = raw.confidence,
                        destPage = raw.destPage,
                        destY = raw.destY,
                        createdAt = now
                    )
                }

                if (citationSpans.isNotEmpty()) {
                    citationRepository.insertAllSpans(citationSpans)
                }

                // Create span ID mapping (raw span -> saved span)
                val rawToSavedMap = extractionResult.spans.zip(citationSpans).toMap()

                // Save citation links
                val citationLinks = linkResults.mapNotNull { linkResult ->
                    val savedSpan = rawToSavedMap[linkResult.span] ?: return@mapNotNull null
                    CitationLink(
                        id = UUID.randomUUID().toString(),
                        citationSpanId = savedSpan.id,
                        referenceId = linkResult.reference.id,
                        linkMethod = linkResult.method,
                        confidence = linkResult.confidence,
                        createdAt = now
                    )
                }

                if (citationLinks.isNotEmpty()) {
                    citationRepository.insertAllLinks(citationLinks)
                }

                // Calculate stats
                val annotationCount = citationSpans.count { it.provenance == CitationProvenance.ANNOTATION }
                val patternCount = citationSpans.count { it.provenance == CitationProvenance.PATTERN }
                val avgConfidence = if (citationSpans.isNotEmpty())
                    citationSpans.map { it.confidence }.average()
                else 0.0

                // Build response with linked reference IDs
                val spanDtos = citationSpans.map { span ->
                    val linkedRefIds = citationLinks
                        .filter { it.citationSpanId == span.id }
                        .map { it.referenceId }
                    span.toDto(linkedRefIds)
                }

                println("[Citations] Extracted ${citationSpans.size} spans (${annotationCount} annotation, ${patternCount} pattern) with ${citationLinks.size} links for paper $paperId")

                call.respond(
                    ApiResponse(
                        data = CitationExtractionResponse(
                            paperId = paperId,
                            spans = spanDtos,
                            links = citationLinks.map { it.toDto() },
                            stats = CitationStatsDto(
                                totalSpans = citationSpans.size,
                                annotationSpans = annotationCount,
                                patternSpans = patternCount,
                                linkedCount = citationLinks.size,
                                avgConfidence = avgConfidence
                            )
                        )
                    )
                )
            } catch (e: Exception) {
                println("[Citations] Extraction failed for paper $paperId: ${e.message}")
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<CitationExtractionResponse>(
                        success = false,
                        error = "Failed to extract citations: ${e.message}"
                    )
                )
            }
        }

        // DELETE /api/papers/{paperId}/citations - Delete all citations for a paper
        delete {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = citationRepository.deleteSpansByPaperId(paperId)
            result.fold(
                onSuccess = {
                    call.respond(ApiResponse(data = mapOf("deleted" to true)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, Boolean>>(
                            success = false,
                            error = error.message ?: "Failed to delete citations"
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/citations/stats - Get citation statistics
        get("/stats") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            try {
                val countResult = citationRepository.countSpansByPaperId(paperId)
                val provenanceResult = citationRepository.countSpansByProvenance(paperId)
                val spansWithLinks = citationRepository.getSpansWithLinkCount(paperId).getOrDefault(emptyList())

                val totalSpans = countResult.getOrDefault(0)
                val provenanceCounts = provenanceResult.getOrDefault(emptyMap())
                val linkedCount = spansWithLinks.count { it.second > 0 }
                val avgConfidence = if (spansWithLinks.isNotEmpty())
                    spansWithLinks.map { it.first.confidence }.average()
                else 0.0

                call.respond(
                    ApiResponse(
                        data = CitationStatsDto(
                            totalSpans = totalSpans,
                            annotationSpans = provenanceCounts["annotation"] ?: 0,
                            patternSpans = provenanceCounts["pattern"] ?: 0,
                            linkedCount = linkedCount,
                            avgConfidence = avgConfidence
                        )
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<CitationStatsDto>(
                        success = false,
                        error = e.message ?: "Failed to get citation stats"
                    )
                )
            }
        }
    }

    // Get citations for a specific reference
    route("/references/{referenceId}/citations") {
        // GET /api/references/{referenceId}/citations - Get citations that link to this reference
        get {
            val referenceId = call.parameters["referenceId"]
                ?: throw IllegalArgumentException("Missing reference ID")

            val spansResult = citationRepository.getSpansForReference(referenceId)
            spansResult.fold(
                onSuccess = { spans ->
                    call.respond(ApiResponse(data = spans.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<CitationSpanDto>>(
                            success = false,
                            error = error.message ?: "Failed to get citations for reference"
                        )
                    )
                }
            )
        }
    }
}
