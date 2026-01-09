package com.potero.service.metadata

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jsoup.Jsoup

/**
 * CVF (Computer Vision Foundation) Open Access resolver
 *
 * Finds PDFs from CVF Open Access portal for CVPR, ICCV, WACV conferences.
 * All papers are freely available without paywall.
 *
 * Examples:
 * - https://openaccess.thecvf.com/content/CVPR2024/papers/...
 * - https://openaccess.thecvf.com/content/ICCV2023/papers/...
 */
class CVFOpenAccessResolver(
    private val httpClient: HttpClient
) {
    companion object {
        private const val CVF_BASE = "https://openaccess.thecvf.com"
        private val CVF_CONFERENCES = listOf("CVPR", "ICCV", "WACV", "ECCV")
    }

    /**
     * Try to find PDF from CVF Open Access
     *
     * Strategy: Construct direct PDF URL from DOI or search papers page
     *
     * @param title Paper title
     * @param year Publication year
     * @param venue Conference name (CVPR, ICCV, etc.)
     * @param doi DOI (if available)
     * @return PDF URL if found, null otherwise
     */
    suspend fun findPdf(
        title: String,
        year: Int?,
        venue: String?,
        doi: String? = null,
        authors: List<String> = emptyList()
    ): String? {
        // Check if this is a CVF conference
        val conference = CVF_CONFERENCES.find { venue?.contains(it, ignoreCase = true) == true }
        if (conference == null || year == null) {
            println("[CVF] Not a CVF conference or year missing: venue=$venue, year=$year")
            return null
        }

        println("[CVF] Trying to find $conference$year paper: $title")

        // Strategy 1: Construct PDF URL from title and first author
        // Pattern: {FirstAuthor}_{Title_Words}_{Conference}_{Year}_paper.pdf
        // Example: Choi_Dual_Exposure_Stereo_for_Extended_Dynamic_Range_3D_Imaging_CVPR_2025_paper.pdf

        if (authors.isNotEmpty()) {
            val firstAuthor = authors.first().split(" ").last() // Get last name
            val titleWords = title
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() && it[0].isLetterOrDigit() } // Include words starting with letters or numbers
                .joinToString("_") { word ->
                    // Keep alphanumeric characters AND hyphens (e.g., "Pixel-aligned", "RGB-NIR")
                    word.replace(Regex("[^a-zA-Z0-9-]"), "")
                }

            val pdfUrl = "$CVF_BASE/content/$conference$year/papers/${firstAuthor}_${titleWords}_${conference}_${year}_paper.pdf"

            println("[CVF] Constructed PDF URL: $pdfUrl")
            return pdfUrl // PdfDownloadService will validate by attempting download
        }

        // Strategy 2: Try to access papers list page with proper headers
        try {
            val contentUrl = "$CVF_BASE/content/$conference$year/papers"
            println("[CVF] Accessing: $contentUrl")

            val response = httpClient.get(contentUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Referer", CVF_BASE)
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            }

            if (response.status.value !in 200..299) {
                println("[CVF] Content page not accessible: ${response.status}")
                return null
            }

            // Parse HTML to find paper links
            val html = response.bodyAsText()
            val doc = Jsoup.parse(html)

            // CVF lists papers with links like:
            // <a href="papers/Author_Title_CVPR_2024_paper.pdf">
            val links = doc.select("a[href*=.pdf]")

            // Find link matching the title
            val titleWords = title.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.take(5)

            for (link in links) {
                val href = link.attr("href")
                val linkText = link.text().lowercase()

                // Check if link text or href contains significant words from title
                val matchCount = titleWords.count { word ->
                    href.lowercase().contains(word) || linkText.contains(word)
                }

                if (matchCount >= 3) {
                    val pdfUrl = if (href.startsWith("http")) {
                        href
                    } else {
                        "$CVF_BASE/$href".replace("//", "/").replace(":/", "://")
                    }

                    println("[CVF] ✓ Found PDF: $pdfUrl")
                    return pdfUrl
                }
            }

            println("[CVF] ✗ No matching PDF found in $conference$year")
            return null

        } catch (e: Exception) {
            println("[CVF] Error: ${e.message}")
            return null
        }
    }

    /**
     * Check if a conference is CVF-hosted
     */
    fun isCVFConference(venue: String?): Boolean {
        return CVF_CONFERENCES.any { venue?.contains(it, ignoreCase = true) == true }
    }
}
