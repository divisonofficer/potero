package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference
import com.potero.domain.repository.GrobidRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Implementation of GrobidRepository using SQLDelight
 */
class GrobidRepositoryImpl(
    private val database: PoteroDatabase
) : GrobidRepository {

    private val citationSpanQueries = database.grobidCitationSpanQueries
    private val referenceQueries = database.grobidReferenceQueries

    // === Citation Span Operations ===

    override suspend fun insertCitationSpan(span: GrobidCitationSpan): Result<GrobidCitationSpan> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.insertGrobidCitationSpan(
                id = span.id,
                paper_id = span.paperId,
                page_num = span.pageNum.toLong(),
                raw_text = span.rawText,
                xml_id = span.xmlId,
                ref_type = span.refType,
                target_xml_id = span.targetXmlId,
                confidence = span.confidence,
                created_at = span.createdAt.toEpochMilliseconds()
            )
            span
        }
    }

    override suspend fun insertAllCitationSpans(spans: List<GrobidCitationSpan>): Result<List<GrobidCitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                spans.forEach { span ->
                    citationSpanQueries.insertGrobidCitationSpan(
                        id = span.id,
                        paper_id = span.paperId,
                        page_num = span.pageNum.toLong(),
                        raw_text = span.rawText,
                        xml_id = span.xmlId,
                        ref_type = span.refType,
                        target_xml_id = span.targetXmlId,
                        confidence = span.confidence,
                        created_at = span.createdAt.toEpochMilliseconds()
                    )
                }
            }
            spans
        }
    }

    override suspend fun getCitationSpansByPaperId(paperId: String): Result<List<GrobidCitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.getGrobidCitationSpansByPaper(paperId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getCitationSpansByPage(paperId: String, pageNum: Int): Result<List<GrobidCitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.getGrobidCitationSpansByPage(paperId, pageNum.toLong()).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getCitationSpansByType(paperId: String, refType: String): Result<List<GrobidCitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.getGrobidCitationSpansByType(paperId, refType).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getCitationSpansByTarget(targetXmlId: String): Result<List<GrobidCitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.getGrobidCitationSpansByTarget(targetXmlId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun deleteCitationSpansByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.deleteGrobidCitationSpansByPaper(paperId)
        }
    }

    override suspend fun countCitationSpansByPaperId(paperId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.countGrobidCitationSpansByPaper(paperId).executeAsOne().toInt()
        }
    }

    // === Reference Operations ===

    override suspend fun insertReference(reference: GrobidReference): Result<GrobidReference> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.insertGrobidReference(
                id = reference.id,
                paper_id = reference.paperId,
                xml_id = reference.xmlId,
                raw_tei = reference.rawTei,
                authors = reference.authors,
                title = reference.title,
                venue = reference.venue,
                year = reference.year?.toLong(),
                doi = reference.doi,
                arxiv_id = reference.arxivId,
                page_num = reference.pageNum?.toLong(),
                confidence = reference.confidence,
                created_at = reference.createdAt.toEpochMilliseconds()
            )
            reference
        }
    }

    override suspend fun insertAllReferences(references: List<GrobidReference>): Result<List<GrobidReference>> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                references.forEach { reference ->
                    referenceQueries.insertGrobidReference(
                        id = reference.id,
                        paper_id = reference.paperId,
                        xml_id = reference.xmlId,
                        raw_tei = reference.rawTei,
                        authors = reference.authors,
                        title = reference.title,
                        venue = reference.venue,
                        year = reference.year?.toLong(),
                        doi = reference.doi,
                        arxiv_id = reference.arxivId,
                        page_num = reference.pageNum?.toLong(),
                        confidence = reference.confidence,
                        created_at = reference.createdAt.toEpochMilliseconds()
                    )
                }
            }
            references
        }
    }

    override suspend fun getReferencesByPaperId(paperId: String): Result<List<GrobidReference>> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.getGrobidReferencesByPaper(paperId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getReferenceByXmlId(paperId: String, xmlId: String): Result<GrobidReference?> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.getGrobidReferenceByXmlId(paperId, xmlId).executeAsOneOrNull()?.toDomainModel()
        }
    }

    override suspend fun getReferencesByPage(paperId: String, pageNum: Int): Result<List<GrobidReference>> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.getGrobidReferencesByPage(paperId, pageNum.toLong()).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getReferenceByDoi(doi: String): Result<GrobidReference?> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.getGrobidReferenceByDoi(doi).executeAsOneOrNull()?.toDomainModel()
        }
    }

    override suspend fun deleteReferencesByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.deleteGrobidReferencesByPaper(paperId)
        }
    }

    override suspend fun countReferencesByPaperId(paperId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.countGrobidReferencesByPaper(paperId).executeAsOne().toInt()
        }
    }

    override suspend fun searchReferencesByTitle(paperId: String, title: String): Result<List<GrobidReference>> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.searchGrobidReferencesByTitle(paperId, title).executeAsList().map { it.toDomainModel() }
        }
    }

    // === Extension functions for mapping ===

    private fun com.potero.db.GrobidCitationSpan.toDomainModel(): GrobidCitationSpan {
        return GrobidCitationSpan(
            id = id,
            paperId = paper_id,
            pageNum = page_num.toInt(),
            rawText = raw_text,
            xmlId = xml_id,
            refType = ref_type,
            targetXmlId = target_xml_id,
            confidence = confidence,
            createdAt = Instant.fromEpochMilliseconds(created_at)
        )
    }

    private fun com.potero.db.GrobidReference.toDomainModel(): GrobidReference {
        return GrobidReference(
            id = id,
            paperId = paper_id,
            xmlId = xml_id,
            rawTei = raw_tei,
            authors = authors,
            title = title,
            venue = venue,
            year = year?.toInt(),
            doi = doi,
            arxivId = arxiv_id,
            pageNum = page_num?.toInt(),
            confidence = confidence,
            createdAt = Instant.fromEpochMilliseconds(created_at)
        )
    }
}
