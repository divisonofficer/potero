package com.potero.service.metadata

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Resolver for arXiv papers using arXiv API
 */
class ArxivResolver(
    private val httpClient: HttpClient
) : MetadataResolver {

    companion object {
        private const val ARXIV_API = "https://export.arxiv.org/api/query"
        // Matches: 1234.56789, 1234.56789v1, hep-th/9901001
        private val ARXIV_ID_REGEX = Regex("""(\d{4}\.\d{4,5})(v\d+)?|([a-z-]+/\d{7})""")
    }

    override fun canResolve(identifier: String): Boolean {
        return identifier.contains("arxiv.org") || ARXIV_ID_REGEX.containsMatchIn(identifier)
    }

    override suspend fun resolve(identifier: String): Result<ResolvedMetadata> = runCatching {
        val arxivId = extractArxivId(identifier)
            ?: throw MetadataResolutionException("Invalid arXiv ID format", identifier)

        val response = httpClient.get(ARXIV_API) {
            parameter("id_list", arxivId)
            parameter("max_results", 1)
        }

        if (!response.status.isSuccess()) {
            throw MetadataResolutionException(
                "arXiv API request failed with status: ${response.status}",
                identifier
            )
        }

        val xml = response.body<String>()
        parseArxivResponse(xml, arxivId)
    }

    private fun extractArxivId(identifier: String): String? {
        // Handle full URLs like https://arxiv.org/abs/1234.56789
        val cleaned = identifier
            .replace(Regex("https?://arxiv\\.org/(abs|pdf)/"), "")
            .replace(".pdf", "")
            .trim()

        return ARXIV_ID_REGEX.find(cleaned)?.value
    }

    private fun parseArxivResponse(xml: String, arxivId: String): ResolvedMetadata {
        // Simple XML parsing without external library
        // In production, consider using a proper XML parser

        fun extractTag(tag: String, content: String = xml): String? {
            val pattern = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            return pattern.find(content)?.groupValues?.get(1)?.trim()
        }

        fun extractAllTags(tag: String, content: String = xml): List<String> {
            val pattern = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            return pattern.findAll(content).map { it.groupValues[1].trim() }.toList()
        }

        // Find the entry element
        val entryPattern = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val entry = entryPattern.find(xml)?.groupValues?.get(1)
            ?: throw MetadataResolutionException("No entry found in arXiv response", arxivId)

        // Extract title
        val title = extractTag("title", entry)?.replace(Regex("\\s+"), " ")
            ?: "Unknown Title"

        // Extract abstract/summary
        val abstract = extractTag("summary", entry)?.replace(Regex("\\s+"), " ")

        // Extract authors
        val authorPattern = Regex("<author>.*?<name>(.*?)</name>.*?</author>", RegexOption.DOT_MATCHES_ALL)
        val authors = authorPattern.findAll(entry).map { match ->
            ResolvedAuthor(name = match.groupValues[1].trim())
        }.toList()

        // Extract published date
        val published = extractTag("published", entry)
        val year = published?.substring(0, 4)?.toIntOrNull()
        val month = published?.substring(5, 7)?.toIntOrNull()

        // Extract categories (subjects)
        val categoryPattern = Regex("""<category[^>]*term="([^"]+)"""")
        val categories = categoryPattern.findAll(entry).map { it.groupValues[1] }.toList()

        // Extract PDF URL
        val pdfLinkPattern = Regex("""<link[^>]*title="pdf"[^>]*href="([^"]+)"""")
        val pdfUrl = pdfLinkPattern.find(entry)?.groupValues?.get(1)

        // Extract DOI if present
        val doiPattern = Regex("""<arxiv:doi[^>]*>(.*?)</arxiv:doi>""")
        val doi = doiPattern.find(entry)?.groupValues?.get(1)

        // Extract primary category as venue
        val primaryCategory = extractTag("arxiv:primary_category", entry)
            ?.let { Regex("""term="([^"]+)"""").find(it)?.groupValues?.get(1) }

        return ResolvedMetadata(
            title = title,
            authors = authors,
            abstract = abstract,
            doi = doi,
            arxivId = arxivId,
            year = year,
            month = month,
            venue = primaryCategory?.let { "arXiv:$it" },
            venueType = "preprint",
            pdfUrl = pdfUrl ?: "https://arxiv.org/pdf/$arxivId.pdf",
            keywords = categories
        )
    }
}
