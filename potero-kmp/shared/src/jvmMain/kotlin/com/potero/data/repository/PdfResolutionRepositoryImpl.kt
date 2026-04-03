package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.PdfResolutionRecord
import com.potero.domain.repository.PdfResolutionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.util.UUID

class PdfResolutionRepositoryImpl(
    private val database: PoteroDatabase
) : PdfResolutionRepository {

    private val queries = database.pdfResolutionRecordQueries

    override suspend fun insert(record: PdfResolutionRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            queries.insert(
                id = record.id,
                paper_id = record.paperId,
                source = record.source,
                candidate_url = record.candidateUrl,
                status = record.status,
                failure_reason = record.failureReason,
                http_status = record.httpStatus?.toLong(),
                resolved_at = record.resolvedAt.toEpochMilliseconds()
            )
        }
    }

    override suspend fun getByPaperId(paperId: String): Result<List<PdfResolutionRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            queries.getByPaperId(paperId).executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getLastSuccess(paperId: String): Result<PdfResolutionRecord?> = withContext(Dispatchers.IO) {
        runCatching {
            queries.getLastSuccessByPaperId(paperId).executeAsOneOrNull()?.toDomain()
        }
    }

    override suspend fun deleteByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            queries.deleteByPaperId(paperId)
        }
    }

    private fun com.potero.db.PdfResolutionRecord.toDomain() = PdfResolutionRecord(
        id = id,
        paperId = paper_id,
        source = source,
        candidateUrl = candidate_url,
        status = status,
        failureReason = failure_reason,
        httpStatus = http_status?.toInt(),
        resolvedAt = Instant.fromEpochMilliseconds(resolved_at)
    )
}
