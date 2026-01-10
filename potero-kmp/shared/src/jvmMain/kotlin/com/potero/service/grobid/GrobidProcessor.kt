package com.potero.service.grobid

import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference
import com.potero.domain.repository.GrobidRepository
import com.potero.service.pdf.PdfAnalyzer
import kotlinx.datetime.Clock
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
    private val llmReferenceParser: LLMReferenceParser
) {

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
            println("[GrobidProcessor] Starting processing for paper: $paperId")

            // Step 1: Try GROBID first
            val teiDocument = try {
                grobidEngine.processFulltext(pdfPath)
            } catch (e: GrobidException) {
                println("[GrobidProcessor] GROBID failed: ${e.message}")
                println("[GrobidProcessor] Attempting LLM fallback...")

                // Step 2: LLM Fallback - Extract References text from PDF
                val referencesText = try {
                    val analyzer = PdfAnalyzer(pdfPath)
                    val result = analyzer.analyzeReferences()

                    if (result.references.isNotEmpty()) {
                        // PdfAnalyzer found references - use structured format
                        println("[GrobidProcessor] PdfAnalyzer found ${result.references.size} references")
                        result.references.joinToString("\n\n") { ref ->
                            "[${ref.number}] ${ref.rawText}"
                        }
                    } else {
                        // PdfAnalyzer failed to find References section - extract last pages as fallback
                        println("[GrobidProcessor] PdfAnalyzer failed to find References section")
                        println("[GrobidProcessor] Extracting last 15 pages for LLM analysis...")

                        extractLastPagesText(pdfPath, maxPages = 15)
                    }
                } catch (pdfError: Exception) {
                    println("[GrobidProcessor] PDF extraction failed: ${pdfError.message}")
                    return@runCatching GrobidProcessingStats(
                        citationSpansExtracted = 0,
                        referencesExtracted = 0,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // Step 3: Parse with LLM
                val llmReferences = llmReferenceParser.parse(referencesText, paperId)
                    .getOrElse { llmError: Throwable ->
                        println("[GrobidProcessor] LLM parsing failed: ${llmError.message}")
                        emptyList<GrobidReference>()
                    }

                if (llmReferences.isEmpty()) {
                    println("[GrobidProcessor] LLM fallback produced no references")
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
                println("[GrobidProcessor] LLM fallback completed: ${llmReferences.size} references in ${time}ms")

                return@runCatching GrobidProcessingStats(
                    citationSpansExtracted = 0,  // LLM doesn't extract citation spans
                    referencesExtracted = llmReferences.size,
                    processingTimeMs = time
                )
            }

            // Step 5: GROBID succeeded - normal flow
            println("[GrobidProcessor] TEI extracted: ${teiDocument.body.citationSpans.size} citations, ${teiDocument.references.size} references")

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
            println("[GrobidProcessor] GROBID processing completed in ${processingTime}ms")

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
     *
     * This indicates ToUnicode CMap issues in PDF fonts.
     */
    private fun isGarbled(text: String): Boolean {
        if (text.isBlank()) return true

        // Count control characters (likely mapping errors)
        val controlChars = text.count { it.code in 0..31 || it.code == 127 }
        val controlRatio = controlChars.toDouble() / text.length

        // Count printable characters (letters, digits, common punctuation)
        val printableChars = text.count { ch ->
            ch.isLetterOrDigit() || ch.isWhitespace() ||
            ch in listOf('.', ',', ';', ':', '-', '–', '—', '(', ')', '[', ']',
                         '{', '}', '/', '\\', '"', '\'', '?', '!')
        }
        val printableRatio = printableChars.toDouble() / text.length

        // Empirically: >1% control chars OR <60% printable = garbled
        val isGarbled = controlRatio > 0.01 || printableRatio < 0.60

        if (isGarbled) {
            println("[GrobidProcessor] Text quality check: control=${String.format("%.2f%%", controlRatio * 100)}, printable=${String.format("%.2f%%", printableRatio * 100)} -> GARBLED")
        }

        return isGarbled
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
            println("[GrobidProcessor] Trying pdftotext fallback for pages $startPage-$endPage")

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
                println("[GrobidProcessor] pdftotext succeeded: ${output.length} chars")
                output
            } else {
                println("[GrobidProcessor] pdftotext failed with exit code $exitCode")
                null
            }
        } catch (e: Exception) {
            println("[GrobidProcessor] pdftotext not available: ${e.message}")
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
                println("[GrobidProcessor] Extracting pages $startPage-$endPage of $totalPages")

                // Step 1: Try PDFBox extraction
                val textBuilder = StringBuilder()
                var garbledPagesCount = 0

                for (pageNum in startPage..endPage) {
                    val stripper = org.apache.pdfbox.text.PDFTextStripper()
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum

                    // CRITICAL: Enable position-based sorting for multi-column PDFs
                    // This ensures text is extracted in reading order (left-to-right, top-to-bottom)
                    stripper.setSortByPosition(true)

                    val pageText = stripper.getText(document)

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

                println("[GrobidProcessor] PDFBox extracted ${pdfboxResult.length} chars, ${garbledPagesCount}/${maxPages} pages garbled (${String.format("%.0f%%", garbledRatio * 100)})")

                // Log a preview to verify extraction quality
                val preview = pdfboxResult.take(500).replace("\n", "\\n")
                println("[GrobidProcessor] Text preview: $preview")

                // Step 2: If >30% pages are garbled, try pdftotext fallback
                if (garbledRatio > 0.30) {
                    println("[GrobidProcessor] High garbled ratio detected, trying pdftotext fallback")

                    val pdftotextResult = extractWithPdftotext(pdfPath, startPage, endPage)

                    if (pdftotextResult != null && !isGarbled(pdftotextResult)) {
                        println("[GrobidProcessor] pdftotext produced clean text, using it instead")
                        return pdftotextResult
                    } else {
                        println("[GrobidProcessor] pdftotext also failed or unavailable, using PDFBox result")
                    }
                }

                pdfboxResult
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            println("[GrobidProcessor] Failed to extract last pages: ${e.message}")
            throw e
        }
    }
}
