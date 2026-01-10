package com.potero.service.search

import com.potero.domain.repository.SettingsRepository
import com.potero.domain.repository.SettingsKeys

/**
 * Provides real-time API enabled/disabled status from settings database
 */
class APIStatusProvider(
    private val settingsRepository: SettingsRepository
) {
    suspend fun isSemanticScholarEnabled(): Boolean =
        settingsRepository.get(SettingsKeys.SEMANTIC_SCHOLAR_ENABLED).getOrNull() == "true"

    suspend fun isOpenAlexEnabled(): Boolean =
        settingsRepository.get(SettingsKeys.OPENALEX_ENABLED).getOrNull() == "true"

    suspend fun isPubMedEnabled(): Boolean =
        settingsRepository.get(SettingsKeys.PUBMED_ENABLED).getOrNull() == "true"

    suspend fun isDBLPEnabled(): Boolean =
        settingsRepository.get(SettingsKeys.DBLP_ENABLED).getOrNull() == "true"

    /**
     * Get list of enabled supplementary APIs (excludes Semantic Scholar)
     */
    suspend fun getEnabledSupplementaryAPIs(): List<String> {
        val enabled = mutableListOf<String>()
        if (isOpenAlexEnabled()) enabled.add("openalex")
        if (isPubMedEnabled()) enabled.add("pubmed")
        if (isDBLPEnabled()) enabled.add("dblp")
        return enabled
    }
}
