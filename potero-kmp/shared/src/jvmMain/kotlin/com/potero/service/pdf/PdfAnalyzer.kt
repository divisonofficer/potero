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

        // References section header patterns
        private val REFERENCES_HEADER_PATTERNS = listOf(
            Regex("""^references\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^bibliography\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^works\s+cited\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^literature\s+cited\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^cited\s+literature\s*$""", RegexOption.IGNORE_CASE),
            Regex("""^r\s*e\s*f\s*e\s*r\s*e\s*n\s*c\s*e\s*s\s*$""", RegexOption.IGNORE_CASE), // Spaced letters
            Regex("""^\d+\.?\s*references\s*$""", RegexOption.IGNORE_CASE), // "7. References" or "7 References"
            Regex("""^references\s*\[\d+\]""", RegexOption.IGNORE_CASE) // "References [1]"
        )

        // Reference entry number patterns
        private val REFERENCE_NUMBER_PATTERNS = listOf(
            Regex("""^\[(\d+)\]\s*(.+)"""),           // [1] Author...
            Regex("""^(\d+)\.\s+(.+)"""),             // 1. Author...
            Regex("""^\((\d+)\)\s*(.+)"""),           // (1) Author...
            Regex("""^(\d+)\s+(.+)""")                // 1 Author... (fallback)
        )

        // Year pattern in references
        private val YEAR_PATTERN = Regex("""(?:19|20)\d{2}""")
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
     * Perform full analysis: metadata + DOI/arXiv detection + title/author extraction
     */
    fun analyze(includeReferences: Boolean = false): PdfAnalysisResult {
        val metadata = extractBuiltInMetadata()
        val firstPagesText = extractFirstPagesText()

        val doi = findDOI(firstPagesText)
        val arxivId = findArxivId(firstPagesText)

        // Try to extract title from first page if not in metadata
        val extractedTitle = if (metadata.title == null) {
            extractTitleFromText(firstPagesText)
        } else null

        // Try to extract authors from first page if not in metadata
        val extractedAuthors = if (metadata.author == null) {
            extractAuthorsFromText(firstPagesText)
        } else emptyList()

        // Optionally analyze references section
        val referencesResult = if (includeReferences) {
            analyzeReferences()
        } else null

        return PdfAnalysisResult(
            metadata = metadata,
            detectedDoi = doi,
            detectedArxivId = arxivId,
            extractedTitle = extractedTitle,
            extractedAuthors = extractedAuthors,
            firstPagesText = firstPagesText,
            referencesResult = referencesResult
        )
    }

    /**
     * Analyze References section: detect location and parse individual references
     */
    fun analyzeReferences(): ReferencesAnalysisResult {
        return withDocument { doc ->
            val totalPages = doc.numberOfPages
            var startPage: Int? = null
            var sectionHeader: String? = null
            val references = mutableListOf<ParsedReference>()

            // Scan last 15 pages for References section (usually at the end)
            val scanStart = maxOf(1, totalPages - 14)

            // First pass: find References section header
            for (pageNum in scanStart..totalPages) {
                val stripper = PDFTextStripper().apply {
                    this.startPage = pageNum
                    this.endPage = pageNum
                }
                val pageText = stripper.getText(doc)
                val lines = pageText.lines().map { it.trim() }.filter { it.isNotBlank() }

                // Check each line for references header
                for (line in lines.take(30)) { // Check first 30 lines of each page
                    for (pattern in REFERENCES_HEADER_PATTERNS) {
                        if (pattern.matches(line)) {
                            startPage = pageNum
                            sectionHeader = line
                            break
                        }
                    }
                    if (startPage != null) break
                }
                if (startPage != null) break
            }

            // Second pass: parse references from the detected section
            if (startPage != null) {
                var currentRefNumber = 0
                var currentRefText = StringBuilder()
                var currentRefPageNum: Int = startPage

                for (pageNum in startPage..totalPages) {
                    val stripper = PDFTextStripper().apply {
                        this.startPage = pageNum
                        this.endPage = pageNum
                    }
                    val pageText = stripper.getText(doc)
                    val lines = pageText.lines()

                    var foundReferencesHeader = false

                    for (line in lines) {
                        val trimmedLine = line.trim()
                        if (trimmedLine.isBlank()) continue

                        // Skip until we find the references header on the first page
                        if (pageNum == startPage && !foundReferencesHeader) {
                            for (pattern in REFERENCES_HEADER_PATTERNS) {
                                if (pattern.matches(trimmedLine)) {
                                    foundReferencesHeader = true
                                    break
                                }
                            }
                            if (!foundReferencesHeader) continue
                            continue // Skip the header line itself
                        }

                        // Check if this line starts a new reference entry
                        var matchedNewRef = false
                        for (pattern in REFERENCE_NUMBER_PATTERNS) {
                            val match = pattern.find(trimmedLine)
                            if (match != null) {
                                val refNum = match.groupValues[1].toIntOrNull() ?: continue
                                val refContent = match.groupValues.getOrNull(2) ?: ""

                                // Save previous reference if exists
                                if (currentRefNumber > 0 && currentRefText.isNotBlank()) {
                                    references.add(parseReferenceText(currentRefNumber, currentRefText.toString().trim(), currentRefPageNum))
                                }

                                // Start new reference
                                currentRefNumber = refNum
                                currentRefText = StringBuilder(refContent)
                                currentRefPageNum = pageNum
                                matchedNewRef = true
                                break
                            }
                        }

                        // If not a new reference, append to current reference
                        if (!matchedNewRef && currentRefNumber > 0) {
                            currentRefText.append(" ").append(trimmedLine)
                        }
                    }
                }

                // Don't forget the last reference
                if (currentRefNumber > 0 && currentRefText.isNotBlank()) {
                    references.add(parseReferenceText(currentRefNumber, currentRefText.toString().trim(), currentRefPageNum))
                }
            }

            ReferencesAnalysisResult(
                startPage = startPage,
                references = references,
                sectionHeader = sectionHeader
            )
        }
    }

    /**
     * Parse a single reference text to extract structured information
     */
    private fun parseReferenceText(number: Int, rawText: String, pageNum: Int): ParsedReference {
        // Extract year
        val yearMatch = YEAR_PATTERN.findAll(rawText).lastOrNull()
        val year = yearMatch?.value?.toIntOrNull()

        // Extract DOI if present
        val doi = findDOI(rawText)

        // Try to split into authors, title, venue
        // Common patterns:
        // 1. "Authors. Title. Venue, Year." (period-separated)
        // 2. "Authors, Title, Venue (Year)" (comma-separated with parenthesized year)
        val parts = rawText.split(Regex("""\.\s+"""))

        val authors: String?
        val title: String?
        val venue: String?

        if (parts.size >= 3) {
            // Period-separated format
            authors = parts[0].takeIf { it.length <= 200 }
            title = parts[1].takeIf { it.length <= 300 }
            venue = parts.drop(2).joinToString(". ").takeIf { it.length <= 200 }
        } else if (parts.size == 2) {
            authors = parts[0].takeIf { it.length <= 200 }
            title = parts[1].takeIf { it.length <= 300 }
            venue = null
        } else {
            // Can't parse cleanly, try to extract at least the title
            // Title is often in quotes or is the longest meaningful segment
            val quotedMatch = Regex(""""([^"]+)"""").find(rawText)
            if (quotedMatch != null) {
                title = quotedMatch.groupValues[1]
                authors = rawText.substringBefore(quotedMatch.value).trim().trimEnd(',', '.', ':')
                venue = rawText.substringAfter(quotedMatch.value).trim().trimStart(',', '.', ':').takeIf { it.isNotBlank() }
            } else {
                // Fallback: use full text
                authors = null
                title = rawText.take(200)
                venue = null
            }
        }

        return ParsedReference(
            number = number,
            rawText = rawText,
            authors = authors?.trim()?.trimEnd(',', '.'),
            title = title?.trim()?.trimEnd(',', '.'),
            venue = venue?.trim()?.trimEnd(',', '.'),
            year = year,
            doi = doi,
            pageNum = pageNum
        )
    }

    /**
     * Generate thumbnail from first page
     */
    fun generateThumbnail(outputPath: String, width: Int = 200): Boolean {
        return try {
            withDocument { doc ->
                val renderer = org.apache.pdfbox.rendering.PDFRenderer(doc)
                val scale = width.toFloat() / doc.getPage(0).mediaBox.width
                val image = renderer.renderImage(0, scale)

                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()
                javax.imageio.ImageIO.write(image, "png", outputFile)
                true
            }
        } catch (e: Exception) {
            println("[PdfAnalyzer] Failed to generate thumbnail: ${e.message}")
            false
        }
    }

    /**
     * Try to extract title from first page text
     * Heuristic: Title is usually the largest/first prominent text
     */
    private fun extractTitleFromText(text: String): String? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Skip common header elements (case-insensitive)
        val skipPatterns = listOf(
            "arXiv", "preprint", "submitted", "accepted", "published",
            "journal", "conference", "abstract", "introduction",
            "proceedings", "copyright", "vol.", "volume", "issue",
            "http", "www.", "doi:", "©", "ieee", "acm", "springer",
            "under review", "workshop", "supplementary", "appendix",
            "cvpr", "iccv", "eccv", "neurips", "icml", "aaai", "ijcai"
        )

        val potentialTitles = mutableListOf<String>()

        for (line in lines.take(20)) {
            // Skip if contains skip patterns
            if (skipPatterns.any { line.contains(it, ignoreCase = true) }) {
                continue
            }

            // Skip lines that look like author names (contain email or multiple commas)
            if (line.contains("@") || line.count { it == ',' } > 3) {
                continue
            }

            // Skip very short lines
            if (line.length < 10) {
                continue
            }

            // Skip lines that are all uppercase and short (likely headers)
            if (line.length < 25 && line == line.uppercase()) {
                continue
            }

            // Skip lines that look like affiliations (university, institute, etc.)
            if (line.contains("university", ignoreCase = true) ||
                line.contains("institute", ignoreCase = true) ||
                line.contains("department", ignoreCase = true) ||
                line.contains("laboratory", ignoreCase = true)) {
                continue
            }

            // A good title candidate
            val endsWithPunctuation = line.endsWith(".") || line.endsWith(":") || line.endsWith(";")
            val isQuestion = line.endsWith("?")

            if (line.length in 15..300 && (!endsWithPunctuation || isQuestion)) {
                potentialTitles.add(line)
            }
        }

        // Prefer the first longer title candidate (titles are usually near the top)
        return potentialTitles.firstOrNull { it.length > 30 } ?: potentialTitles.firstOrNull()
    }

    /**
     * Try to extract authors from first page text
     */
    fun extractAuthorsFromText(text: String): List<String> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val authors = mutableListOf<String>()

        // Look in first 25 lines for author-like patterns
        for (i in 0 until minOf(25, lines.size)) {
            val line = lines[i]

            // Skip lines that are clearly not authors
            if (line.length > 200 ||
                line.contains("abstract", ignoreCase = true) ||
                line.contains("introduction", ignoreCase = true) ||
                line.contains("@") || // email
                line.startsWith("http") ||
                line.contains("university", ignoreCase = true) ||
                line.contains("institute", ignoreCase = true)) {
                continue
            }

            // Check if line contains comma-separated or "and"-separated names
            if ((line.contains(",") || line.contains(" and ", ignoreCase = true)) &&
                !line.endsWith(".") && line.length in 10..150) {

                // Split by comma or "and"
                val parts = line.split(Regex("""\s*,\s*|\s+and\s+""", RegexOption.IGNORE_CASE))

                for (part in parts) {
                    val trimmed = part.trim()
                        .replace(Regex("""\d+$"""), "") // Remove trailing numbers (affiliations)
                        .replace(Regex("""^\d+\s*"""), "") // Remove leading numbers
                        .replace(Regex("""\*+$"""), "") // Remove asterisks
                        .trim()

                    // Valid author name: 2-4 words, reasonable length
                    val words = trimmed.split(Regex("""\s+"""))
                    if (trimmed.length in 3..50 &&
                        words.size in 2..5 &&
                        words.all { it.isNotBlank() && it.first().isUpperCase() }) {
                        authors.add(trimmed)
                    }
                }

                if (authors.size >= 2) {
                    break // Found authors, stop searching
                }
            }
        }

        return authors.take(10) // Limit to 10 authors
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
 * Parsed reference entry from References section
 */
