package com.potero.server.routes

import com.potero.domain.model.Author
import com.potero.domain.model.Paper
import com.potero.domain.repository.SettingsKeys
import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class UploadResponse(
    val paperId: String,
    val fileName: String,
    val filePath: String,
    val title: String
)

fun Route.uploadRoutes() {
    val paperRepository = ServiceLocator.paperRepository
    val settingsRepository = ServiceLocator.settingsRepository

    // GET /api/pdf/file - Serve PDF file by path
    route("/pdf") {
        get("/file") {
            val path = call.request.queryParameters["path"]
            if (path.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing path parameter")
                return@get
            }

            // Expand ~ to home directory
            val expandedPath = if (path.startsWith("~")) {
                path.replaceFirst("~", System.getProperty("user.home"))
            } else {
                path
            }

            val file = File(expandedPath)
            if (!file.exists() || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, "File not found: $path")
                return@get
            }

            // Security: Only serve PDF files
            if (!file.name.lowercase().endsWith(".pdf")) {
                call.respond(HttpStatusCode.Forbidden, "Only PDF files can be served")
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline.withParameter(
                    ContentDisposition.Parameters.FileName,
                    file.name
                ).toString()
            )
            call.respondFile(file)
        }
    }

    route("/upload") {
        // POST /api/upload/pdf - Upload a PDF file
        post("/pdf") {
            val multipart = call.receiveMultipart()

            var fileName: String? = null
            var fileBytes: ByteArray? = null
            var title: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName
                        fileBytes = part.streamProvider().readBytes()
                    }
                    is PartData.FormItem -> {
                        if (part.name == "title") {
                            title = part.value
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileName == null || fileBytes == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<UploadResponse>(
                        success = false,
                        error = "No file uploaded"
                    )
                )
                return@post
            }

            // Get storage path from settings or use default
            val storagePath = settingsRepository.get(SettingsKeys.PDF_STORAGE_PATH).getOrNull()
                ?: "${System.getProperty("user.home")}/.potero/pdfs"

            // Ensure directory exists
            val storageDir = File(storagePath)
            storageDir.mkdirs()

            // Generate unique filename
            val paperId = UUID.randomUUID().toString()
            val safeFileName = fileName!!.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(storageDir, "${paperId}_$safeFileName")

            // Save file
            targetFile.writeBytes(fileBytes!!)

            // Extract title from filename if not provided
            val paperTitle = title ?: fileName!!
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .replace("_", " ")
                .replace("-", " ")

            // Create paper entry
            val now = Clock.System.now()
            val paper = Paper(
                id = paperId,
                title = paperTitle,
                pdfPath = targetFile.absolutePath,
                authors = emptyList(),
                tags = emptyList(),
                createdAt = now,
                updatedAt = now
            )

            val result = paperRepository.insert(paper)
            result.fold(
                onSuccess = { insertedPaper ->
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse(
                            data = UploadResponse(
                                paperId = insertedPaper.id,
                                fileName = safeFileName,
                                filePath = targetFile.absolutePath,
                                title = insertedPaper.title
                            )
                        )
                    )
                },
                onFailure = { error ->
                    // Clean up file on failure
                    targetFile.delete()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<UploadResponse>(
                            success = false,
                            error = error.message ?: "Failed to create paper entry"
                        )
                    )
                }
            )
        }

        // POST /api/upload/pdf/{paperId} - Upload PDF for existing paper
        post("/pdf/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            // Check if paper exists
            val existingPaper = paperRepository.getById(paperId).getOrNull()
            if (existingPaper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<UploadResponse>(
                        success = false,
                        error = "Paper not found: $paperId"
                    )
                )
                return@post
            }

            val multipart = call.receiveMultipart()

            var fileName: String? = null
            var fileBytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName
                        fileBytes = part.streamProvider().readBytes()
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileName == null || fileBytes == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<UploadResponse>(
                        success = false,
                        error = "No file uploaded"
                    )
                )
                return@post
            }

            // Get storage path
            val storagePath = settingsRepository.get(SettingsKeys.PDF_STORAGE_PATH).getOrNull()
                ?: "${System.getProperty("user.home")}/.potero/pdfs"

            val storageDir = File(storagePath)
            storageDir.mkdirs()

            val safeFileName = fileName!!.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(storageDir, "${paperId}_$safeFileName")

            // Delete old file if exists
            existingPaper.pdfPath?.let { oldPath ->
                File(oldPath).delete()
            }

            // Save new file
            targetFile.writeBytes(fileBytes!!)

            // Update paper
            val updatedPaper = existingPaper.copy(
                pdfPath = targetFile.absolutePath,
                updatedAt = Clock.System.now()
            )

            val result = paperRepository.update(updatedPaper)
            result.fold(
                onSuccess = { paper ->
                    call.respond(
                        ApiResponse(
                            data = UploadResponse(
                                paperId = paper.id,
                                fileName = safeFileName,
                                filePath = targetFile.absolutePath,
                                title = paper.title
                            )
                        )
                    )
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<UploadResponse>(
                            success = false,
                            error = error.message ?: "Failed to update paper"
                        )
                    )
                }
            )
        }
    }
}
