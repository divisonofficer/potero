package com.potero.service.grobid

import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference
import com.potero.domain.repository.GrobidRepository
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
 * Orchestrates GROBID processing and database storage.
 *
 * Workflow:
 * 1. Call GrobidRestEngine to get TEI XML
 * 2. Parse TEI using TEIParser
 * 3. Convert TEI models to domain models
 * 4. Store in database via GrobidRepository
 *
 * Error handling:
 * - GROBID failures are logged but not thrown (non-fatal)
 * - TEI parse errors are logged but not thrown
 * - Database errors are propagated as Result failures
 */
class GrobidProcessor(
    private val grobidEngine: GrobidEngine,
    private val grobidRepository: GrobidRepository
) {

    /**
     * Process a PDF with GROBID and store results in database.
     *
     * @param paperId The paper ID to associate with extracted data
     * @param pdfPath Absolute path to the PDF file
     * @return Result with processing statistics or error
     */
    suspend fun process(paperId: String, pdfPath: String): Result<GrobidProcessingStats> {
        val startTime = System.currentTimeMillis()

        return runCatching {
            println("[GrobidProcessor] Starting processing for paper: $paperId")

            // Step 1: Call GROBID to get TEI XML
            val teiDocument = try {
                grobidEngine.processFulltext(pdfPath)
            } catch (e: GrobidException) {
                println("[GrobidProcessor] GROBID processing failed: ${e.message}")
                throw e
            }

            println("[GrobidProcessor] TEI extracted: ${teiDocument.body.citationSpans.size} citations, ${teiDocument.references.size} references")

            // Step 2: Convert TEI models to domain models
            val citationSpans = convertCitationSpans(paperId, teiDocument.body.citationSpans)
            val references = convertReferences(paperId, teiDocument.references)

            // Step 3: Delete old GROBID data for this paper (if any)
            grobidRepository.deleteCitationSpansByPaperId(paperId).getOrThrow()
            grobidRepository.deleteReferencesByPaperId(paperId).getOrThrow()

            // Step 4: Store in database
            grobidRepository.insertAllCitationSpans(citationSpans).getOrThrow()
            grobidRepository.insertAllReferences(references).getOrThrow()

            val processingTime = System.currentTimeMillis() - startTime
            println("[GrobidProcessor] Processing completed in ${processingTime}ms")

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
}
