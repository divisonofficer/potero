package com.potero.server.routes

import com.potero.domain.model.Author
import com.potero.domain.model.Paper
import com.potero.domain.model.Tag
import com.potero.domain.repository.SettingsKeys
import com.potero.server.di.ServiceLocator
import com.potero.service.job.GlobalJobQueue
import com.potero.service.job.JobType
import com.potero.service.metadata.SearchResult
import com.potero.service.pdf.PdfAnalyzer
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
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
 * Response for async re-extract job submission
 */
@Serializable
data class ReExtractJobResponse(
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
    val needsUserConfirmation: Boolean = false,
    // References analysis result (integrated)
    val referencesCount: Int = 0,
    val referencesStartPage: Int? = null,
    // Auto-tagging result (integrated)
    val autoTaggedCount: Int = 0,
    val autoTaggedTags: List<String> = emptyList()
)

fun Route.uploadRoutes() {
    val paperRepository = ServiceLocator.paperRepository
    val settingsRepository = ServiceLocator.settingsRepository
    val doiResolver = ServiceLocator.doiResolver
    val arxivResolver = ServiceLocator.arxivResolver
    val semanticScholarResolver = ServiceLocator.semanticScholarResolver
    val pdfPreprocessingService = ServiceLocator.pdfPreprocessingService

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

            // Generate thumbnail
            var thumbnailPath: String? = null
            try {
                val thumbnailDir = File(storagePath, "thumbnails")
                thumbnailDir.mkdirs()
                val thumbnailFile = File(thumbnailDir, "${paperId}.png")
                val analyzer = PdfAnalyzer(targetFile.absolutePath)
                if (analyzer.generateThumbnail(thumbnailFile.absolutePath, width = 200)) {
                    thumbnailPath = thumbnailFile.absolutePath
                    println("[Upload] Generated thumbnail: $thumbnailPath")
                }
            } catch (e: Exception) {
                println("[Upload] Thumbnail generation failed: ${e.message}")
            }

            // Create paper entry
            val now = Clock.System.now()
            val paper = Paper(
                id = paperId,
                title = paperTitle,
                pdfPath = targetFile.absolutePath,
                thumbnailPath = thumbnailPath,
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
                    // Trigger PDF preprocessing in background
                    call.application.launch {
                        try {
                            pdfPreprocessingService.preprocessPaper(
                                paperId = insertedPaper.id,
                                pdfPath = targetFile.absolutePath,
                                forceReprocess = false
                            ).onFailure { e ->
                                println("[Upload] Preprocessing failed for ${insertedPaper.id}: ${e.message}")
                            }
                        } catch (e: Exception) {
                            println("[Upload] Preprocessing exception for ${insertedPaper.id}: ${e.message}")
                        }
                    }

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

            // Check if there's already an active re-analysis job for this paper
            val jobQueue = GlobalJobQueue.instance
            if (jobQueue.hasActiveJobForPaper(paperId, JobType.PDF_REANALYSIS)) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse<ReanalyzeJobResponse>(
                        success = false,
                        error = "A re-analysis job is already running for this paper"
                    )
                )
                return@post
            }

            // Submit async job
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

                // Step 4: Generate thumbnail if missing
                ctx.updateProgress(82, "Generating thumbnail...")
                try {
                    val freshPaper = paperRepository.getById(paperId).getOrNull()
                    if (freshPaper != null && (freshPaper.thumbnailPath.isNullOrBlank() || !File(freshPaper.thumbnailPath!!).exists())) {
                        val pdfFile = File(pdfPath)
                        val thumbnailDir = File(pdfFile.parentFile, "thumbnails")
                        thumbnailDir.mkdirs()
                        val thumbnailPath = File(thumbnailDir, "${paperId}.png").absolutePath

                        val pdfThumbnailExtractor = ServiceLocator.pdfThumbnailExtractor
                        val generatedPath = pdfThumbnailExtractor.extractThumbnail(pdfPath, thumbnailPath)
                        if (generatedPath != null) {
                            val updatedWithThumb = freshPaper.copy(
                                thumbnailPath = generatedPath,
                                updatedAt = Clock.System.now()
                            )
                            paperRepository.update(updatedWithThumb)
                            println("[Re-analysis] Generated thumbnail: $generatedPath")
                        } else {
                            println("[Re-analysis] Thumbnail extraction returned null for $paperId")
                        }
                    }
                } catch (e: Exception) {
                    println("[Re-analysis] Thumbnail generation failed: ${e.message}")
                    e.printStackTrace()
                }

                // Step 5: Analyze references section
                ctx.updateProgress(85, "Analyzing references section...")

                var referencesCount = 0
                var referencesStartPage: Int? = null
                try {
                    val referenceRepository = ServiceLocator.referenceRepository
                    val referencesResult = analyzer.analyzeReferences()

                    // Delete existing references for this paper
                    referenceRepository.deleteByPaperId(paperId)

                    // Convert and save new references
                    val refNow = Clock.System.now()
                    val references = referencesResult.references.map { parsed ->
                        com.potero.domain.model.Reference(
                            id = UUID.randomUUID().toString(),
                            paperId = paperId,
                            number = parsed.number,
                            rawText = parsed.rawText,
                            authors = parsed.authors,
                            title = parsed.title,
                            venue = parsed.venue,
                            year = parsed.year,
                            doi = parsed.doi,
                            pageNum = parsed.pageNum,
                            createdAt = refNow
                        )
                    }

                    if (references.isNotEmpty()) {
                        referenceRepository.insertAll(references)
                    }

                    referencesCount = references.size
                    referencesStartPage = referencesResult.startPage
                    println("[Re-analysis] Extracted $referencesCount references starting at page $referencesStartPage")
                } catch (e: Exception) {
                    println("[Re-analysis] References extraction failed: ${e.message}")
                    // Continue even if references fail
                }

                // Step 6: GROBID processing and citation extraction
                ctx.updateProgress(88, "Processing citations with GROBID...")

                var citationsCount = 0
                try {
                    // Ensure preprocessing is complete (text extraction + OCR) before GROBID
                    val preprocessedPdfProvider = ServiceLocator.preprocessedPdfProvider
                    if (!preprocessedPdfProvider.isPreprocessed(paperId)) {
                        println("[Re-analysis] Triggering preprocessing for $paperId")
                        val preprocessingService = ServiceLocator.pdfPreprocessingService
                        preprocessingService.preprocessPaper(paperId, pdfFile.absolutePath, forceReprocess = false)
                    } else {
                        println("[Re-analysis] Preprocessing already completed, reusing cached text extraction")
                    }

                    // Run GROBID processing (it will use preprocessed text internally)
                    val grobidProcessor = ServiceLocator.grobidProcessor
                    val grobidResult = grobidProcessor.process(paperId, pdfFile.absolutePath)

                    if (grobidResult.isSuccess) {
                        val stats = grobidResult.getOrNull()
                        println("[Re-analysis] GROBID processing completed: ${stats?.citationSpansExtracted} citations, ${stats?.referencesExtracted} references")
                    } else {
                        println("[Re-analysis] GROBID processing failed: ${grobidResult.exceptionOrNull()?.message}")
                    }

                    // Extract citations from PDF and link to references
                    ctx.updateProgress(90, "Extracting and linking citations...")

                    val citationRepository = ServiceLocator.citationRepository
                    val grobidRepository = ServiceLocator.grobidRepository
                    val referenceRepository = ServiceLocator.referenceRepository

                    // Get GROBID data
                    val grobidCitations = grobidRepository.getCitationSpansByPaperId(paperId).getOrDefault(emptyList())
                    val grobidReferences = grobidRepository.getReferencesByPaperId(paperId).getOrDefault(emptyList())

                    // Get references for linking
                    val references = referenceRepository.getByPaperId(paperId).getOrDefault(emptyList())

                    if (references.isNotEmpty()) {
                            // Extract citations from PDF
                            val extractor = com.potero.service.pdf.CitationExtractor(pdfFile.absolutePath)
                            val extractionResult = extractor.extract()

                            // Link citations to references with GROBID enhancement
                            val linker = com.potero.service.pdf.CitationLinker()
                            val linkResults = linker.link(
                                extractionResult.spans,
                                references,
                                referencesStartPage,
                                grobidCitations = grobidCitations,
                                grobidReferences = grobidReferences
                            )

                            // Delete existing citation data
                            citationRepository.deleteSpansByPaperId(paperId)

                            // Save citation spans
                            val citNow = Clock.System.now()
                            val citationSpans = extractionResult.spans.map { raw ->
                                com.potero.domain.model.CitationSpan(
                                    id = UUID.randomUUID().toString(),
                                    paperId = paperId,
                                    pageNum = raw.pageNum,
                                    bbox = com.potero.domain.model.BoundingBox(
                                        x1 = raw.bbox.x1,
                                        y1 = raw.bbox.y1,
                                        x2 = raw.bbox.x2,
                                        y2 = raw.bbox.y2
                                    ),
                                    rawText = raw.rawText,
                                    style = when (raw.style) {
                                        "numeric" -> com.potero.domain.model.CitationStyle.NUMERIC
                                        "author_year" -> com.potero.domain.model.CitationStyle.AUTHOR_YEAR
                                        else -> com.potero.domain.model.CitationStyle.UNKNOWN
                                    },
                                    provenance = if (raw.provenance == "annotation")
                                        com.potero.domain.model.CitationProvenance.ANNOTATION
                                    else
                                        com.potero.domain.model.CitationProvenance.PATTERN,
                                    confidence = raw.confidence,
                                    destPage = raw.destPage,
                                    destY = raw.destY,
                                    createdAt = citNow
                                )
                            }

                            if (citationSpans.isNotEmpty()) {
                                citationRepository.insertAllSpans(citationSpans)
                            }

                            // Save citation links
                            val rawToSavedMap = extractionResult.spans.zip(citationSpans).toMap()
                            val citationLinks = linkResults.mapNotNull { linkResult ->
                                val savedSpan = rawToSavedMap[linkResult.span] ?: return@mapNotNull null
                                com.potero.domain.model.CitationLink(
                                    id = UUID.randomUUID().toString(),
                                    citationSpanId = savedSpan.id,
                                    referenceId = linkResult.reference.id,
                                    linkMethod = linkResult.method,
                                    confidence = linkResult.confidence,
                                    createdAt = citNow
                                )
                            }

                            if (citationLinks.isNotEmpty()) {
                                citationRepository.insertAllLinks(citationLinks)
                            }

                            citationsCount = citationSpans.size
                            println("[Re-analysis] Extracted ${citationSpans.size} citations with ${citationLinks.size} links")
                        }
                } catch (e: Exception) {
                    println("[Re-analysis] Citation extraction failed: ${e.message}")
                    e.printStackTrace()
                    // Continue even if citation extraction fails
                }

                // Step 7: Comprehensive LLM analysis (title, authors, tags, metadata)
                ctx.updateProgress(92, "Analyzing with LLM (comprehensive)...")

                var autoTaggedCount = 0
                var autoTaggedTags = emptyList<String>()
                try {
                    val comprehensiveService = ServiceLocator.comprehensivePaperAnalysisService
                    val tagService = ServiceLocator.tagService
                    val tagRepository = ServiceLocator.tagRepository

                    // Get updated paper (may have new metadata from external APIs)
                    val updatedPaper = paperRepository.getById(paperId).getOrNull() ?: currentPaper

                    // Extract text for better analysis (using preprocessed cache)
                    val preprocessedPdfProvider = ServiceLocator.preprocessedPdfProvider
                    val fullText = try {
                        preprocessedPdfProvider.getFirstPages(paperId, maxPages = 3).getOrElse {
                            analyzer.extractFirstPagesText(maxPages = 3)
                        }
                    } catch (e: Exception) {
                        null
                    }

                    // Get existing tags for context
                    val existingTags = tagRepository.getAll().getOrDefault(emptyList()).map { it.name }

                    // ONE LLM call to extract everything
                    val analysisResult = comprehensiveService.analyzeComprehensive(
                        title = updatedPaper.title,
                        authors = updatedPaper.authors.map { it.name },
                        abstract = updatedPaper.abstract,
                        fullText = fullText,
                        existingTags = existingTags
                    )

                    if (analysisResult.isSuccess) {
                        val llmAnalysis = analysisResult.getOrThrow()

                        println("[Re-analysis] LLM Analysis Result:")
                        println("  - Title: ${llmAnalysis.cleanedTitle}")
                        println("  - Authors: ${llmAnalysis.cleanedAuthors}")
                        println("  - Tags: ${llmAnalysis.tags}")
                        println("  - Year: ${llmAnalysis.year}")
                        println("  - Venue: ${llmAnalysis.venue}")

                        // Update paper with cleaned metadata
                        var finalPaper = updatedPaper

                        println("[Re-analysis] Current paper title: '${updatedPaper.title}'")
                        println("[Re-analysis] LLM cleaned title: '${llmAnalysis.cleanedTitle}'")
                        println("[Re-analysis] Titles equal? ${llmAnalysis.cleanedTitle == updatedPaper.title}")

                        // Check if paper has DOI (authoritative metadata source)
                        val hasAuthoritativeMetadata = !updatedPaper.doi.isNullOrBlank()

                        // Even if DOI exists, check if current metadata is actually correct
                        // by comparing title similarity
                        val titleSimilarity = calculateTitleSimilarity(updatedPaper.title, llmAnalysis.cleanedTitle)
                        println("[Re-analysis] Title similarity: ${"%.2f".format(titleSimilarity)}")

                        // If DOI exists but titles are completely different (< 0.3 similarity),
                        // it means wrong metadata was entered → use LLM result
                        val shouldPreserveMetadata = hasAuthoritativeMetadata && titleSimilarity >= 0.3

                        if (hasAuthoritativeMetadata) {
                            println("[Re-analysis] Paper has DOI: ${updatedPaper.doi}")
                            if (shouldPreserveMetadata) {
                                println("[Re-analysis] Titles are similar - preserving authoritative metadata")
                            } else {
                                println("[Re-analysis] Titles are completely different - current metadata is WRONG, using LLM result")
                            }
                        }

                        // Update title if cleaned version is different
                        // Allow update if: no DOI OR (has DOI but titles are completely different)
                        if (!shouldPreserveMetadata && llmAnalysis.cleanedTitle != updatedPaper.title) {
                            finalPaper = finalPaper.copy(
                                title = llmAnalysis.cleanedTitle,
                                updatedAt = Clock.System.now()
                            )
                            println("[Re-analysis] Cleaned title: '${updatedPaper.title}' -> '${llmAnalysis.cleanedTitle}'")
                        }

                        // Update authors if cleaned
                        // Check if current authors are malformed:
                        // 1. All names concatenated into one (length > 50)
                        // 2. Names contain separators like semicolon or " and "
                        // 3. Author count doesn't match LLM result
                        val hasCorruptedAuthors = updatedPaper.authors.any { author ->
                            author.name.length > 50 ||
                            author.name.contains(";") ||
                            author.name.contains(" and ", ignoreCase = true)
                        } || (updatedPaper.authors.size == 1 && llmAnalysis.cleanedAuthors.size > 1)

                        println("[Re-analysis] Current authors: ${updatedPaper.authors.map { it.name }}")
                        println("[Re-analysis] LLM authors: ${llmAnalysis.cleanedAuthors}")
                        println("[Re-analysis] hasCorruptedAuthors: $hasCorruptedAuthors")

                        // Allow update if: no DOI OR (has DOI but titles are completely different) OR authors are corrupted
                        val shouldUpdateAuthors = !shouldPreserveMetadata || hasCorruptedAuthors

                        if (shouldUpdateAuthors && llmAnalysis.cleanedAuthors != updatedPaper.authors.map { it.name }) {
                            if (hasCorruptedAuthors) {
                                println("[Re-analysis] Detected corrupted authors - fixing with LLM data")
                            }
                            finalPaper = finalPaper.copy(
                                authors = llmAnalysis.cleanedAuthors.mapIndexed { index: Int, name: String ->
                                    Author(id = UUID.randomUUID().toString(), name = name, order = index)
                                },
                                updatedAt = Clock.System.now()
                            )
                            println("[Re-analysis] Cleaned authors: ${updatedPaper.authors.map { it.name }} -> ${llmAnalysis.cleanedAuthors}")
                        }

                        // Update abstract if missing
                        if (updatedPaper.abstract.isNullOrBlank() && !llmAnalysis.abstract.isNullOrBlank()) {
                            finalPaper = finalPaper.copy(
                                abstract = llmAnalysis.abstract,
                                updatedAt = Clock.System.now()
                            )
                        }

                        // Update year/venue if missing
                        if (updatedPaper.year == null && llmAnalysis.year != null) {
                            finalPaper = finalPaper.copy(year = llmAnalysis.year, updatedAt = Clock.System.now())
                        }
                        if (updatedPaper.conference.isNullOrBlank() && !llmAnalysis.venue.isNullOrBlank()) {
                            finalPaper = finalPaper.copy(conference = llmAnalysis.venue, updatedAt = Clock.System.now())
                        }

                        if (finalPaper != updatedPaper) {
                            println("[Re-analysis] Updating paper in DB...")
                            paperRepository.update(finalPaper)
                            println("[Re-analysis] ✓ Paper updated successfully")
                        } else {
                            println("[Re-analysis] No changes to paper metadata")
                        }

                        // Assign tags
                        if (llmAnalysis.tags.isNotEmpty()) {
                            // Get or create tags one by one
                            val assignedTags = mutableListOf<Tag>()
                            for (tagName in llmAnalysis.tags) {
                                val existingTag = tagRepository.getByName(tagName).getOrNull()
                                val tag = existingTag ?: run {
                                    val newTag = Tag(
                                        id = UUID.randomUUID().toString(),
                                        name = tagName,
                                        color = "#6366f1"
                                    )
                                    tagRepository.insert(newTag).getOrNull() ?: newTag
                                }
                                assignedTags.add(tag)
                                tagRepository.linkTagToPaper(paperId, tag.id)
                            }

                            autoTaggedCount = assignedTags.size
                            autoTaggedTags = assignedTags.map { it.name }

                            println("[Re-analysis] Comprehensive analysis: cleaned metadata + ${assignedTags.size} tags: ${autoTaggedTags.joinToString(", ")}")
                        }
                    }
                } catch (e: Exception) {
                    println("[Re-analysis] Comprehensive LLM analysis failed: ${e.message}")
                    // Continue even if analysis fails
                }

                ctx.updateProgress(100, "Re-analysis complete")

                // Return result as JSON
                val result = ReanalyzeResult(
                    paperId = paperId,
                    autoResolved = autoResolved,
                    detectedDoi = detectedDoi,
                    detectedArxivId = detectedArxivId,
                    searchResultCount = searchResults.size,
                    needsUserConfirmation = !autoResolved && searchResults.isNotEmpty(),
                    referencesCount = referencesCount,
                    referencesStartPage = referencesStartPage,
                    autoTaggedCount = autoTaggedCount,
                    autoTaggedTags = autoTaggedTags
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

        // POST /api/upload/re-extract/{paperId} - Force re-extraction of PDF preprocessing data
        post("/re-extract/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            // Check if paper exists
            val existingPaper = paperRepository.getById(paperId).getOrNull()
            if (existingPaper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ReExtractJobResponse>(
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
                    ApiResponse<ReExtractJobResponse>(
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
                    ApiResponse<ReExtractJobResponse>(
                        success = false,
                        error = "PDF file not found at path: $pdfPath"
                    )
                )
                return@post
            }

            // Check if there's already an active re-extract job for this paper
            val jobQueue = GlobalJobQueue.instance
            if (jobQueue.hasActiveJobForPaper(paperId, JobType.PDF_ANALYSIS)) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse<ReExtractJobResponse>(
                        success = false,
                        error = "A re-extraction job is already running for this paper"
                    )
                )
                return@post
            }

            // Submit async job
            val job = jobQueue.submitJob(
                type = JobType.PDF_ANALYSIS,
                title = "Re-extracting: ${existingPaper.title.take(50)}...",
                description = "Force re-extraction of PDF text and preprocessing data",
                paperId = paperId
            ) { ctx ->
                ctx.updateProgress(10, "Starting PDF re-extraction...")

                // Force preprocessing with forceReprocess=true
                val preprocessingService = ServiceLocator.pdfPreprocessingService
                preprocessingService.preprocessPaper(
                    paperId = paperId,
                    pdfPath = pdfFile.absolutePath,
                    forceReprocess = true
                ).onSuccess {
                    ctx.updateProgress(100, "PDF re-extraction completed")
                    println("[Re-extract] Successfully re-extracted PDF for $paperId")
                }.onFailure { error ->
                    ctx.updateProgress(0, "Re-extraction failed: ${error.message}")
                    throw error
                }

                // Return simple success result
                kotlinx.serialization.json.Json.encodeToString("{\"status\": \"completed\", \"paperId\": \"$paperId\"}")
            }

            call.respond(
                HttpStatusCode.Accepted,
                ApiResponse(
                    data = ReExtractJobResponse(
                        jobId = job.id,
                        paperId = paperId,
                        message = "Re-extraction job submitted"
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
                // Extract text from PDF for LLM analysis (using preprocessed cache)
                val preprocessedPdfProvider = ServiceLocator.preprocessedPdfProvider
                val pdfText = preprocessedPdfProvider.getFirstPages(paperId, maxPages = 3)
                    .getOrElse {
                        // Fallback to direct extraction if not preprocessed
                        println("[UploadRoutes] Preprocessing not available for $paperId, using direct extraction")
                        val analyzer = PdfAnalyzer(pdfPath)
                        analyzer.extractFirstPagesText(maxPages = 3)
                    }

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

        // POST /api/upload/thumbnail/{paperId} - Generate thumbnail for a paper (async job)
        post("/thumbnail/{paperId}") {
            val paperId = call.parameters["paperId"]
                ?: throw IllegalArgumentException("Missing paper ID")

            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ThumbnailJobResponse>(
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
                    ApiResponse<ThumbnailJobResponse>(
                        success = false,
                        error = "Paper has no PDF file"
                    )
                )
                return@post
            }

            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<ThumbnailJobResponse>(
                        success = false,
                        error = "PDF file not found"
                    )
                )
                return@post
            }

            // Submit async job
            val jobQueue = GlobalJobQueue.instance
            val job = jobQueue.submitJob(
                type = JobType.THUMBNAIL_GENERATION,
                title = "Generating thumbnail: ${paper.title.take(50)}...",
                description = "Extracting thumbnail from PDF",
                paperId = paperId
            ) { ctx ->
                ctx.updateProgress(20, "Loading PDF...")

                val pdfThumbnailExtractor = ServiceLocator.pdfThumbnailExtractor

                val thumbnailDir = File(pdfFile.parentFile, "thumbnails")
                thumbnailDir.mkdirs()
                val thumbnailPath = File(thumbnailDir, "${paperId}.png").absolutePath

                ctx.updateProgress(50, "Extracting thumbnail image...")

                val generatedPath = pdfThumbnailExtractor.extractThumbnail(pdfPath, thumbnailPath)
                    ?: throw Exception("Failed to extract thumbnail from PDF")

                ctx.updateProgress(80, "Updating paper record...")

                // Update paper with thumbnail path
                val currentPaper = paperRepository.getById(paperId).getOrNull() ?: paper
                val updatedPaper = currentPaper.copy(
                    thumbnailPath = generatedPath,
                    updatedAt = Clock.System.now()
                )
                paperRepository.update(updatedPaper)

                ctx.updateProgress(100, "Thumbnail generated")

                Json.encodeToString(ThumbnailResult(paperId = paperId, thumbnailPath = generatedPath))
            }

            call.respond(
                HttpStatusCode.Accepted,
                ApiResponse(
                    data = ThumbnailJobResponse(
                        jobId = job.id,
                        paperId = paperId,
                        message = "Thumbnail generation started"
                    )
                )
            )
        }

        // POST /api/upload/from-url - Download PDF from URL and create paper entry
        post("/from-url") {
            val request = call.receive<ImportFromUrlRequest>()

            if (request.pdfUrl.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<UploadAnalysisResponse>(
                        success = false,
                        error = "PDF URL is required"
                    )
                )
                return@post
            }

            // Validate URL
            val url = try {
                java.net.URL(request.pdfUrl)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<UploadAnalysisResponse>(
                        success = false,
                        error = "Invalid URL: ${request.pdfUrl}"
                    )
                )
                return@post
            }

            // Get storage path
            val storagePath = settingsRepository.get(SettingsKeys.PDF_STORAGE_PATH).getOrNull()
                ?: "${System.getProperty("user.home")}/.potero/pdfs"
            val storageDir = File(storagePath)
            storageDir.mkdirs()

            // Generate paper ID and filename
            val paperId = UUID.randomUUID().toString()
            val urlFileName = url.path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "paper.pdf"
            val safeFileName = urlFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .let { if (!it.lowercase().endsWith(".pdf")) "$it.pdf" else it }
            val targetFile = File(storageDir, "${paperId}_$safeFileName")

            try {
                // Download PDF with validation
                val httpClient = ServiceLocator.httpClient
                val response = httpClient.get(request.pdfUrl)

                // Check response status
                if (response.status.value !in 200..299) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<UploadAnalysisResponse>(
                            success = false,
                            error = "Failed to download PDF: HTTP ${response.status.value} from ${request.pdfUrl}"
                        )
                    )
                    return@post
                }

                val pdfBytes: ByteArray = response.body()

                // Validate PDF magic bytes (PDF files start with %PDF-)
                if (pdfBytes.size < 5 ||
                    pdfBytes[0] != 0x25.toByte() || // %
                    pdfBytes[1] != 0x50.toByte() || // P
                    pdfBytes[2] != 0x44.toByte() || // D
                    pdfBytes[3] != 0x46.toByte() || // F
                    pdfBytes[4] != 0x2D.toByte()) { // -

                    // Check if it's HTML (might be a login page or error page)
                    val firstChars = String(pdfBytes.take(100).toByteArray())
                    val isHtml = firstChars.contains("<!DOCTYPE", ignoreCase = true) ||
                                 firstChars.contains("<html", ignoreCase = true)

                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<UploadAnalysisResponse>(
                            success = false,
                            error = if (isHtml) {
                                "Download returned HTML instead of PDF. The PDF might require authentication or the link may have expired."
                            } else {
                                "Downloaded file is not a valid PDF (invalid magic bytes). The URL might not point to an actual PDF file."
                            }
                        )
                    )
                    return@post
                }

                // Check minimum size (PDFs should be at least a few KB)
                if (pdfBytes.size < 1024) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<UploadAnalysisResponse>(
                            success = false,
                            error = "Downloaded PDF is too small (${pdfBytes.size} bytes). It might be corrupted or incomplete."
                        )
                    )
                    return@post
                }

                targetFile.writeBytes(pdfBytes)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<UploadAnalysisResponse>(
                        success = false,
                        error = "Failed to download PDF: ${e.message}"
                    )
                )
                return@post
            }

            // Create paper entry with provided metadata
            val now = Clock.System.now()
            val paper = Paper(
                id = paperId,
                title = request.title ?: safeFileName.removeSuffix(".pdf").replace("_", " "),
                pdfPath = targetFile.absolutePath,
                authors = request.authors?.mapIndexed { index, name ->
                    Author(id = UUID.randomUUID().toString(), name = name, order = index)
                } ?: emptyList(),
                tags = emptyList(),
                abstract = request.abstract,
                doi = request.doi,
                arxivId = request.arxivId,
                year = request.year,
                conference = request.venue,
                citationsCount = request.citationsCount ?: 0,
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
                                autoResolved = true,
                                resolvedMetadata = if (request.title != null) {
                                    ResolvedMetadataDto(
                                        title = request.title,
                                        authors = request.authors ?: emptyList(),
                                        abstract = request.abstract,
                                        doi = request.doi,
                                        arxivId = request.arxivId,
                                        year = request.year,
                                        venue = request.venue,
                                        citationsCount = request.citationsCount
                                    )
                                } else null
                            )
                        )
                    )
                },
                onFailure = { error ->
                    // Clean up downloaded file on failure
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

        // POST /api/upload/bulk-reanalyze - Bulk re-analyze papers based on criteria
        post("/bulk-reanalyze") {
            val request = call.receive<BulkReanalyzeRequest>()

            // Get all papers to determine which need re-analysis
            val allPapers = paperRepository.getAll().getOrNull() ?: emptyList()

            val papersToProcess = if (!request.paperIds.isNullOrEmpty()) {
                // Specific paper IDs provided
                allPapers.filter { it.id in request.paperIds }
            } else {
                // Filter by criteria
                val criteria = request.criteria.ifEmpty { listOf("all") }
                allPapers.filter { paper ->
                    criteria.any { criterion ->
                        when (criterion) {
                            "missing_thumbnail" -> paper.thumbnailPath.isNullOrBlank()
                            "missing_venue" -> paper.conference.isNullOrBlank()
                            "missing_doi" -> paper.doi.isNullOrBlank()
                            "missing_abstract" -> paper.abstract.isNullOrBlank()
                            "missing_year" -> paper.year == null
                            "all" -> true
                            else -> false
                        }
                    }
                }
            }.filter { it.pdfPath != null && File(it.pdfPath!!).exists() } // Only papers with valid PDFs

            if (papersToProcess.isEmpty()) {
                call.respond(
                    ApiResponse(
                        data = BulkReanalyzeResponse(
                            jobId = "",
                            papersQueued = 0,
                            message = "No papers match the criteria"
                        )
                    )
                )
                return@post
            }

            // Submit async bulk job
            val jobQueue = GlobalJobQueue.instance
            val pdfThumbnailExtractor = ServiceLocator.pdfThumbnailExtractor

            val job = jobQueue.submitJob(
                type = JobType.BULK_IMPORT,  // Reuse BULK_IMPORT type for bulk operations
                title = "Bulk Re-analysis: ${papersToProcess.size} papers",
                description = "Re-analyzing papers with criteria: ${request.criteria.joinToString(", ")}"
            ) { ctx ->
                var successful = 0
                var failed = 0
                val failures = mutableListOf<BulkReanalyzeFailure>()

                papersToProcess.forEachIndexed { index, paper ->
                    val progress = ((index + 1) * 100) / papersToProcess.size
                    ctx.updateProgress(progress, "Processing: ${paper.title.take(40)}...")

                    try {
                        val pdfPath = paper.pdfPath!!
                        val pdfFile = File(pdfPath)

                        // Generate thumbnail if missing
                        if (paper.thumbnailPath.isNullOrBlank() || !File(paper.thumbnailPath!!).exists()) {
                            val thumbnailDir = File(pdfFile.parentFile, "thumbnails")
                            thumbnailDir.mkdirs()
                            val thumbnailPath = File(thumbnailDir, "${paper.id}.png").absolutePath
                            val generatedPath = pdfThumbnailExtractor.extractThumbnail(pdfPath, thumbnailPath)
                            if (generatedPath != null) {
                                val updatedPaper = paper.copy(
                                    thumbnailPath = generatedPath,
                                    updatedAt = Clock.System.now()
                                )
                                paperRepository.update(updatedPaper)
                            }
                        }

                        // Try to extract DOI/arXiv and resolve metadata if missing key fields
                        if (paper.doi.isNullOrBlank() || paper.conference.isNullOrBlank() || paper.abstract.isNullOrBlank()) {
                            val analyzer = PdfAnalyzer(pdfPath)
                            val firstPagesText = analyzer.extractFirstPagesText(2)
                            val doi = analyzer.findDOI(firstPagesText)
                            val arxivId = analyzer.findArxivId(firstPagesText)

                            // Try to resolve from DOI
                            if (doi != null && paper.doi.isNullOrBlank()) {
                                val resolved = ServiceLocator.doiResolver.resolve(doi).getOrNull()
                                if (resolved != null) {
                                    val currentPaper = paperRepository.getById(paper.id).getOrNull() ?: paper
                                    val updatedPaper = currentPaper.copy(
                                        doi = doi,
                                        conference = resolved.venue ?: currentPaper.conference,
                                        year = resolved.year ?: currentPaper.year,
                                        citationsCount = resolved.citationsCount ?: currentPaper.citationsCount,
                                        abstract = resolved.abstract ?: currentPaper.abstract,
                                        updatedAt = Clock.System.now()
                                    )
                                    paperRepository.update(updatedPaper)
                                }
                            }

                            // Try to resolve from arXiv
                            if (arxivId != null && paper.arxivId.isNullOrBlank()) {
                                val resolved = ServiceLocator.arxivResolver.resolve(arxivId).getOrNull()
                                if (resolved != null) {
                                    val currentPaper = paperRepository.getById(paper.id).getOrNull() ?: paper
                                    val updatedPaper = currentPaper.copy(
                                        arxivId = arxivId,
                                        year = resolved.year ?: currentPaper.year,
                                        abstract = resolved.abstract ?: currentPaper.abstract,
                                        updatedAt = Clock.System.now()
                                    )
                                    paperRepository.update(updatedPaper)
                                }
                            }

                            // Also analyze references
                            try {
                                val referenceRepository = ServiceLocator.referenceRepository
                                val referencesResult = analyzer.analyzeReferences()

                                // Delete existing and save new references
                                referenceRepository.deleteByPaperId(paper.id)
                                val refNow = Clock.System.now()
                                val references = referencesResult.references.map { parsed ->
                                    com.potero.domain.model.Reference(
                                        id = UUID.randomUUID().toString(),
                                        paperId = paper.id,
                                        number = parsed.number,
                                        rawText = parsed.rawText,
                                        authors = parsed.authors,
                                        title = parsed.title,
                                        venue = parsed.venue,
                                        year = parsed.year,
                                        doi = parsed.doi,
                                        pageNum = parsed.pageNum,
                                        createdAt = refNow
                                    )
                                }
                                if (references.isNotEmpty()) {
                                    referenceRepository.insertAll(references)
                                }
                            } catch (refError: Exception) {
                                // References extraction failed, continue
                                println("[Bulk Re-analysis] References failed for ${paper.id}: ${refError.message}")
                            }

                            // Comprehensive LLM analysis (title, authors, tags, metadata)
                            try {
                                val comprehensiveService = ServiceLocator.comprehensivePaperAnalysisService
                                val tagRepository = ServiceLocator.tagRepository
                                val updatedPaper = paperRepository.getById(paper.id).getOrNull() ?: paper

                                // Extract text using preprocessed cache
                                val preprocessedPdfProvider = ServiceLocator.preprocessedPdfProvider
                                val fullText = try {
                                    preprocessedPdfProvider.getFirstPages(paper.id, maxPages = 3).getOrElse {
                                        analyzer.extractFirstPagesText(maxPages = 3)
                                    }
                                } catch (e: Exception) { null }

                                val existingTags = tagRepository.getAll().getOrDefault(emptyList()).map { it.name }

                                val analysisResult = comprehensiveService.analyzeComprehensive(
                                    title = updatedPaper.title,
                                    authors = updatedPaper.authors.map { it.name },
                                    abstract = updatedPaper.abstract,
                                    fullText = fullText,
                                    existingTags = existingTags
                                )

                                if (analysisResult.isSuccess) {
                                    val llmAnalysis = analysisResult.getOrThrow()
                                    var finalPaper = updatedPaper

                                    // Update metadata
                                    if (llmAnalysis.cleanedTitle != updatedPaper.title) {
                                        finalPaper = finalPaper.copy(title = llmAnalysis.cleanedTitle, updatedAt = Clock.System.now())
                                    }
                                    if (llmAnalysis.cleanedAuthors != updatedPaper.authors.map { it.name }) {
                                        finalPaper = finalPaper.copy(
                                            authors = llmAnalysis.cleanedAuthors.mapIndexed { index, name ->
                                                Author(id = UUID.randomUUID().toString(), name = name, order = index)
                                            },
                                            updatedAt = Clock.System.now()
                                        )
                                    }
                                    if (updatedPaper.abstract.isNullOrBlank() && !llmAnalysis.abstract.isNullOrBlank()) {
                                        finalPaper = finalPaper.copy(abstract = llmAnalysis.abstract, updatedAt = Clock.System.now())
                                    }
                                    if (updatedPaper.year == null && llmAnalysis.year != null) {
                                        finalPaper = finalPaper.copy(year = llmAnalysis.year, updatedAt = Clock.System.now())
                                    }
                                    if (updatedPaper.conference.isNullOrBlank() && !llmAnalysis.venue.isNullOrBlank()) {
                                        finalPaper = finalPaper.copy(conference = llmAnalysis.venue, updatedAt = Clock.System.now())
                                    }

                                    if (finalPaper != updatedPaper) {
                                        paperRepository.update(finalPaper)
                                    }

                                    // Assign tags
                                    if (llmAnalysis.tags.isNotEmpty()) {
                                        for (tagName in llmAnalysis.tags) {
                                            val existingTag = tagRepository.getByName(tagName).getOrNull()
                                            val tag = existingTag ?: run {
                                                val newTag = Tag(id = UUID.randomUUID().toString(), name = tagName, color = "#6366f1")
                                                tagRepository.insert(newTag).getOrNull() ?: newTag
                                            }
                                            tagRepository.linkTagToPaper(paper.id, tag.id)
                                        }
                                        println("[Bulk Re-analysis] Comprehensive analysis for ${paper.id}: ${llmAnalysis.tags.size} tags")
                                    }
                                }
                            } catch (tagError: Exception) {
                                println("[Bulk Re-analysis] Comprehensive analysis failed for ${paper.id}: ${tagError.message}")
                            }
                        }

                        successful++
                    } catch (e: Exception) {
                        failed++
                        failures.add(BulkReanalyzeFailure(
                            paperId = paper.id,
                            title = paper.title,
                            error = e.message ?: "Unknown error"
                        ))
                    }
                }

                ctx.updateProgress(100, "Completed: $successful success, $failed failed")

                Json.encodeToString(BulkReanalyzeResult(
                    totalProcessed = papersToProcess.size,
                    successful = successful,
                    failed = failed,
                    failures = failures
                ))
            }

            call.respond(
                HttpStatusCode.Accepted,
                ApiResponse(
                    data = BulkReanalyzeResponse(
                        jobId = job.id,
                        papersQueued = papersToProcess.size,
                        message = "Bulk re-analysis started for ${papersToProcess.size} papers"
                    )
                )
            )
        }

        // GET /api/upload/bulk-reanalyze/preview - Preview which papers would be affected
        post("/bulk-reanalyze/preview") {
            val request = call.receive<BulkReanalyzeRequest>()

            val allPapers = paperRepository.getAll().getOrNull() ?: emptyList()

            val papersToProcess = if (!request.paperIds.isNullOrEmpty()) {
                allPapers.filter { it.id in request.paperIds }
            } else {
                val criteria = request.criteria.ifEmpty { listOf("all") }
                allPapers.filter { paper ->
                    criteria.any { criterion ->
                        when (criterion) {
                            "missing_thumbnail" -> paper.thumbnailPath.isNullOrBlank()
                            "missing_venue" -> paper.conference.isNullOrBlank()
                            "missing_doi" -> paper.doi.isNullOrBlank()
                            "missing_abstract" -> paper.abstract.isNullOrBlank()
                            "missing_year" -> paper.year == null
                            "all" -> true
                            else -> false
                        }
                    }
                }
            }.filter { it.pdfPath != null && File(it.pdfPath!!).exists() }

            call.respond(
                ApiResponse(
                    data = BulkReanalyzePreview(
                        totalPapers = papersToProcess.size,
                        papers = papersToProcess.map { paper ->
                            BulkReanalyzePaperPreview(
                                id = paper.id,
                                title = paper.title,
                                missingFields = buildList {
                                    if (paper.thumbnailPath.isNullOrBlank()) add("thumbnail")
                                    if (paper.conference.isNullOrBlank()) add("venue")
                                    if (paper.doi.isNullOrBlank()) add("doi")
                                    if (paper.abstract.isNullOrBlank()) add("abstract")
                                    if (paper.year == null) add("year")
                                }
                            )
                        }
                    )
                )
            )
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

/**
 * Response for thumbnail generation job submission
 */
@Serializable
data class ThumbnailJobResponse(
    val jobId: String,
    val paperId: String,
    val message: String
)

/**
 * Result data stored in job after thumbnail generation completes
 */
@Serializable
data class ThumbnailResult(
    val paperId: String,
    val thumbnailPath: String
)

/**
 * Request to import a paper by downloading PDF from URL
 */
@Serializable
data class ImportFromUrlRequest(
    val pdfUrl: String,
    val title: String? = null,
    val authors: List<String>? = null,
    val abstract: String? = null,
    val doi: String? = null,
    val arxivId: String? = null,
    val year: Int? = null,
    val venue: String? = null,
    val citationsCount: Int? = null
)

/**
 * Request for bulk re-analysis
 */
@Serializable
data class BulkReanalyzeRequest(
    val criteria: List<String> = emptyList(),  // "missing_thumbnail", "missing_venue", "missing_doi", "missing_abstract", "all"
    val paperIds: List<String>? = null  // If specified, only these papers (overrides criteria)
)

/**
 * Response for bulk re-analysis job
 */
@Serializable
data class BulkReanalyzeResponse(
    val jobId: String,
    val papersQueued: Int,
    val message: String
)

/**
 * Result of bulk re-analysis
 */
@Serializable
data class BulkReanalyzeResult(
    val totalProcessed: Int,
    val successful: Int,
    val failed: Int,
    val failures: List<BulkReanalyzeFailure>
)

@Serializable
data class BulkReanalyzeFailure(
    val paperId: String,
    val title: String,
    val error: String
)

/**
 * Preview of papers that would be affected by bulk re-analysis
 */
@Serializable
data class BulkReanalyzePreview(
    val totalPapers: Int,
    val papers: List<BulkReanalyzePaperPreview>
)

@Serializable
data class BulkReanalyzePaperPreview(
    val id: String,
    val title: String,
    val missingFields: List<String>
)

/**
 * Calculate similarity between two titles based on common words.
 * Returns a value between 0.0 (completely different) and 1.0 (identical).
 *
 * Examples:
 * - "Monte-Carlo 시뮬레이션..." vs "Differentiable Mobile..." → ~0.0
 * - "Pixel-aligned RGB-NIR Stereo" vs "Pixel-Aligned RGB-NIR Stereo System" → ~0.8
 */
private fun calculateTitleSimilarity(title1: String, title2: String): Double {
    // Normalize: lowercase and split into words
    val words1 = title1.lowercase()
        .split(Regex("""[\s\-_:,;.]+"""))
        .filter { it.length >= 3 } // Skip very short words
        .toSet()

    val words2 = title2.lowercase()
        .split(Regex("""[\s\-_:,;.]+"""))
        .filter { it.length >= 3 }
        .toSet()

    if (words1.isEmpty() || words2.isEmpty()) return 0.0

    // Calculate Jaccard similarity: |intersection| / |union|
    val intersection = words1.intersect(words2).size
    val union = words1.union(words2).size

    return intersection.toDouble() / union.toDouble()
}
