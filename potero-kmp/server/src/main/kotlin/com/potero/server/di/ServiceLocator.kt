package com.potero.server.di

import com.potero.data.repository.PaperRepositoryImpl
import com.potero.data.repository.SettingsRepositoryImpl
import com.potero.data.repository.TagRepositoryImpl
import com.potero.database.DriverFactory
import com.potero.domain.repository.PaperRepository
import com.potero.domain.repository.SettingsRepository
import com.potero.domain.repository.TagRepository
import com.potero.network.HttpClientFactory
import com.potero.service.llm.LLMConfig
import com.potero.service.llm.LLMProvider
import com.potero.service.llm.LLMService
import com.potero.service.llm.PostechLLMService
import com.potero.service.metadata.ArxivResolver
import com.potero.service.metadata.DOIResolver
import com.potero.service.metadata.MetadataResolver
import io.ktor.client.HttpClient

/**
 * Simple service locator for dependency injection in the server
 */
object ServiceLocator {

    private var initialized = false
    private var llmConfig: LLMConfig = LLMConfig(apiKey = "")

    // Lazy initialization of services
    val httpClient: HttpClient by lazy {
        HttpClientFactory.getClient()
    }

    val database by lazy {
        DriverFactory.getDatabase()
    }

    val paperRepository: PaperRepository by lazy {
        PaperRepositoryImpl(database)
    }

    val tagRepository: TagRepository by lazy {
        TagRepositoryImpl(database)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(database)
    }

    val doiResolver: MetadataResolver by lazy {
        DOIResolver(httpClient)
    }

    val arxivResolver: MetadataResolver by lazy {
        ArxivResolver(httpClient)
    }

    val metadataResolvers: List<MetadataResolver> by lazy {
        listOf(doiResolver, arxivResolver)
    }

    val llmService: LLMService by lazy {
        PostechLLMService(
            httpClient = httpClient,
            config = llmConfig
        )
    }

    /**
     * Initialize the service locator with configuration
     */
    fun init(config: ServerConfig = ServerConfig()) {
        if (initialized) return

        llmConfig = LLMConfig(
            apiKey = config.llmApiKey,
            provider = config.llmProvider
        )
        initialized = true
    }

    /**
     * Resolve metadata from a DOI or arXiv identifier
     */
    suspend fun resolveMetadata(identifier: String): com.potero.service.metadata.ResolvedMetadata? {
        for (resolver in metadataResolvers) {
            if (resolver.canResolve(identifier)) {
                return resolver.resolve(identifier).getOrNull()
            }
        }
        return null
    }

    /**
     * Shutdown all services
     */
    fun shutdown() {
        HttpClientFactory.close()
        DriverFactory.close()
        initialized = false
    }
}

/**
 * Server configuration
 */
data class ServerConfig(
    val llmApiKey: String = System.getenv("POTERO_LLM_API_KEY") ?: "",
    val llmProvider: LLMProvider = LLMProvider.GPT,
    val dbPath: String? = null
)
