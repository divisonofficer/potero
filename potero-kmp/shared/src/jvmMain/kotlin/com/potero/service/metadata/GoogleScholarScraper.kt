package com.potero.service.metadata

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Google Scholar scraper for paper search
 * Uses HTML scraping since Google Scholar doesn't have an official API
 *
 * WARNING: Use with caution - may be blocked by Google if used too frequently
 */
class GoogleScholarScraper {

    companion object {
        private const val SCHOLAR_URL = "https://scholar.google.com/scholar"
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
        )
    }

    /**
     * Search Google Scholar for papers
     * @param query Search query
     * @param limit Maximum results to return
     * @return List of search results
     */
    suspend fun search(query: String, limit: Int = 10): Result<List<GoogleScholarResult>> = runCatching {
        println("[GoogleScholar] Searching for: $query")

        val doc = fetchScholarPage(query)
        val results = parseResults(doc, limit)

        println("[GoogleScholar] Found ${results.size} results")
        results
    }

    private fun fetchScholarPage(query: String): Document {
        return Jsoup.connect(SCHOLAR_URL)
            .userAgent(USER_AGENTS.random())
            .data("q", query)
            .data("hl", "en")
            .timeout(10000)
            .get()
    }

    private fun parseResults(doc: Document, limit: Int): List<GoogleScholarResult> {
        val results = mutableListOf<GoogleScholarResult>()

        // Each result is in a div with class "gs_r gs_or gs_scl"
        val resultElements = doc.select(".gs_r.gs_or.gs_scl")

        for (element in resultElements.take(limit)) {
            try {
                val result = parseResultElement(element)
                if (result != null) {
                    results.add(result)
                }
            } catch (e: Exception) {
                println("[GoogleScholar] Failed to parse result: ${e.message}")
            }
        }

        return results
    }

    private fun parseResultElement(element: Element): GoogleScholarResult? {
        // Title and link
        val titleElement = element.selectFirst(".gs_rt a")
        val title = titleElement?.text() ?: element.selectFirst(".gs_rt")?.text() ?: return null
        val link = titleElement?.attr("href")

        // Authors and metadata line (e.g., "A Smith, B Jones - Journal Name, 2023 - publisher.com")
        val metaLine = element.selectFirst(".gs_a")?.text() ?: ""
        val (authors, year, venue) = parseMetaLine(metaLine)

        // Abstract/snippet
        val abstractText = element.selectFirst(".gs_rs")?.text()

        // Citation count
        val citedByElement = element.select(".gs_fl a").find { it.text().contains("Cited by") }
        val citationCount = citedByElement?.text()?.replace("Cited by ", "")?.toIntOrNull()

        // PDF link (if available)
        val pdfElement = element.selectFirst(".gs_or_ggsm a")
        val pdfUrl = pdfElement?.attr("href")

        return GoogleScholarResult(
            title = title,
            authors = authors,
            year = year,
            venue = venue,
            abstract = abstractText,
            citationCount = citationCount,
            link = link,
            pdfUrl = pdfUrl
        )
    }

    private fun parseMetaLine(metaLine: String): Triple<List<String>, Int?, String?> {
        // Example: "A Smith, B Jones - Journal of Example, 2023 - example.com"
        val parts = metaLine.split(" - ")

        val authors = parts.getOrNull(0)?.split(",")?.map { it.trim() } ?: emptyList()

        var year: Int? = null
        var venue: String? = null

        if (parts.size > 1) {
            val venueYearPart = parts[1]
            // Extract year (4 digits)
            val yearMatch = Regex("\\b(19|20)\\d{2}\\b").find(venueYearPart)
            year = yearMatch?.value?.toIntOrNull()

            // Extract venue (remove year and publisher)
            venue = venueYearPart
                .replace(Regex("\\b(19|20)\\d{2}\\b"), "")
                .split(",")
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        return Triple(authors, year, venue)
    }
}

/**
 * Google Scholar search result
 */
data class GoogleScholarResult(
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val venue: String?,
    val abstract: String?,
    val citationCount: Int?,
    val link: String?,
    val pdfUrl: String?
) {
    /**
     * Convert to unified SearchResult
     */
    fun toSearchResult(): SearchResult = SearchResult(
        id = link?.hashCode()?.toString() ?: title.hashCode().toString(),
        title = title,
        authors = authors,
        year = year,
        venue = venue,
        abstract = abstract,
        citationCount = citationCount,
        pdfUrl = pdfUrl,
        doi = null,
        arxivId = null,
        source = "google_scholar"
    )
}
