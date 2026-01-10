package com.potero.service.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate limiter for API requests
 *
 * Ensures that requests are throttled to a specific rate (requests per second).
 * Uses a simple token bucket-like approach with delay.
 *
 * @param requestsPerSecond Maximum number of requests allowed per second
 */
class RateLimiter(
    private val requestsPerSecond: Int
) {
    private val minIntervalMs = 1000L / requestsPerSecond
    private var lastRequestTime = 0L
    private val mutex = Mutex()

    /**
     * Throttle the current request
     *
     * If called too frequently, this function will delay to ensure
     * the rate limit is respected.
     */
    suspend fun throttle() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime

            if (elapsed < minIntervalMs) {
                val delayTime = minIntervalMs - elapsed
                delay(delayTime)
            }

            lastRequestTime = System.currentTimeMillis()
        }
    }

    /**
     * Get the minimum interval between requests in milliseconds
     */
    fun getMinIntervalMs(): Long = minIntervalMs

    /**
     * Get the configured rate in requests per second
     */
    fun getRequestsPerSecond(): Int = requestsPerSecond
}
