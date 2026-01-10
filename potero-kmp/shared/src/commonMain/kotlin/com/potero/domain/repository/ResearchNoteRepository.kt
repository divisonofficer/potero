package com.potero.domain.repository

import com.potero.domain.model.BacklinkInfo
import com.potero.domain.model.ResearchNote
import com.potero.domain.model.ResearchNoteWithLinks
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for ResearchNote operations.
 * Handles markdown research notes with wiki-style links.
 */
interface ResearchNoteRepository {
    /**
     * Observe all notes (reactive)
     */
    fun observeAll(): Flow<List<ResearchNote>>

    /**
     * Observe a single note by ID
     */
    fun observeById(id: String): Flow<ResearchNote?>

    /**
     * Observe notes for a specific paper
     */
    fun observeByPaper(paperId: String): Flow<List<ResearchNote>>

    /**
     * Get all notes
     */
    suspend fun getAll(): Result<List<ResearchNote>>

    /**
     * Get a note by ID
     */
    suspend fun getById(id: String): Result<ResearchNote?>

    /**
     * Get notes linked to a specific paper
     */
    suspend fun getByPaper(paperId: String): Result<List<ResearchNote>>

    /**
     * Get standalone notes (not linked to any paper)
     */
    suspend fun getStandalone(): Result<List<ResearchNote>>

    /**
     * Insert a new note
     */
    suspend fun insert(note: ResearchNote): Result<ResearchNote>

    /**
     * Update an existing note
     */
    suspend fun update(note: ResearchNote): Result<ResearchNote>

    /**
     * Delete a note
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Search notes by title
     */
    suspend fun searchByTitle(query: String): Result<List<ResearchNote>>

    /**
     * Search notes by content
     */
    suspend fun searchByContent(query: String): Result<List<ResearchNote>>

    /**
     * Get a note with its outgoing links and backlinks
     */
    suspend fun getWithLinks(noteId: String): Result<ResearchNoteWithLinks?>

    /**
     * Get backlinks to a note (notes that link to this note)
     */
    suspend fun getBacklinks(noteId: String): Result<List<BacklinkInfo>>

    /**
     * Get backlinks to a paper (notes that reference this paper)
     */
    suspend fun getBacklinksToPaper(paperId: String): Result<List<BacklinkInfo>>

    /**
     * Parse and update links for a note.
     * Extracts [[...]] links from content and updates NoteLink table.
     */
    suspend fun updateLinks(noteId: String, content: String): Result<Unit>

    /**
     * Resolve an unresolved link by setting its target
     */
    suspend fun resolveLink(linkId: String, targetNoteId: String): Result<Unit>

    /**
     * Find a note by exact title (case-insensitive)
     */
    suspend fun findNoteByTitle(title: String): Result<ResearchNote?>
}
