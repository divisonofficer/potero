package com.potero.service.search

import com.potero.service.metadata.SemanticScholarPaper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache for search results to reduce API calls
 * - Caches search results for configurable TTL
 * - Provides LRU eviction when cache is full
 */
class SearchCacheService(
    private val maxEntries: Int = 100,
    private val ttlMs: Long = 5 * 60 * 1000 // 5 minutes default
) {
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(ttlMs: Long): Boolean =
            System.currentTimeMillis() - timestamp > ttlMs
    }

    private val searchCache = LinkedHashMap<String, CacheEntry<List<SemanticScholarPaper>>>(
        maxEntries, 0.75f, true // accessOrder=true for LRU
    )

    private val mutex = Mutex()

    /**
     * Get cached search results if available and not expired
     */
    suspend fun getSearchResults(query: String): List<SemanticScholarPaper>? = mutex.withLock {
        val normalizedQuery = normalizeQuery(query)
        val entry = searchCache[normalizedQuery]

        if (entry != null && !entry.isExpired(ttlMs)) {
            return entry.data
        }

        // Remove expired entry
        if (entry != null) {
            searchCache.remove(normalizedQuery)
        }

        return null
    }

    /**
     * Cache search results
     */
    suspend fun cacheSearchResults(query: String, results: List<SemanticScholarPaper>) = mutex.withLock {
        val normalizedQuery = normalizeQuery(query)

        // Evict oldest entry if at capacity
        if (searchCache.size >= maxEntries) {
            val oldestKey = searchCache.keys.firstOrNull()
            if (oldestKey != null) {
                searchCache.remove(oldestKey)
            }
        }

        searchCache[normalizedQuery] = CacheEntry(results)
    }

    /**
     * Clear all cached results
     */
    suspend fun clear() = mutex.withLock {
        searchCache.clear()
    }

    /**
     * Get cache statistics
     */
    suspend fun getStats(): CacheStats = mutex.withLock {
        val validEntries = searchCache.values.count { !it.isExpired(ttlMs) }
        CacheStats(
            totalEntries = searchCache.size,
            validEntries = validEntries,
            expiredEntries = searchCache.size - validEntries
        )
    }

    /**
     * Normalize query for consistent cache keys
     */
    private fun normalizeQuery(query: String): String {
        return query.lowercase().trim()
            .replace(Regex("\\s+"), " ")
    }
}

data class CacheStats(
    val totalEntries: Int,
    val validEntries: Int,
    val expiredEntries: Int
)