data class ParsedReference(
    val number: Int,
    val rawText: String,
    val authors: String? = null,
    val title: String? = null,
    val venue: String? = null,
    val year: Int? = null,
    val doi: String? = null,
    val pageNum: Int = 0
)

/**
 * Result of References section analysis
 */
data class ReferencesAnalysisResult(
    val startPage: Int?,
    val references: List<ParsedReference>,
    val sectionHeader: String? = null
)

/**
 * Result of PDF analysis
 */
data class PdfAnalysisResult(
    val metadata: PdfMetadata,
    val detectedDoi: String? = null,
    val detectedArxivId: String? = null,
    val extractedTitle: String? = null,
    val extractedAuthors: List<String> = emptyList(),
    val firstPagesText: String = "",
    val referencesResult: ReferencesAnalysisResult? = null
) {
    /**
     * Best title: metadata > extracted > null
     */
    val bestTitle: String?
        get() = metadata.title ?: extractedTitle

    /**
     * Best authors: metadata > extracted > empty
     */
    val bestAuthors: List<String>
        get() = if (!metadata.author.isNullOrBlank()) {
            // Split metadata author by common separators
            metadata.author.split(Regex("""\s*[,;]\s*|\s+and\s+""", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } else {
            extractedAuthors
        }

    /**
     * Whether we found any identifier that can be used for metadata lookup
     */
    val hasIdentifier: Boolean
        get() = detectedDoi != null || detectedArxivId != null
}
