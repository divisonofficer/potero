package com.potero.server.routes

import com.potero.domain.model.ResearchNote
import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String,
    val paperId: String? = null
)

@Serializable
data class UpdateNoteRequest(
    val title: String,
    val content: String,
    val paperId: String? = null
)

fun Route.researchNoteRoutes() {
    val repository = ServiceLocator.researchNoteRepository

    route("/notes") {
        // GET /api/notes - List all notes
        get {
            val result = repository.getAll()
            result.fold(
                onSuccess = { notes ->
                    call.respond(ApiResponse(data = notes))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<ResearchNote>>(
                            success = false,
                            error = error.message ?: "Failed to fetch notes"
                        )
                    )
                }
            )
        }

        // GET /api/notes/standalone - Get standalone notes (not linked to paper)
        get("/standalone") {
            val result = repository.getStandalone()
            result.fold(
                onSuccess = { notes ->
                    call.respond(ApiResponse(data = notes))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<ResearchNote>>(
                            success = false,
                            error = error.message ?: "Failed to fetch standalone notes"
                        )
                    )
                }
            )
        }

        // GET /api/notes/paper/{paperId} - Get notes for a paper
        get("/paper/{paperId}") {
            val paperId = call.parameters["paperId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing paperId")
            )

            val result = repository.getByPaper(paperId)
            result.fold(
                onSuccess = { notes ->
                    call.respond(ApiResponse(data = notes))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<ResearchNote>>(
                            success = false,
                            error = error.message
                        )
                    )
                }
            )
        }

        // GET /api/notes/{id} - Get note by ID with links
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing note ID")
            )

            val result = repository.getWithLinks(id)
            result.fold(
                onSuccess = { noteWithLinks ->
                    if (noteWithLinks != null) {
                        call.respond(ApiResponse(data = noteWithLinks))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(success = false, error = "Note not found")
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = error.message)
                    )
                }
            )
        }

        // POST /api/notes - Create new note
        post {
            val request = call.receive<CreateNoteRequest>()

            // Validate title
            if (request.title.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, error = "Title cannot be empty")
                )
            }

            val now = Clock.System.now()
            val note = ResearchNote(
                id = UUID.randomUUID().toString(),
                paperId = request.paperId,
                title = request.title,
                content = request.content,
                createdAt = now,
                updatedAt = now
            )

            val result = repository.insert(note)
            result.fold(
                onSuccess = { insertedNote ->
                    // Parse and store links
                    repository.updateLinks(insertedNote.id, insertedNote.content)
                    call.respond(HttpStatusCode.Created, ApiResponse(data = insertedNote))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = error.message)
                    )
                }
            )
        }

        // PATCH /api/notes/{id} - Update note
        patch("/{id}") {
            val id = call.parameters["id"] ?: return@patch call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing note ID")
            )

            val request = call.receive<UpdateNoteRequest>()

            // Validate title
            if (request.title.isBlank()) {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(success = false, error = "Title cannot be empty")
                )
            }

            val existing = repository.getById(id).getOrNull()
            if (existing == null) {
                return@patch call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, error = "Note not found")
                )
            }

            val updated = existing.copy(
                title = request.title,
                content = request.content,
                paperId = request.paperId
            )

            val result = repository.update(updated)
            result.fold(
                onSuccess = { updatedNote ->
                    // Re-parse links
                    repository.updateLinks(updatedNote.id, updatedNote.content)
                    call.respond(ApiResponse(data = updatedNote))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = error.message)
                    )
                }
            )
        }

        // DELETE /api/notes/{id} - Delete note
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing note ID")
            )

            val result = repository.delete(id)
            result.fold(
                onSuccess = {
                    call.respond(ApiResponse(data = mapOf("deletedId" to id)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = error.message)
                    )
                }
            )
        }

        // GET /api/notes/{id}/backlinks - Get backlinks to a note
        get("/{id}/backlinks") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing note ID")
            )

            val result = repository.getBacklinks(id)
            result.fold(
                onSuccess = { backlinks ->
                    call.respond(ApiResponse(data = backlinks))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = error.message)
                    )
                }
            )
        }

        // GET /api/notes/search - Search notes
        get("/search") {
            val query = call.request.queryParameters["q"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing query parameter")
            )

            // Search both title and content
            val titleResults = repository.searchByTitle(query).getOrDefault(emptyList())
            val contentResults = repository.searchByContent(query).getOrDefault(emptyList())

            // Combine and remove duplicates
            val combined = (titleResults + contentResults).distinctBy { it.id }
            call.respond(ApiResponse(data = combined))
        }
    }
}
