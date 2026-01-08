package com.potero.server.routes

import com.potero.domain.model.Tag
import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

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
    }
}
