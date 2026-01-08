package com.potero.domain.repository

import com.potero.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Tag operations
 */
interface TagRepository {
    /**
     * Observe all tags
     */
    fun observeAll(): Flow<List<Tag>>

    /**
     * Get all tags
     */
    suspend fun getAll(): Result<List<Tag>>

    /**
     * Get a tag by ID
     */
    suspend fun getById(id: String): Result<Tag?>

    /**
     * Get a tag by name
     */
    suspend fun getByName(name: String): Result<Tag?>

    /**
     * Insert a new tag
     */
    suspend fun insert(tag: Tag): Result<Tag>

    /**
     * Update an existing tag
     */
    suspend fun update(tag: Tag): Result<Tag>

    /**
     * Delete a tag
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Get tags with paper counts
     */
    suspend fun getTagsWithCounts(): Result<List<Pair<Tag, Int>>>

    /**
     * Get tags for a specific paper
     */
    suspend fun getTagsForPaper(paperId: String): Result<List<Tag>>

    /**
     * Link a tag to a paper
     */
    suspend fun linkTagToPaper(paperId: String, tagId: String): Result<Unit>

    /**
     * Unlink a tag from a paper
     */
    suspend fun unlinkTagFromPaper(paperId: String, tagId: String): Result<Unit>

    /**
     * Set tags for a paper (replace all existing)
     */
    suspend fun setTagsForPaper(paperId: String, tagIds: List<String>): Result<Unit>

    /**
     * Merge one tag into another (for tag consolidation)
     * All papers with sourceTagId will be linked to targetTagId
     */
    suspend fun mergeTags(sourceTagId: String, targetTagId: String): Result<Unit>

    /**
     * Get total tag count
     */
    suspend fun getCount(): Result<Int>

    /**
     * Search tags by name (partial match)
     */
    suspend fun searchByName(query: String): Result<List<Tag>>
}
