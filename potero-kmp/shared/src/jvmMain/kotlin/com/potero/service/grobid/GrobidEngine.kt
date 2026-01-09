package com.potero.service.grobid

/**
 * Interface for GROBID PDF processing engine
 *
 * Provides methods to extract structured information from PDF files using GROBID.
 * Implementations can be embedded (EmbeddedGrobidEngine) or disabled (DisabledGrobidEngine).
 */
interface GrobidEngine {
    /**
     * Process full text of a PDF and extract all structural elements
     *
     * This includes:
     * - Header metadata (title, authors, abstract)
     * - Body elements (citations, figures, formulas, person mentions)
     * - References with bounding boxes
     *
     * @param pdfPath Absolute path to the PDF file
     * @return TEIDocument containing all extracted information
     * @throws GrobidException if processing fails
     */
    suspend fun processFulltext(pdfPath: String): TEIDocument

    /**
     * Process only the header of a PDF (faster than full text)
     *
     * Extracts:
     * - Title
     * - Authors
     * - Abstract
     * - Keywords
     *
     * @param pdfPath Absolute path to the PDF file
     * @return TEIDocument with only header information
     * @throws GrobidException if processing fails
     */
    suspend fun processHeader(pdfPath: String): TEIDocument

    /**
     * Check if GROBID engine is available and initialized
     *
     * @return true if engine is ready to process PDFs
     */
    fun isAvailable(): Boolean

    /**
     * Get information about the GROBID engine
     *
     * @return Engine information including version, model path, etc.
     */
    fun getInfo(): GrobidEngineInfo
}

/**
 * Information about the GROBID engine instance
 */
data class GrobidEngineInfo(
    val version: String,
    val grobidHomePath: String,
    val isInitialized: Boolean,
    val modelsDownloaded: Boolean
)

/**
 * Exception thrown when GROBID processing fails
 */
class GrobidException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
