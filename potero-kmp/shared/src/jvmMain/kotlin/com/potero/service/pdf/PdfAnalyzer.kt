package com.potero.service.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Analyzes PDF files to extract metadata and content for academic paper identification.
 */
class PdfAnalyzer(private val pdfPath: String) {

    companion object {
        // DOI pattern: 10.xxxx/xxxxx
        private val DOI_PATTERN = Regex("""10\.\d{4,}/[^\s\]>)]+""")

        // arXiv patterns: 1234.56789, 1234.56789v1, arXiv:1234.56789
        private val ARXIV_PATTERNS = listOf(
            Regex("""arXiv:(\d{4}\.\d{4,5}(?:v\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""arxiv\.org/abs/(\d{4}\.\d{4,5}(?:v\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(?<!\d)(\d{4}\.\d{4,5}(?:v\d+)?)(?!\d)""")
        )
    }

    private var document: PDDocument? = null

    /**
     * Extract built-in PDF metadata (title, author, subject, keywords)
     */
    fun extractBuiltInMetadata(): PdfMetadata {
        return withDocument { doc ->
            val info = doc.documentInformation
            PdfMetadata(
                title = info?.title?.takeIf { it.isNotBlank() },
                author = info?.author?.takeIf { it.isNotBlank() },
                subject = info?.subject?.takeIf { it.isNotBlank() },
                keywords = info?.keywords?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
                creator = info?.creator?.takeIf { it.isNotBlank() },
                producer = info?.producer?.takeIf { it.isNotBlank() },
                pageCount = doc.numberOfPages
            )
        }
    }

    /**
     * Extract text from first N pages (for DOI/arXiv detection)
     */
    fun extractFirstPagesText(maxPages: Int = 2): String {
        return withDocument { doc ->
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(maxPages, doc.numberOfPages)
            }
            stripper.getText(doc)
        }
    }

    /**
     * Extract full text from PDF
     */
    fun extractFullText(): String {
        return withDocument { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }
    }

    /**
     * Find DOI in text
     */
    fun findDOI(text: String): String? {
        val match = DOI_PATTERN.find(text)
        return match?.value?.trimEnd('.', ',', ';', ')')
    }

    /**
     * Find arXiv ID in text
     */
    fun findArxivId(text: String): String? {
        for (pattern in ARXIV_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                // Return the captured group if available, otherwise the full match
                return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: match.value
            }
        }
        return null
    }

    /**
     * Perform full analysis: metadata + DOI/arXiv detection
     */
    fun analyze(): PdfAnalysisResult {
        val metadata = extractBuiltInMetadata()
        val firstPagesText = extractFirstPagesText()

        val doi = findDOI(firstPagesText)
        val arxivId = findArxivId(firstPagesText)

        // Try to extract title from first page if not in metadata
        val extractedTitle = if (metadata.title == null) {
            extractTitleFromText(firstPagesText)
        } else null

        return PdfAnalysisResult(
            metadata = metadata,
            detectedDoi = doi,
            detectedArxivId = arxivId,
            extractedTitle = extractedTitle,
            firstPagesText = firstPagesText
        )
    }

    /**
     * Try to extract title from first page text
     * Heuristic: Title is usually the largest/first prominent text
     */
    private fun extractTitleFromText(text: String): String? {
        // Simple heuristic: first non-empty line that's reasonably long
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 10 }

        // Skip common header elements
        val skipPatterns = listOf(
            "arXiv", "preprint", "submitted", "accepted", "published",
            "journal", "conference", "abstract", "introduction"
        )

        for (line in lines.take(10)) {
            if (skipPatterns.none { line.contains(it, ignoreCase = true) }) {
                // Likely a title if it doesn't end with common sentence endings
                // and is reasonably short (titles are usually < 200 chars)
                if (line.length < 200 && !line.endsWith(".") && !line.endsWith(":")) {
                    return line
                }
            }
        }

        return lines.firstOrNull()
    }

    private fun <T> withDocument(block: (PDDocument) -> T): T {
        val file = File(pdfPath)
        if (!file.exists()) {
            throw IllegalArgumentException("PDF file not found: $pdfPath")
        }

        return Loader.loadPDF(file).use { doc ->
            block(doc)
        }
    }

    fun close() {
        document?.close()
        document = null
    }
}

/**
 * PDF built-in metadata
 */
data class PdfMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: List<String>? = null,
    val creator: String? = null,
    val producer: String? = null,
    val pageCount: Int = 0
)

/**
 * Result of PDF analysis
 */
data class PdfAnalysisResult(
    val metadata: PdfMetadata,
    val detectedDoi: String? = null,
    val detectedArxivId: String? = null,
    val extractedTitle: String? = null,
    val firstPagesText: String = ""
) {
    /**
     * Best title: metadata > extracted > null
     */
    val bestTitle: String?
        get() = metadata.title ?: extractedTitle

    /**
     * Whether we found any identifier that can be used for metadata lookup
     */
    val hasIdentifier: Boolean
        get() = detectedDoi != null || detectedArxivId != null
}
