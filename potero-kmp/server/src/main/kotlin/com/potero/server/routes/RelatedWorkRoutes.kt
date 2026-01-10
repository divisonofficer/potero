package com.potero.server.routes

import com.potero.domain.model.*
import com.potero.server.di.ServiceLocator
import com.potero.service.relatedwork.ComparisonTableRequest
import com.potero.service.relatedwork.ColumnDefinition
import com.potero.service.relatedwork.RelatedPaperCandidate
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

// ===== Request DTOs =====

@Serializable
data class FindRelatedPapersRequest(
    val limit: Int = 20,
    val forceRefresh: Boolean = false
)

@Serializable
data class GenerateComparisonTableRequest(
    val relatedPaperIds: List<String>,
    val title: String,
    val description: String? = null,
    val columns: List<ColumnDefinitionDto> = emptyList(),
    val generateNarrative: Boolean = true
)

@Serializable
data class ColumnDefinitionDto(
    val name: String,
    val description: String?,
    val dataType: String
)

// ===== Response DTOs =====

@Serializable
data class RelatedPaperCandidateDto(
    val paperId: String,
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val doi: String?,
    val abstract: String?,
    val citationsCount: Int,
    val relationshipType: String,
    val relevanceScore: Double,
    val source: String,
    val reasoning: String?
)

