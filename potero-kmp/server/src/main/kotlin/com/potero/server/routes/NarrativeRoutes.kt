package com.potero.server.routes

import com.potero.domain.model.*
import com.potero.server.di.ServiceLocator
import com.potero.service.job.GlobalJobQueue
import com.potero.service.job.JobType
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class GenerateNarrativeRequest(
    val styles: List<String>? = null,
    val languages: List<String>? = null,
    val regenerate: Boolean = false
)

@Serializable
data class NarrativeDto(
    val id: String,
    val paperId: String,
    val style: String,
    val language: String,
    val title: String,
    val content: String,
    val summary: String,
    val figureExplanations: List<FigureExplanationDto>,
    val conceptExplanations: List<ConceptExplanationDto>,
    val estimatedReadTime: Int,
    val createdAt: String
)

@Serializable
data class FigureExplanationDto(
    val id: String,
    val figureId: String,
    val label: String,
    val originalCaption: String?,
    val explanation: String,
    val relevance: String?
)

@Serializable
data class ConceptExplanationDto(
    val id: String,
    val term: String,
    val definition: String,
    val analogy: String?,
    val relatedTerms: List<String>
)

@Serializable
data class NarrativeStyleInfo(
    val name: String,
    val displayName: String,
    val description: String
)

@Serializable
data class NarrativeLanguageInfo(
    val code: String,
    val displayName: String
)

@Serializable
data class NarrativeGenerationStartedResponse(
    val jobId: String,
    val status: String,
    val totalNarratives: Int
)

@Serializable
data class NarrativeAlreadyExistsResponse(
    val status: String,
    val message: String
)

@Serializable
data class NarrativeDeletedResponse(
    val deleted: Boolean
)

