package com.potero.server.di

import com.potero.data.repository.CitationRepositoryImpl
import com.potero.data.repository.GrobidRepositoryImpl
import com.potero.data.repository.NarrativeRepositoryImpl
import com.potero.data.repository.PaperRepositoryImpl
import com.potero.data.repository.PdfPreprocessingRepositoryImpl
import com.potero.data.repository.ReferenceRepositoryImpl
import com.potero.data.repository.ResearchNoteRepositoryImpl
import com.potero.data.repository.SettingsRepositoryImpl
import com.potero.data.repository.TagRepositoryImpl
import com.potero.database.DriverFactory
import com.potero.domain.repository.CitationRepository
import com.potero.domain.repository.GrobidRepository
import com.potero.domain.repository.NarrativeRepository
import com.potero.domain.repository.PaperRepository
import com.potero.domain.repository.PdfPreprocessingRepository
import com.potero.domain.repository.ReferenceRepository
import com.potero.domain.repository.ResearchNoteRepository
import com.potero.domain.repository.SettingsKeys
import com.potero.domain.repository.SettingsRepository
import com.potero.domain.repository.TagRepository
import com.potero.network.HttpClientFactory
import com.potero.service.llm.LLMConfig
import com.potero.service.llm.LLMLogger
import com.potero.service.llm.LLMProvider
import com.potero.service.llm.LLMService
import com.potero.service.llm.MetadataCleaningService
import com.potero.service.llm.PdfLLMAnalysisService
import com.potero.service.llm.PostechLLMService
import com.potero.service.llm.ComprehensivePaperAnalysisService
import com.potero.service.pdf.PdfThumbnailExtractor
import com.potero.service.pdf.PreprocessedPdfProvider
import com.potero.service.pdf.PreprocessedPdfProviderImpl
import com.potero.service.pdf.preprocessing.PdfPreprocessingService
import com.potero.service.metadata.ArxivResolver
import com.potero.service.metadata.DOIResolver
import com.potero.service.metadata.GoogleScholarScraper
import com.potero.service.metadata.MetadataResolver
import com.potero.service.metadata.SemanticScholarResolver
import com.potero.service.metadata.OpenAlexResolver
import com.potero.service.metadata.PubMedResolver
import com.potero.service.metadata.DBLPResolver
import com.potero.service.search.APIStatusProvider
import com.potero.service.search.SearchCacheService
import com.potero.service.search.UnifiedSearchService
import com.potero.service.tag.TagService
import com.potero.service.genai.GenAIFileUploadService
import com.potero.service.grobid.GrobidEngine
import com.potero.service.grobid.GrobidProcessor
import com.potero.service.grobid.GrobidRestEngine
import com.potero.service.grobid.DisabledGrobidEngine
import com.potero.service.grobid.LLMReferenceParser
import com.potero.service.pdf.PdfDownloadService
import com.potero.service.metadata.UnpaywallResolver
import com.potero.service.metadata.SciHubResolver
import com.potero.service.metadata.CVFOpenAccessResolver
import com.potero.service.narrative.NarrativeCacheService
import com.potero.service.narrative.NarrativeEngineService
import com.potero.service.pdf.PdfAnalyzer
import com.potero.service.chat.*
import com.potero.service.chat.tools.*
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

    val referenceRepository: ReferenceRepository by lazy {
        ReferenceRepositoryImpl(database)
    }

    val citationRepository: CitationRepository by lazy {
        CitationRepositoryImpl(database)
    }

    val grobidRepository: GrobidRepository by lazy {
        GrobidRepositoryImpl(database)
    }

    val formulaRepository: com.potero.domain.repository.FormulaRepository by lazy {
        com.potero.data.repository.FormulaRepositoryImpl(database)
    }

    val narrativeRepository: NarrativeRepository by lazy {
        NarrativeRepositoryImpl(database)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(database)
    }

    val researchNoteRepository: ResearchNoteRepository by lazy {
        ResearchNoteRepositoryImpl(database)
    }

    val pdfPreprocessingRepository: PdfPreprocessingRepository by lazy {
        PdfPreprocessingRepositoryImpl(database)
    }

    val doiResolver: MetadataResolver by lazy {
        DOIResolver(httpClient)
    }

    val arxivResolver: MetadataResolver by lazy {
        ArxivResolver(httpClient)
    }

    val semanticScholarResolver: SemanticScholarResolver by lazy {
        SemanticScholarResolver(
            httpClient = httpClient,
            apiKeyProvider = {
                // Dynamically load API key from settings database
                settingsRepository.get(SettingsKeys.SEMANTIC_SCHOLAR_API_KEY).getOrNull()
            }
        )
    }

    val openAlexResolver: OpenAlexResolver by lazy {
        OpenAlexResolver(
            httpClient = httpClient,
            apiKeyProvider = {
                settingsRepository.get(SettingsKeys.OPENALEX_API_KEY).getOrNull()
            }
        )
    }

    val pubMedResolver: PubMedResolver by lazy {
        PubMedResolver(
            httpClient = httpClient,
            apiKeyProvider = {
                settingsRepository.get(SettingsKeys.PUBMED_API_KEY).getOrNull()
            }
        )
    }

    val dblpResolver: DBLPResolver by lazy {
        DBLPResolver(httpClient = httpClient)
    }

    val metadataResolvers: List<MetadataResolver> by lazy {
        listOf(
            doiResolver,
            arxivResolver,
            openAlexResolver,
            pubMedResolver,
            dblpResolver
        )
    }

    val llmLogger: LLMLogger by lazy {
        LLMLogger(maxEntries = 100)
    }

    val llmService: LLMService by lazy {
        PostechLLMService(
            httpClient = httpClient,
            apiKeyProvider = {
                // Dynamically load API key from settings database
                settingsRepository.get(SettingsKeys.LLM_API_KEY).getOrNull() ?: ""
            },
            providerProvider = {
                // Dynamically load provider from settings database
                val providerName = settingsRepository.get(SettingsKeys.LLM_PROVIDER).getOrNull()
                when (providerName?.lowercase()) {
                    "claude" -> LLMProvider.CLAUDE
                    "gemini" -> LLMProvider.GEMINI
                    else -> LLMProvider.GPT
                }
            }
        )
    }

    val metadataCleaningService: MetadataCleaningService by lazy {
        MetadataCleaningService(
            llmService = llmService,
            llmLogger = llmLogger
        )
    }

    val tagService: TagService by lazy {
        TagService(
            tagRepository = tagRepository,
            llmService = llmService,
            llmLogger = llmLogger
        )
    }

    val googleScholarScraper: GoogleScholarScraper by lazy {
        GoogleScholarScraper()
    }

    val searchCacheService: SearchCacheService by lazy {
        SearchCacheService()
    }

    val apiStatusProvider: APIStatusProvider by lazy {
        APIStatusProvider(settingsRepository)
    }

    val unifiedSearchService: UnifiedSearchService by lazy {
        UnifiedSearchService(
            semanticScholarResolver = semanticScholarResolver,
            openAlexResolver = openAlexResolver,
            pubMedResolver = pubMedResolver,
            dblpResolver = dblpResolver,
            apiStatusProvider = apiStatusProvider,
            googleScholarScraper = googleScholarScraper,
            cacheService = searchCacheService
        )
    }

    val pdfLLMAnalysisService: PdfLLMAnalysisService by lazy {
        PdfLLMAnalysisService(
            llmService = llmService,
            llmLogger = llmLogger
        )
    }

    val comprehensivePaperAnalysisService: ComprehensivePaperAnalysisService by lazy {
        ComprehensivePaperAnalysisService(
            llmService = llmService,
            llmLogger = llmLogger
        )
    }

    val pdfThumbnailExtractor: PdfThumbnailExtractor by lazy {
        PdfThumbnailExtractor()
    }

    val genAIFileUploadService: com.potero.service.genai.GenAIFileUploadService by lazy {
        com.potero.service.genai.GenAIFileUploadService(
            httpClient = httpClient,
            tokenProvider = {
                // Dynamically load SSO token from settings database
                settingsRepository.get(SettingsKeys.SSO_ACCESS_TOKEN).getOrNull()
            },
            siteNameProvider = {
                // Load site name from settings, default to "robi-gpt-dev"
                settingsRepository.get(SettingsKeys.SSO_SITE_NAME).getOrNull() ?: "robi-gpt-dev"
            }
        )
    }

    val grobidEngine: GrobidEngine by lazy {
        try {
            // Try to use REST engine (local process)
            GrobidRestEngine(httpClient)
        } catch (e: Exception) {
            println("[ServiceLocator] GROBID not available: ${e.message}")
            // Fallback to disabled engine
            DisabledGrobidEngine
        }
    }

    val llmReferenceParser: LLMReferenceParser by lazy {
        LLMReferenceParser(
            llmService = llmService,
            llmLogger = llmLogger
        )
    }

    val pdfOcrService: com.potero.service.ocr.PdfOcrService by lazy {
        com.potero.service.ocr.PdfOcrService(
            settingsRepository = settingsRepository
        )
    }

    val grobidProcessor: GrobidProcessor by lazy {
        GrobidProcessor(
            grobidEngine = grobidEngine,
            grobidRepository = grobidRepository,
            formulaRepository = formulaRepository,
            llmReferenceParser = llmReferenceParser,
            paperRepository = paperRepository,
            pdfDownloadService = pdfDownloadService,
            settingsRepository = settingsRepository,
            pdfOcrService = pdfOcrService,
            preprocessingRepository = pdfPreprocessingRepository
        )
    }

    val pdfPreprocessingService: PdfPreprocessingService by lazy {
        PdfPreprocessingService(
            preprocessingRepository = pdfPreprocessingRepository,
            grobidProcessor = grobidProcessor,
            pdfOcrService = pdfOcrService,
            pdfDownloadService = pdfDownloadService,
            paperRepository = paperRepository,
            settingsRepository = settingsRepository
        )
    }

    val preprocessedPdfProvider: PreprocessedPdfProvider by lazy {
        PreprocessedPdfProviderImpl(
            preprocessingRepository = pdfPreprocessingRepository,
            grobidRepository = grobidRepository,
            preprocessingService = pdfPreprocessingService
        )
    }

    val unpaywallResolver: UnpaywallResolver by lazy {
        UnpaywallResolver(
            httpClient = httpClient,
            email = "potero@postech.ac.kr"
        )
    }

    val cvfOpenAccessResolver: CVFOpenAccessResolver by lazy {
        CVFOpenAccessResolver(httpClient = httpClient)
    }

    val sciHubResolver: SciHubResolver by lazy {
        SciHubResolver(
            httpClient = httpClient,
            enabledProvider = {
                // Check if Sci-Hub is enabled in settings
                settingsRepository.get("scihub.enabled").getOrNull() == "true"
            }
        )
    }

    val pdfDownloadService: PdfDownloadService by lazy {
        PdfDownloadService(
            httpClient = httpClient,
            semanticScholarResolver = semanticScholarResolver,
            cvfResolver = cvfOpenAccessResolver,
            unpaywallResolver = unpaywallResolver,
            sciHubResolver = sciHubResolver
        )
    }

    val narrativeCacheService: NarrativeCacheService by lazy {
        NarrativeCacheService(narrativeRepository)
    }

    val narrativeEngineService: NarrativeEngineService by lazy {
        NarrativeEngineService(
            llmService = llmService,
            llmLogger = llmLogger,
            paperRepository = paperRepository,
            narrativeRepository = narrativeRepository,
            cacheService = narrativeCacheService,
            preprocessedPdfProvider = preprocessedPdfProvider,
            figureProvider = { paperId ->
                // Load figures from database
                try {
                    database.figureQueries.getFiguresByPaper(paperId).executeAsList().map { fig ->
                        com.potero.service.narrative.FigureInfo(
                            id = fig.id,
                            label = fig.label,
                            caption = fig.caption
                        )
                    }
                } catch (e: Exception) {
                    println("[ServiceLocator] Failed to load figures: ${e.message}")
                    emptyList()
                }
            },
            formulaProvider = { paperId ->
                // Load formulas from database
                try {
                    database.formulaQueries.getFormulasByPaper(paperId).executeAsList().map { formula ->
                        com.potero.service.narrative.FormulaInfo(
                            id = formula.id,
                            label = formula.label,
                            latex = formula.latex,
                            pageNum = formula.page_num.toInt()
                        )
                    }
                } catch (e: Exception) {
                    println("[ServiceLocator] Failed to load formulas: ${e.message}")
                    emptyList()
                }
            }
        )
    }

    // ===== Chat Services (Tool Calling System) =====

    val toolCallParser: ToolCallParser by lazy {
        ToolCallParser()
    }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry().apply {
            // Register available tools
            register(ScrollToLocationTool())
            // More tools will be added in Phase 2
            println("[ServiceLocator] Registered ${getToolCount()} chat tools")
        }
    }

    val toolExecutor: ToolExecutor by lazy {
        ToolExecutor(
            tools = toolRegistry.getAllTools(),
            llmLogger = llmLogger
        )
    }

    val chatService: ChatService by lazy {
        ChatService(
            llmService = llmService,
            toolRegistry = toolRegistry,
            toolParser = toolCallParser,
            toolExecutor = toolExecutor,
            paperRepository = paperRepository
        )
    }

    // ===== Related Work Services =====

    val relatedWorkRepository: com.potero.domain.repository.RelatedWorkRepository by lazy {
        com.potero.data.repository.RelatedWorkRepositoryImpl(database)
    }

    val comparisonTableRepository: com.potero.domain.repository.ComparisonTableRepository by lazy {
        com.potero.data.repository.ComparisonTableRepositoryImpl(
            database = database,
            paperRepository = paperRepository
        )
    }

    val metricExtractionService: com.potero.service.relatedwork.MetricExtractionService by lazy {
        com.potero.service.relatedwork.MetricExtractionService(
            paperRepository = paperRepository,
            preprocessedPdfProvider = preprocessedPdfProvider,
            llmService = llmService
        )
    }

    val comparisonService: com.potero.service.relatedwork.ComparisonService by lazy {
        com.potero.service.relatedwork.ComparisonService(
            comparisonRepository = comparisonTableRepository,
            paperRepository = paperRepository,
            metricExtractionService = metricExtractionService,
            llmService = llmService
        )
    }

    val relatedWorkService: com.potero.service.relatedwork.RelatedWorkService by lazy {
        com.potero.service.relatedwork.RelatedWorkService(
            relatedWorkRepository = relatedWorkRepository,
            paperRepository = paperRepository,
            grobidRepository = grobidRepository,
            semanticScholarResolver = semanticScholarResolver
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