@Serializable
data class ComparisonTableDto(
    val id: String,
    val sourcePaperId: String,
    val title: String,
    val description: String?,
    val columns: List<ComparisonColumnDto>,
    val generationMethod: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ComparisonColumnDto(
    val id: String,
    val name: String,
    val description: String?,
    val dataType: String,
    val order: Int
)

@Serializable
data class ComparisonEntryDto(
    val id: String,
    val paperId: String,
    val columnId: String,
    val value: String,
    val confidence: Double?,
    val extractionSource: String?
)

@Serializable
data class ComparisonNarrativeDto(
    val id: String,
    val content: String,
    val keyInsights: List<String>
)

@Serializable
data class ComparisonTableWithDataDto(
    val table: ComparisonTableDto,
    val entries: Map<String, Map<String, ComparisonEntryDto>>,
    val papers: List<PaperDto>,
    val narrative: ComparisonNarrativeDto?
)

// ===== Extension Functions for Conversion =====

fun RelatedPaperCandidate.toDto(): RelatedPaperCandidateDto = RelatedPaperCandidateDto(
    paperId = paperId,
    title = title,
    authors = authors,
    year = year,
    doi = doi,
    abstract = abstract,
    citationsCount = citationsCount,
    relationshipType = relationshipType.name,
    relevanceScore = relevanceScore,
    source = source.name,
    reasoning = reasoning
)

fun ComparisonTable.toDto(): ComparisonTableDto = ComparisonTableDto(
    id = id,
    sourcePaperId = sourcePaperId,
    title = title,
    description = description,
    columns = columns.map { it.toDto() },
    generationMethod = generationMethod.name,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds()
)

fun ComparisonColumn.toDto(): ComparisonColumnDto = ComparisonColumnDto(
    id = id,
    name = name,
    description = description,
    dataType = dataType.name,
    order = order
)

fun ComparisonEntry.toDto(): ComparisonEntryDto = ComparisonEntryDto(
    id = id,
    paperId = paperId,
    columnId = columnId,
    value = value,
    confidence = confidence,
    extractionSource = extractionSource
)

fun ComparisonNarrative.toDto(): ComparisonNarrativeDto = ComparisonNarrativeDto(
    id = id,
    content = content,
    keyInsights = keyInsights
)

fun ComparisonTableWithData.toDto(): ComparisonTableWithDataDto = ComparisonTableWithDataDto(
    table = table.toDto(),
    entries = entries.mapValues { (_, columnMap) ->
        columnMap.mapValues { (_, entry) -> entry.toDto() }
    },
    papers = papers.map { it.toDto() },
    narrative = narrative?.toDto()
)

fun GenerateComparisonTableRequest.toServiceRequest(): ComparisonTableRequest {
    return ComparisonTableRequest(
        title = title,
        description = description,
        columns = columns.map { columnDto ->
            ColumnDefinition(
                name = columnDto.name,
                description = columnDto.description,
                dataType = ColumnDataType.valueOf(columnDto.dataType.uppercase())
            )
        },
        generateNarrative = generateNarrative
    )
}

// ===== Routes =====

fun Route.relatedWorkRoutes() {
    val relatedWorkService = ServiceLocator.relatedWorkService
    val comparisonService = ServiceLocator.comparisonService

    route("/papers/{paperId}/related") {

        // POST /api/papers/{paperId}/related/find
        // Find and rank related papers
        post("/find") {
            val paperId = call.parameters["paperId"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<List<RelatedPaperCandidateDto>>(
                        success = false,
                        error = "Missing paperId"
                    )
                )

            val request = try {
                call.receive<FindRelatedPapersRequest>()
            } catch (e: Exception) {
                FindRelatedPapersRequest()
            }

            relatedWorkService.findRelatedPapers(
                sourcePaperId = paperId,
                limit = request.limit,
                forceRefresh = request.forceRefresh
            ).fold(
                onSuccess = { candidates ->
                    call.respond(
                        ApiResponse(data = candidates.map { it.toDto() })
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<RelatedPaperCandidateDto>>(
                            success = false,
                            error = error.message
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/related
        // Get cached related papers
        get {
            val paperId = call.parameters["paperId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<List<RelatedWork>>(success = false, error = "Missing paperId")
                )

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

            relatedWorkService.getRelatedPapers(paperId, limit).fold(
                onSuccess = { relations ->
                    call.respond(ApiResponse(data = relations))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<RelatedWork>>(success = false, error = error.message)
                    )
                }
            )
        }
    }

    route("/papers/{paperId}/comparisons") {

        // POST /api/papers/{paperId}/comparisons/generate
        // Generate comparison table
        post("/generate") {
            val paperId = call.parameters["paperId"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ComparisonTableWithDataDto>(
                        success = false,
                        error = "Missing paperId"
                    )
                )

            val request = try {
                call.receive<GenerateComparisonTableRequest>()
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ComparisonTableWithDataDto>(
                        success = false,
                        error = "Invalid request body: ${e.message}"
                    )
                )
            }

            comparisonService.generateComparisonTable(
                sourcePaperId = paperId,
                relatedPaperIds = request.relatedPaperIds,
                request = request.toServiceRequest()
            ).fold(
                onSuccess = { tableWithData ->
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse(data = tableWithData.toDto())
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ComparisonTableWithDataDto>(
                            success = false,
                            error = error.message
                        )
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/comparisons
        // List all comparison tables for a paper
        get {
            val paperId = call.parameters["paperId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<List<ComparisonTableDto>>(success = false, error = "Missing paperId")
                )

            comparisonService.getTablesBySourcePaper(paperId).fold(
                onSuccess = { tables ->
                    call.respond(
                        ApiResponse(data = tables.map { it.toDto() })
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<ComparisonTableDto>>(success = false, error = error.message)
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/comparisons/{tableId}
        // Get specific comparison table with all data
        get("/{tableId}") {
            val tableId = call.parameters["tableId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ComparisonTableWithDataDto>(
                        success = false,
                        error = "Missing tableId"
                    )
                )

            comparisonService.getTableWithData(tableId).fold(
                onSuccess = { tableWithData ->
                    if (tableWithData != null) {
                        call.respond(
                            ApiResponse(data = tableWithData.toDto())
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<ComparisonTableWithDataDto>(
                                success = false,
                                error = "Table not found"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<ComparisonTableWithDataDto>(
                            success = false,
                            error = error.message
                        )
                    )
                }
            )
        }

        // DELETE /api/papers/{paperId}/comparisons/{tableId}
        // Delete comparison table
        delete("/{tableId}") {
            val tableId = call.parameters["tableId"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Map<String, Boolean>>(success = false, error = "Missing tableId")
                )

            comparisonService.deleteTable(tableId).fold(
                onSuccess = {
                    call.respond(
                        ApiResponse(data = mapOf("deleted" to true))
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, Boolean>>(success = false, error = error.message)
                    )
                }
            )
        }
    }
}
