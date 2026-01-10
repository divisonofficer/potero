package com.potero.service.pdf

import com.potero.domain.model.*
import com.potero.domain.repository.GrobidRepository
import com.potero.domain.repository.PdfPreprocessingRepository
import com.potero.service.pdf.preprocessing.PdfPreprocessingService

/**
 * Implementation of PreprocessedPdfProvider.
 * Provides access to cached PDF extraction results and GROBID data.
 */
class PreprocessedPdfProviderImpl(
    private val preprocessingRepository: PdfPreprocessingRepository,
    private val grobidRepository: GrobidRepository,
    private val preprocessingService: PdfPreprocessingService
) : PreprocessedPdfProvider {

    // === Text Access ===

    override suspend fun getFullText(paperId: String): Result<String> {
        // Check if preprocessed
        if (!isPreprocessed(paperId)) {
            return Result.failure(
                PreprocessingPendingException("Paper $paperId has not been preprocessed yet")
            )
        }

        return preprocessingRepository.getFullText(paperId)
    }

    override suspend fun getPageText(paperId: String, pageNum: Int): Result<String> {
        // Check if preprocessed
        if (!isPreprocessed(paperId)) {
            return Result.failure(
                PreprocessingPendingException("Paper $paperId has not been preprocessed yet")
            )
        }

        return preprocessingRepository.getPageText(paperId, pageNum)
            .mapCatching { it?.textContent ?: throw NoSuchElementException("Page $pageNum not found") }
    }

    override suspend fun getFirstPages(paperId: String, maxPages: Int): Result<String> {
        // Check if preprocessed
        if (!isPreprocessed(paperId)) {
            return Result.failure(
                PreprocessingPendingException("Paper $paperId has not been preprocessed yet")
            )
        }

        return preprocessingRepository.getFirstPages(paperId, maxPages)
    }

    override suspend fun getPageRange(paperId: String, startPage: Int, endPage: Int): Result<String> {
        // Check if preprocessed
        if (!isPreprocessed(paperId)) {
            return Result.failure(
                PreprocessingPendingException("Paper $paperId has not been preprocessed yet")
            )
        }

        return preprocessingRepository.getPageRange(paperId, startPage, endPage)
            .mapCatching { pages ->
                pages.joinToString("\n\n") { it.textContent }
            }
    }

    // === Structured Data (from GROBID) ===

    override suspend fun getReferences(paperId: String): Result<List<GrobidReference>> {
        return grobidRepository.getReferencesByPaperId(paperId)
    }

    override suspend fun getCitations(paperId: String): Result<List<GrobidCitationSpan>> {
        return grobidRepository.getCitationSpansByPaperId(paperId)
    }

    // === Status Checks ===

    override suspend fun isPreprocessed(paperId: String): Boolean {
        return preprocessingRepository.isPreprocessed(paperId)
    }

    override suspend fun isProcessing(paperId: String): Boolean {
        return preprocessingRepository.isProcessing(paperId)
    }

    override suspend fun getPreprocessingStatus(paperId: String): Result<PdfPreprocessingStatus?> {
        return preprocessingRepository.getStatus(paperId)
    }

    override suspend fun ensurePreprocessed(paperId: String, pdfPath: String): Result<Unit> {
        // Check current status
        val status = preprocessingRepository.getStatus(paperId).getOrNull()

        return when (status?.status) {
            PreprocessingStatus.COMPLETED -> {
                // Already preprocessed
                Result.success(Unit)
            }
            PreprocessingStatus.PROCESSING -> {
                // Currently processing
                Result.failure(PreprocessingPendingException("Preprocessing in progress for paper $paperId"))
            }
            PreprocessingStatus.FAILED -> {
                // Failed previously, trigger reprocessing
                println("[PreprocessedPdfProvider] Re-triggering preprocessing for $paperId (previous failure: ${status.errorMessage})")
                preprocessingService.preprocessPaper(paperId, pdfPath, forceReprocess = true)
                Result.failure(PreprocessingPendingException("Preprocessing re-triggered for paper $paperId"))
            }
            PreprocessingStatus.PENDING, null -> {
                // Not started yet, trigger preprocessing
                println("[PreprocessedPdfProvider] Triggering preprocessing for $paperId")
                preprocessingService.preprocessPaper(paperId, pdfPath, forceReprocess = false)
                Result.failure(PreprocessingPendingException("Preprocessing started for paper $paperId"))
            }
        }
    }
}
