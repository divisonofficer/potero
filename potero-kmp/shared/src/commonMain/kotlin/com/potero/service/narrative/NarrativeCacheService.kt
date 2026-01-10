package com.potero.service.narrative

import com.potero.domain.model.*
import com.potero.domain.repository.NarrativeRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * Caching service for narrative generation pipeline.
 *
 * Cache Strategy:
 * - Stage 1-3 results are cached for 24 hours (paper content doesn't change)
 * - Cache is paper-specific
 * - Invalidate on paper update or explicit regeneration request
 *
 * Cost Impact:
 * - Without cache: Multiple LLM calls per narrative variant
 * - With cache: Shared stages (1-3) computed once, only rendering (stage 4) runs per variant
 * - Savings: ~75% on initial generation, 100% on subsequent views
 */
class NarrativeCacheService(
    private val narrativeRepository: NarrativeRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val cacheTTL = 24.hours

    companion object {
        const val STAGE_STRUCTURAL = "structural"
        const val STAGE_RECOMPOSED = "recomposed"
        const val STAGE_CONCEPTS = "concepts"
    }

    /**
     * Get cached structural understanding or null if not cached/expired
     */
    suspend fun getStructuralUnderstanding(paperId: String): StructuralUnderstanding? {
        val cache = getCacheData(paperId, STAGE_STRUCTURAL) ?: return null
        return try {
            json.decodeFromString<StructuralUnderstanding>(cache)
        } catch (e: Exception) {
            println("[NarrativeCache] Failed to decode structural understanding: ${e.message}")
            null
        }
    }

    /**
     * Cache structural understanding
     */
    suspend fun setStructuralUnderstanding(paperId: String, data: StructuralUnderstanding) {
        setCacheData(paperId, STAGE_STRUCTURAL, json.encodeToString(data))
    }

    /**
     * Get cached recomposed content or null if not cached/expired
     */
    suspend fun getRecomposedContent(paperId: String): RecomposedContent? {
        val cache = getCacheData(paperId, STAGE_RECOMPOSED) ?: return null
        return try {
            json.decodeFromString<RecomposedContent>(cache)
        } catch (e: Exception) {
            println("[NarrativeCache] Failed to decode recomposed content: ${e.message}")
            null
        }
    }

    /**
     * Cache recomposed content
     */
    suspend fun setRecomposedContent(paperId: String, data: RecomposedContent) {
        setCacheData(paperId, STAGE_RECOMPOSED, json.encodeToString(data))
    }

    /**
     * Get cached concept explanations or null if not cached/expired
     * Note: Returns serialized list, not domain objects (to preserve IDs)
     */
    suspend fun getConceptsData(paperId: String): String? {
        return getCacheData(paperId, STAGE_CONCEPTS)
    }

    /**
     * Cache concept explanations
     */
    suspend fun setConceptsData(paperId: String, data: String) {
        setCacheData(paperId, STAGE_CONCEPTS, data)
    }

    /**
     * Get or compute structural understanding with caching
     */
    suspend fun getOrComputeStructural(
        paperId: String,
        compute: suspend () -> StructuralUnderstanding
    ): StructuralUnderstanding {
        val cached = getStructuralUnderstanding(paperId)
        if (cached != null) {
            println("[NarrativeCache] Cache hit for structural understanding ($paperId)")
            return cached
        }

        println("[NarrativeCache] Cache miss for structural understanding ($paperId), computing...")
        val result = compute()
        setStructuralUnderstanding(paperId, result)
        return result
    }

    /**
     * Get or compute recomposed content with caching
     */
    suspend fun getOrComputeRecomposed(
        paperId: String,
        compute: suspend () -> RecomposedContent
    ): RecomposedContent {
        val cached = getRecomposedContent(paperId)
        if (cached != null) {
            println("[NarrativeCache] Cache hit for recomposed content ($paperId)")
            return cached
        }

        println("[NarrativeCache] Cache miss for recomposed content ($paperId), computing...")
        val result = compute()
        setRecomposedContent(paperId, result)
        return result
    }

    /**
     * Invalidate all cache for a paper
     */
    suspend fun invalidate(paperId: String) {
        narrativeRepository.deleteCacheByPaperId(paperId)
        println("[NarrativeCache] Invalidated cache for $paperId")
    }

    /**
     * Clean up expired cache entries
     */
    suspend fun cleanupExpired() {
        val now = Clock.System.now().toEpochMilliseconds()
        narrativeRepository.deleteExpiredCache(now)
    }

    /**
     * Check if cache exists for a stage
     */
    suspend fun hasCacheFor(paperId: String, stage: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return narrativeRepository.getCache(paperId, stage, now).getOrNull() != null
    }

    private suspend fun getCacheData(paperId: String, stage: String): String? {
        val now = Clock.System.now().toEpochMilliseconds()
        return narrativeRepository.getCache(paperId, stage, now).getOrNull()?.data
    }

    private suspend fun setCacheData(paperId: String, stage: String, data: String) {
        val now = Clock.System.now()
        val expiresAt = now + cacheTTL

        val cache = NarrativeCache(
            id = UUID.randomUUID().toString(),
            paperId = paperId,
            stage = stage,
            data = data,
            createdAt = now.toEpochMilliseconds(),
            expiresAt = expiresAt.toEpochMilliseconds()
        )

        narrativeRepository.insertOrReplaceCache(cache)
    }
}
