package com.potero.domain.repository

/**
 * Repository interface for Settings operations
 */
interface SettingsRepository {
    /**
     * Get a setting value by key
     */
    suspend fun get(key: String): Result<String?>

    /**
     * Set a setting value
     */
    suspend fun set(key: String, value: String): Result<Unit>

    /**
     * Get all settings
     */
    suspend fun getAll(): Result<Map<String, String>>

    /**
     * Delete a setting
     */
    suspend fun delete(key: String): Result<Unit>

    /**
     * Clear all settings
     */
    suspend fun clearAll(): Result<Unit>
}

/**
 * Known settings keys
 */
object SettingsKeys {
    const val LLM_API_KEY = "llm.apiKey"
    const val LLM_PROVIDER = "llm.provider"
    const val PDF_STORAGE_PATH = "storage.pdfPath"
    const val THEME = "ui.theme"
    const val SEMANTIC_SCHOLAR_API_KEY = "semanticScholar.apiKey"
}
