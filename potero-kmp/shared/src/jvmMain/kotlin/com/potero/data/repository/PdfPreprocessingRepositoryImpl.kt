package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.*
import com.potero.domain.repository.PdfPreprocessingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Implementation of PdfPreprocessingRepository using SQLDelight
 */
class PdfPreprocessingRepositoryImpl(
    private val database: PoteroDatabase
) : PdfPreprocessingRepository {

    private val statusQueries = database.pdfPreprocessingQueries
    private val pageTextQueries = database.pdfPreprocessingQueries

    // === Status Management ===

    override suspend fun getStatus(paperId: String): Result<PdfPreprocessingStatus?> = withContext(Dispatchers.IO) {
        runCatching {
            // Get caller info from stack trace
            val caller = Throwable().stackTrace.getOrNull(2)?.let {
                "${it.className.substringAfterLast('.')}.${it.methodName}"
            } ?: "Unknown"

            println("[CACHE SERVICE] getStatus() called by: $caller for paper: $paperId")

            val status = statusQueries.getStatus(paperId).executeAsOneOrNull()?.toDomainModel()
            if (status != null) {
                println("[CACHE SERVICE] ✓ Status found: ${status.status} (${status.totalPages} pages, method: ${status.extractionMethod})")
            } else {
                println("[CACHE SERVICE] ✗ No preprocessing status found for paper $paperId")
            }

            status
        }
    }

    override suspend fun insertStatus(status: PdfPreprocessingStatus): Result<PdfPreprocessingStatus> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.insertStatus(
                paper_id = status.paperId,
                version = status.version.toLong(),
                status = status.status.name.lowercase(),
                started_at = status.startedAt?.toEpochMilliseconds(),
                completed_at = status.completedAt?.toEpochMilliseconds(),
                total_pages = status.totalPages.toLong(),
                extraction_method = status.extractionMethod?.name?.lowercase(),
                quality_score = status.qualityScore,
                grobid_status = status.grobidStatus?.name?.lowercase(),
                ocr_status = status.ocrStatus?.name?.lowercase(),
                latex_ocr_status = status.latexOcrStatus?.name?.lowercase(),
                error_message = status.errorMessage,
                created_at = status.createdAt.toEpochMilliseconds(),
                updated_at = status.updatedAt.toEpochMilliseconds()
            )
            status
        }
    }

    override suspend fun updateStatus(status: PdfPreprocessingStatus): Result<PdfPreprocessingStatus> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.updateStatus(
                version = status.version.toLong(),
                status = status.status.name.lowercase(),
                started_at = status.startedAt?.toEpochMilliseconds(),
                completed_at = status.completedAt?.toEpochMilliseconds(),
                total_pages = status.totalPages.toLong(),
                extraction_method = status.extractionMethod?.name?.lowercase(),
                quality_score = status.qualityScore,
                grobid_status = status.grobidStatus?.name?.lowercase(),
                ocr_status = status.ocrStatus?.name?.lowercase(),
                latex_ocr_status = status.latexOcrStatus?.name?.lowercase(),
                error_message = status.errorMessage,
                updated_at = status.updatedAt.toEpochMilliseconds(),
                paper_id = status.paperId
            )
            status
        }
    }

    override suspend fun markProcessing(paperId: String, startedAt: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.markProcessing(
                started_at = startedAt,
                updated_at = System.currentTimeMillis(),
                paper_id = paperId
            )
        }
    }

    override suspend fun markCompleted(
        paperId: String,
        completedAt: Long,
        totalPages: Int,
        extractionMethod: ExtractionMethod,
        qualityScore: Double,
        grobidStatus: GrobidProcessStatus?,
        ocrStatus: OcrProcessStatus?,
        latexOcrStatus: LatexOcrStatus?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.markCompleted(
                completed_at = completedAt,
                total_pages = totalPages.toLong(),
                extraction_method = extractionMethod.name.lowercase(),
                quality_score = qualityScore,
                grobid_status = grobidStatus?.name?.lowercase(),
                ocr_status = ocrStatus?.name?.lowercase(),
                latex_ocr_status = latexOcrStatus?.name?.lowercase(),
                updated_at = System.currentTimeMillis(),
                paper_id = paperId
            )
        }
    }

    override suspend fun markFailed(
        paperId: String,
        completedAt: Long,
        errorMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.markFailed(
                completed_at = completedAt,
                error_message = errorMessage,
                updated_at = System.currentTimeMillis(),
                paper_id = paperId
            )
        }
    }

    override suspend fun deleteStatus(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.deleteStatus(paperId)
        }
    }

    override suspend fun getAllPending(): Result<List<PdfPreprocessingStatus>> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.getAllPending().executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getAllProcessing(): Result<List<PdfPreprocessingStatus>> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.getAllProcessing().executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getAllCompleted(): Result<List<PdfPreprocessingStatus>> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.getAllCompleted().executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getStatusCounts(): Result<Map<PreprocessingStatus, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            statusQueries.countByStatus().executeAsList().associate {
                PreprocessingStatus.valueOf(it.status.uppercase()) to it.total.toInt()
            }
        }
    }

    // === Page Text Management ===

    override suspend fun getPageText(paperId: String, pageNum: Int): Result<PdfPageText?> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.getPageText(paperId, pageNum.toLong()).executeAsOneOrNull()?.toDomainModel()
        }
    }

    override suspend fun getAllPageTexts(paperId: String): Result<List<PdfPageText>> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.getAllPageTexts(paperId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getPageRange(
        paperId: String,
        startPage: Int,
        endPage: Int
    ): Result<List<PdfPageText>> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.getPageRange(paperId, startPage.toLong(), endPage.toLong())
                .executeAsList()
                .map { it.toDomainModel() }
        }
    }

    override suspend fun getFullText(paperId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Get caller info from stack trace
            val caller = Throwable().stackTrace.getOrNull(2)?.let {
                "${it.className.substringAfterLast('.')}.${it.methodName}"
            } ?: "Unknown"

            println("[CACHE SERVICE] ========================================")
            println("[CACHE SERVICE] getFullText() called by: $caller")
            println("[CACHE SERVICE] Paper ID: $paperId")

            val result = pageTextQueries.getFullText(paperId).executeAsOneOrNull() ?: ""
            if (result.isNotBlank()) {
                println("[CACHE SERVICE] ✓ Cache HIT: Retrieved ${result.length} chars")
                println("[CACHE SERVICE] ✓ Caller '$caller' will use cached text")
            } else {
                println("[CACHE SERVICE] ✗ Cache MISS: No text found")
                println("[CACHE SERVICE] ✗ Caller '$caller' will need to extract text")
            }
            println("[CACHE SERVICE] ========================================")
            result
        }
    }

    override suspend fun getFirstPages(paperId: String, maxPages: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.getFirstPages(paperId, maxPages.toLong()).executeAsOneOrNull() ?: ""
        }
    }

    override suspend fun insertPageText(pageText: PdfPageText): Result<PdfPageText> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.insertPageText(
                id = pageText.id,
                paper_id = pageText.paperId,
                page_num = pageText.pageNum.toLong(),
                text_content = pageText.textContent,
                extraction_method = pageText.extractionMethod.name.lowercase(),
                is_garbled = if (pageText.isGarbled) 1 else 0,
                quality_score = pageText.qualityScore,
                ocr_confidence = pageText.ocrConfidence,
                word_count = pageText.wordCount.toLong(),
                created_at = pageText.createdAt.toEpochMilliseconds()
            )
            pageText
        }
    }

    override suspend fun insertAllPageTexts(pages: List<PdfPageText>): Result<List<PdfPageText>> = withContext(Dispatchers.IO) {
        runCatching {
            // Get caller info from stack trace
            val caller = Throwable().stackTrace.getOrNull(2)?.let {
                "${it.className.substringAfterLast('.')}.${it.methodName}"
            } ?: "Unknown"

            val paperId = pages.firstOrNull()?.paperId ?: "unknown"
            val totalChars = pages.sumOf { it.textContent.length }
            val extractionMethods = pages.map { it.extractionMethod }.distinct()

            println("[CACHE SERVICE] ========================================")
            println("[CACHE SERVICE] insertAllPageTexts() called by: $caller")
            println("[CACHE SERVICE] Paper ID: $paperId")
            println("[CACHE SERVICE] Saving ${pages.size} pages to cache")
            println("[CACHE SERVICE] Total text size: $totalChars chars")
            println("[CACHE SERVICE] Extraction methods: $extractionMethods")

            database.transaction {
                pages.forEach { pageText ->
                    pageTextQueries.insertPageText(
                        id = pageText.id,
                        paper_id = pageText.paperId,
                        page_num = pageText.pageNum.toLong(),
                        text_content = pageText.textContent,
                        extraction_method = pageText.extractionMethod.name.lowercase(),
                        is_garbled = if (pageText.isGarbled) 1 else 0,
                        quality_score = pageText.qualityScore,
                        ocr_confidence = pageText.ocrConfidence,
                        word_count = pageText.wordCount.toLong(),
                        created_at = pageText.createdAt.toEpochMilliseconds()
                    )
                }
            }

            println("[CACHE SERVICE] ✓ Cache SAVED: ${pages.size} pages stored successfully")
            println("[CACHE SERVICE] ✓ Future calls can now use cached text")
            println("[CACHE SERVICE] ========================================")

            pages
        }
    }

    override suspend fun insertOrReplacePageText(pageText: PdfPageText): Result<PdfPageText> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.insertOrReplacePageText(
                id = pageText.id,
                paper_id = pageText.paperId,
                page_num = pageText.pageNum.toLong(),
                text_content = pageText.textContent,
                extraction_method = pageText.extractionMethod.name.lowercase(),
                is_garbled = if (pageText.isGarbled) 1 else 0,
                quality_score = pageText.qualityScore,
                ocr_confidence = pageText.ocrConfidence,
                word_count = pageText.wordCount.toLong(),
                created_at = pageText.createdAt.toEpochMilliseconds()
            )
            pageText
        }
    }

    override suspend fun updatePageText(pageText: PdfPageText): Result<PdfPageText> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.updatePageText(
                text_content = pageText.textContent,
                extraction_method = pageText.extractionMethod.name.lowercase(),
                is_garbled = if (pageText.isGarbled) 1 else 0,
                quality_score = pageText.qualityScore,
                ocr_confidence = pageText.ocrConfidence,
                word_count = pageText.wordCount.toLong(),
                id = pageText.id
            )
            pageText
        }
    }

    override suspend fun deletePageText(pageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.deletePageText(pageId)
        }
    }

    override suspend fun deleteAllPageTexts(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.deleteAllPageTexts(paperId)
        }
    }

    override suspend fun countPageTexts(paperId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.countPageTexts(paperId).executeAsOne().toInt()
        }
    }

    override suspend fun getExtractionMethodStats(paperId: String): Result<Map<ExtractionMethod, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.getExtractionMethodStats(paperId).executeAsList().associate {
                ExtractionMethod.valueOf(it.extraction_method.uppercase()) to it.total.toInt()
            }
        }
    }

    override suspend fun getAverageQualityScore(paperId: String): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            pageTextQueries.getAverageQualityScore(paperId).executeAsOneOrNull()?.avgQuality ?: 0.0
        }
    }

    // === Cleanup ===

    override suspend fun deleteAllPreprocessingData(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                pageTextQueries.deleteAllPageTexts(paperId)
                statusQueries.deleteStatus(paperId)
            }
        }
    }

    // === Domain Model Mappers ===

    private fun com.potero.db.PdfPreprocessingStatus.toDomainModel(): PdfPreprocessingStatus {
        return PdfPreprocessingStatus(
            paperId = paper_id,
            version = version.toInt(),
            status = PreprocessingStatus.valueOf(status.uppercase()),
            startedAt = started_at?.let { Instant.fromEpochMilliseconds(it) },
            completedAt = completed_at?.let { Instant.fromEpochMilliseconds(it) },
            totalPages = total_pages.toInt(),
            extractionMethod = extraction_method?.let { ExtractionMethod.valueOf(it.uppercase()) },
            qualityScore = quality_score,
            grobidStatus = grobid_status?.let { GrobidProcessStatus.valueOf(it.uppercase()) },
            ocrStatus = ocr_status?.let { OcrProcessStatus.valueOf(it.uppercase()) },
            errorMessage = error_message,
            createdAt = Instant.fromEpochMilliseconds(created_at),
            updatedAt = Instant.fromEpochMilliseconds(updated_at)
        )
    }

    private fun com.potero.db.PdfPageText.toDomainModel(): PdfPageText {
        return PdfPageText(
            id = id,
            paperId = paper_id,
            pageNum = page_num.toInt(),
            textContent = text_content,
            extractionMethod = ExtractionMethod.valueOf(extraction_method.uppercase()),
            isGarbled = is_garbled == 1L,
            qualityScore = quality_score,
            ocrConfidence = ocr_confidence,
            wordCount = word_count.toInt(),
            createdAt = Instant.fromEpochMilliseconds(created_at)
        )
    }
}
