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

    // SSO Authentication keys for POSTECH GenAI file upload
    const val SSO_ACCESS_TOKEN = "sso.accessToken"
    const val SSO_TOKEN_EXPIRY = "sso.tokenExpiry"
    const val SSO_SITE_NAME = "sso.siteName"

    // Academic API Enable/Disable flags
    const val OPENALEX_ENABLED = "api.openalex.enabled"
    const val PUBMED_ENABLED = "api.pubmed.enabled"
    const val DBLP_ENABLED = "api.dblp.enabled"
    const val EUROPEPMC_ENABLED = "api.europepmc.enabled"
    const val CORE_ENABLED = "api.core.enabled"
    const val IEEE_ENABLED = "api.ieee.enabled"
    const val ACL_ENABLED = "api.acl.enabled"
    const val DOAJ_ENABLED = "api.doaj.enabled"

    // Academic API Keys
    const val OPENALEX_API_KEY = "api.openalex.apiKey"
    const val PUBMED_API_KEY = "api.pubmed.apiKey"
    const val CORE_API_KEY = "api.core.apiKey"
    const val IEEE_API_KEY = "api.ieee.apiKey"

    // PDF Processing - Garbled text handling
    const val GARBLED_PDF_STRATEGY = "pdf.garbled.strategy" // "arxiv_fallback" | "ocr" | "skip"
    const val GARBLED_PDF_ARXIV_AUTO_DOWNLOAD = "pdf.garbled.arxivAutoDownload" // "true" | "false"
}
