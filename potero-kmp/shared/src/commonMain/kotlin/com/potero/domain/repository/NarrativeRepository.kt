package com.potero.domain.repository

import com.potero.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Narrative operations
 */
interface NarrativeRepository {
    /**
     * Observe all narratives for a paper (reactive)
     */
    fun observeByPaperId(paperId: String): Flow<List<Narrative>>

    /**
     * Get all narratives for a paper
     */
    suspend fun getByPaperId(paperId: String): Result<List<Narrative>>

    /**
     * Get a specific narrative by paper, style and language
     */
    suspend fun getByPaperStyleLanguage(
        paperId: String,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Result<Narrative?>

    /**
     * Get a narrative by ID
     */
    suspend fun getById(id: String): Result<Narrative?>

    /**
     * Check if narratives exist for a paper
     */
    suspend fun hasNarratives(paperId: String): Result<Boolean>

    /**
     * Insert a new narrative
     */
    suspend fun insert(narrative: Narrative): Result<Narrative>

    /**
     * Update an existing narrative
     */
    suspend fun update(narrative: Narrative): Result<Narrative>

    /**
     * Delete all narratives for a paper
     */
    suspend fun deleteByPaperId(paperId: String): Result<Unit>

    /**
     * Delete a narrative by ID
     */
    suspend fun deleteById(id: String): Result<Unit>

    // Figure Explanation operations

    /**
     * Get figure explanations for a narrative
     */
    suspend fun getFigureExplanations(narrativeId: String): Result<List<FigureExplanation>>

    /**
     * Insert a figure explanation
     */
    suspend fun insertFigureExplanation(explanation: FigureExplanation): Result<FigureExplanation>

    /**
     * Delete figure explanations for a narrative
     */
    suspend fun deleteFigureExplanations(narrativeId: String): Result<Unit>

    // Concept Explanation operations

    /**
     * Get concept explanations for a narrative
     */
    suspend fun getConceptExplanations(narrativeId: String): Result<List<ConceptExplanation>>

    /**
     * Insert a concept explanation
     */
    suspend fun insertConceptExplanation(explanation: ConceptExplanation): Result<ConceptExplanation>

    /**
     * Delete concept explanations for a narrative
     */
    suspend fun deleteConceptExplanations(narrativeId: String): Result<Unit>

    // Cache operations

    /**
     * Get cached intermediate result
     */
    suspend fun getCache(paperId: String, stage: String, currentTime: Long): Result<NarrativeCache?>

    /**
     * Insert or replace cache
     */
    suspend fun insertOrReplaceCache(cache: NarrativeCache): Result<NarrativeCache>

    /**
     * Delete cache for a paper
     */
    suspend fun deleteCacheByPaperId(paperId: String): Result<Unit>

    /**
     * Delete expired cache entries
     */
    suspend fun deleteExpiredCache(currentTime: Long): Result<Unit>
}
