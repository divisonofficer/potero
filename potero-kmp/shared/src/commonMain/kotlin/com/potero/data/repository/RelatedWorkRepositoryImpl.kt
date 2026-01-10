package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.RelatedWork
import com.potero.domain.model.RelationshipType
import com.potero.domain.model.RelationshipSource
import com.potero.domain.repository.RelatedWorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Implementation of RelatedWorkRepository using SQLDelight
 */
class RelatedWorkRepositoryImpl(
    private val database: PoteroDatabase
) : RelatedWorkRepository {

    private val queries = database.relatedWorkQueries

    override suspend fun getBySourcePaper(sourcePaperId: String): Result<List<RelatedWork>> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.selectBySourcePaper(sourcePaperId)
                    .executeAsList()
                    .map { it.toDomain() }
            }
        }

    override suspend fun getByRelatedPaper(relatedPaperId: String): Result<List<RelatedWork>> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.selectByRelatedPaper(relatedPaperId)
                    .executeAsList()
                    .map { it.toDomain() }
            }
        }

    override suspend fun getByType(
        sourcePaperId: String,
        type: RelationshipType
    ): Result<List<RelatedWork>> = withContext(Dispatchers.IO) {
        runCatching {
            queries.selectByType(sourcePaperId, type.name)
                .executeAsList()
                .map { it.toDomain() }
        }
    }

    override suspend fun getTopRelated(
        sourcePaperId: String,
        limit: Int
    ): Result<List<RelatedWork>> = withContext(Dispatchers.IO) {
        runCatching {
            queries.selectTopRelated(sourcePaperId, limit.toLong())
                .executeAsList()
                .map { it.toDomain() }
        }
    }

    override suspend fun insert(relatedWork: RelatedWork): Result<RelatedWork> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.insert(
                    id = relatedWork.id,
                    source_paper_id = relatedWork.sourcePaperId,
                    related_paper_id = relatedWork.relatedPaperId,
                    relationship_type = relatedWork.relationshipType.name,
                    relevance_score = relatedWork.relevanceScore,
                    source = relatedWork.source.name,
                    reasoning = relatedWork.reasoning,
                    created_at = relatedWork.createdAt.toEpochMilliseconds(),
                    updated_at = relatedWork.updatedAt.toEpochMilliseconds()
                )
                relatedWork
            }
        }

    override suspend fun update(relatedWork: RelatedWork): Result<RelatedWork> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.update(
                    relevance_score = relatedWork.relevanceScore,
                    reasoning = relatedWork.reasoning,
                    updated_at = relatedWork.updatedAt.toEpochMilliseconds(),
                    id = relatedWork.id
                )
                relatedWork
            }
        }

    override suspend fun deleteBySourcePaper(sourcePaperId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.deleteBySourcePaper(sourcePaperId)
            }
        }

    override suspend fun deleteById(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.deleteById(id)
            }
        }

    override suspend fun count(sourcePaperId: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                queries.countBySourcePaper(sourcePaperId).executeAsOne()
            }
        }

    // Helper extension function to convert DB model to domain model
    private fun com.potero.db.RelatedWork.toDomain(): RelatedWork {
        return RelatedWork(
            id = id,
            sourcePaperId = source_paper_id,
            relatedPaperId = related_paper_id,
            relationshipType = RelationshipType.valueOf(relationship_type),
            relevanceScore = relevance_score,
            source = RelationshipSource.valueOf(source),
            reasoning = reasoning,
            createdAt = Instant.fromEpochMilliseconds(created_at),
            updatedAt = Instant.fromEpochMilliseconds(updated_at)
        )
    }
}
