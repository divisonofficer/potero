package com.potero.service.metadata

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jsoup.Jsoup

/**
 * Sci-Hub resolver for finding PDFs
 *
 * WARNING: Sci-Hub operates in a legal gray area and may violate copyright laws
 * in many jurisdictions. Use at your own risk and only for research/educational purposes.
 *
 * This resolver is DISABLED by default and must be explicitly enabled in Settings.
 */
class SciHubResolver(
    private val httpClient: HttpClient,
    private val enabledProvider: suspend () -> Boolean
) {
    companion object {
        // Sci-Hub mirrors (frequently change)
        private val SCIHUB_MIRRORS = listOf(
            "https://sci-hub.se",
            "https://sci-hub.st",
            "https://sci-hub.ru",
            "https://sci-hub.wf"
        )
    }

    /**
     * Check if Sci-Hub is enabled in settings
     */
    suspend fun isEnabled(): Boolean {
        return enabledProvider()
    }

    /**
     * Try to find PDF URL via Sci-Hub
     *
     * @param doi The DOI to look up
     * @return PDF URL if found, null otherwise
     */
    suspend fun findPdf(doi: String): String? {
        if (!isEnabled()) {
            println("[SciHub] Disabled in settings")
            return null
        }

        val cleanDoi = doi.removePrefix("https://doi.org/").removePrefix("http://doi.org/")

        // Try each mirror until one works
        for (mirror in SCIHUB_MIRRORS) {
            try {
                println("[SciHub] Trying mirror: $mirror")
                val pdfUrl = tryMirror(mirror, cleanDoi)
                if (pdfUrl != null) {
                    println("[SciHub] ✓ Found PDF at: $mirror")
                    return pdfUrl
                }
            } catch (e: Exception) {
                println("[SciHub] Mirror $mirror failed: ${e.message}")
                continue
            }
        }

        println("[SciHub] ✗ All mirrors failed")
        return null
    }

    /**
     * Try to get PDF from a specific Sci-Hub mirror
     */
    private suspend fun tryMirror(mirror: String, doi: String): String? {
        try {
            // Sci-Hub URL pattern
            val url = "$mirror/$doi"

            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; Potero/1.0; +https://github.com/yourusername/potero)")
            }

            if (response.status.value !in 200..299) {
                return null
            }

            // Parse HTML to find PDF iframe or embed
            val html = response.bodyAsText()
            val doc = Jsoup.parse(html)

            // Sci-Hub embeds PDF in iframe with id="pdf"
            val pdfIframe = doc.selectFirst("iframe#pdf")?.attr("src")
            if (pdfIframe != null) {
                // Make absolute URL
                return if (pdfIframe.startsWith("http")) {
                    pdfIframe
                } else if (pdfIframe.startsWith("//")) {
                    "https:$pdfIframe"
                } else {
                    "$mirror$pdfIframe"
                }
            }

            // Alternative: Look for embed tag
            val pdfEmbed = doc.selectFirst("embed#pdf")?.attr("src")
            if (pdfEmbed != null) {
                return if (pdfEmbed.startsWith("http")) {
                    pdfEmbed
                } else if (pdfEmbed.startsWith("//")) {
                    "https:$pdfEmbed"
                } else {
                    "$mirror$pdfEmbed"
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }
}
