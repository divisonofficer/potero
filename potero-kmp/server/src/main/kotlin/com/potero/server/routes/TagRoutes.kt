package com.potero.server.routes

import com.potero.domain.model.Tag
import com.potero.server.di.ServiceLocator
import com.potero.service.job.GlobalJobQueue
import com.potero.service.job.JobType
import com.potero.service.pdf.PdfAnalyzer
import com.potero.service.tag.TagSuggestion
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TagDto(
    val id: String,
    val name: String,
    val color: String = "#6366f1",
    val count: Int = 0
)

@Serializable
data class CreateTagRequest(
    val name: String,
    val color: String = "#6366f1"
)

@Serializable
data class AssignTagsRequest(
    val tags: List<String>
)

@Serializable
data class AutoTagRequest(
    val paperId: String
)

@Serializable
data class AutoTagResponse(
    val paperId: String,
    val suggestedTags: List<TagSuggestionDto>,
    val assignedTags: List<TagDto>
)

@Serializable
data class AutoTagJobResponse(
    val jobId: String,
    val paperId: String,
    val status: String // "processing", "completed", "failed"
)

@Serializable
data class AutoTagJobStatus(
    val jobId: String,
    val paperId: String,
    val status: String, // "processing", "completed", "failed"
    val result: AutoTagResponse? = null,
    val error: String? = null
)

// Background job tracking
private data class AutoTagJob(
    val paperId: String,
    var status: String = "processing",
    var result: AutoTagResponse? = null,
    var error: String? = null
)

private val autoTagJobs = ConcurrentHashMap<String, AutoTagJob>()

@Serializable
data class TagSuggestionDto(
    val name: String,
    val existingTagId: String? = null,
    val existingTagName: String? = null,
    val isNew: Boolean
)

// Extension to convert Tag to DTO
fun Tag.toDto(count: Int = 0): TagDto = TagDto(
    id = id,
    name = name,
    color = color,
    count = count
)

