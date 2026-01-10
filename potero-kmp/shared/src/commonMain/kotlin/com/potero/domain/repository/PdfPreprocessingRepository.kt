package com.potero.domain.repository

import com.potero.domain.model.*

/**
 * Repository for PDF preprocessing status and cached page text.
 * Manages the database-backed cache of extracted PDF content.
 */
interface PdfPreprocessingRepository {

    // === Status Management ===

    /**
     * Get preprocessing status for a paper
     */
    suspend fun getStatus(paperId: String): Result<PdfPreprocessingStatus?>

    /**
     * Insert initial preprocessing status (pending)
     */
    suspend fun insertStatus(status: PdfPreprocessingStatus): Result<PdfPreprocessingStatus>

    /**
     * Update preprocessing status
     */
    suspend fun updateStatus(status: PdfPreprocessingStatus): Result<PdfPreprocessingStatus>

    /**
     * Mark preprocessing as started
     */
    suspend fun markProcessing(paperId: String, startedAt: Long): Result<Unit>

    /**
     * Mark preprocessing as completed with quality metrics
     */
    suspend fun markCompleted(
        paperId: String,
        completedAt: Long,
        totalPages: Int,
        extractionMethod: ExtractionMethod,
        qualityScore: Double,
        grobidStatus: GrobidProcessStatus?,
        ocrStatus: OcrProcessStatus?,
        latexOcrStatus: LatexOcrStatus? = null
    ): Result<Unit>

    /**
     * Mark preprocessing as failed
     */
    suspend fun markFailed(
        paperId: String,
        completedAt: Long,
        errorMessage: String
    ): Result<Unit>

    /**
     * Delete preprocessing status
     */
    suspend fun deleteStatus(paperId: String): Result<Unit>

    /**
     * Get all papers with pending status
     */
    suspend fun getAllPending(): Result<List<PdfPreprocessingStatus>>

    /**
     * Get all papers currently processing
     */
    suspend fun getAllProcessing(): Result<List<PdfPreprocessingStatus>>

    /**
     * Get all completed preprocessings
     */
    suspend fun getAllCompleted(): Result<List<PdfPreprocessingStatus>>

    /**
     * Get status count by status type
     */
    suspend fun getStatusCounts(): Result<Map<PreprocessingStatus, Int>>

    // === Page Text Management ===

    /**
     * Get text for a specific page
     */
    suspend fun getPageText(paperId: String, pageNum: Int): Result<PdfPageText?>

    /**
     * Get all page texts for a paper
     */
    suspend fun getAllPageTexts(paperId: String): Result<List<PdfPageText>>

    /**
     * Get page texts in a range
     */
    suspend fun getPageRange(
        paperId: String,
        startPage: Int,
        endPage: Int
    ): Result<List<PdfPageText>>

    /**
     * Get full text by concatenating all pages
     */
    suspend fun getFullText(paperId: String): Result<String>

    /**
     * Get first N pages concatenated
     */
    suspend fun getFirstPages(paperId: String, maxPages: Int): Result<String>

    /**
     * Insert a single page text
     */
    suspend fun insertPageText(pageText: PdfPageText): Result<PdfPageText>

    /**
     * Insert multiple page texts in a transaction
     */
    suspend fun insertAllPageTexts(pages: List<PdfPageText>): Result<List<PdfPageText>>

    /**
     * Insert or replace a page text
     */
    suspend fun insertOrReplacePageText(pageText: PdfPageText): Result<PdfPageText>

    /**
     * Update page text
     */
    suspend fun updatePageText(pageText: PdfPageText): Result<PdfPageText>

    /**
     * Delete a specific page text
     */
    suspend fun deletePageText(pageId: String): Result<Unit>

    /**
     * Delete all page texts for a paper
     */
    suspend fun deleteAllPageTexts(paperId: String): Result<Unit>

    /**
     * Count page texts for a paper
     */
    suspend fun countPageTexts(paperId: String): Result<Int>

    /**
     * Get extraction method statistics for a paper
     */
    suspend fun getExtractionMethodStats(paperId: String): Result<Map<ExtractionMethod, Int>>

    /**
     * Get average quality score for a paper
     */
    suspend fun getAverageQualityScore(paperId: String): Result<Double>

    // === Cleanup ===

    /**
     * Delete all preprocessing data (status + pages) for a paper
     */
    suspend fun deleteAllPreprocessingData(paperId: String): Result<Unit>

    // === Convenience Methods ===

    /**
     * Check if a paper has been preprocessed
     */
    suspend fun isPreprocessed(paperId: String): Boolean {
        val status = getStatus(paperId).getOrNull()
        return status?.status == PreprocessingStatus.COMPLETED
    }

    /**
     * Check if preprocessing is in progress
     */
    suspend fun isProcessing(paperId: String): Boolean {
        val status = getStatus(paperId).getOrNull()
        return status?.status == PreprocessingStatus.PROCESSING
    }

    /**
     * Check if preprocessing failed
     */
    suspend fun isFailed(paperId: String): Boolean {
        val status = getStatus(paperId).getOrNull()
        return status?.status == PreprocessingStatus.FAILED
    }
}
