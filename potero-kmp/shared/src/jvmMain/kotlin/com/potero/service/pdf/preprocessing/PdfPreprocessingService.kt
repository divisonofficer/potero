package com.potero.service.pdf.preprocessing

import com.potero.domain.model.*
import com.potero.domain.repository.PaperRepository
import com.potero.domain.repository.PdfPreprocessingRepository
import com.potero.domain.repository.SettingsKeys
import com.potero.domain.repository.SettingsRepository
import com.potero.service.grobid.GrobidProcessor
import com.potero.service.ocr.PdfOcrService
import com.potero.service.pdf.PdfAnalyzer
import com.potero.service.pdf.PdfDownloadService
import kotlinx.datetime.Clock
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * PDF Preprocessing Service - Independent text extraction and caching.
 *
 * Consolidates multi-layer PDF extraction logic from GrobidProcessor:
 * - PDFBox extraction (fastest)
 * - pdftotext fallback (for ToUnicode CMap issues)
 * - OCR fallback (for garbled/scanned PDFs)
 * - arXiv PDF fallback (download clean version)
 * - GROBID structured extraction (citations + references)
 *
 * All extraction results are cached in database to eliminate duplicate processing.
 */
class PdfPreprocessingService(
    private val preprocessingRepository: PdfPreprocessingRepository,
    private val grobidProcessor: GrobidProcessor,
    private val pdfOcrService: PdfOcrService,
    private val pdfDownloadService: PdfDownloadService,
    private val paperRepository: PaperRepository,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private val LOG_FILE = File(System.getProperty("user.home"), ".potero/preprocessing.log")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        init {
            LOG_FILE.parentFile?.mkdirs()
        }

        private fun log(message: String) {
            val timestamp = LocalDateTime.now().format(DATE_FORMATTER)
            val logLine = "[$timestamp] $message"
            println(logLine)

            try {
                FileWriter(LOG_FILE, true).use { writer ->
                    writer.appendLine(logLine)
                }
            } catch (e: Exception) {
                System.err.println("Failed to write to log file: ${e.message}")
            }
        }
    }

    /**
     * Preprocess a PDF: extract all pages + GROBID processing.
     * Stores results in database for future reuse.
     *
     * @param paperId Paper ID
     * @param pdfPath Path to PDF file
     * @param forceReprocess Force reprocessing even if already completed
     * @return Result with preprocessing status
     */
    suspend fun preprocessPaper(
        paperId: String,
        pdfPath: String,
        forceReprocess: Boolean = false
    ): Result<PdfPreprocessingStatus> {
        val startTime = System.currentTimeMillis()

        return runCatching {
            log("[Preprocessing] Starting for paper: $paperId")

            // Check if already preprocessed
            if (!forceReprocess) {
                val existingStatus = preprocessingRepository.getStatus(paperId).getOrNull()
                if (existingStatus?.status == PreprocessingStatus.COMPLETED) {
                    log("[Preprocessing] Already completed for $paperId, skipping")
                    return Result.success(existingStatus)
                }
            }

            // Create or update status to PROCESSING
            val now = Clock.System.now()
            val existingStatus = preprocessingRepository.getStatus(paperId).getOrNull()

            if (existingStatus == null) {
                // First time - insert new status
                val status = PdfPreprocessingStatus(
                    paperId = paperId,
                    version = 1,
                    status = PreprocessingStatus.PROCESSING,
                    startedAt = now,
                    completedAt = null,
                    totalPages = 0,
                    extractionMethod = null,
                    qualityScore = null,
                    grobidStatus = null,
                    ocrStatus = null,
                    latexOcrStatus = null,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now
                )
                preprocessingRepository.insertStatus(status).getOrThrow()
            } else {
                // Update existing status to PROCESSING
                preprocessingRepository.markProcessing(paperId, now.toEpochMilliseconds()).getOrThrow()
            }

            try {
                // Stage 1: Text extraction with multi-layer fallback
                val textResult = extractTextWithFallback(paperId, pdfPath)

                // Stage 2: GROBID structured extraction
                val grobidStatus = runGrobidExtraction(paperId, textResult.currentPdfPath)

                // Stage 3: Save page texts to database
                savePageTexts(paperId, textResult.pages)

                // Stage 4: Mark completed
                val completedAt = Clock.System.now()
                preprocessingRepository.markCompleted(
                    paperId = paperId,
                    completedAt = completedAt.toEpochMilliseconds(),
                    totalPages = textResult.totalPages,
                    extractionMethod = textResult.overallMethod,
                    qualityScore = textResult.averageQuality,
                    grobidStatus = grobidStatus,
                    ocrStatus = textResult.ocrStatus,
                    latexOcrStatus = LatexOcrStatus.SKIPPED  // LaTeX OCR not yet implemented
                ).getOrThrow()

                val processingTime = System.currentTimeMillis() - startTime
                log("[Preprocessing] Completed for $paperId in ${processingTime}ms")

                PdfPreprocessingStatus(
                    paperId = paperId,
                    version = 1,
                    status = PreprocessingStatus.COMPLETED,
                    startedAt = existingStatus?.startedAt ?: now,
                    completedAt = completedAt,
                    totalPages = textResult.totalPages,
                    extractionMethod = textResult.overallMethod,
                    qualityScore = textResult.averageQuality,
                    grobidStatus = grobidStatus,
                    ocrStatus = textResult.ocrStatus,
                    latexOcrStatus = LatexOcrStatus.SKIPPED,
                    errorMessage = null,
                    createdAt = existingStatus?.createdAt ?: now,
                    updatedAt = completedAt
                )
            } catch (e: Exception) {
                log("[Preprocessing] Failed for $paperId: ${e.message}")

                // Mark as failed
                val failedAt = Clock.System.now()
                preprocessingRepository.markFailed(
                    paperId = paperId,
                    completedAt = failedAt.toEpochMilliseconds(),
                    errorMessage = e.message ?: "Unknown error"
                ).getOrThrow()

                throw PreprocessingFailedException("Preprocessing failed for $paperId", e)
            }
        }
    }

    /**
     * Extract text from all pages with multi-layer fallback strategy.
     * Based on GrobidProcessor logic.
     */
    private suspend fun extractTextWithFallback(
        paperId: String,
        pdfPath: String
    ): ExtractTextResult {
        log("[Preprocessing] Extracting text for: $pdfPath")

        var currentPdfPath = pdfPath
        val pages = mutableListOf<PageTextData>()
        var ocrUsed = false

        // Try arXiv fallback if initial PDF seems problematic
        val arxivPath = tryArxivPdfFallback(paperId)
        if (arxivPath != null) {
            log("[Preprocessing] Using arXiv PDF: $arxivPath")
            currentPdfPath = arxivPath
        }

        // Load PDF to get total pages
        val pdfFile = File(currentPdfPath)
        val document = Loader.loadPDF(pdfFile)
        val totalPages = document.numberOfPages

        try {
            for (pageNum in 1..totalPages) {
                val pageData = extractPageWithFallback(currentPdfPath, pageNum, document)
                pages.add(pageData)

                if (pageData.method == ExtractionMethod.OCR) {
                    ocrUsed = true
                }
            }
        } finally {
            document.close()
        }

        // Determine overall extraction method
        val methodCounts = pages.groupBy { it.method }.mapValues { it.value.size }
        val dominantMethod = methodCounts.maxByOrNull { it.value }?.key ?: ExtractionMethod.PDFBOX
        val overallMethod = if (methodCounts.size > 1) ExtractionMethod.HYBRID else dominantMethod

        // Calculate average quality
        val avgQuality = pages.map { it.qualityScore }.average()

        log("[Preprocessing] Extracted $totalPages pages, method=$overallMethod, avgQuality=${String.format("%.2f", avgQuality)}")

        return ExtractTextResult(
            pages = pages,
            totalPages = totalPages,
            overallMethod = overallMethod,
            averageQuality = avgQuality,
            ocrStatus = if (ocrUsed) OcrProcessStatus.SUCCESS else OcrProcessStatus.SKIPPED,
            currentPdfPath = currentPdfPath
        )
    }

    /**
     * Extract single page with fallback: PDFBox → pdftotext → OCR
     */
    private suspend fun extractPageWithFallback(
        pdfPath: String,
        pageNum: Int,
        document: org.apache.pdfbox.pdmodel.PDDocument
    ): PageTextData {
        // Step 1: Try PDFBox
        val stripper = PDFTextStripper()
        stripper.startPage = pageNum
        stripper.endPage = pageNum
        stripper.setSortByPosition(true)  // Multi-column support

        val pdfboxText = stripper.getText(document)

        if (!isGarbled(pdfboxText)) {
            // PDFBox succeeded
            return PageTextData(
                pageNum = pageNum,
                text = pdfboxText,
                method = ExtractionMethod.PDFBOX,
                isGarbled = false,
                qualityScore = calculateQualityScore(pdfboxText),
                ocrConfidence = null
            )
        }

        log("[Preprocessing] Page $pageNum is garbled, trying pdftotext")

        // Step 2: Try pdftotext
        val pdftotextText = extractWithPdftotext(pdfPath, pageNum, pageNum)
        if (pdftotextText != null && !isGarbled(pdftotextText)) {
            return PageTextData(
                pageNum = pageNum,
                text = pdftotextText,
                method = ExtractionMethod.PDFTOTEXT,
                isGarbled = false,
                qualityScore = calculateQualityScore(pdftotextText),
                ocrConfidence = null
            )
        }

        log("[Preprocessing] Page $pageNum still garbled, trying OCR")

        // Step 3: Try OCR
        val ocrEnabled = settingsRepository.get(SettingsKeys.OCR_ENABLED)
            .getOrNull()?.equals("true", ignoreCase = true) ?: false

        if (ocrEnabled && pdfOcrService.isAvailable()) {
            val ocrResult = pdfOcrService.ocrPdf(pdfPath, startPage = pageNum, endPage = pageNum).getOrNull()
            if (ocrResult != null) {
                return PageTextData(
                    pageNum = pageNum,
                    text = ocrResult,
                    method = ExtractionMethod.OCR,
                    isGarbled = false,
                    qualityScore = calculateQualityScore(ocrResult),
                    ocrConfidence = 0.8  // Estimate
                )
            }
        }

        // Fallback: Use garbled PDFBox text
        log("[Preprocessing] All methods failed for page $pageNum, using garbled PDFBox text")
        return PageTextData(
            pageNum = pageNum,
            text = pdfboxText,
            method = ExtractionMethod.PDFBOX,
            isGarbled = true,
            qualityScore = calculateQualityScore(pdfboxText),
            ocrConfidence = null
        )
    }

    /**
     * Run GROBID extraction (citations + references).
     * Delegates to GrobidProcessor.
     */
    private suspend fun runGrobidExtraction(paperId: String, pdfPath: String): GrobidProcessStatus {
        return try {
            val grobidEnabled = settingsRepository.get(SettingsKeys.GROBID_ENABLED)
                .getOrNull()?.equals("true", ignoreCase = true) ?: true

            if (!grobidEnabled) {
                log("[Preprocessing] GROBID disabled, skipping")
                return GrobidProcessStatus.SKIPPED
            }

            log("[Preprocessing] Running GROBID extraction")
            val result = grobidProcessor.process(paperId, pdfPath)

            result.fold(
                onSuccess = { stats ->
                    log("[Preprocessing] GROBID succeeded: ${stats.referencesExtracted} refs, ${stats.citationSpansExtracted} citations")
                    GrobidProcessStatus.SUCCESS
                },
                onFailure = { error ->
                    log("[Preprocessing] GROBID failed: ${error.message}")
                    GrobidProcessStatus.FAILED
                }
            )
        } catch (e: Exception) {
            log("[Preprocessing] GROBID exception: ${e.message}")
            GrobidProcessStatus.FAILED
        }
    }

    /**
     * Save page texts to database
     */
    private suspend fun savePageTexts(paperId: String, pages: List<PageTextData>) {
        val now = Clock.System.now()

        val pageTexts = pages.map { page ->
            PdfPageText(
                id = UUID.randomUUID().toString(),
                paperId = paperId,
                pageNum = page.pageNum,
                textContent = page.text,
                extractionMethod = page.method,
                isGarbled = page.isGarbled,
                qualityScore = page.qualityScore,
                ocrConfidence = page.ocrConfidence,
                wordCount = page.wordCount,
                createdAt = now
            )
        }

        preprocessingRepository.insertAllPageTexts(pageTexts).getOrThrow()
        log("[Preprocessing] Saved ${pageTexts.size} page texts to database")
    }

    // === Helper Methods (from GrobidProcessor) ===

    private suspend fun tryArxivPdfFallback(paperId: String): String? {
        return try {
            val strategyEnabled = settingsRepository.get(SettingsKeys.GARBLED_PDF_ARXIV_AUTO_DOWNLOAD)
                .getOrNull() == "true"

            if (!strategyEnabled) return null

            val paper = paperRepository.getById(paperId).getOrNull() ?: return null
            val arxivId = paper.arxivId
            if (arxivId.isNullOrBlank()) return null

            log("[Preprocessing] Downloading arXiv PDF: $arxivId")

            val downloadResult = pdfDownloadService.downloadFromArxiv(arxivId)
            val arxivPdfPath = downloadResult.getOrNull()

            if (arxivPdfPath != null) {
                log("[Preprocessing] ✓ arXiv PDF downloaded: $arxivPdfPath")

                // Update paper's PDF path
                paper.copy(pdfPath = arxivPdfPath).let { updatedPaper ->
                    paperRepository.update(updatedPaper).getOrNull()
                }

                arxivPdfPath
            } else {
                null
            }
        } catch (e: Exception) {
            log("[Preprocessing] arXiv fallback error: ${e.message}")
            null
        }
    }

    private suspend fun extractWithPdftotext(pdfPath: String, startPage: Int, endPage: Int): String? {
        return try {
            val pdftotextEnabled = settingsRepository.get(SettingsKeys.PDFTOTEXT_ENABLED)
                .getOrNull()?.equals("true", ignoreCase = true) ?: true

            if (!pdftotextEnabled) return null

            val process = ProcessBuilder(
                "pdftotext",
                "-f", startPage.toString(),
                "-l", endPage.toString(),
                "-layout",
                "-enc", "UTF-8",
                pdfPath,
                "-"
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank()) {
                output
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isGarbled(text: String): Boolean {
        if (text.isBlank()) return true

        val len = text.length
        val controlChars = text.count { it.code in 0..31 || it.code == 127 }
        val controlRatio = controlChars.toDouble() / len

        val letters = text.count { it.isLetter() }
        val letterRatio = letters.toDouble() / len

        val printableChars = text.count { ch ->
            ch.isLetterOrDigit() || ch.isWhitespace() ||
            ch in listOf('.', ',', ';', ':', '-', '–', '—', '(', ')', '[', ']',
                         '{', '}', '/', '\\', '"', '\'', '?', '!')
        }
        val printableRatio = printableChars.toDouble() / len

        return controlRatio > 0.005 || letterRatio < 0.40 || printableRatio < 0.65
    }

    private fun calculateQualityScore(text: String): Double {
        if (text.isBlank()) return 0.0

        val len = text.length
        val letters = text.count { it.isLetter() }
        val letterRatio = letters.toDouble() / len

        // Quality = letter ratio (0-1 scale)
        return letterRatio.coerceIn(0.0, 1.0)
    }

    /**
     * Internal result type for text extraction
     */
    private data class ExtractTextResult(
        val pages: List<PageTextData>,
        val totalPages: Int,
        val overallMethod: ExtractionMethod,
        val averageQuality: Double,
        val ocrStatus: OcrProcessStatus,
        val currentPdfPath: String
    )
}