fun Route.narrativeRoutes() {
    val narrativeService = ServiceLocator.narrativeEngineService
    val jobQueue = GlobalJobQueue.instance

    route("/papers/{paperId}/narratives") {

        // POST /api/papers/{paperId}/narratives/generate
        // Start narrative generation (returns job ID)
        post("/generate") {
            val paperId = call.parameters["paperId"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<String>(success = false, error = "Missing paper ID")
                )

            val request = try {
                call.receive<GenerateNarrativeRequest>()
            } catch (e: Exception) {
                GenerateNarrativeRequest()
            }

            // Check if already generating
            if (jobQueue.hasActiveJobForPaper(paperId, JobType.NARRATIVE_GENERATION)) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse<Map<String, String>>(
                        success = false,
                        error = "Narrative generation already in progress for this paper"
                    )
                )
            }

            // Check if narratives exist and regenerate not requested
            if (!request.regenerate && narrativeService.hasNarratives(paperId)) {
                return@post call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(data = NarrativeAlreadyExistsResponse(
                        status = "already_exists",
                        message = "Narratives already exist. Use regenerate=true to regenerate."
                    ))
                )
            }

            // Parse styles and languages
            val styles = try {
                request.styles?.map { NarrativeStyle.valueOf(it.uppercase()) }
                    ?: NarrativeStyle.entries
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<String>(success = false, error = "Invalid style: ${e.message}")
                )
            }

            val languages = try {
                request.languages?.map { langCode ->
                    NarrativeLanguage.entries.find {
                        it.code == langCode || it.name.equals(langCode, ignoreCase = true)
                    } ?: throw IllegalArgumentException("Unknown language: $langCode")
                } ?: NarrativeLanguage.entries
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<String>(success = false, error = "Invalid language: ${e.message}")
                )
            }

            // Submit background job
            val job = jobQueue.submitJob(
                type = JobType.NARRATIVE_GENERATION,
                title = "Generating narratives",
                description = "${styles.size} styles x ${languages.size} languages",
                paperId = paperId
            ) { ctx ->
                val narrativeRequest = NarrativeGenerationRequest(
                    paperId = paperId,
                    styles = styles,
                    languages = languages
                )

                val result = if (request.regenerate) {
                    narrativeService.regenerateNarratives(narrativeRequest) { progress ->
                        val percent = if (progress.totalNarratives > 0) {
                            (progress.completedNarratives * 100) / progress.totalNarratives
                        } else 0
                        ctx.updateProgress(
                            progress = percent,
                            message = "Stage: ${progress.currentStage}, " +
                                    "Style: ${progress.currentStyle?.name ?: "-"}, " +
                                    "Language: ${progress.currentLanguage?.displayName ?: "-"}"
                        )
                    }
                } else {
                    narrativeService.generateNarratives(narrativeRequest) { progress ->
                        val percent = if (progress.totalNarratives > 0) {
                            (progress.completedNarratives * 100) / progress.totalNarratives
                        } else 0
                        ctx.updateProgress(
                            progress = percent,
                            message = "Stage: ${progress.currentStage}"
                        )
                    }
                }

                result.getOrThrow()
                "${styles.size * languages.size} narratives generated"
            }

            call.respond(
                HttpStatusCode.Accepted,
                ApiResponse(data = NarrativeGenerationStartedResponse(
                    jobId = job.id,
                    status = "started",
                    totalNarratives = styles.size * languages.size
                ))
            )
        }

        // GET /api/papers/{paperId}/narratives
        // Get all narratives for a paper
        get {
            val paperId = call.parameters["paperId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<String>(success = false, error = "Missing paper ID")
                )

            val style = call.request.queryParameters["style"]
            val language = call.request.queryParameters["language"]

            val narrativesResult = narrativeService.getNarratives(paperId)

            narrativesResult.fold(
                onSuccess = { narratives ->
                    val filtered = narratives.filter { n ->
                        (style == null || n.style.name.equals(style, ignoreCase = true)) &&
                                (language == null || n.language.code == language ||
                                        n.language.name.equals(language, ignoreCase = true))
                    }
                    call.respond(ApiResponse(data = filtered.map { it.toDto() }))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<List<NarrativeDto>>(success = false, error = error.message)
                    )
                }
            )
        }

        // GET /api/papers/{paperId}/narratives/{style}/{language}
        // Get specific narrative
        get("/{style}/{language}") {
            val paperId = call.parameters["paperId"]!!
            val styleParam = call.parameters["style"]!!
            val languageParam = call.parameters["language"]!!

            val style = try {
                NarrativeStyle.valueOf(styleParam.uppercase())
            } catch (e: Exception) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<NarrativeDto>(success = false, error = "Invalid style: $styleParam")
                )
            }

            val language = NarrativeLanguage.entries.find {
                it.code == languageParam || it.name.equals(languageParam, ignoreCase = true)
            } ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<NarrativeDto>(success = false, error = "Invalid language: $languageParam")
            )

            val narrativeResult = narrativeService.getNarrative(paperId, style, language)

            narrativeResult.fold(
                onSuccess = { narrative ->
                    if (narrative != null) {
                        call.respond(ApiResponse(data = narrative.toDto()))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<NarrativeDto>(success = false, error = "Narrative not found")
                        )
                    }
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<NarrativeDto>(success = false, error = error.message)
                    )
                }
            )
        }

        // DELETE /api/papers/{paperId}/narratives
        // Delete all narratives for a paper
        delete {
            val paperId = call.parameters["paperId"]!!

            narrativeService.deleteNarratives(paperId).fold(
                onSuccess = {
                    call.respond(ApiResponse(data = NarrativeDeletedResponse(deleted = true)))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<NarrativeDeletedResponse>(success = false, error = error.message)
                    )
                }
            )
        }
    }

    // GET /api/narratives/styles - List available styles
    get("/narratives/styles") {
        val styles = NarrativeStyle.entries.map {
            NarrativeStyleInfo(
                name = it.name,
                displayName = it.displayName,
                description = it.description
            )
        }
        call.respond(ApiResponse(data = styles))
    }

    // GET /api/narratives/languages - List available languages
    get("/narratives/languages") {
        val languages = NarrativeLanguage.entries.map {
            NarrativeLanguageInfo(
                code = it.code,
                displayName = it.displayName
            )
        }
        call.respond(ApiResponse(data = languages))
    }
}

private fun Narrative.toDto(): NarrativeDto = NarrativeDto(
    id = id,
    paperId = paperId,
    style = style.name,
    language = language.code,
    title = title,
    content = content,
    summary = summary,
    figureExplanations = figureExplanations.map { it.toDto() },
    conceptExplanations = conceptExplanations.map { it.toDto() },
    estimatedReadTime = estimatedReadTime,
    createdAt = createdAt.toString()
)

private fun FigureExplanation.toDto(): FigureExplanationDto = FigureExplanationDto(
    id = id,
    figureId = figureId,
    label = label,
    originalCaption = originalCaption,
    explanation = explanation,
    relevance = relevance
)

private fun ConceptExplanation.toDto(): ConceptExplanationDto = ConceptExplanationDto(
    id = id,
    term = term,
    definition = definition,
    analogy = analogy,
    relatedTerms = relatedTerms
)
