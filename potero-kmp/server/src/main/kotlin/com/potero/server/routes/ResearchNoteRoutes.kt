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

        // POST /api/notes/generate-template - Generate markdown template for a paper
        post("/generate-template") {
            val paperId = call.request.queryParameters["paperId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing paperId parameter")
            )

            val paperRepository = ServiceLocator.paperRepository
            val llmService = ServiceLocator.llmService

            // Get paper details
            val paperResult = paperRepository.getById(paperId)
            val paper = paperResult.getOrNull() ?: return@post call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "Paper not found")
            )

            try {
                val authorNames = paper.authors.map { it.name }
                val abstractText = paper.abstract ?: ""

                // Generate template using LLM
                val prompt = """
                    논문 정보를 바탕으로 연구 노트 템플릿을 마크다운 형식으로 생성해주세요.

                    논문 제목: ${paper.title}
                    저자: ${authorNames.joinToString(", ")}
                    연도: ${paper.year ?: "Unknown"}
                    학회/저널: ${paper.conference ?: "Unknown"}
                    ${if (abstractText.isNotEmpty()) "초록: $abstractText" else ""}

                    다음 구조로 마크다운 템플릿을 생성해주세요:

                    # [논문 제목]

                    ## 📋 기본 정보
                    - **저자**: [저자 목록]
                    - **출처**: [학회/저널]
                    - **연도**: [연도]
                    - **DOI**: [DOI 정보]

                    ## 🎯 핵심 내용
                    [초록을 바탕으로 한 3-4줄 요약]

                    ## 💡 주요 기여
                    -

                    ## 🔬 방법론

                    ## 📊 실험 결과

                    ## 🤔 인사이트 및 메모

                    ## 🔗 관련 연구
                    -

                    ## 📝 참고사항

                    위 형식을 따라 실제 내용을 채워주세요. 없는 정보는 빈 칸으로 남겨두세요.
                """.trimIndent()

                val response = llmService.chat(prompt)

                if (response.isSuccess) {
                    val template = response.getOrNull() ?: ""
                    call.respond(ApiResponse(data = mapOf(
                        "title" to "${paper.title} - Notes",
                        "template" to template
                    )))
                } else {
                    // Fallback template if LLM fails
                    val fallbackTemplate = """
                        # ${paper.title}

                        ## 📋 기본 정보
                        - **저자**: ${authorNames.joinToString(", ")}
                        - **출처**: ${paper.conference ?: "Unknown"}
                        - **연도**: ${paper.year ?: "Unknown"}
                        ${if (!paper.doi.isNullOrEmpty()) "- **DOI**: ${paper.doi}" else ""}

                        ## 🎯 핵심 내용
                        ${if (abstractText.isNotEmpty()) abstractText else ""}

                        ## 💡 주요 기여
                        -

                        ## 🔬 방법론

                        ## 📊 실험 결과

                        ## 🤔 인사이트 및 메모

                        ## 🔗 관련 연구
                        -

                        ## 📝 참고사항
                    """.trimIndent()

                    call.respond(ApiResponse(data = mapOf(
                        "title" to "${paper.title} - Notes",
                        "template" to fallbackTemplate
                    )))
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(success = false, error = "Failed to generate template: ${e.message}")
                )
            }
        }
    }
}
