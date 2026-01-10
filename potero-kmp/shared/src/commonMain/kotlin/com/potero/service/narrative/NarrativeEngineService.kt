package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.domain.repository.NarrativeRepository
import com.potero.domain.repository.PaperRepository
import com.potero.service.llm.LLMService
import com.potero.service.llm.LLMLogger
import com.potero.service.pdf.PreprocessedPdfProvider
import kotlinx.serialization.json.Json

/**
 * Main orchestrator for Paper-to-Narrative Engine.
 * Coordinates the 4-stage pipeline and manages caching.
 */
class NarrativeEngineService(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger,
    private val paperRepository: PaperRepository,
    private val narrativeRepository: NarrativeRepository,
    private val cacheService: NarrativeCacheService,
    private val preprocessedPdfProvider: PreprocessedPdfProvider,
    private val figureProvider: suspend (String) -> List<FigureInfo>,
    private val formulaProvider: suspend (String) -> List<FormulaInfo>
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // Stage processors
    private val structuralProcessor = StructuralUnderstandingProcessor(llmService, llmLogger)
    private val recompositionProcessor = ContentRecompositionProcessor(llmService, llmLogger)
    private val simplificationProcessor = ConceptSimplificationProcessor(llmService, llmLogger)
    private val renderingProcessor = StyleRenderingProcessor(llmService, llmLogger)

    /**
     * Generate narratives for a paper.
     *
     * @param request Generation request with paper ID, styles, and languages
     * @param progressCallback Callback for progress updates
     * @return List of generated narratives
     */
    suspend fun generateNarratives(
        request: NarrativeGenerationRequest,
        progressCallback: suspend (NarrativeGenerationProgress) -> Unit = {}
    ): Result<List<Narrative>> = runCatching {
        val paperId = request.paperId
        val totalNarratives = request.styles.size * request.languages.size

        // 1. Load paper and related data
        val paper = paperRepository.getById(paperId).getOrThrow()
            ?: throw IllegalArgumentException("Paper not found: $paperId")

        val figures = loadFigures(paperId)
        val formulas = loadFormulas(paperId)

        // Get PDF text from preprocessed cache
        val pdfText = preprocessedPdfProvider.getFullText(paperId).getOrNull()
            ?: throw IllegalStateException("PDF text not available for paper $paperId. Preprocessing may be required.")

        // 2. Stage 1: Structural Understanding (cacheable)
        progressCallback(NarrativeGenerationProgress(
            paperId = paperId,
            totalNarratives = totalNarratives,
            completedNarratives = 0,
            currentStage = "structural_understanding"
        ))

        val structural = cacheService.getOrComputeStructural(paperId) {
            structuralProcessor.process(paper, pdfText, figures, formulas).getOrThrow()
        }

        // 3. Stage 2: Content Recomposition (cacheable)
        progressCallback(NarrativeGenerationProgress(
            paperId = paperId,
            totalNarratives = totalNarratives,
            completedNarratives = 0,
            currentStage = "content_recomposition"
        ))

        val recomposed = cacheService.getOrComputeRecomposed(paperId) {
            recompositionProcessor.process(structural, figures, formulas).getOrThrow()
        }

        // 4. Stage 3: Concept Simplification
        // Note: Concepts are generated once but stored per narrative
        progressCallback(NarrativeGenerationProgress(
            paperId = paperId,
            totalNarratives = totalNarratives,
            completedNarratives = 0,
            currentStage = "concept_simplification"
        ))

        val conceptsToExplain = (structural.prerequisites + recomposed.conceptsToExplain).distinct()

        // 5. Stage 4: Render for each style/language combination
        val narratives = mutableListOf<Narrative>()
        var completed = 0

        for (style in request.styles) {
            for (language in request.languages) {
                progressCallback(NarrativeGenerationProgress(
                    paperId = paperId,
                    totalNarratives = totalNarratives,
                    completedNarratives = completed,
                    currentStage = "rendering",
                    currentStyle = style,
                    currentLanguage = language
                ))

                // Generate concept explanations for this narrative
                val tempNarrativeId = java.util.UUID.randomUUID().toString()
                val concepts = if (conceptsToExplain.isNotEmpty() && request.includeConceptExplanations) {
                    simplificationProcessor.process(
                        concepts = conceptsToExplain,
                        paperContext = "${paper.title}. ${structural.mainObjective}",
                        narrativeId = tempNarrativeId
                    ).getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }

                // Render the narrative
                val narrative = renderingProcessor.render(
                    paper = paper,
                    structural = structural,
                    recomposed = recomposed,
                    concepts = concepts,
                    figures = figures,
                    style = style,
                    language = language
                ).getOrThrow()

                // Save to database
                narrativeRepository.insert(narrative)
                narratives.add(narrative)
                completed++
            }
        }

        // Update paper.hasBlogView flag
        paperRepository.update(paper.copy(hasBlogView = true))

        narratives
    }

    /**
     * Get existing narratives for a paper (if already generated)
     */
    suspend fun getNarratives(paperId: String): Result<List<Narrative>> {
        return narrativeRepository.getByPaperId(paperId)
    }

    /**
     * Get a specific narrative by paper, style, and language
     */
    suspend fun getNarrative(
        paperId: String,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Result<Narrative?> {
        return narrativeRepository.getByPaperStyleLanguage(paperId, style, language)
    }

    /**
     * Check if narratives exist for a paper
     */
    suspend fun hasNarratives(paperId: String): Boolean {
        return narrativeRepository.hasNarratives(paperId).getOrNull() ?: false
    }

    /**
     * Regenerate narratives (delete existing and create new)
     */
    suspend fun regenerateNarratives(
        request: NarrativeGenerationRequest,
        progressCallback: suspend (NarrativeGenerationProgress) -> Unit = {}
    ): Result<List<Narrative>> {
        // Clear cache and existing narratives
        cacheService.invalidate(request.paperId)
        narrativeRepository.deleteByPaperId(request.paperId)

        return generateNarratives(request, progressCallback)
    }

    /**
     * Delete all narratives for a paper
     */
    suspend fun deleteNarratives(paperId: String): Result<Unit> {
        cacheService.invalidate(paperId)
        return narrativeRepository.deleteByPaperId(paperId)
    }

    /**
     * Get available styles
     */
    fun getAvailableStyles(): List<NarrativeStyle> = NarrativeStyle.entries

    /**
     * Get available languages
     */
    fun getAvailableLanguages(): List<NarrativeLanguage> = NarrativeLanguage.entries

    private suspend fun loadFigures(paperId: String): List<FigureInfo> {
        return try {
            figureProvider(paperId)
        } catch (e: Exception) {
            println("[NarrativeEngine] Failed to load figures: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadFormulas(paperId: String): List<FormulaInfo> {
        return try {
            formulaProvider(paperId)
        } catch (e: Exception) {
            println("[NarrativeEngine] Failed to load formulas: ${e.message}")
            emptyList()
        }
    }
}
