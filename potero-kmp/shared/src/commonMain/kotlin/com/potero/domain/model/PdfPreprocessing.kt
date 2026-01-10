package com.potero.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Status of PDF preprocessing pipeline for a paper.
 * Tracks extraction progress and quality metrics.
 */
@Serializable
data class PdfPreprocessingStatus(
    val paperId: String,
    val version: Int = 1,
    val status: PreprocessingStatus,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val totalPages: Int = 0,
    val extractionMethod: ExtractionMethod? = null,
    val qualityScore: Double? = null,
    val grobidStatus: GrobidProcessStatus? = null,
    val ocrStatus: OcrProcessStatus? = null,
    val latexOcrStatus: LatexOcrStatus? = null,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Overall preprocessing status
 */
@Serializable
enum class PreprocessingStatus {
    PENDING,     // Queued for processing
    PROCESSING,  // Currently being processed
    COMPLETED,   // Successfully completed
    FAILED       // Processing failed
}

/**
 * Primary extraction method used
 */
@Serializable
enum class ExtractionMethod {
    PDFBOX,      // PDFBox text extraction (fastest)
    PDFTOTEXT,   // Poppler pdftotext fallback
    OCR,         // Tesseract OCR (slowest)
    HYBRID       // Mixed methods per page
}

/**
 * GROBID processing status
 */
@Serializable
enum class GrobidProcessStatus {
    SUCCESS,   // GROBID successfully extracted citations/references
    FAILED,    // GROBID failed, used LLM fallback
    SKIPPED    // GROBID disabled in settings
}

/**
 * OCR processing status
 */
@Serializable
enum class OcrProcessStatus {
    SUCCESS,   // OCR used for some pages
    FAILED,    // OCR failed to extract
    SKIPPED    // OCR disabled or not needed
}

/**
 * Extracted text for a single PDF page with metadata.
 * Stores cached text to avoid re-extraction.
 */
@Serializable
data class PdfPageText(
    val id: String,
    val paperId: String,
    val pageNum: Int,
    val textContent: String,
    val extractionMethod: ExtractionMethod,
    val isGarbled: Boolean = false,
    val qualityScore: Double = 1.0,
    val ocrConfidence: Double? = null,
    val wordCount: Int = 0,
    val createdAt: Instant
) {
    /**
     * Check if this page has acceptable quality
     */
    val isAcceptableQuality: Boolean
        get() = !isGarbled && qualityScore >= 0.5
}

/**
 * Result of text extraction with quality metrics
 */
@Serializable
data class TextExtractionResult(
    val pages: List<PageTextData>,
    val totalPages: Int,
    val overallMethod: ExtractionMethod,
    val averageQuality: Double
) {
    /**
     * Get full text by concatenating all pages
     */
    val fullText: String
        get() = pages.joinToString("\n\n") { it.text }

    /**
     * Get statistics on extraction methods used
     */
    val methodStats: Map<ExtractionMethod, Int>
        get() = pages.groupBy { it.method }.mapValues { it.value.size }
}

/**
 * Single page text with extraction metadata
 */
@Serializable
data class PageTextData(
    val pageNum: Int,
    val text: String,
    val method: ExtractionMethod,
    val isGarbled: Boolean = false,
    val qualityScore: Double = 1.0,
    val ocrConfidence: Double? = null
) {
    val wordCount: Int
        get() = text.split("\\s+".toRegex()).size
}

/**
 * Exception thrown when preprocessing is required but not yet completed
 */
class PreprocessingPendingException(
    message: String = "PDF preprocessing is in progress. Please try again later."
) : Exception(message)

/**
 * Exception thrown when preprocessing fails
 */
class PreprocessingFailedException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * LaTeX equation extracted from PDF using OCR.
 * Stores mathematical formulas in LaTeX format for rendering.
 */
@Serializable
data class PdfEquation(
    val id: String,
    val paperId: String,
    val pageNum: Int,
    val equationIndex: Int,  // Index within page
    val latexCode: String,
    val originalText: String? = null,  // Raw text from GROBID
    val confidence: Double? = null,  // OCR confidence (0-1)
    val isInline: Boolean = false,  // Display vs inline equation
    val boundingBox: String? = null,  // JSON bbox
    val mathpixRequestId: String? = null,
    val createdAt: Instant
) {
    /**
     * Check if equation has acceptable confidence
     */
    val isHighConfidence: Boolean
        get() = confidence?.let { it >= 0.8 } ?: false
}

/**
 * LaTeX OCR processing status
 */
@Serializable
enum class LatexOcrStatus {
    SUCCESS,   // LaTeX OCR completed successfully
    PARTIAL,   // Some equations failed
    FAILED,    // LaTeX OCR failed completely
    SKIPPED    // LaTeX OCR disabled or no equations found
}
