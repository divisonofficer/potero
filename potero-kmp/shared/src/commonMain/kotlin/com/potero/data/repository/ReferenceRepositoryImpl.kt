package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.Reference
import com.potero.domain.repository.ReferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Implementation of ReferenceRepository using SQLDelight
 */
class ReferenceRepositoryImpl(
    private val database: PoteroDatabase
) : ReferenceRepository {

    private val referenceQueries = database.referenceQueries

    override fun observeByPaperId(paperId: String): Flow<List<Reference>> = flow {
        emit(getByPaperId(paperId).getOrDefault(emptyList()))
    }

    override suspend fun getByPaperId(paperId: String): Result<List<Reference>> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.selectByPaperId(paperId).executeAsList().map { dbRef ->
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

    override suspend fun getByPaperIdAndNumber(paperId: String, number: Int): Result<Reference?> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.selectByPaperIdAndNumber(paperId, number.toLong()).executeAsOneOrNull()?.let { dbRef ->
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

    override suspend fun insert(reference: Reference): Result<Reference> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.insert(
                id = reference.id,
                paper_id = reference.paperId,
                number = reference.number.toLong(),
                raw_text = reference.rawText,
                authors = reference.authors,
                title = reference.title,
                venue = reference.venue,
                year = reference.year?.toLong(),
                doi = reference.doi,
                page_num = reference.pageNum.toLong(),
                created_at = reference.createdAt.toEpochMilliseconds()
            )
            reference
        }
    }

    override suspend fun insertAll(references: List<Reference>): Result<List<Reference>> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                references.forEach { reference ->
                    referenceQueries.insert(
                        id = reference.id,
                        paper_id = reference.paperId,
                        number = reference.number.toLong(),
                        raw_text = reference.rawText,
                        authors = reference.authors,
                        title = reference.title,
                        venue = reference.venue,
                        year = reference.year?.toLong(),
                        doi = reference.doi,
                        page_num = reference.pageNum.toLong(),
                        created_at = reference.createdAt.toEpochMilliseconds()
                    )
                }
            }
            references
        }
    }

    override suspend fun deleteByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.deleteByPaperId(paperId)
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.deleteById(id)
        }
    }

    override suspend fun countByPaperId(paperId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.countByPaperId(paperId).executeAsOne().toInt()
        }
    }

    override suspend fun searchByTitle(query: String): Result<List<Reference>> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.searchByTitle(query).executeAsList().map { dbRef ->
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

    override suspend fun searchByAuthors(query: String): Result<List<Reference>> = withContext(Dispatchers.IO) {
        runCatching {
            referenceQueries.searchByAuthors(query).executeAsList().map { dbRef ->
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
}
