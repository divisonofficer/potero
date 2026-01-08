package com.potero.server.routes

import com.potero.service.job.GlobalJobQueue
import com.potero.service.job.Job
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class JobDto(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val status: String,
    val progress: Int,
    val progressMessage: String,
    val createdAt: String,
    val startedAt: String?,
    val completedAt: String?,
    val error: String?,
    val paperId: String?
)

fun Job.toDto(): JobDto = JobDto(
    id = id,
    type = type.name,
    title = title,
    description = description,
    status = status.name,
    progress = progress,
    progressMessage = progressMessage,
    createdAt = createdAt.toString(),
    startedAt = startedAt?.toString(),
    completedAt = completedAt?.toString(),
    error = error,
    paperId = paperId
)

fun Route.jobRoutes() {
    val jobQueue = GlobalJobQueue.instance

    route("/jobs") {
        // GET /api/jobs - List all jobs
        get {
            val includeCompleted = call.request.queryParameters["includeCompleted"]?.toBoolean() ?: true

            val jobs = if (includeCompleted) {
                jobQueue.getAllJobs()
            } else {
                jobQueue.getActiveJobs()
            }

            call.respond(ApiResponse(data = jobs.map { it.toDto() }))
        }

        // GET /api/jobs/active - List only active jobs
        get("/active") {
            val jobs = jobQueue.getActiveJobs()
            call.respond(ApiResponse(data = jobs.map { it.toDto() }))
        }

        // GET /api/jobs/{id} - Get job by ID
        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing job ID")

            val job = jobQueue.getJob(id)
            if (job != null) {
                call.respond(ApiResponse(data = job.toDto()))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<JobDto>(
                        success = false,
                        error = "Job not found: $id"
                    )
                )
            }
        }

        // POST /api/jobs/{id}/cancel - Cancel a job
        post("/{id}/cancel") {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing job ID")

            val cancelled = jobQueue.cancelJob(id)
            if (cancelled) {
                call.respond(ApiResponse(data = mapOf("cancelled" to true, "jobId" to id)))
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Map<String, Any>>(
                        success = false,
                        error = "Cannot cancel job: $id"
                    )
                )
            }
        }

        // GET /api/jobs/paper/{paperId} - Get jobs for a specific paper
        get("/paper/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val jobs = jobQueue.getAllJobs().filter { it.paperId == paperId }
            call.respond(ApiResponse(data = jobs.map { it.toDto() }))
        }

        // DELETE /api/jobs/completed - Clear completed jobs
        delete("/completed") {
            jobQueue.clearCompletedJobs()
            call.respond(ApiResponse(data = mapOf("cleared" to true)))
        }
    }
}
