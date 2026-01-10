package com.potero.service.pdf

import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference
import com.potero.domain.model.PdfPreprocessingStatus

/**
 * Unified API for accessing preprocessed PDF data.
 * Provides cached text extraction results and structured data from GROBID.
 *
 * All downstream services (Chat, Narrative, LLM Analysis, Blog) should use this
 * interface instead of directly extracting PDF content.
 */
interface PreprocessedPdfProvider {

    // === Text Access ===

    /**
     * Get full text for a paper (all pages concatenated)
     * @return Full text from cache, or error if not preprocessed
     */
    suspend fun getFullText(paperId: String): Result<String>

    /**
     * Get text for a specific page
     * @return Page text from cache, or error if not preprocessed
     */
    suspend fun getPageText(paperId: String, pageNum: Int): Result<String>

    /**
     * Get first N pages concatenated
     * @param maxPages Number of pages to retrieve
     * @return Concatenated text of first N pages
     */
    suspend fun getFirstPages(paperId: String, maxPages: Int): Result<String>

    /**
     * Get text for a page range
     * @param startPage First page (1-indexed)
     * @param endPage Last page (inclusive)
     * @return Concatenated text of pages in range
     */
    suspend fun getPageRange(paperId: String, startPage: Int, endPage: Int): Result<String>

    // === Structured Data (from GROBID) ===

    /**
     * Get all GROBID-extracted references for a paper
     * @return List of structured references with metadata
     */
    suspend fun getReferences(paperId: String): Result<List<GrobidReference>>

    /**
     * Get all GROBID-extracted citation spans for a paper
     * @return List of in-text citations with locations
     */
    suspend fun getCitations(paperId: String): Result<List<GrobidCitationSpan>>

    // === Status Checks ===

    /**
     * Check if a paper has been preprocessed
     * @return true if preprocessing is completed
     */
    suspend fun isPreprocessed(paperId: String): Boolean

    /**
     * Check if preprocessing is currently in progress
     * @return true if currently being processed
     */
    suspend fun isProcessing(paperId: String): Boolean

    /**
     * Get preprocessing status for a paper
     * @return Status with quality metrics, or null if not started
     */
    suspend fun getPreprocessingStatus(paperId: String): Result<PdfPreprocessingStatus?>

    /**
     * Ensure a paper has been preprocessed, triggering it if needed.
     *
     * This is the recommended method for lazy preprocessing:
     * - If already preprocessed, returns success immediately
     * - If processing, returns failure with PreprocessingPendingException
     * - If not started, triggers background preprocessing and returns pending exception
     *
     * @param paperId Paper to preprocess
     * @param pdfPath Path to PDF file
     * @return Success if completed, failure with exception if pending/processing
     */
    suspend fun ensurePreprocessed(paperId: String, pdfPath: String): Result<Unit>

    // === Convenience Methods ===

    /**
     * Get text with fallback to direct extraction.
     * Tries preprocessed cache first, falls back to legacy extraction if not available.
     *
     * @param paperId Paper ID
     * @param pdfPath Path to PDF file for fallback
     * @param extractor Fallback extraction function (e.g., PdfAnalyzer::extractFullText)
     * @return Full text from cache or fallback
     */
    suspend fun getFullTextWithFallback(
        paperId: String,
        pdfPath: String,
        extractor: suspend (String) -> String
    ): Result<String> {
        // Try preprocessed first
        getFullText(paperId).onSuccess { return Result.success(it) }

        // Fallback to direct extraction
        println("[PreprocessedPdfProvider] Cache miss for $paperId, using fallback extractor")
        return runCatching { extractor(pdfPath) }
    }

    /**
     * Get first pages with fallback to direct extraction.
     *
     * @param paperId Paper ID
     * @param pdfPath Path to PDF file for fallback
     * @param maxPages Number of pages to retrieve
     * @param extractor Fallback extraction function
     * @return Text from cache or fallback
     */
    suspend fun getFirstPagesWithFallback(
        paperId: String,
        pdfPath: String,
        maxPages: Int,
        extractor: suspend (String, Int) -> String
    ): Result<String> {
        // Try preprocessed first
        getFirstPages(paperId, maxPages).onSuccess { return Result.success(it) }

        // Fallback to direct extraction
        println("[PreprocessedPdfProvider] Cache miss for $paperId, using fallback extractor")
        return runCatching { extractor(pdfPath, maxPages) }
    }
}
