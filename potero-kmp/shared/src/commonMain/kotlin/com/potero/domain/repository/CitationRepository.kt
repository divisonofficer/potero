package com.potero.domain.repository

import com.potero.domain.model.CitationLink
import com.potero.domain.model.CitationSpan
import com.potero.domain.model.Reference
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for CitationSpan and CitationLink operations.
 * Manages in-text citations and their links to reference entries.
 */
interface CitationRepository {

    // === CitationSpan Operations ===

    /**
     * Observe citation spans for a specific paper
     */
    fun observeByPaperId(paperId: String): Flow<List<CitationSpan>>

    /**
     * Get all citation spans for a paper
     */
    suspend fun getSpansByPaperId(paperId: String): Result<List<CitationSpan>>

    /**
     * Get citation spans for a specific page
     */
    suspend fun getSpansByPage(paperId: String, pageNum: Int): Result<List<CitationSpan>>

    /**
     * Get a specific citation span by ID
     */
    suspend fun getSpanById(id: String): Result<CitationSpan?>

    /**
     * Insert a new citation span
     */
    suspend fun insertSpan(span: CitationSpan): Result<CitationSpan>

    /**
     * Insert multiple citation spans at once (bulk insert)
     */
    suspend fun insertAllSpans(spans: List<CitationSpan>): Result<List<CitationSpan>>

    /**
     * Delete all citation spans for a paper
     */
    suspend fun deleteSpansByPaperId(paperId: String): Result<Unit>

    /**
     * Get citation span count for a paper
     */
    suspend fun countSpansByPaperId(paperId: String): Result<Int>

    /**
     * Get citation span count by provenance type
     */
    suspend fun countSpansByProvenance(paperId: String): Result<Map<String, Int>>

    // === CitationLink Operations ===

    /**
     * Get all links for a citation span
     */
    suspend fun getLinksBySpanId(spanId: String): Result<List<CitationLink>>

    /**
     * Get all links for a reference
     */
    suspend fun getLinksByReferenceId(referenceId: String): Result<List<CitationLink>>

    /**
     * Get references linked to a citation span
     */
    suspend fun getReferencesForSpan(spanId: String): Result<List<Reference>>

    /**
     * Get citation spans linked to a reference
     */
    suspend fun getSpansForReference(referenceId: String): Result<List<CitationSpan>>

    /**
     * Insert a new citation link
     */
    suspend fun insertLink(link: CitationLink): Result<CitationLink>

    /**
     * Insert multiple citation links at once (bulk insert)
     */
    suspend fun insertAllLinks(links: List<CitationLink>): Result<List<CitationLink>>

    /**
     * Delete all links for a citation span
     */
    suspend fun deleteLinksBySpanId(spanId: String): Result<Unit>

    /**
     * Delete all links for a reference
     */
    suspend fun deleteLinksByReferenceId(referenceId: String): Result<Unit>

    /**
     * Get citation spans with their link count for a paper
     */
    suspend fun getSpansWithLinkCount(paperId: String): Result<List<Pair<CitationSpan, Int>>>
}
