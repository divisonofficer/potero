package com.potero.service.pdf

import com.potero.domain.model.Paper
import com.potero.service.metadata.SemanticScholarResolver
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File

/**
 * Service for downloading PDF files from various sources
 *
 * Tries multiple strategies in order:
 * 1. Semantic Scholar API (openAccessPdf)
 * 2. arXiv direct download (if arxivId present)
 * 3. Direct URL download (if provided)
 */
class PdfDownloadService(
    private val httpClient: HttpClient,
    private val semanticScholarResolver: SemanticScholarResolver
) {

    companion object {
        private const val PDF_MAGIC_BYTES = "%PDF"
        private const val MAX_PDF_SIZE = 100 * 1024 * 1024  // 100MB
    }

    /**
     * Try to download PDF from multiple sources
     *
     * @param paper The paper to download PDF for
     * @param directUrl Optional direct PDF URL to try first
     * @return Path to downloaded PDF file
     * @throws PdfDownloadException if no PDF source found or download fails
     */
    suspend fun downloadPdf(
        paper: Paper,
        directUrl: String? = null
    ): Result<String> = runCatching {
        val paperId = paper.id
        val fileName = sanitizeFileName(paper.title)

        // Try 0: Direct URL if provided
        if (directUrl != null) {
            try {
                return@runCatching downloadFromUrl(directUrl, paperId, fileName)
            } catch (e: Exception) {
                println("[PdfDownload] Direct URL failed: ${e.message}")
            }
        }

        // Try 1: Semantic Scholar API
        val pdfUrl = trySemanticScholar(paper.title, paper.authors.map { it.name })
        if (pdfUrl != null) {
            try {
                return@runCatching downloadFromUrl(pdfUrl, paperId, fileName)
            } catch (e: Exception) {
                println("[PdfDownload] Semantic Scholar URL failed: ${e.message}")
            }
        }

        // Try 2: arXiv direct download
        if (paper.arxivId != null) {
            try {
                val arxivUrl = buildArxivPdfUrl(paper.arxivId)
                return@runCatching downloadFromUrl(arxivUrl, paperId, fileName)
            } catch (e: Exception) {
                println("[PdfDownload] arXiv download failed: ${e.message}")
            }
        }

        // Try 3: DOI redirect (may not always work)
        if (paper.doi != null) {
            try {
                val doiUrl = "https://doi.org/${paper.doi}"
                return@runCatching downloadFromUrl(doiUrl, paperId, fileName)
            } catch (e: Exception) {
                println("[PdfDownload] DOI redirect failed: ${e.message}")
            }
        }

        throw PdfDownloadException("No PDF source found for paper: ${paper.title}")
    }

    /**
     * Try to find PDF URL using Semantic Scholar API
     */
    private suspend fun trySemanticScholar(
        title: String,
        authors: List<String>
    ): String? {
        try {
            // Search by title
            val results = semanticScholarResolver.search(title, limit = 3)
            if (results.isEmpty()) return null

            // Find best match by title similarity
            val bestMatch = results.firstOrNull { result ->
                val titleSimilarity = calculateSimilarity(result.title, title)
                titleSimilarity > 0.85
            } ?: results.firstOrNull()

            // Get paper details with openAccessPdf field
            val paperDetails = semanticScholarResolver.getPaperDetails(bestMatch?.paperId ?: return null)
                .getOrNull()

            return paperDetails?.openAccessPdf?.url
        } catch (e: Exception) {
            println("[PdfDownload] Semantic Scholar search failed: ${e.message}")
            return null
        }
    }

    /**
     * Download PDF from URL and save to local storage
     */
    private suspend fun downloadFromUrl(
        url: String,
        paperId: String,
        fileName: String
    ): String {
        println("[PdfDownload] Downloading from: $url")

        val response = httpClient.get(url) {
            // Follow redirects
            header(HttpHeaders.UserAgent, "Potero/1.0 (Research Paper Manager)")
        }

        if (!response.status.isSuccess()) {
            throw PdfDownloadException("HTTP ${response.status.value}: ${response.status.description}")
        }

        // Check content type
        val contentType = response.contentType()?.contentType
        if (contentType != null && !contentType.contains("pdf", ignoreCase = true)) {
            println("[PdfDownload] Warning: Content-Type is $contentType (expected application/pdf)")
        }

        // Read bytes
        val pdfBytes = response.bodyAsBytes()

        // Validate size
        if (pdfBytes.size > MAX_PDF_SIZE) {
            throw PdfDownloadException("PDF file too large: ${pdfBytes.size} bytes (max: $MAX_PDF_SIZE)")
        }

        // Validate PDF magic bytes
        if (!isPdf(pdfBytes)) {
            throw PdfDownloadException("Downloaded file is not a valid PDF")
        }

        // Save to local storage
        val storageDir = File(System.getProperty("user.home"), ".potero/pdfs")
        storageDir.mkdirs()

        val pdfFile = File(storageDir, "$paperId-$fileName")
        pdfFile.writeBytes(pdfBytes)

        println("[PdfDownload] Downloaded PDF: ${pdfFile.absolutePath} (${pdfBytes.size} bytes)")

        return pdfFile.absolutePath
    }

    /**
     * Build arXiv PDF URL from arXiv ID
     * Supports both old and new arXiv ID formats
     */
    private fun buildArxivPdfUrl(arxivId: String): String {
        val cleanedId = arxivId.removePrefix("arXiv:")
        return "https://arxiv.org/pdf/$cleanedId.pdf"
    }

    /**
     * Validate PDF file by checking magic bytes
     */
    private fun isPdf(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false

        return bytes[0] == 0x25.toByte() &&  // %
               bytes[1] == 0x50.toByte() &&  // P
               bytes[2] == 0x44.toByte() &&  // D
               bytes[3] == 0x46.toByte()     // F
    }

    /**
     * Sanitize filename for file system
     */
    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^a-zA-Z0-9가-힣\\s.-]"), "_")
            .take(200)  // Limit length
            .trim()
            .ifBlank { "paper" }
            .plus(".pdf")
    }

    /**
     * Calculate simple title similarity (Jaccard similarity)
     */
    private fun calculateSimilarity(str1: String, str2: String): Double {
        val words1 = str1.lowercase().split(Regex("\\s+")).toSet()
        val words2 = str2.lowercase().split(Regex("\\s+")).toSet()

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}

/**
 * Exception thrown when PDF download fails
 */
class PdfDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
