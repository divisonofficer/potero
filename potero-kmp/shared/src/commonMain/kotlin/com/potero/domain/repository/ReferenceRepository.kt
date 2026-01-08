package com.potero.domain.repository

import com.potero.domain.model.Reference
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Reference operations.
 * References are citations extracted from a paper's References/Bibliography section.
 */
interface ReferenceRepository {
    /**
     * Observe references for a specific paper
     */
    fun observeByPaperId(paperId: String): Flow<List<Reference>>

    /**
     * Get all references for a paper
     */
    suspend fun getByPaperId(paperId: String): Result<List<Reference>>

    /**
     * Get a specific reference by paper ID and reference number
     */
    suspend fun getByPaperIdAndNumber(paperId: String, number: Int): Result<Reference?>

    /**
     * Insert a new reference
     */
    suspend fun insert(reference: Reference): Result<Reference>

    /**
     * Insert multiple references at once (bulk insert)
     */
    suspend fun insertAll(references: List<Reference>): Result<List<Reference>>

    /**
     * Delete all references for a paper
     */
    suspend fun deleteByPaperId(paperId: String): Result<Unit>

    /**
     * Delete a specific reference
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Get reference count for a paper
     */
    suspend fun countByPaperId(paperId: String): Result<Int>

    /**
     * Search references by title across all papers
     */
    suspend fun searchByTitle(query: String): Result<List<Reference>>

    /**
     * Search references by authors across all papers
     */
    suspend fun searchByAuthors(query: String): Result<List<Reference>>
}
