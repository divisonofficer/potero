package com.potero.server.routes

import com.potero.domain.model.Author
import com.potero.domain.model.Paper
import com.potero.domain.repository.SettingsKeys
import com.potero.server.di.ServiceLocator
import com.potero.service.job.GlobalJobQueue
import com.potero.service.job.JobType
import com.potero.service.metadata.SearchResult
import com.potero.service.pdf.PdfAnalyzer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class UploadResponse(
    val paperId: String,
    val fileName: String,
    val filePath: String,
    val title: String
)

/**
 * Response for PDF upload with analysis results
 * If searchResults is not empty, the frontend should show a selection dialog
 */
@Serializable
data class UploadAnalysisResponse(
    val paperId: String,
    val fileName: String,
    val filePath: String,
    val title: String,
    // Analysis results
    val detectedDoi: String? = null,
    val detectedArxivId: String? = null,
    val pdfMetadataTitle: String? = null,
    val pdfMetadataAuthor: String? = null,
    // If DOI/arXiv found and resolved automatically
    val autoResolved: Boolean = false,
    val resolvedMetadata: ResolvedMetadataDto? = null,
    // If no identifier found, search results for user selection
    val searchResults: List<SearchResult> = emptyList(),
    val needsUserConfirmation: Boolean = false
)

@Serializable
data class ResolvedMetadataDto(
    val title: String,
    val authors: List<String>,
    val abstract: String? = null,
    val abstractKorean: String? = null,
    val doi: String? = null,
    val arxivId: String? = null,
    val year: Int? = null,
    val venue: String? = null,
    val pdfUrl: String? = null,
    val citationsCount: Int? = null
)

/**
 * Request to confirm/update paper with selected metadata
 */
@Serializable
data class ConfirmMetadataRequest(
    val paperId: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val abstract: String? = null,
    val abstractKorean: String? = null,
    val doi: String? = null,
    val arxivId: String? = null,
    val year: Int? = null,
    val venue: String? = null,
    val citationsCount: Int? = null
)

/**
 * Response for async re-analyze job submission
 */
@Serializable
data class ReanalyzeJobResponse(
    val jobId: String,
    val paperId: String,
    val message: String
)

/**
 * Result data stored in job after re-analysis completes
 */
@Serializable
data class ReanalyzeResult(
    val paperId: String,
    val autoResolved: Boolean,
    val detectedDoi: String? = null,
    val detectedArxivId: String? = null,
    val searchResultCount: Int = 0,
    val needsUserConfirmation: Boolean = false
)

