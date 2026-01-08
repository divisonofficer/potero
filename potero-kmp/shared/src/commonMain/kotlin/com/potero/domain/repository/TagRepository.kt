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
}
