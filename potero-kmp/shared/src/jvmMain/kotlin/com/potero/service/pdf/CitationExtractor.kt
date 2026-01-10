package com.potero.service.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.PDFTextStripperByArea
import org.apache.pdfbox.text.TextPosition
import java.awt.geom.Rectangle2D
import java.io.File

/**
 * Extracts citation spans with bounding boxes from PDFs.
 *
 * Strategy: "Link annotation first, pattern fallback"
 * 1. Primary: Extract PDF link annotations (/Annots with /Subtype /Link)
 * 2. Fallback: Pattern-based detection with text position extraction
 */
class CitationExtractor(private val pdfPath: String) {

    companion object {
        // Citation patterns for fallback extraction
        private val NUMERIC_CITATION = Regex("""\[(\d+(?:\s*[,–-]\s*\d+)*)\]""")
        private val AUTHOR_YEAR_CITATION = Regex(
            """\(([A-Z][a-z]+(?:\s+(?:et\s+al\.?|&\s+[A-Z][a-z]+))?,\s*(\d{4}))\)"""
        )

        // Reference header patterns (reuse from PdfAnalyzer)
        private val REFERENCES_HEADER_PATTERNS = listOf(
            Regex("""^references\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^bibliography\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^\d+\.?\s*references\s*$""", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Extract all citation spans from the PDF.
     */
    fun extract(): CitationExtractionResult {
        val file = File(pdfPath)
        if (!file.exists()) {
            return CitationExtractionResult(
                spans = emptyList(),
                referencesStartPage = null,
                hasAnnotations = false,
                error = "PDF file not found: $pdfPath"
            )
        }

        return try {
            Loader.loadPDF(file).use { doc ->
                // Detect references section (to exclude from citation detection)
                val referencesStartPage = detectReferencesSection(doc)

                // Phase 1: Try link annotations (highest quality)
                val annotationSpans = extractFromLinkAnnotations(doc, referencesStartPage)

                // Phase 2: Pattern-based fallback for pages without annotations
                val pagesWithAnnotations = annotationSpans.map { it.pageNum }.toSet()
                val patternSpans = extractFromPatterns(doc, pagesWithAnnotations, referencesStartPage)

                CitationExtractionResult(
                    spans = annotationSpans + patternSpans,
                    referencesStartPage = referencesStartPage,
                    hasAnnotations = annotationSpans.isNotEmpty()
                )
            }
        } catch (e: Exception) {
            CitationExtractionResult(
                spans = emptyList(),
                referencesStartPage = null,
                hasAnnotations = false,
                error = "Extraction failed: ${e.message}"
            )
        }
    }

    /**
     * Extract citation spans from PDF link annotations.
     *
     * PDF link annotations contain:
     * - rect: The clickable area (citation bounding box)
     * - action/dest: Where the link points (reference location)
     */
    private fun extractFromLinkAnnotations(
        doc: PDDocument,
        referencesStartPage: Int?
    ): List<RawCitationSpan> {
        val spans = mutableListOf<RawCitationSpan>()

        for ((pageIndex, page) in doc.pages.withIndex()) {
            val pageNum = pageIndex + 1

            // Skip reference section (these are entries, not citations)
            if (referencesStartPage != null && pageNum >= referencesStartPage) {
                continue
            }

            val annotations = page.annotations ?: continue

            // Group adjacent link annotations by destination
            // (for multi-part citations like "[1,2,3]" which may span multiple rects)
            val destinationGroups = mutableMapOf<String, MutableList<AnnotationData>>()

            for (annot in annotations) {
                if (annot !is PDAnnotationLink) continue

                val rect = annot.rectangle ?: continue
                val destInfo = resolveDestination(annot, doc)

                if (destInfo != null) {
                    val key = "${destInfo.destPage}:${destInfo.destY?.toInt() ?: 0}"
                    destinationGroups.getOrPut(key) { mutableListOf() }
                        .add(AnnotationData(rect, destInfo, pageNum))
                }
            }

            // Merge adjacent rects with same destination into single spans
            for ((_, annotDatas) in destinationGroups) {
                if (annotDatas.isEmpty()) continue

                // Merge all rects (they point to the same reference)
                val mergedBbox = mergeRects(annotDatas.map { it.rect })
                val text = extractTextFromRegion(doc, pageNum, mergedBbox)

                // Only include if it looks like a citation
                if (text.isBlank() || text.length > 50) continue
                if (!looksLikeCitation(text)) continue

                val destInfo = annotDatas.first().destInfo
                spans.add(RawCitationSpan(
                    pageNum = pageNum,
                    bbox = BoundingBox.fromPDRectangle(mergedBbox),
                    rawText = text.trim(),
                    style = detectCitationStyle(text),
                    provenance = "annotation",
                    confidence = 0.95,
                    destPage = destInfo.destPage,
                    destY = destInfo.destY
                ))
            }
        }

        return spans
    }

    /**
     * Resolve link annotation destination to page number and Y position.
     */
    private fun resolveDestination(link: PDAnnotationLink, doc: PDDocument): DestinationInfo? {
        val action = link.action
        val dest = link.destination

        return when {
            // GoTo action (internal link)
            action is PDActionGoTo -> {
                resolvePageDestination(action.destination, doc)
            }
            // Direct destination
            dest != null -> {
                resolvePageDestination(dest, doc)
            }
            // URI action (external link) - not a citation
            action is PDActionURI -> null
            else -> null
        }
    }

    /**
     * Resolve a PDDestination to page number and Y coordinate.
     */
    private fun resolvePageDestination(
        dest: org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination?,
        doc: PDDocument
    ): DestinationInfo? {
        return when (dest) {
            is PDPageDestination -> {
                val pageNum = dest.pageNumber + 1 // PDFBox is 0-indexed
                // Try to get Y position from destination (XYZ, FitBH, etc.)
                val yPos = try {
                    // Cast to access top/left properties if available
                    val top = dest.javaClass.getMethod("getTop")?.invoke(dest) as? Number
                    top?.toDouble()
                } catch (e: Exception) {
                    null
                }
                DestinationInfo(pageNum, yPos)
            }
            is PDNamedDestination -> {
                // Resolve named destination
                val resolved = doc.documentCatalog.findNamedDestinationPage(dest)
                if (resolved != null) {
                    // Find the page index by iterating through pages
                    var pageIndex = -1
                    for (i in 0 until doc.numberOfPages) {
                        if (doc.getPage(i) == resolved) {
                            pageIndex = i
                            break
                        }
                    }
                    if (pageIndex >= 0) {
                        DestinationInfo(pageIndex + 1, null)
                    } else null
                } else null
            }
            else -> null
        }
    }

    /**
     * Extract citation spans using pattern matching with text positions.
     * This is the fallback when link annotations are not available.
     */
    private fun extractFromPatterns(
        doc: PDDocument,
        excludePages: Set<Int>,
        referencesStartPage: Int?
    ): List<RawCitationSpan> {
        val spans = mutableListOf<RawCitationSpan>()

        for ((pageIndex, page) in doc.pages.withIndex()) {
            val pageNum = pageIndex + 1

            // Skip pages already processed by annotation extraction
            if (pageNum in excludePages) continue

            // Skip reference section
            if (referencesStartPage != null && pageNum >= referencesStartPage) continue

            // Extract text with positions
            val textPositions = extractTextWithPositions(doc, pageNum)
            val pageText = textPositions.joinToString("") { it.char }

            // Find numeric citations [1], [1,2,3], [1-5]
            for (match in NUMERIC_CITATION.findAll(pageText)) {
                val bbox = computeBboxForRange(textPositions, match.range)
                if (bbox != null) {
                    spans.add(RawCitationSpan(
                        pageNum = pageNum,
                        bbox = bbox,
                        rawText = match.value,
                        style = "numeric",
                        provenance = "pattern",
                        confidence = 0.85,
                        destPage = null,
                        destY = null
                    ))
                }
            }

            // Find author-year citations (Smith et al., 2024)
            for (match in AUTHOR_YEAR_CITATION.findAll(pageText)) {
                val bbox = computeBboxForRange(textPositions, match.range)
                if (bbox != null) {
                    spans.add(RawCitationSpan(
                        pageNum = pageNum,
                        bbox = bbox,
                        rawText = match.value,
                        style = "author_year",
                        provenance = "pattern",
                        confidence = 0.75,
                        destPage = null,
                        destY = null
                    ))
                }
            }
        }

        return spans
    }

    /**
     * Extract text with character positions for a page.
     */
    private fun extractTextWithPositions(doc: PDDocument, pageNum: Int): List<CharPosition> {
        val positions = mutableListOf<CharPosition>()

        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                if (text != null && textPositions != null) {
                    for (tp in textPositions) {
                        // Use getX/getY instead of xDirAdj/yDirAdj for more accurate positioning
                        // xDirAdj can be incorrect for rotated or complex layouts
                        positions.add(CharPosition(
                            char = tp.unicode ?: "",
                            x = tp.x.toDouble(),
                            y = tp.y.toDouble(),
                            width = tp.width.toDouble(),
                            height = tp.height.toDouble()
                        ))
                    }
                }
                super.writeString(text, textPositions)
            }
        }

        stripper.startPage = pageNum
        stripper.endPage = pageNum
        // Enable position-based sorting for multi-column PDFs
        stripper.setSortByPosition(true)
        stripper.getText(doc)

        return positions
    }

    /**
     * Compute bounding box for a character range.
     */
    private fun computeBboxForRange(positions: List<CharPosition>, range: IntRange): BoundingBox? {
        if (range.first >= positions.size || range.isEmpty()) return null

        val startIdx = range.first.coerceIn(0, positions.lastIndex)
        val endIdx = (range.last).coerceIn(0, positions.lastIndex)

        val relevantPositions = positions.subList(startIdx, endIdx + 1)
        if (relevantPositions.isEmpty()) return null

        // PDF coordinates: origin at bottom-left
        // Text positions from PDFBox: Y is distance from top
        // We need to compute the bounding box in PDF coordinate system
        return BoundingBox(
            x1 = relevantPositions.minOf { it.x },
            y1 = relevantPositions.minOf { it.y - it.height },
            x2 = relevantPositions.maxOf { it.x + it.width },
            y2 = relevantPositions.maxOf { it.y }
        )
    }

    /**
     * Extract text from a specific region of a page.
     */
    private fun extractTextFromRegion(doc: PDDocument, pageNum: Int, rect: PDRectangle): String {
        return try {
            val page = doc.getPage(pageNum - 1)
            val stripper = PDFTextStripperByArea()

            // Convert PDF coordinates to Java2D coordinates
            val pageHeight = page.mediaBox.height
            val region = Rectangle2D.Float(
                rect.lowerLeftX,
                pageHeight - rect.upperRightY,
                rect.width,
                rect.height
            )

            stripper.addRegion("citation", region)
            stripper.extractRegions(page)
            stripper.getTextForRegion("citation").trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Merge multiple rectangles into a single bounding box.
     */
    private fun mergeRects(rects: List<PDRectangle>): PDRectangle {
        if (rects.isEmpty()) return PDRectangle()
        if (rects.size == 1) return rects[0]

        val minX = rects.minOf { it.lowerLeftX }
        val minY = rects.minOf { it.lowerLeftY }
        val maxX = rects.maxOf { it.upperRightX }
        val maxY = rects.maxOf { it.upperRightY }

        return PDRectangle(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * Detect if text looks like a citation.
     */
    private fun looksLikeCitation(text: String): Boolean {
        val trimmed = text.trim()
        // Numeric citation: [1], [1,2], [1-5]
        if (trimmed.matches(Regex("""\[\d+(?:\s*[,–-]\s*\d+)*\]"""))) return true
        // Author-year: (Smith, 2024), (Smith et al., 2024)
        if (trimmed.matches(Regex("""\([A-Z][a-z]+.*\d{4}\)"""))) return true
        return false
    }

    /**
     * Detect citation style from text.
     */
    private fun detectCitationStyle(text: String): String {
        return when {
            text.contains(Regex("""\[\d+""")) -> "numeric"
            text.contains(Regex("""\([A-Z].*\d{4}\)""")) -> "author_year"
            else -> "unknown"
        }
    }

    /**
     * Detect references section start page.
     */
    private fun detectReferencesSection(doc: PDDocument): Int? {
        val totalPages = doc.numberOfPages
        val scanStart = maxOf(1, totalPages - 14)

        for (pageNum in scanStart..totalPages) {
            val stripper = PDFTextStripper().apply {
                startPage = pageNum
                endPage = pageNum
                // Enable position-based sorting for multi-column PDFs
                setSortByPosition(true)
            }
            val pageText = stripper.getText(doc)
            val lines = pageText.lines().map { it.trim() }.filter { it.isNotBlank() }

            for (line in lines.take(30)) {
                for (pattern in REFERENCES_HEADER_PATTERNS) {
                    if (pattern.matches(line)) {
                        return pageNum
                    }
                }
            }
        }

        return null
    }
}

/**
 * Bounding box in PDF coordinate system (origin at bottom-left).
 */
data class BoundingBox(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double
) {
    val width: Double get() = x2 - x1
    val height: Double get() = y2 - y1

    companion object {
        fun fromPDRectangle(rect: PDRectangle) = BoundingBox(
            x1 = rect.lowerLeftX.toDouble(),
            y1 = rect.lowerLeftY.toDouble(),
            x2 = rect.upperRightX.toDouble(),
            y2 = rect.upperRightY.toDouble()
        )
    }

    /**
     * Convert PDF coordinates (origin bottom-left) to viewport coordinates (origin top-left).
     */
    fun toViewport(pageHeight: Double): BoundingBox = BoundingBox(
        x1 = x1,
        y1 = pageHeight - y2,
        x2 = x2,
        y2 = pageHeight - y1
    )
}

/**
 * Character position from PDF text extraction.
 */
data class CharPosition(
    val char: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)

/**
 * Raw citation span before database insertion.
 */
data class RawCitationSpan(
    val pageNum: Int,
    val bbox: BoundingBox,
    val rawText: String,
    val style: String,
    val provenance: String,
    val confidence: Double,
    val destPage: Int?,
    val destY: Double?
)

/**
 * Link destination information.
 */
data class DestinationInfo(
    val destPage: Int,
    val destY: Double?
)

/**
 * Annotation data for grouping.
 */
private data class AnnotationData(
    val rect: PDRectangle,
    val destInfo: DestinationInfo,
    val pageNum: Int
)

/**
 * Result of citation extraction.
 */
data class CitationExtractionResult(
    val spans: List<RawCitationSpan>,
    val referencesStartPage: Int?,
    val hasAnnotations: Boolean,
    val error: String? = null
) {
    val annotationCount: Int get() = spans.count { it.provenance == "annotation" }
    val patternCount: Int get() = spans.count { it.provenance == "pattern" }
}
