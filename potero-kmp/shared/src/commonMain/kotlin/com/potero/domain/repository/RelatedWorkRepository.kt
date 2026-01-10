package com.potero.domain.repository

import com.potero.domain.model.RelatedWork
import com.potero.domain.model.RelationshipType

/**
 * Repository for managing RelatedWork relationships between papers
 */
interface RelatedWorkRepository {

    /**
     * Get all related papers for a source paper
     */
    suspend fun getBySourcePaper(sourcePaperId: String): Result<List<RelatedWork>>

    /**
     * Get all papers that cite the given paper
     */
    suspend fun getByRelatedPaper(relatedPaperId: String): Result<List<RelatedWork>>

    /**
     * Get related papers filtered by relationship type
     */
    suspend fun getByType(
        sourcePaperId: String,
        type: RelationshipType
    ): Result<List<RelatedWork>>

    /**
     * Get top N related papers by relevance score
     */
    suspend fun getTopRelated(sourcePaperId: String, limit: Int): Result<List<RelatedWork>>

    /**
     * Insert a new related work relationship
     */
    suspend fun insert(relatedWork: RelatedWork): Result<RelatedWork>

    /**
     * Update an existing related work relationship
     */
    suspend fun update(relatedWork: RelatedWork): Result<RelatedWork>

    /**
     * Delete all related work entries for a source paper
     */
    suspend fun deleteBySourcePaper(sourcePaperId: String): Result<Unit>

    /**
     * Delete a specific related work relationship
     */
    suspend fun deleteById(id: String): Result<Unit>

    /**
     * Count related papers for a source paper
     */
    suspend fun count(sourcePaperId: String): Result<Long>
}
