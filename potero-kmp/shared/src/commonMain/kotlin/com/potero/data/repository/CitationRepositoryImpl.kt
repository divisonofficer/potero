package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.*
import com.potero.domain.repository.CitationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Implementation of CitationRepository using SQLDelight
 */
class CitationRepositoryImpl(
    private val database: PoteroDatabase
) : CitationRepository {

    private val citationSpanQueries = database.citationSpanQueries
    private val citationLinkQueries = database.citationLinkQueries
    private val referenceQueries = database.referenceQueries

    // === CitationSpan Operations ===

    override fun observeByPaperId(paperId: String): Flow<List<CitationSpan>> = flow {
        emit(getSpansByPaperId(paperId).getOrDefault(emptyList()))
    }

    override suspend fun getSpansByPaperId(paperId: String): Result<List<CitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.selectByPaperId(paperId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getSpansByPage(paperId: String, pageNum: Int): Result<List<CitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.selectByPage(paperId, pageNum.toLong()).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getSpanById(id: String): Result<CitationSpan?> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.selectById(id).executeAsOneOrNull()?.toDomainModel()
        }
    }

    override suspend fun insertSpan(span: CitationSpan): Result<CitationSpan> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.insert(
                id = span.id,
                paper_id = span.paperId,
                page_num = span.pageNum.toLong(),
                bbox_x1 = span.bbox.x1,
                bbox_y1 = span.bbox.y1,
                bbox_x2 = span.bbox.x2,
                bbox_y2 = span.bbox.y2,
                raw_text = span.rawText,
                style = span.style.name.lowercase(),
                provenance = span.provenance.name.lowercase(),
                confidence = span.confidence,
                dest_page = span.destPage?.toLong(),
                dest_y = span.destY,
                created_at = span.createdAt.toEpochMilliseconds()
            )
            span
        }
    }

    override suspend fun insertAllSpans(spans: List<CitationSpan>): Result<List<CitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                spans.forEach { span ->
                    citationSpanQueries.insert(
                        id = span.id,
                        paper_id = span.paperId,
                        page_num = span.pageNum.toLong(),
                        bbox_x1 = span.bbox.x1,
                        bbox_y1 = span.bbox.y1,
                        bbox_x2 = span.bbox.x2,
                        bbox_y2 = span.bbox.y2,
                        raw_text = span.rawText,
                        style = span.style.name.lowercase(),
                        provenance = span.provenance.name.lowercase(),
                        confidence = span.confidence,
                        dest_page = span.destPage?.toLong(),
                        dest_y = span.destY,
                        created_at = span.createdAt.toEpochMilliseconds()
                    )
                }
            }
            spans
        }
    }

    override suspend fun deleteSpansByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.deleteByPaperId(paperId)
        }
    }

    override suspend fun countSpansByPaperId(paperId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.countByPaperId(paperId).executeAsOne().toInt()
        }
    }

    override suspend fun countSpansByProvenance(paperId: String): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            citationSpanQueries.countByProvenance(paperId).executeAsList().associate { row ->
                row.provenance to row.count.toInt()
            }
        }
    }

    // === CitationLink Operations ===

    override suspend fun getLinksBySpanId(spanId: String): Result<List<CitationLink>> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.selectBySpanId(spanId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getLinksByReferenceId(referenceId: String): Result<List<CitationLink>> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.selectByReferenceId(referenceId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getReferencesForSpan(spanId: String): Result<List<Reference>> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.selectReferencesForSpan(spanId).executeAsList().map { dbRef ->
                Reference(
                    id = dbRef.id,
                    paperId = dbRef.paper_id,
                    number = dbRef.number.toInt(),
                    rawText = dbRef.raw_text,
                    authors = dbRef.authors,
                    title = dbRef.title,
                    venue = dbRef.venue,
                    year = dbRef.year?.toInt(),
                    doi = dbRef.doi,
                    pageNum = dbRef.page_num.toInt(),
                    createdAt = Instant.fromEpochMilliseconds(dbRef.created_at)
                )
            }
        }
    }

    override suspend fun getSpansForReference(referenceId: String): Result<List<CitationSpan>> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.selectSpansForReference(referenceId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun insertLink(link: CitationLink): Result<CitationLink> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.insert(
                id = link.id,
                citation_span_id = link.citationSpanId,
                reference_id = link.referenceId,
                link_method = link.linkMethod,
                confidence = link.confidence,
                created_at = link.createdAt.toEpochMilliseconds()
            )
            link
        }
    }

    override suspend fun insertAllLinks(links: List<CitationLink>): Result<List<CitationLink>> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                links.forEach { link ->
                    citationLinkQueries.insert(
                        id = link.id,
                        citation_span_id = link.citationSpanId,
                        reference_id = link.referenceId,
                        link_method = link.linkMethod,
                        confidence = link.confidence,
                        created_at = link.createdAt.toEpochMilliseconds()
                    )
                }
            }
            links
        }
    }

    override suspend fun deleteLinksBySpanId(spanId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.deleteBySpanId(spanId)
        }
    }

    override suspend fun deleteLinksByReferenceId(referenceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.deleteByReferenceId(referenceId)
        }
    }

    override suspend fun getSpansWithLinkCount(paperId: String): Result<List<Pair<CitationSpan, Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            citationLinkQueries.selectSpansWithLinkCount(paperId).executeAsList().map { row ->
                val span = CitationSpan(
                    id = row.id,
                    paperId = row.paper_id,
                    pageNum = row.page_num.toInt(),
                    bbox = BoundingBox(
                        x1 = row.bbox_x1,
                        y1 = row.bbox_y1,
                        x2 = row.bbox_x2,
                        y2 = row.bbox_y2
                    ),
                    rawText = row.raw_text,
                    style = CitationStyle.valueOf(row.style.uppercase()),
                    provenance = CitationProvenance.valueOf(row.provenance.uppercase()),
                    confidence = row.confidence,
                    destPage = row.dest_page?.toInt(),
                    destY = row.dest_y,
                    createdAt = Instant.fromEpochMilliseconds(row.created_at)
                )
                span to row.link_count.toInt()
            }
        }
    }

    // === Extension functions for mapping ===

    private fun com.potero.db.CitationSpan.toDomainModel(): CitationSpan {
        return CitationSpan(
            id = id,
            paperId = paper_id,
            pageNum = page_num.toInt(),
            bbox = BoundingBox(
                x1 = bbox_x1,
                y1 = bbox_y1,
                x2 = bbox_x2,
                y2 = bbox_y2
            ),
            rawText = raw_text,
            style = try { CitationStyle.valueOf(style.uppercase()) } catch (e: Exception) { CitationStyle.UNKNOWN },
            provenance = try { CitationProvenance.valueOf(provenance.uppercase()) } catch (e: Exception) { CitationProvenance.PATTERN },
            confidence = confidence,
            destPage = dest_page?.toInt(),
            destY = dest_y,
            createdAt = Instant.fromEpochMilliseconds(created_at)
        )
    }

    private fun com.potero.db.CitationLink.toDomainModel(): CitationLink {
        return CitationLink(
            id = id,
            citationSpanId = citation_span_id,
            referenceId = reference_id,
            linkMethod = link_method,
            confidence = confidence,
            createdAt = Instant.fromEpochMilliseconds(created_at)
        )
    }
}