fun Route.uploadRoutes() {
    val paperRepository = ServiceLocator.paperRepository
    val settingsRepository = ServiceLocator.settingsRepository
    val doiResolver = ServiceLocator.doiResolver
    val arxivResolver = ServiceLocator.arxivResolver
    val semanticScholarResolver = ServiceLocator.semanticScholarResolver

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
        // POST /api/upload/pdf - Upload a PDF file with automatic analysis
        post("/pdf") {
            val multipart = call.receiveMultipart()

            var fileName: String? = null
            var fileBytes: ByteArray? = null
            var title: String? = null
            var skipAnalysis = false

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName
                        fileBytes = part.streamProvider().readBytes()
                    }
                    is PartData.FormItem -> {
                        when (part.name) {
                            "title" -> title = part.value
                            "skipAnalysis" -> skipAnalysis = part.value.toBoolean()
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileName == null || fileBytes == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<UploadAnalysisResponse>(
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

            // === PDF Analysis Pipeline ===
            var detectedDoi: String? = null
            var detectedArxivId: String? = null
            var pdfMetadataTitle: String? = null
            var pdfMetadataAuthor: String? = null
            var autoResolved = false
            var resolvedMetadata: ResolvedMetadataDto? = null
            var searchResults: List<SearchResult> = emptyList()

            if (!skipAnalysis) {
                try {
                    // Step 1: Analyze PDF
                    val analyzer = PdfAnalyzer(targetFile.absolutePath)
                    val analysis = analyzer.analyze()

                    pdfMetadataTitle = analysis.metadata.title ?: analysis.extractedTitle
                    pdfMetadataAuthor = analysis.metadata.author ?: analysis.extractedAuthors.joinToString(", ")
                    detectedDoi = analysis.detectedDoi
                    detectedArxivId = analysis.detectedArxivId

                    // Step 2: Try to resolve metadata from identifiers
                    if (detectedDoi != null) {
                        // Try DOI resolver first
                        val doiResult = doiResolver.resolve(detectedDoi).getOrNull()
                        if (doiResult != null) {
                            autoResolved = true
                            resolvedMetadata = ResolvedMetadataDto(
                                title = doiResult.title,
                                authors = doiResult.authors.map { it.name },
                                abstract = doiResult.abstract,
                                doi = doiResult.doi,
                                arxivId = doiResult.arxivId,
                                year = doiResult.year,
                                venue = doiResult.venue,
                                pdfUrl = doiResult.pdfUrl,
                                citationsCount = doiResult.citationsCount
                            )
                        }
                    }

                    if (!autoResolved && detectedArxivId != null) {
                        // Try arXiv resolver
                        val arxivResult = arxivResolver.resolve(detectedArxivId).getOrNull()
                        if (arxivResult != null) {
                            autoResolved = true
                            resolvedMetadata = ResolvedMetadataDto(
                                title = arxivResult.title,
                                authors = arxivResult.authors.map { it.name },
                                abstract = arxivResult.abstract,
                                doi = arxivResult.doi,
                                arxivId = arxivResult.arxivId,
                                year = arxivResult.year,
                                venue = arxivResult.venue,
                                pdfUrl = arxivResult.pdfUrl,
                                citationsCount = arxivResult.citationsCount
                            )
                        }
                    }

                    // Step 3: If no identifier found, search by title
                    if (!autoResolved) {
                        val searchTitle = analysis.bestTitle ?: fileName!!
                            .removeSuffix(".pdf")
                            .removeSuffix(".PDF")
                            .replace("_", " ")
                            .replace("-", " ")

                        // Search Semantic Scholar
                        try {
                            searchResults = semanticScholarResolver.search(searchTitle, limit = 5)
                                .map { it.toSearchResult() }
                        } catch (e: Exception) {
                            // Search failed, continue without results
                        }
                    }
                } catch (e: Exception) {
                    // PDF analysis failed, continue with basic upload
                }
            }

            // Determine paper title
            val paperTitle = when {
                autoResolved && resolvedMetadata != null -> resolvedMetadata!!.title
                title != null -> title!!
                pdfMetadataTitle != null -> pdfMetadataTitle!!
                else -> fileName!!
                    .removeSuffix(".pdf")
                    .removeSuffix(".PDF")
                    .replace("_", " ")
                    .replace("-", " ")
            }

            // Determine authors
            val paperAuthors = when {
                autoResolved && resolvedMetadata != null -> resolvedMetadata.authors.mapIndexed { index, name ->
                    Author(id = UUID.randomUUID().toString(), name = name, order = index)
                }
                !pdfMetadataAuthor.isNullOrBlank() -> {
                    // Parse authors from PDF metadata/extraction
                    pdfMetadataAuthor.split(Regex("""\s*,\s*|\s+and\s+""", RegexOption.IGNORE_CASE))
                        .filter { it.isNotBlank() }
                        .mapIndexed { index, name ->
                            Author(id = UUID.randomUUID().toString(), name = name.trim(), order = index)
                        }
                }
                else -> emptyList()
            }

            // Create paper entry
            val now = Clock.System.now()
            val paper = Paper(
                id = paperId,
                title = paperTitle,
                pdfPath = targetFile.absolutePath,
                authors = paperAuthors,
                tags = emptyList(),
                abstract = resolvedMetadata?.abstract,
                doi = resolvedMetadata?.doi ?: detectedDoi,
                arxivId = resolvedMetadata?.arxivId ?: detectedArxivId,
                year = resolvedMetadata?.year,
                conference = resolvedMetadata?.venue,
                citationsCount = resolvedMetadata?.citationsCount ?: 0,
                createdAt = now,
                updatedAt = now
            )

            val result = paperRepository.insert(paper)
            result.fold(
                onSuccess = { insertedPaper ->
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse(
                            data = UploadAnalysisResponse(
                                paperId = insertedPaper.id,
                                fileName = safeFileName,
                                filePath = targetFile.absolutePath,
                                title = insertedPaper.title,
                                detectedDoi = detectedDoi,
                                detectedArxivId = detectedArxivId,
                                pdfMetadataTitle = pdfMetadataTitle,
                                pdfMetadataAuthor = pdfMetadataAuthor,
                                autoResolved = autoResolved,
                                resolvedMetadata = resolvedMetadata,
                                searchResults = searchResults,
                                needsUserConfirmation = !autoResolved && searchResults.isNotEmpty()
                            )
                        )
                    )
                },
                onFailure = { error ->
                    // Clean up file on failure
                    targetFile.delete()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<UploadAnalysisResponse>(
                            success = false,
                            error = error.message ?: "Failed to create paper entry"
                        )
                    )
                }
            )
        }

        // POST /api/upload/confirm - Confirm metadata selection for a paper
        post("/confirm") {
            val request = call.receive<ConfirmMetadataRequest>()

            val existingPaper = paperRepository.getById(request.paperId).getOrNull()
            if (existingPaper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<UploadResponse>(
                        success = false,
                        error = "Paper not found: ${request.paperId}"
                    )
                )
                return@post
            }

            val updatedPaper = existingPaper.copy(
                title = request.title,
                authors = request.authors.mapIndexed { index, name ->
                    Author(id = UUID.randomUUID().toString(), name = name, order = index)
                },
                abstract = request.abstract,
                doi = request.doi,
                arxivId = request.arxivId,
                year = request.year,
                conference = request.venue,
                citationsCount = request.citationsCount ?: 0,
                updatedAt = Clock.System.now()
            )

            val result = paperRepository.update(updatedPaper)
            result.fold(
                onSuccess = { paper ->
                    call.respond(
                        ApiResponse(
                            data = UploadResponse(
                                paperId = paper.id,
                                fileName = File(paper.pdfPath ?: "").name,
                                filePath = paper.pdfPath ?: "",
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

        // POST /api/upload/reanalyze/{paperId} - Re-analyze an existing paper's PDF (async background job)
        post("/reanalyze/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            // Check if paper exists
            val existingPaper = paperRepository.getById(paperId).getOrNull()
            if (existingPaper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ReanalyzeJobResponse>(
                        success = false,
                        error = "Paper not found: $paperId"
                    )
                )
                return@post
            }

            // Check if paper has a PDF
            val pdfPath = existingPaper.pdfPath
            if (pdfPath.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ReanalyzeJobResponse>(
                        success = false,
                        error = "Paper does not have a PDF file"
                    )
                )
                return@post
            }

            val pdfFile = java.io.File(pdfPath)
            if (!pdfFile.exists()) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ReanalyzeJobResponse>(
                        success = false,
                        error = "PDF file not found at path: $pdfPath"
                    )
                )
                return@post
            }

            // Submit async job
            val jobQueue = GlobalJobQueue.instance
            val job = jobQueue.submitJob(
                type = JobType.PDF_REANALYSIS,
                title = "Re-analyzing: ${existingPaper.title.take(50)}...",
                description = "Re-analyzing PDF metadata and searching for external references",
                paperId = paperId
            ) { ctx ->
                ctx.updateProgress(10, "Analyzing PDF structure...")

                // Step 1: Analyze PDF
                val analyzer = PdfAnalyzer(pdfFile.absolutePath)
                val analysis = analyzer.analyze()

                val pdfMetadataTitle = analysis.metadata.title ?: analysis.extractedTitle
                val pdfMetadataAuthor = analysis.metadata.author ?: analysis.extractedAuthors.joinToString(", ")
                val detectedDoi = analysis.detectedDoi
                val detectedArxivId = analysis.detectedArxivId

                ctx.updateProgress(30, "Looking up metadata from external sources...")

                // Step 2: Try to resolve metadata from identifiers
                var autoResolved = false
                var resolvedMetadata: ResolvedMetadataDto? = null

                val doi = detectedDoi
                if (doi != null) {
                    ctx.updateProgress(40, "Resolving DOI: $doi")
                    val doiResult = doiResolver.resolve(doi).getOrNull()
                    if (doiResult != null) {
                        autoResolved = true
                        resolvedMetadata = ResolvedMetadataDto(
                            title = doiResult.title,
                            authors = doiResult.authors.map { it.name },
                            abstract = doiResult.abstract,
                            doi = doiResult.doi,
                            arxivId = doiResult.arxivId,
                            year = doiResult.year,
                            venue = doiResult.venue,
                            pdfUrl = doiResult.pdfUrl,
                            citationsCount = doiResult.citationsCount
                        )
                    }
                }

                val arxivId = detectedArxivId
                if (!autoResolved && arxivId != null) {
                    ctx.updateProgress(50, "Resolving arXiv ID: $arxivId")
                    val arxivResult = arxivResolver.resolve(arxivId).getOrNull()
                    if (arxivResult != null) {
                        autoResolved = true
                        resolvedMetadata = ResolvedMetadataDto(
                            title = arxivResult.title,
                            authors = arxivResult.authors.map { it.name },
                            abstract = arxivResult.abstract,
                            doi = arxivResult.doi,
                            arxivId = arxivResult.arxivId,
                            year = arxivResult.year,
                            venue = arxivResult.venue,
                            pdfUrl = arxivResult.pdfUrl,
                            citationsCount = arxivResult.citationsCount
                        )
                    }
                }

                // Step 3: If no identifier found, search by title
                var searchResults: List<SearchResult> = emptyList()
                if (!autoResolved) {
                    ctx.updateProgress(60, "Searching Semantic Scholar...")
                    val searchTitle = analysis.bestTitle ?: existingPaper.title
                    try {
                        searchResults = semanticScholarResolver.search(searchTitle, limit = 5)
                            .map { it.toSearchResult() }
                    } catch (e: Exception) {
                        // Search failed, continue without results
                    }
                }

                ctx.updateProgress(80, "Updating paper metadata...")

                // Get fresh copy of paper (might have been updated)
                val currentPaper = paperRepository.getById(paperId).getOrNull() ?: existingPaper

                // Update paper with best available metadata
                val metadata = resolvedMetadata
                if (autoResolved && metadata != null) {
                    // Auto-resolved: use external API metadata
                    val updatedPaper = currentPaper.copy(
                        title = metadata.title,
                        authors = metadata.authors.mapIndexed { index, name ->
                            Author(id = UUID.randomUUID().toString(), name = name, order = index)
                        },
                        abstract = metadata.abstract,
                        doi = metadata.doi ?: detectedDoi,
                        arxivId = metadata.arxivId ?: detectedArxivId,
                        year = metadata.year,
                        conference = metadata.venue,
                        citationsCount = metadata.citationsCount ?: 0,
                        updatedAt = Clock.System.now()
                    )
                    paperRepository.update(updatedPaper)
                } else {
                    // Not auto-resolved: update with extracted PDF data if available
                    val extractedTitle = pdfMetadataTitle
                    val extractedAuthor = pdfMetadataAuthor

                    if (!extractedTitle.isNullOrBlank() || !extractedAuthor.isNullOrBlank()) {
                        val extractedAuthors = if (!extractedAuthor.isNullOrBlank()) {
                            extractedAuthor.split(Regex("""\s*,\s*|\s+and\s+""", RegexOption.IGNORE_CASE))
                                .filter { it.isNotBlank() }
                                .mapIndexed { index, name ->
                                    Author(id = UUID.randomUUID().toString(), name = name.trim(), order = index)
                                }
                        } else {
                            currentPaper.authors
                        }

                        val updatedPaper = currentPaper.copy(
                            title = extractedTitle?.takeIf { it.isNotBlank() } ?: currentPaper.title,
                            authors = extractedAuthors.ifEmpty { currentPaper.authors },
                            doi = detectedDoi ?: currentPaper.doi,
                            arxivId = detectedArxivId ?: currentPaper.arxivId,
                            updatedAt = Clock.System.now()
                        )
                        paperRepository.update(updatedPaper)
                    }
                }

                ctx.updateProgress(100, "Re-analysis complete")

                // Return result as JSON
                val result = ReanalyzeResult(
                    paperId = paperId,
                    autoResolved = autoResolved,
                    detectedDoi = detectedDoi,
                    detectedArxivId = detectedArxivId,
                    searchResultCount = searchResults.size,
                    needsUserConfirmation = !autoResolved && searchResults.isNotEmpty()
                )
                Json.encodeToString(result)
            }

            call.respond(
                HttpStatusCode.Accepted,
                ApiResponse(
                    data = ReanalyzeJobResponse(
                        jobId = job.id,
                        paperId = paperId,
                        message = "Re-analysis job submitted"
                    )
                )
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

        // POST /api/upload/analyze/{paperId} - Analyze paper PDF with LLM (extract title, abstract, Korean translation)
        post("/analyze/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val pdfLLMAnalysisService = ServiceLocator.pdfLLMAnalysisService
            val pdfThumbnailExtractor = ServiceLocator.pdfThumbnailExtractor

            // Get paper
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<LLMAnalysisResponse>(
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
                    ApiResponse<LLMAnalysisResponse>(
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
                    ApiResponse<LLMAnalysisResponse>(
                        success = false,
                        error = "PDF file not found"
                    )
                )
                return@post
            }

            try {
                // Extract text from PDF for LLM analysis
                val analyzer = PdfAnalyzer(pdfPath)
                val pdfText = analyzer.extractFirstPagesText(maxPages = 3)

                // Analyze with LLM
                val analysisResult = pdfLLMAnalysisService.analyzePdf(
                    pdfText = pdfText,
                    rawTitle = paper.title,
                    paperId = paperId
                )

                // Extract thumbnail
                val thumbnailDir = File(pdfFile.parentFile, "thumbnails")
                thumbnailDir.mkdirs()
                val thumbnailPath = File(thumbnailDir, "${paperId}.png").absolutePath
                val extractedThumbnail = pdfThumbnailExtractor.extractThumbnail(pdfPath, thumbnailPath)

                if (analysisResult.isSuccess) {
                    val analysis = analysisResult.getOrThrow()
                    // Update paper with LLM analysis results
                    val updatedPaper = paper.copy(
                        title = analysis.title ?: paper.title,
                        abstract = analysis.abstract ?: paper.abstract,
                        abstractKorean = analysis.abstractKorean ?: paper.abstractKorean,
                        thumbnailPath = extractedThumbnail ?: paper.thumbnailPath,
                        authors = if (analysis.authors.isNotEmpty()) {
                            analysis.authors.mapIndexed { index: Int, name: String ->
                                Author(id = UUID.randomUUID().toString(), name = name, order = index)
                            }
                        } else {
                            paper.authors
                        },
                        updatedAt = Clock.System.now()
                    )

                    paperRepository.update(updatedPaper)

                    call.respond(
                        ApiResponse(
                            data = LLMAnalysisResponse(
                                paperId = paperId,
                                title = analysis.title,
                                authors = analysis.authors,
                                abstract = analysis.abstract,
                                abstractKorean = analysis.abstractKorean,
                                thumbnailPath = extractedThumbnail
                            )
                        )
                    )
                } else {
                    val error = analysisResult.exceptionOrNull()
                    // Even if LLM fails, we might have a thumbnail
                    if (extractedThumbnail != null) {
                        val updatedPaper = paper.copy(
                            thumbnailPath = extractedThumbnail,
                            updatedAt = Clock.System.now()
                        )
                        paperRepository.update(updatedPaper)
                    }

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<LLMAnalysisResponse>(
                            success = false,
                            error = "LLM analysis failed: ${error?.message}"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<LLMAnalysisResponse>(
                        success = false,
                        error = "Analysis failed: ${e.message}"
                    )
                )
            }
        }

        // GET /api/upload/thumbnail/{paperId} - Get or generate thumbnail for a paper
        get("/thumbnail/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val pdfThumbnailExtractor = ServiceLocator.pdfThumbnailExtractor

            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(HttpStatusCode.NotFound, "Paper not found")
                return@get
            }

            // If thumbnail already exists, serve it
            val existingThumbnail = paper.thumbnailPath
            if (!existingThumbnail.isNullOrBlank()) {
                val thumbnailFile = File(existingThumbnail)
                if (thumbnailFile.exists()) {
                    call.respondFile(thumbnailFile)
                    return@get
                }
            }

            // Generate thumbnail on the fly
            val pdfPath = paper.pdfPath
            if (pdfPath.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound, "Paper has no PDF")
                return@get
            }

            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                call.respond(HttpStatusCode.NotFound, "PDF file not found")
                return@get
            }

            // Generate and save thumbnail
            val thumbnailDir = File(pdfFile.parentFile, "thumbnails")
            thumbnailDir.mkdirs()
            val thumbnailPath = File(thumbnailDir, "${paperId}.png").absolutePath

            val generatedPath: String? = pdfThumbnailExtractor.extractThumbnail(pdfPath, thumbnailPath)
            if (generatedPath != null) {
                // Update paper with thumbnail path
                val updatedPaper = paper.copy(
                    thumbnailPath = generatedPath,
                    updatedAt = Clock.System.now()
                )
                paperRepository.update(updatedPaper)

                val thumbnailFile = java.io.File(generatedPath)
                call.respondFile(thumbnailFile)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to generate thumbnail")
            }
        }
    }
}

@Serializable
data class LLMAnalysisResponse(
    val paperId: String,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val abstract: String? = null,
    val abstractKorean: String? = null,
    val thumbnailPath: String? = null
)