fun Route.tagRoutes() {
    val tagRepository = ServiceLocator.tagRepository
    val paperRepository = ServiceLocator.paperRepository

    route("/tags") {
        // GET /api/tags - List all tags with counts
        get {
            val sort = call.request.queryParameters["sort"] ?: "count"
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

            val result = tagRepository.getTagsWithCounts()
            result.fold(
                onSuccess = { tagsWithCounts ->
                    val sortedTags = when (sort) {
                        "name" -> tagsWithCounts.sortedBy { it.first.name }
                        "count" -> tagsWithCounts.sortedByDescending { it.second }
                        else -> tagsWithCounts
                    }.take(limit)

                    val tagDtos = sortedTags.map { (tag, count) -> tag.toDto(count) }
                    call.respond(ApiResponse(data = tagDtos))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<TagDto>>(
                            success = false,
                            error = error.message ?: "Failed to fetch tags"
                        )
                    )
                }
            )
        }

        // POST /api/tags - Create a new tag
        post {
            val request = call.receive<CreateTagRequest>()

            // Check if tag already exists
            val existing = tagRepository.getByName(request.name).getOrNull()
            if (existing != null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse<TagDto>(
                        success = false,
                        error = "Tag with name '${request.name}' already exists"
                    )
                )
                return@post
            }

            val tag = Tag(
                id = UUID.randomUUID().toString(),
                name = request.name,
                color = request.color
            )

            val result = tagRepository.insert(tag)
            result.fold(
                onSuccess = { insertedTag ->
                    call.respond(HttpStatusCode.Created, ApiResponse(data = insertedTag.toDto()))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<TagDto>(
                            success = false,
                            error = error.message ?: "Failed to create tag"
                        )
                    )
                }
            )
        }

        // GET /api/tags/{id} - Get tag by ID
        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing tag ID")

            val result = tagRepository.getById(id)
            result.fold(
                onSuccess = { tag ->
                    if (tag != null) {
                        call.respond(ApiResponse(data = tag.toDto()))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<TagDto>(
                                success = false,
                                error = "Tag not found: $id"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<TagDto>(
                            success = false,
                            error = error.message ?: "Failed to fetch tag"
                        )
                    )
                }
            )
        }

        // PATCH /api/tags/{id} - Update a tag
        patch("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing tag ID")
            val request = call.receive<CreateTagRequest>()

            // Check if tag exists
            val existing = tagRepository.getById(id).getOrNull()
            if (existing == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<TagDto>(
                        success = false,
                        error = "Tag not found: $id"
                    )
                )
                return@patch
            }

            val updatedTag = existing.copy(
                name = request.name,
                color = request.color
            )

            val result = tagRepository.update(updatedTag)
            result.fold(
                onSuccess = { tag ->
                    call.respond(ApiResponse(data = tag.toDto()))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<TagDto>(
                            success = false,
                            error = error.message ?: "Failed to update tag"
                        )
                    )
                }
            )
        }

        // DELETE /api/tags/{id} - Delete a tag
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing tag ID")

            val result = tagRepository.delete(id)
            result.fold(
                onSuccess = {
                    call.respond(
                        ApiResponse(
                            data = mapOf("deletedTagId" to id)
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, String>>(
                            success = false,
                            error = error.message ?: "Failed to delete tag"
                        )
                    )
                }
            )
        }
    }

    // Tag assignment to papers
    route("/papers/{paperId}/tags") {
        // GET /api/papers/{paperId}/tags - Get tags for a paper
        get {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val result = paperRepository.getTags(paperId)
            result.fold(
                onSuccess = { tags ->
                    call.respond(ApiResponse(data = tags.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<TagDto>>(
                            success = false,
                            error = error.message ?: "Failed to fetch tags"
                        )
                    )
                }
            )
        }

        // POST /api/papers/{paperId}/tags - Assign tags to paper
        post {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")
            val request = call.receive<AssignTagsRequest>()

            // Verify paper exists
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Map<String, Any>>(
                        success = false,
                        error = "Paper not found: $paperId"
                    )
                )
                return@post
            }

            // Create or get tags and assign them
            val tags = request.tags.map { tagName ->
                val existing = tagRepository.getByName(tagName).getOrNull()
                existing ?: Tag(
                    id = UUID.randomUUID().toString(),
                    name = tagName
                ).also { tagRepository.insert(it) }
            }

            val result = paperRepository.setTags(paperId, tags)
            result.fold(
                onSuccess = {
                    call.respond(
                        ApiResponse(
                            data = mapOf(
                                "paperId" to paperId,
                                "tags" to tags.map { it.toDto() }
                            )
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, Any>>(
                            success = false,
                            error = error.message ?: "Failed to assign tags"
                        )
                    )
                }
            )
        }

        // DELETE /api/papers/{paperId}/tags/{tagId} - Remove tag from paper
        delete("/{tagId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")
            val tagId = call.parameters["tagId"]
                ?: throw IllegalArgumentException("Missing tag ID")

            val result = paperRepository.removeTag(paperId, tagId)
            result.fold(
                onSuccess = {
                    call.respond(
                        ApiResponse(
                            data = mapOf(
                                "paperId" to paperId,
                                "removedTagId" to tagId
                            )
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, String>>(
                            success = false,
                            error = error.message ?: "Failed to remove tag"
                        )
                    )
                }
            )
        }

        // POST /api/papers/{paperId}/tags/auto - Auto-tag a paper using LLM (async via GlobalJobQueue)
        post("/auto") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            // Verify paper exists first
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<AutoTagJobResponse>(
                        success = false,
                        error = "Paper not found: $paperId"
                    )
                )
                return@post
            }

            // Submit to GlobalJobQueue for unified job management
            val jobQueue = GlobalJobQueue.instance
            val job = jobQueue.submitJob(
                type = JobType.AUTO_TAGGING,
                title = "Auto-tagging: ${paper.title.take(50)}...",
                description = "Generating tags using LLM analysis",
                paperId = paperId
            ) { ctx ->
                ctx.updateProgress(10, "Extracting text from PDF...")

                val tagService = ServiceLocator.tagService

                // Extract text from PDF if available
                var fullText: String? = null
                val pdfPath = paper.pdfPath
                if (!pdfPath.isNullOrBlank()) {
                    try {
                        val pdfFile = File(pdfPath)
                        if (pdfFile.exists()) {
                            val analyzer = PdfAnalyzer(pdfFile.absolutePath)
                            fullText = analyzer.extractFirstPagesText(maxPages = 3)
                        }
                    } catch (e: Exception) {
                        println("[AutoTag] PDF extraction failed: ${e.message}")
                    }
                }

                ctx.updateProgress(30, "Getting tag suggestions from LLM...")

                // Get tag suggestions
                val suggestionsResult = tagService.suggestTags(
                    title = paper.title,
                    abstract = paper.abstract
                )

                if (suggestionsResult.isFailure) {
                    throw Exception(suggestionsResult.exceptionOrNull()?.message ?: "Failed to suggest tags")
                }

                val suggestions: List<TagSuggestion> = suggestionsResult.getOrThrow()
                val suggestionDtos: List<TagSuggestionDto> = suggestions.map { suggestion ->
                    TagSuggestionDto(
                        name = suggestion.name,
                        existingTagId = suggestion.existingTag?.id,
                        existingTagName = suggestion.existingTag?.name,
                        isNew = suggestion.isNew
                    )
                }

                ctx.updateProgress(60, "Auto-tagging paper...")

                // Auto-tag the paper
                val autoTagResult = tagService.autoTagPaper(
                    paperId = paperId,
                    title = paper.title,
                    abstract = paper.abstract,
                    fullText = fullText
                )

                if (autoTagResult.isFailure) {
                    throw Exception(autoTagResult.exceptionOrNull()?.message ?: "Failed to auto-tag paper")
                }

                val assignedTags: List<Tag> = autoTagResult.getOrThrow()

                ctx.updateProgress(80, "Linking tags to paper...")

                // Link tags to paper
                for (tag in assignedTags) {
                    tagRepository.linkTagToPaper(paperId, tag.id)
                }

                val assignedTagDtos: List<TagDto> = assignedTags.map { tag -> tag.toDto() }

                ctx.updateProgress(100, "Auto-tagging complete")

                // Return JSON result for job storage
                val result = AutoTagResponse(
                    paperId = paperId,
                    suggestedTags = suggestionDtos,
                    assignedTags = assignedTagDtos
                )
                Json.encodeToString(result)
            }

            // Store job reference for backward compatibility with status endpoint
            autoTagJobs[job.id] = AutoTagJob(paperId = paperId, status = "processing")

            // Return immediately with job ID
            call.respond(
                HttpStatusCode.Accepted,
                ApiResponse(
                    data = AutoTagJobResponse(
                        jobId = job.id,
                        paperId = paperId,
                        status = "processing"
                    )
                )
            )
        }

        // GET /api/papers/{paperId}/tags/auto/{jobId} - Check auto-tag job status
        get("/auto/{jobId}") {
            val jobId = call.parameters["jobId"]
                ?: throw IllegalArgumentException("Missing job ID")

            val job = autoTagJobs[jobId]
            if (job == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<AutoTagJobStatus>(
                        success = false,
                        error = "Job not found: $jobId"
                    )
                )
                return@get
            }

            call.respond(
                ApiResponse(
                    data = AutoTagJobStatus(
                        jobId = jobId,
                        paperId = job.paperId,
                        status = job.status,
                        result = job.result,
                        error = job.error
                    )
                )
            )

            // Clean up completed/failed jobs after retrieval
            if (job.status != "processing") {
                autoTagJobs.remove(jobId)
            }
        }

        // POST /api/papers/{paperId}/tags/suggest - Get tag suggestions without applying
        post("/suggest") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val tagService = ServiceLocator.tagService

            // Get paper
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<List<TagSuggestionDto>>(
                        success = false,
                        error = "Paper not found: $paperId"
                    )
                )
                return@post
            }

            val suggestionsResult = tagService.suggestTags(
                title = paper.title,
                abstract = paper.abstract
            )

            if (suggestionsResult.isFailure) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<TagSuggestionDto>>(
                        success = false,
                        error = suggestionsResult.exceptionOrNull()?.message ?: "Failed to suggest tags"
                    )
                )
                return@post
            }

            val suggestions: List<TagSuggestion> = suggestionsResult.getOrThrow()
            val suggestionDtos: List<TagSuggestionDto> = suggestions.map { suggestion: TagSuggestion ->
                TagSuggestionDto(
                    name = suggestion.name,
                    existingTagId = suggestion.existingTag?.id,
                    existingTagName = suggestion.existingTag?.name,
                    isNew = suggestion.isNew
                )
            }

            call.respond(ApiResponse(data = suggestionDtos))
        }
    }

    // Tag merge endpoint
    route("/tags/merge") {
        // POST /api/tags/merge - Merge similar tags (admin operation)
        post {
            val tagService = ServiceLocator.tagService

            val mergeResult = tagService.mergeSimilarTags()
            mergeResult.fold(
                onSuccess = { mergedCount: Int ->
                    call.respond(
                        ApiResponse(
                            data = mapOf(
                                "mergedCount" to mergedCount,
                                "message" to "Successfully merged $mergedCount similar tags"
                            )
                        )
                    )
                },
                onFailure = { error: Throwable ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Map<String, Any>>(
                            success = false,
                            error = error.message ?: "Failed to merge tags"
                        )
                    )
                }
            )
        }
    }
}
