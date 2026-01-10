package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.*
import com.potero.domain.repository.NarrativeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of NarrativeRepository using SQLDelight
 */
class NarrativeRepositoryImpl(
    private val database: PoteroDatabase
) : NarrativeRepository {

    private val narrativeQueries = database.narrativeQueries
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeByPaperId(paperId: String): Flow<List<Narrative>> = flow {
        emit(getByPaperId(paperId).getOrDefault(emptyList()))
    }

    override suspend fun getByPaperId(paperId: String): Result<List<Narrative>> = withContext(Dispatchers.IO) {
        runCatching {
            narrativeQueries.selectAllByPaperId(paperId).executeAsList().map { dbNarrative ->
                dbNarrative.toDomain(
                    figureExplanations = getFigureExplanationsForNarrative(dbNarrative.id),
                    conceptExplanations = getConceptExplanationsForNarrative(dbNarrative.id)
                )
            }
        }
    }

    override suspend fun getByPaperStyleLanguage(
        paperId: String,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Result<Narrative?> = withContext(Dispatchers.IO) {
        runCatching {
            narrativeQueries.selectByPaperStyleLanguage(
                paper_id = paperId,
                style = style.name,
                language = language.code
            ).executeAsOneOrNull()?.let { dbNarrative ->
                dbNarrative.toDomain(
                    figureExplanations = getFigureExplanationsForNarrative(dbNarrative.id),
                    conceptExplanations = getConceptExplanationsForNarrative(dbNarrative.id)
                )
            }
        }
    }

    override suspend fun getById(id: String): Result<Narrative?> = withContext(Dispatchers.IO) {
        runCatching {
            narrativeQueries.selectById(id).executeAsOneOrNull()?.let { dbNarrative ->
                dbNarrative.toDomain(
                    figureExplanations = getFigureExplanationsForNarrative(dbNarrative.id),
                    conceptExplanations = getConceptExplanationsForNarrative(dbNarrative.id)
                )
            }
        }
    }

    override suspend fun hasNarratives(paperId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            narrativeQueries.hasNarrativesForPaper(paperId).executeAsOne() > 0
        }
    }

    override suspend fun insert(narrative: Narrative): Result<Narrative> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()

            narrativeQueries.insertNarrative(
                id = narrative.id,
                paper_id = narrative.paperId,
                style = narrative.style.name,
                language = narrative.language.code,
                title = narrative.title,
                content = narrative.content,
                summary = narrative.summary,
                estimated_read_time = narrative.estimatedReadTime.toLong(),
                created_at = now,
                updated_at = now
            )

            // Insert figure explanations
            narrative.figureExplanations.forEach { explanation ->
                insertFigureExplanationInternal(explanation)
            }

            // Insert concept explanations
            narrative.conceptExplanations.forEach { explanation ->
                insertConceptExplanationInternal(explanation)
            }

            narrative.copy(
                createdAt = Instant.fromEpochMilliseconds(now),
                updatedAt = Instant.fromEpochMilliseconds(now)
            )
        }
    }

    override suspend fun update(narrative: Narrative): Result<Narrative> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()

            narrativeQueries.updateNarrative(
                title = narrative.title,
                content = narrative.content,
                summary = narrative.summary,
                estimated_read_time = narrative.estimatedReadTime.toLong(),
                updated_at = now,
                id = narrative.id
            )

            // Update figure explanations (delete and re-insert)
            narrativeQueries.deleteFigureExplanationsByNarrativeId(narrative.id)
            narrative.figureExplanations.forEach { explanation ->
                insertFigureExplanationInternal(explanation)
            }

            // Update concept explanations (delete and re-insert)
            narrativeQueries.deleteConceptExplanationsByNarrativeId(narrative.id)
            narrative.conceptExplanations.forEach { explanation ->
                insertConceptExplanationInternal(explanation)
            }

            narrative.copy(updatedAt = Instant.fromEpochMilliseconds(now))
        }
    }

    override suspend fun deleteByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Foreign key cascades will handle related tables
            narrativeQueries.deleteByPaperId(paperId)
        }
    }

    override suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            narrativeQueries.deleteById(id)
        }
    }

    // Figure Explanation operations

    override suspend fun getFigureExplanations(narrativeId: String): Result<List<FigureExplanation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                getFigureExplanationsForNarrative(narrativeId)
            }
        }

    override suspend fun insertFigureExplanation(explanation: FigureExplanation): Result<FigureExplanation> =
        withContext(Dispatchers.IO) {
            runCatching {
                insertFigureExplanationInternal(explanation)
                explanation
            }
        }

    override suspend fun deleteFigureExplanations(narrativeId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                narrativeQueries.deleteFigureExplanationsByNarrativeId(narrativeId)
            }
        }

    // Concept Explanation operations

    override suspend fun getConceptExplanations(narrativeId: String): Result<List<ConceptExplanation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                getConceptExplanationsForNarrative(narrativeId)
            }
        }

    override suspend fun insertConceptExplanation(explanation: ConceptExplanation): Result<ConceptExplanation> =
        withContext(Dispatchers.IO) {
            runCatching {
                insertConceptExplanationInternal(explanation)
                explanation
            }
        }

    override suspend fun deleteConceptExplanations(narrativeId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                narrativeQueries.deleteConceptExplanationsByNarrativeId(narrativeId)
            }
        }

    // Cache operations

    override suspend fun getCache(paperId: String, stage: String, currentTime: Long): Result<NarrativeCache?> =
        withContext(Dispatchers.IO) {
            runCatching {
                narrativeQueries.selectCache(paperId, stage, currentTime).executeAsOneOrNull()?.let { dbCache ->
                    NarrativeCache(
                        id = dbCache.id,
                        paperId = dbCache.paper_id,
                        stage = dbCache.stage,
                        data = dbCache.data_,
                        createdAt = dbCache.created_at,
                        expiresAt = dbCache.expires_at
                    )
                }
            }
        }

    override suspend fun insertOrReplaceCache(cache: NarrativeCache): Result<NarrativeCache> =
        withContext(Dispatchers.IO) {
            runCatching {
                narrativeQueries.insertOrReplaceCache(
                    id = cache.id,
                    paper_id = cache.paperId,
                    stage = cache.stage,
                    data_ = cache.data,
                    created_at = cache.createdAt,
                    expires_at = cache.expiresAt
                )
                cache
            }
        }

    override suspend fun deleteCacheByPaperId(paperId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                narrativeQueries.deleteCacheByPaperId(paperId)
            }
        }

    override suspend fun deleteExpiredCache(currentTime: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                narrativeQueries.deleteExpiredCache(currentTime)
            }
        }

    // Helper functions

    private fun getFigureExplanationsForNarrative(narrativeId: String): List<FigureExplanation> {
        return narrativeQueries.selectFigureExplanationsByNarrativeId(narrativeId).executeAsList().map { db ->
            FigureExplanation(
                id = db.id,
                narrativeId = db.narrative_id,
                figureId = db.figure_id,
                label = db.label,
                originalCaption = db.original_caption,
                explanation = db.explanation,
                relevance = db.relevance,
                createdAt = Instant.fromEpochMilliseconds(db.created_at)
            )
        }
    }

    private fun getConceptExplanationsForNarrative(narrativeId: String): List<ConceptExplanation> {
        return narrativeQueries.selectConceptExplanationsByNarrativeId(narrativeId).executeAsList().map { db ->
            ConceptExplanation(
                id = db.id,
                narrativeId = db.narrative_id,
                term = db.term,
                definition = db.definition,
                analogy = db.analogy,
                relatedTerms = db.related_terms?.let {
                    try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { emptyList() }
                } ?: emptyList(),
                createdAt = Instant.fromEpochMilliseconds(db.created_at)
            )
        }
    }

    private fun insertFigureExplanationInternal(explanation: FigureExplanation) {
        val now = Clock.System.now().toEpochMilliseconds()
        narrativeQueries.insertFigureExplanation(
            id = explanation.id,
            narrative_id = explanation.narrativeId,
            figure_id = explanation.figureId,
            label = explanation.label,
            original_caption = explanation.originalCaption,
            explanation = explanation.explanation,
            relevance = explanation.relevance,
            created_at = now
        )
    }

    private fun insertConceptExplanationInternal(explanation: ConceptExplanation) {
        val now = Clock.System.now().toEpochMilliseconds()
        narrativeQueries.insertConceptExplanation(
            id = explanation.id,
            narrative_id = explanation.narrativeId,
            term = explanation.term,
            definition = explanation.definition,
            analogy = explanation.analogy,
            related_terms = json.encodeToString(explanation.relatedTerms),
            created_at = now
        )
    }

    // Extension function to convert DB model to domain model
    private fun com.potero.db.Narrative.toDomain(
        figureExplanations: List<FigureExplanation>,
        conceptExplanations: List<ConceptExplanation>
    ): Narrative {
        return Narrative(
            id = this.id,
            paperId = this.paper_id,
            style = NarrativeStyle.valueOf(this.style),
            language = NarrativeLanguage.entries.find { it.code == this.language } ?: NarrativeLanguage.ENGLISH,
            title = this.title,
            content = this.content,
            summary = this.summary,
            figureExplanations = figureExplanations,
            conceptExplanations = conceptExplanations,
            estimatedReadTime = this.estimated_read_time.toInt(),
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at)
        )
    }
}
