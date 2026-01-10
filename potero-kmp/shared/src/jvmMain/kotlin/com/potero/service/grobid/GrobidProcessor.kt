package com.potero.service.grobid

import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference
import com.potero.domain.repository.GrobidRepository
import com.potero.domain.repository.PaperRepository
import com.potero.domain.repository.SettingsKeys
import com.potero.domain.repository.SettingsRepository
import com.potero.service.pdf.PdfAnalyzer
import com.potero.service.pdf.PdfDownloadService
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Statistics from GROBID processing
 */
data class GrobidProcessingStats(
    val citationSpansExtracted: Int,
    val referencesExtracted: Int,
    val processingTimeMs: Long
)

/**
 * Orchestrates GROBID processing and database storage with LLM fallback.
 *
 * Workflow:
 * 1. Try GROBID processing first
 * 2. If GROBID fails (OOM, timeout, error), fall back to LLM-based parsing
 * 3. Convert to domain models
 * 4. Store in database via GrobidRepository
 *
 * Error handling:
 * - GROBID failures trigger LLM fallback (non-fatal)
 * - LLM fallback failures are logged (non-fatal)
 * - Database errors are propagated as Result failures
 */
class GrobidProcessor(
    private val grobidEngine: GrobidEngine,
    private val grobidRepository: GrobidRepository,
    private val llmReferenceParser: LLMReferenceParser,
    private val paperRepository: PaperRepository,
    private val pdfDownloadService: PdfDownloadService,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private val LOG_FILE = File(System.getProperty("user.home"), ".grobid/grobid-processor.log")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        init {
            // Ensure log directory exists
            LOG_FILE.parentFile?.mkdirs()
        }

        /**
         * Log message to both console and file with timestamp
         */
        private fun log(message: String) {
            val timestamp = LocalDateTime.now().format(DATE_FORMATTER)
            val logLine = "[$timestamp] $message"

            // Print to console
            println(logLine)

            // Append to log file
            try {
                FileWriter(LOG_FILE, true).use { writer ->
                    writer.appendLine(logLine)
                }
            } catch (e: Exception) {
                // Fail silently if file logging fails - don't break the flow
                System.err.println("Failed to write to log file: ${e.message}")
            }
        }
    }

    /**
     * Process a PDF with GROBID and store results in database.
     * Falls back to LLM-based parsing if GROBID fails.
     *
     * @param paperId The paper ID to associate with extracted data
     * @param pdfPath Absolute path to the PDF file
     * @return Result with processing statistics or error
     */
    suspend fun process(paperId: String, pdfPath: String): Result<GrobidProcessingStats> {
        val startTime = System.currentTimeMillis()

        return runCatching {
            log("[GrobidProcessor] Starting processing for paper: $paperId")

            // Step 1: Try GROBID first
            var currentPdfPath = pdfPath
            val teiDocument = try {
                grobidEngine.processFulltext(currentPdfPath)
            } catch (e: GrobidException) {
                log("[GrobidProcessor] GROBID failed: ${e.message}")

                // Step 1.5: Try arXiv PDF fallback before LLM
                val arxivPdfPath = tryArxivPdfFallback(paperId)
                if (arxivPdfPath != null) {
                    log("[GrobidProcessor] arXiv PDF downloaded, retrying GROBID...")
                    currentPdfPath = arxivPdfPath

                    try {
                        // Retry GROBID with arXiv PDF
                        val arxivTeiDocument = grobidEngine.processFulltext(arxivPdfPath)
                        log("[GrobidProcessor] ✓ GROBID succeeded with arXiv PDF!")

                        // Continue with normal GROBID flow (jump to line 153)
                        arxivTeiDocument
                    } catch (arxivGrobidError: GrobidException) {
                        log("[GrobidProcessor] GROBID failed even with arXiv PDF: ${arxivGrobidError.message}")
                        log("[GrobidProcessor] Falling back to LLM...")

                        // Fall through to LLM fallback
                        null
                    }
                } else {
                    log("[GrobidProcessor] No arXiv PDF available, attempting LLM fallback...")
                    null
                }
            } ?: run {
                // GROBID failed and arXiv retry also failed → LLM Fallback

                // Step 2: LLM Fallback - Extract References text from PDF
                val referencesText = try {
                    val analyzer = PdfAnalyzer(pdfPath)
                    val result = analyzer.analyzeReferences()

                    if (result.references.isNotEmpty()) {
                        // PdfAnalyzer found references - use structured format
                        log("[GrobidProcessor] PdfAnalyzer found ${result.references.size} references")
                        result.references.joinToString("\n\n") { ref ->
                            "[${ref.number}] ${ref.rawText}"
                        }
                    } else {
                        // PdfAnalyzer failed to find References section - extract last pages as fallback
                        log("[GrobidProcessor] PdfAnalyzer failed to find References section")
                        log("[GrobidProcessor] Extracting last 15 pages for LLM analysis...")

                        extractLastPagesText(pdfPath, maxPages = 15)
                    }
                } catch (pdfError: Exception) {
                    log("[GrobidProcessor] PDF extraction failed: ${pdfError.message}")
                    return@runCatching GrobidProcessingStats(
                        citationSpansExtracted = 0,
                        referencesExtracted = 0,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // Step 3: Parse with LLM
                val llmReferences = llmReferenceParser.parse(referencesText, paperId)
                    .getOrElse { llmError: Throwable ->
                        log("[GrobidProcessor] LLM parsing failed: ${llmError.message}")
                        emptyList<GrobidReference>()
                    }

                if (llmReferences.isEmpty()) {
                    log("[GrobidProcessor] LLM fallback produced no references")
                    return@runCatching GrobidProcessingStats(
                        citationSpansExtracted = 0,
                        referencesExtracted = 0,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // Step 4: Save LLM references to database
                grobidRepository.deleteReferencesByPaperId(paperId).getOrThrow()
                grobidRepository.insertAllReferences(llmReferences).getOrThrow()

                val time = System.currentTimeMillis() - startTime
                log("[GrobidProcessor] LLM fallback completed: ${llmReferences.size} references in ${time}ms")

                return@runCatching GrobidProcessingStats(
                    citationSpansExtracted = 0,  // LLM doesn't extract citation spans
                    referencesExtracted = llmReferences.size,
                    processingTimeMs = time
                )
            }

            // Step 5: GROBID succeeded - normal flow
            log("[GrobidProcessor] TEI extracted: ${teiDocument.body.citationSpans.size} citations, ${teiDocument.references.size} references")

            // Convert TEI models to domain models
            val citationSpans = convertCitationSpans(paperId, teiDocument.body.citationSpans)
            val references = convertReferences(paperId, teiDocument.references)

            // Delete old GROBID data for this paper (if any)
            grobidRepository.deleteCitationSpansByPaperId(paperId).getOrThrow()
            grobidRepository.deleteReferencesByPaperId(paperId).getOrThrow()

            // Store in database
            grobidRepository.insertAllCitationSpans(citationSpans).getOrThrow()
            grobidRepository.insertAllReferences(references).getOrThrow()

            val processingTime = System.currentTimeMillis() - startTime
            log("[GrobidProcessor] GROBID processing completed in ${processingTime}ms")

            GrobidProcessingStats(
                citationSpansExtracted = citationSpans.size,
                referencesExtracted = references.size,
                processingTimeMs = processingTime
            )
        }
    }

    /**
     * Convert TEI citation spans to domain models.
     */
    private fun convertCitationSpans(
        paperId: String,
        teiSpans: List<TEICitationSpan>
    ): List<GrobidCitationSpan> {
        val now = Clock.System.now()

        return teiSpans.flatMap { teiSpan ->
            // Group by page (a citation can span multiple lines/boxes on same page)
            val pageGroups = teiSpan.bboxes.groupBy { it.pageNum }

            pageGroups.map { (pageNum, bboxes) ->
                GrobidCitationSpan(
                    id = UUID.randomUUID().toString(),
                    paperId = paperId,
                    pageNum = pageNum,
                    rawText = teiSpan.rawText,
                    xmlId = teiSpan.xmlId,
                    refType = teiSpan.refType,
                    targetXmlId = teiSpan.targetXmlId,
                    confidence = 0.95,  // GROBID TEI data is high confidence
                    createdAt = now
                )
            }
        }
    }

    /**
     * Convert TEI references to domain models.
     *
     * Applies text normalization to clean up raw TEI content.
     */
    private fun convertReferences(
        paperId: String,
        teiRefs: List<TEIReference>
    ): List<GrobidReference> {
        val now = Clock.System.now()

        return teiRefs.map { teiRef ->
            // Apply normalization to raw TEI text
            val normalizedRawTei = teiRef.rawTei.let { normalizeReferenceText(it) }

            // Extract page number from first bbox (if available)
            val pageNum = teiRef.bboxes.firstOrNull()?.pageNum

            GrobidReference(
                id = UUID.randomUUID().toString(),
                paperId = paperId,
                xmlId = teiRef.xmlId,
                rawTei = normalizedRawTei,
                authors = teiRef.authors,
                title = teiRef.title,
                venue = teiRef.venue,
                year = teiRef.year,
                doi = teiRef.doi,
                arxivId = null,  // TODO: Extract from TEI if available
                pageNum = pageNum,
                confidence = 0.95,
                createdAt = now
            )
        }
    }

    /**
     * Normalize reference text to improve search quality.
     *
     * 1. Fix hyphenated line breaks: "inter-\npolation" → "interpolation"
     * 2. Remove trailing in-paper page numbers: "... 2013. 2, 6, 7" → "... 2013."
     * 3. Collapse multiple spaces
     */
    private fun normalizeReferenceText(text: String): String {
        var normalized = text

        // 1. Fix hyphenated line breaks
        normalized = normalized.replace(Regex("""(\w)-\s*\n\s*(\w)"""), "$1$2")

        // 2. Remove trailing in-paper page numbers
        normalized = normalized.replace(Regex("""\.\s+(?:\d+\s*,\s*)*\d+\s*$"""), ".")

        // 3. Collapse multiple spaces
        normalized = normalized.replace(Regex("""\s+"""), " ")

        return normalized.trim()
    }

    /**
     * Check if extracted text looks garbled (encoding/mapping issues).
     *
     * Heuristics:
     * - High ratio of control characters (0x00-0x1F, 0x7F)
     * - Low ratio of printable characters
     * - Very low ratio of letters (main indicator)
     *
     * This indicates ToUnicode CMap issues in PDF fonts.
     */
    private fun isGarbled(text: String): Boolean {
        if (text.isBlank()) return true

        val len = text.length

        // Count control characters (likely mapping errors)
        val controlChars = text.count { it.code in 0..31 || it.code == 127 }
        val controlRatio = controlChars.toDouble() / len

        // Count letters specifically (most important indicator)
        val letters = text.count { it.isLetter() }
        val letterRatio = letters.toDouble() / len

        // Count printable characters (letters, digits, common punctuation)
        val printableChars = text.count { ch ->
            ch.isLetterOrDigit() || ch.isWhitespace() ||
            ch in listOf('.', ',', ';', ':', '-', '–', '—', '(', ')', '[', ']',
                         '{', '}', '/', '\\', '"', '\'', '?', '!')
        }
        val printableRatio = printableChars.toDouble() / len

        // STRICTER: >0.5% control chars OR <40% letters OR <65% printable = garbled
        val isGarbled = controlRatio > 0.005 || letterRatio < 0.40 || printableRatio < 0.65

        log("[GrobidProcessor] Text quality check: control=${String.format("%.2f%%", controlRatio * 100)}, letters=${String.format("%.2f%%", letterRatio * 100)}, printable=${String.format("%.2f%%", printableRatio * 100)} -> ${if (isGarbled) "GARBLED ✗" else "OK ✓"}")

        return isGarbled
    }

    /**
     * Try arXiv PDF fallback when current PDF is garbled.
     * Downloads arXiv version if available and returns new PDF path.
     *
     * @param paperId Paper ID to look up arXiv ID
     * @return New PDF path if arXiv download succeeded, null otherwise
     */
    private suspend fun tryArxivPdfFallback(paperId: String): String? {
        return try {
            log("[GrobidProcessor] Attempting arXiv PDF fallback for paper: $paperId")

            // Check if arXiv fallback is enabled
            val strategyEnabled = settingsRepository.get(SettingsKeys.GARBLED_PDF_ARXIV_AUTO_DOWNLOAD)
                .getOrNull() == "true"

            if (!strategyEnabled) {
                log("[GrobidProcessor] arXiv auto-download is disabled in settings")
                return null
            }

            // Get paper from database to check arXiv ID
            val paper = paperRepository.getById(paperId).getOrNull()
            if (paper == null) {
                log("[GrobidProcessor] Paper not found in database: $paperId")
                return null
            }

            val arxivId = paper.arxivId
            if (arxivId.isNullOrBlank()) {
                log("[GrobidProcessor] No arXiv ID found for paper: $paperId")
                return null
            }

            log("[GrobidProcessor] Found arXiv ID: $arxivId, attempting download...")

            // Download arXiv PDF
            val downloadResult = pdfDownloadService.downloadFromArxiv(arxivId)
            val arxivPdfPath = downloadResult.getOrNull()

            if (arxivPdfPath != null) {
                log("[GrobidProcessor] ✓ arXiv PDF downloaded successfully: $arxivPdfPath")

                // Update paper's PDF path to arXiv version
                paper.copy(pdfPath = arxivPdfPath).let { updatedPaper ->
                    paperRepository.update(updatedPaper).getOrNull()
                }

                arxivPdfPath
            } else {
                log("[GrobidProcessor] ✗ arXiv PDF download failed")
                null
            }
        } catch (e: Exception) {
            log("[GrobidProcessor] arXiv fallback error: ${e.message}")
            null
        }
    }

    /**
     * Extract text using pdftotext (poppler) as fallback.
     * Better at handling PDFs with ToUnicode CMap issues.
     *
     * @param pdfPath Path to PDF file
     * @param startPage First page (1-indexed)
     * @param endPage Last page (1-indexed)
     * @return Extracted text or null if pdftotext not available
     */
    private fun extractWithPdftotext(pdfPath: String, startPage: Int, endPage: Int): String? {
        return try {
            log("[GrobidProcessor] Trying pdftotext fallback for pages $startPage-$endPage")

            val process = ProcessBuilder(
                "pdftotext",
                "-f", startPage.toString(),
                "-l", endPage.toString(),
                "-layout",          // Preserve layout (helps with 2-column)
                "-enc", "UTF-8",    // Force UTF-8 output
                pdfPath,
                "-"                 // Output to stdout
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank()) {
                log("[GrobidProcessor] pdftotext succeeded: ${output.length} chars")

                // Log preview to verify quality
                val preview = output.take(500).replace("\n", "\\n")
                log("[GrobidProcessor] pdftotext preview: $preview")

                output
            } else {
                log("[GrobidProcessor] pdftotext failed with exit code $exitCode")
                null
            }
        } catch (e: Exception) {
            log("[GrobidProcessor] pdftotext not available: ${e.message}")
            null
        }
    }

    /**
     * Extract text from the last N pages of a PDF with fallback strategies.
     * Used as fallback when PdfAnalyzer fails to find References section.
     *
     * Strategy:
     * 1. Try PDFBox with position-based sorting
     * 2. Check if text is garbled (ToUnicode CMap issues)
     * 3. If garbled, try pdftotext (poppler) as fallback
     * 4. If still garbled, return what we have (LLM might still extract some info)
     *
     * @param pdfPath Path to PDF file
     * @param maxPages Maximum number of pages to extract from end
     * @return Combined text from last pages
     */
    private fun extractLastPagesText(pdfPath: String, maxPages: Int = 15): String {
        return try {
            val pdfFile = java.io.File(pdfPath)
            val document = org.apache.pdfbox.Loader.loadPDF(pdfFile)

            val totalPages = document.numberOfPages
            val startPage = maxOf(1, totalPages - maxPages + 1)
            val endPage = totalPages

            try {
                log("[GrobidProcessor] Extracting pages $startPage-$endPage of $totalPages")

                // Step 1: Try PDFBox extraction
                val textBuilder = StringBuilder()
                var garbledPagesCount = 0
                var isCVFPdf = false  // Detect CVF Open Access PDFs (known to have ToUnicode issues)

                for (pageNum in startPage..endPage) {
                    val stripper = org.apache.pdfbox.text.PDFTextStripper()
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum

                    // CRITICAL: Enable position-based sorting for multi-column PDFs
                    // This ensures text is extracted in reading order (left-to-right, top-to-bottom)
                    stripper.setSortByPosition(true)

                    val pageText = stripper.getText(document)

                    // Detect CVF Open Access PDF (first page only)
                    if (pageNum == startPage && pageText.contains("This CVPR paper is the Open Access version", ignoreCase = true)) {
                        isCVFPdf = true
                        log("[GrobidProcessor] ⚠️  CVF Open Access PDF detected - these often have ToUnicode CMap issues")
                    }

                    // Check if this page is garbled
                    if (isGarbled(pageText)) {
                        garbledPagesCount++
                    }

                    textBuilder.append("<<<PAGE $pageNum>>>\n")
                    textBuilder.append(pageText)
                    textBuilder.append("\n\n")
                }

                val pdfboxResult = textBuilder.toString()
                val garbledRatio = garbledPagesCount.toDouble() / maxPages

                log("[GrobidProcessor] PDFBox extracted ${pdfboxResult.length} chars, ${garbledPagesCount}/${maxPages} pages garbled (${String.format("%.0f%%", garbledRatio * 100)})")

                // Log a preview to verify extraction quality
                val preview = pdfboxResult.take(500).replace("\n", "\\n")
                log("[GrobidProcessor] Text preview: $preview")

                // Step 2: If >10% pages are garbled, try pdftotext fallback (lowered from 30%)
                if (garbledRatio > 0.10) {
                    log("[GrobidProcessor] High garbled ratio (${String.format("%.0f%%", garbledRatio * 100)}) detected, trying pdftotext fallback")

                    val pdftotextResult = extractWithPdftotext(pdfPath, startPage, endPage)

                    if (pdftotextResult != null && !isGarbled(pdftotextResult)) {
                        log("[GrobidProcessor] ✓ pdftotext produced clean text, using it instead of PDFBox")
                        return pdftotextResult
                    } else if (pdftotextResult != null) {
                        log("[GrobidProcessor] ✗ pdftotext also returned garbled text, falling back to PDFBox result")
                    } else {
                        log("[GrobidProcessor] ✗ pdftotext unavailable or failed, falling back to PDFBox result")
                    }
                }

                pdfboxResult
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            log("[GrobidProcessor] Failed to extract last pages: ${e.message}")
            throw e
        }
    }
}
