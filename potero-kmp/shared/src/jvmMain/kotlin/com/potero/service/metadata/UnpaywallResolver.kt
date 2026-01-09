package com.potero.service.metadata

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unpaywall API resolver for finding open access PDFs
 *
 * Unpaywall is a free service that finds legal, open access versions of scholarly articles.
 * API Documentation: https://unpaywall.org/products/api
 *
 * Note: Requires an email address for polite API usage (no API key needed)
 */
class UnpaywallResolver(
    private val httpClient: HttpClient,
    private val email: String = "potero@example.com"  // TODO: Make this configurable
) {
    companion object {
        private const val BASE_URL = "https://api.unpaywall.org/v2"
    }

    /**
     * Find open access PDF for a DOI
     *
     * @param doi The DOI to look up
     * @return PDF URL if found, null otherwise
     */
    suspend fun findOpenAccessPdf(doi: String): String? {
        return try {
            val cleanDoi = doi.removePrefix("https://doi.org/").removePrefix("http://doi.org/")
            val url = "$BASE_URL/$cleanDoi?email=$email"

            println("[Unpaywall] Looking up DOI: $cleanDoi")

            val response = httpClient.get(url)

            if (response.status.value !in 200..299) {
                println("[Unpaywall] API returned status: ${response.status}")
                return null
            }

            val result = response.body<UnpaywallResponse>()

            // Try best_oa_location first (highest quality)
            val pdfUrl = result.bestOaLocation?.urlForPdf
                ?: result.oaLocations.firstOrNull()?.urlForPdf

            if (pdfUrl != null) {
                println("[Unpaywall] ✓ Found open access PDF: $pdfUrl")
            } else {
                println("[Unpaywall] ✗ No open access version available")
            }

            pdfUrl
        } catch (e: Exception) {
            println("[Unpaywall] Error: ${e.message}")
            null
        }
    }
}

@Serializable
data class UnpaywallResponse(
    val doi: String,
    val title: String? = null,
    @SerialName("is_oa") val isOa: Boolean,
    @SerialName("best_oa_location") val bestOaLocation: OaLocation? = null,
    @SerialName("oa_locations") val oaLocations: List<OaLocation> = emptyList()
)

@Serializable
data class OaLocation(
    @SerialName("url_for_pdf") val urlForPdf: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("host_type") val hostType: String? = null, // "publisher", "repository"
    @SerialName("version") val version: String? = null // "publishedVersion", "acceptedVersion"
)
