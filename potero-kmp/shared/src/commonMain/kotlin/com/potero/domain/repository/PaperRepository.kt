package com.potero.domain.repository

import com.potero.domain.model.Author
import com.potero.domain.model.Paper
import com.potero.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Paper operations
 */
interface PaperRepository {
    /**
     * Observe all papers (reactive)
     */
    fun observeAll(): Flow<List<Paper>>

    /**
     * Observe a single paper by ID
     */
    fun observeById(id: String): Flow<Paper?>

    /**
     * Get all papers
     */
    suspend fun getAll(): Result<List<Paper>>

    /**
     * Get a paper by ID
     */
    suspend fun getById(id: String): Result<Paper?>

    /**
     * Get a paper by DOI
     */
    suspend fun getByDoi(doi: String): Result<Paper?>

    /**
     * Get a paper by arXiv ID
     */
    suspend fun getByArxivId(arxivId: String): Result<Paper?>

    /**
     * Search papers by title
     */
    suspend fun searchByTitle(query: String): Result<List<Paper>>

    /**
     * Get papers by conference
     */
    suspend fun getByConference(conference: String): Result<List<Paper>>

    /**
     * Get papers by year range
     */
    suspend fun getByYearRange(startYear: Int, endYear: Int): Result<List<Paper>>

    /**
     * Insert a new paper
     */
    suspend fun insert(paper: Paper): Result<Paper>

    /**
     * Update an existing paper
     */
    suspend fun update(paper: Paper): Result<Paper>

    /**
     * Delete a paper
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Get paper count
     */
    suspend fun count(): Result<Long>

    /**
     * Set authors for a paper
     */
    suspend fun setAuthors(paperId: String, authors: List<Author>): Result<Unit>

    /**
     * Get authors for a paper
     */
    suspend fun getAuthors(paperId: String): Result<List<Author>>

    /**
     * Set tags for a paper
     */
    suspend fun setTags(paperId: String, tags: List<Tag>): Result<Unit>

    /**
     * Get tags for a paper
     */
    suspend fun getTags(paperId: String): Result<List<Tag>>

    /**
     * Add a tag to a paper
     */
    suspend fun addTag(paperId: String, tagId: String): Result<Unit>

    /**
     * Remove a tag from a paper
     */
    suspend fun removeTag(paperId: String, tagId: String): Result<Unit>
}
