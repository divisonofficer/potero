package com.potero.service.metadata

import com.potero.service.common.RateLimiter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay

/**
 * Resolver for PubMed/NCBI E-utilities API
 *
 * PubMed provides biomedical literature from NLM/NCBI:
 * - 36M+ citations for biomedical literature
 * - Free access with optional API key
 * - Without key: 3 requests/second
 * - With key: 10 requests/second
 * - PubMed Central (PMC) provides full-text access for open access papers
 *
 * Uses NCBI E-utilities (ESearch + EFetch)
 *
 * @param httpClient HTTP client for making requests
 * @param apiKeyProvider Optional provider for API key (increases rate limits)
 */
class PubMedResolver(
    private val httpClient: HttpClient,
    private val apiKeyProvider: (suspend () -> String?)? = null
) : MetadataResolver {

    companion object {
        private const val EUTILS_BASE = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
        private const val ESEARCH_URL = "$EUTILS_BASE/esearch.fcgi"
        private const val EFETCH_URL = "$EUTILS_BASE/efetch.fcgi"
        private const val TOOL_NAME = "potero"
        private const val EMAIL = "potero@postech.ac.kr"

        private val rateLimiterWithKey = RateLimiter(10) // 10 req/s with key
        private val rateLimiterWithoutKey = RateLimiter(3) // 3 req/s without key

        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    override fun canResolve(identifier: String): Boolean {
        // PubMed can resolve PMIDs, PMCIDs, DOIs, and search queries
        return identifier.matches(Regex("^\\d+$")) || // PMID
                identifier.uppercase().startsWith("PMC") || // PMCID
                identifier.startsWith("10.") || // DOI
                identifier.contains(" ") // Search query
    }

    override suspend fun resolve(identifier: String): Result<ResolvedMetadata> = runCatching {
        val pmid = when {
            identifier.matches(Regex("^\\d+$")) -> identifier // Already a PMID
            identifier.uppercase().startsWith("PMC") -> searchByPMCID(identifier)
            identifier.startsWith("10.") -> searchByDOI(identifier)
            else -> searchByQuery(identifier)
        } ?: throw MetadataResolutionException("No results found", identifier)

        fetchMetadata(pmid)
    }

    /**
     * Search for papers by query string
     *
     * @param query Search query
     * @param limit Maximum number of results
     * @return List of search results
     */
    suspend fun search(query: String, limit: Int = 10): List<SearchResult> {
        throttle()

        val pmids = searchByQueryMultiple(query, limit)
        if (pmids.isEmpty()) return emptyList()

        // Fetch metadata for all PMIDs
        return pmids.mapNotNull { pmid ->
            try {
                val metadata = fetchMetadata(pmid)
                SearchResult(
                    id = pmid,
                    title = metadata.title,
                    authors = metadata.authors.map { it.name },
                    year = metadata.year,
                    venue = metadata.venue,
                    citationCount = null, // PubMed doesn't provide citation count
                    abstract = metadata.abstract,
                    pdfUrl = metadata.pdfUrl,
                    doi = metadata.doi,
                    arxivId = null,
                    source = "pubmed"
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Search by DOI and return PMID
     */
    private suspend fun searchByDOI(doi: String): String? {
        return searchByQuery("$doi[doi]")
    }

    /**
     * Search by PMCID and return PMID
     */
    private suspend fun searchByPMCID(pmcid: String): String? {
        return searchByQuery("$pmcid[pmcid]")
    }

    /**
     * Search by query and return first PMID
     */
    private suspend fun searchByQuery(query: String): String? {
        return searchByQueryMultiple(query, 1).firstOrNull()
    }

    /**
     * Search by query and return multiple PMIDs
     */
    private suspend fun searchByQueryMultiple(query: String, limit: Int): List<String> {
        throttle()

        val response = withRetry {
            httpClient.get(ESEARCH_URL) {
                parameter("db", "pubmed")
                parameter("term", query)
                parameter("retmax", limit)
                parameter("retmode", "xml")
                parameter("tool", TOOL_NAME)
                parameter("email", EMAIL)
                apiKeyProvider?.invoke()?.let { key ->
                    if (key.isNotBlank()) {
                        parameter("api_key", key)
                    }
                }
            }.bodyAsText()
        }

        return parseESearchResponse(response)
    }

    /**
     * Fetch metadata for a PMID
     */
    private suspend fun fetchMetadata(pmid: String): ResolvedMetadata {
        throttle()

        val response = withRetry {
            httpClient.get(EFETCH_URL) {
                parameter("db", "pubmed")
                parameter("id", pmid)
                parameter("retmode", "xml")
                parameter("tool", TOOL_NAME)
                parameter("email", EMAIL)
                apiKeyProvider?.invoke()?.let { key ->
                    if (key.isNotBlank()) {
                        parameter("api_key", key)
                    }
                }
            }.bodyAsText()
        }

        return parseEFetchResponse(response)
    }

    /**
     * Parse ESearch XML response to extract PMIDs
     */
    private fun parseESearchResponse(xml: String): List<String> {
        val pmids = mutableListOf<String>()
        val idRegex = Regex("<Id>(\\d+)</Id>")

        idRegex.findAll(xml).forEach { match ->
            match.groupValues.getOrNull(1)?.let { pmids.add(it) }
        }

        return pmids
    }

    /**
     * Parse EFetch XML response to extract metadata
     *
     * This is a simplified parser that extracts key fields from PubMed XML
     */
    private fun parseEFetchResponse(xml: String): ResolvedMetadata {
        val title = extractXmlTag(xml, "ArticleTitle") ?: "Unknown Title"
        val abstract = extractXmlTag(xml, "AbstractText")
        val year = extractXmlTag(xml, "Year")?.toIntOrNull()
        val journal = extractXmlTag(xml, "Title") // Journal title
        val doi = extractXmlTag(xml, "ELocationID EIdType=\"doi\"") ?: extractDoiFromArticleIds(xml)

        // Extract authors
        val authors = extractAuthors(xml)

        // Extract PMCID for PDF link
        val pmcid = extractPMCID(xml)
        val pdfUrl = pmcid?.let { "https://www.ncbi.nlm.nih.gov/pmc/articles/$it/pdf/" }

        return ResolvedMetadata(
            title = title,
            authors = authors,
            abstract = abstract,
            doi = doi,
            arxivId = null,
            year = year,
            venue = journal,
            venueType = "journal",
            pdfUrl = pdfUrl,
            citationsCount = null
        )
    }

    /**
     * Extract authors from PubMed XML
     */
    private fun extractAuthors(xml: String): List<ResolvedAuthor> {
        val authors = mutableListOf<ResolvedAuthor>()
        val authorRegex = Regex("<Author[^>]*>([\\s\\S]*?)</Author>")

        authorRegex.findAll(xml).forEach { match ->
            val authorXml = match.groupValues.getOrNull(1) ?: return@forEach
            val lastName = extractXmlTag(authorXml, "LastName")
            val foreName = extractXmlTag(authorXml, "ForeName")
            val affiliation = extractXmlTag(authorXml, "Affiliation")

            if (lastName != null || foreName != null) {
                val name = listOfNotNull(foreName, lastName).joinToString(" ")
                authors.add(ResolvedAuthor(name = name, affiliation = affiliation))
            }
        }

        return authors
    }

    /**
     * Extract PMCID from ArticleId list
     */
    private fun extractPMCID(xml: String): String? {
        val pmcidRegex = Regex("<ArticleId IdType=\"pmc\">(PMC\\d+)</ArticleId>")
        return pmcidRegex.find(xml)?.groupValues?.getOrNull(1)
    }

    /**
     * Extract DOI from ArticleId list (fallback method)
     */
    private fun extractDoiFromArticleIds(xml: String): String? {
        val doiRegex = Regex("<ArticleId IdType=\"doi\">([^<]+)</ArticleId>")
        return doiRegex.find(xml)?.groupValues?.getOrNull(1)
    }

    /**
     * Extract text content from an XML tag
     */
    private fun extractXmlTag(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName[^>]*>([^<]*)</$tagName>")
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Throttle based on whether API key is present
     */
    private suspend fun throttle() {
        val hasKey = apiKeyProvider?.invoke()?.isNotBlank() == true
        if (hasKey) {
            rateLimiterWithKey.throttle()
        } else {
            rateLimiterWithoutKey.throttle()
        }
    }

    /**
     * Retry logic with exponential backoff
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        throw lastException ?: Exception("Failed after $MAX_RETRIES retries")
    }
}
