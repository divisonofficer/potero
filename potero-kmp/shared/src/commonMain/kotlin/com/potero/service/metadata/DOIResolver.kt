package com.potero.service.metadata

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Resolver for DOI (Digital Object Identifier) using CrossRef API
 */
class DOIResolver(
    private val httpClient: HttpClient
) : MetadataResolver {

    companion object {
        private const val CROSSREF_API = "https://api.crossref.org/works"
        private val DOI_REGEX = Regex("""10\.\d{4,}/[^\s]+""")
    }

    override fun canResolve(identifier: String): Boolean {
        return DOI_REGEX.containsMatchIn(identifier)
    }

    override suspend fun resolve(identifier: String): Result<ResolvedMetadata> = runCatching {
        val doi = extractDoi(identifier)
            ?: throw MetadataResolutionException("Invalid DOI format", identifier)

        val response = httpClient.get("$CROSSREF_API/$doi") {
            accept(ContentType.Application.Json)
            header("User-Agent", "Potero/1.0 (https://github.com/potero)")
        }

        if (!response.status.isSuccess()) {
            throw MetadataResolutionException(
                "CrossRef API request failed with status: ${response.status}",
                identifier
            )
        }

        val json = response.body<JsonObject>()
        parseCrossRefResponse(json, doi)
    }

    private fun extractDoi(identifier: String): String? {
        // Handle full URLs like https://doi.org/10.1234/...
        val cleaned = identifier
            .removePrefix("https://doi.org/")
            .removePrefix("http://doi.org/")
            .removePrefix("doi:")
            .trim()

        return DOI_REGEX.find(cleaned)?.value
    }

    private fun parseCrossRefResponse(json: JsonObject, doi: String): ResolvedMetadata {
        val message = json["message"]?.jsonObject
            ?: throw MetadataResolutionException("Invalid CrossRef response: missing 'message'", doi)

        // Extract title
        val title = message["title"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?: "Unknown Title"

        // Extract authors
        val authors = message["author"]?.jsonArray?.mapNotNull { authorElement ->
            val authorObj = authorElement.jsonObject
            val given = authorObj["given"]?.jsonPrimitive?.content ?: ""
            val family = authorObj["family"]?.jsonPrimitive?.content ?: ""
            val name = "$given $family".trim()

            if (name.isNotBlank()) {
                ResolvedAuthor(
                    name = name,
                    affiliation = authorObj["affiliation"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("name")?.jsonPrimitive?.content,
                    orcid = authorObj["ORCID"]?.jsonPrimitive?.content
                )
            } else null
        } ?: emptyList()

        // Extract abstract
        val abstract = message["abstract"]?.jsonPrimitive?.content?.let {
            // Clean up JATS XML tags if present
            it.replace(Regex("<[^>]+>"), "").trim()
        }

        // Extract year
        val year = message["published"]?.jsonObject
            ?.get("date-parts")?.jsonArray?.firstOrNull()
            ?.jsonArray?.firstOrNull()?.jsonPrimitive?.int
            ?: message["created"]?.jsonObject
                ?.get("date-parts")?.jsonArray?.firstOrNull()
                ?.jsonArray?.firstOrNull()?.jsonPrimitive?.int

        // Extract month
        val month = message["published"]?.jsonObject
            ?.get("date-parts")?.jsonArray?.firstOrNull()
            ?.jsonArray?.getOrNull(1)?.jsonPrimitive?.int

        // Extract venue (container-title)
        val venue = message["container-title"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content

        // Extract type
        val venueType = when (message["type"]?.jsonPrimitive?.content) {
            "journal-article" -> "journal"
            "proceedings-article" -> "conference"
            "book-chapter" -> "book"
            else -> null
        }

        // Extract publisher
        val publisher = message["publisher"]?.jsonPrimitive?.content

        // Extract PDF URL (if available)
        val pdfUrl = message["link"]?.jsonArray?.firstNotNullOfOrNull { linkElement ->
            val linkObj = linkElement.jsonObject
            if (linkObj["content-type"]?.jsonPrimitive?.content == "application/pdf") {
                linkObj["URL"]?.jsonPrimitive?.content
            } else null
        }

        // Extract citation count
        val citationsCount = message["is-referenced-by-count"]?.jsonPrimitive?.int

        // Extract keywords/subjects
        val keywords = message["subject"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.contentOrNull
        } ?: emptyList()

        return ResolvedMetadata(
            title = title,
            authors = authors,
            abstract = abstract,
            doi = doi,
            year = year,
            month = month,
            venue = venue,
            venueType = venueType,
            publisher = publisher,
            pdfUrl = pdfUrl,
            citationsCount = citationsCount,
            keywords = keywords
        )
    }
}
