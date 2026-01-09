package com.potero.service.pdf

import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference
import com.potero.domain.model.Reference
import kotlin.math.max
import kotlin.math.min

/**
 * Links citation spans to reference entries.
 *
 * Linking strategies (in priority order):
 * 1. GROBID TEI target link: target_xml_id -> GrobidReference.xml_id (highest accuracy: 0.98)
 * 2. Annotation destination: GoTo links pointing to reference page (0.95)
 * 3. Numeric matching: [n] -> Reference entry #n (0.95)
 * 4. Author-year fuzzy: (Smith, 2024) -> Reference with matching author/year (0.75-0.85)
 */
class CitationLinker {

    /**
     * Link citation spans to references.
     *
     * @param spans Raw citation spans from CitationExtractor
     * @param references Reference entries from the paper
     * @param referencesStartPage First page of references section
     * @param grobidCitations GROBID citation spans with target links (optional)
     * @param grobidReferences GROBID reference entries (optional)
     * @return List of citation links with confidence scores
     */
    fun link(
        spans: List<RawCitationSpan>,
        references: List<Reference>,
        referencesStartPage: Int?,
        grobidCitations: List<GrobidCitationSpan> = emptyList(),
        grobidReferences: List<GrobidReference> = emptyList()
    ): List<CitationLinkResult> {
        if (references.isEmpty()) return emptyList()

        return spans.flatMap { span ->
            linkSpan(span, references, referencesStartPage, grobidCitations, grobidReferences)
        }
    }

    /**
     * Link a single citation span to references.
     */
    private fun linkSpan(
        span: RawCitationSpan,
        references: List<Reference>,
        referencesStartPage: Int?,
        grobidCitations: List<GrobidCitationSpan>,
        grobidReferences: List<GrobidReference>
    ): List<CitationLinkResult> {
        // Priority 1: Try GROBID target link (highest accuracy)
        if (grobidCitations.isNotEmpty() && grobidReferences.isNotEmpty()) {
            val grobidLinks = linkByGrobidTarget(span, references, grobidCitations, grobidReferences)
            if (grobidLinks.isNotEmpty()) {
                return grobidLinks
            }
        }

        // Priority 2: Annotation with destination
        if (span.provenance == "annotation" && span.destPage != null) {
            val annotationLinks = linkByAnnotationDestination(span, references, referencesStartPage)
            if (annotationLinks.isNotEmpty()) {
                return annotationLinks
            }
        }

        // Priority 3: Numeric citation
        if (span.style == "numeric") {
            val numericLinks = linkByNumericMatch(span, references)
            if (numericLinks.isNotEmpty()) {
                return numericLinks
            }
        }

        // Priority 4: Author-year citation
        if (span.style == "author_year") {
            return linkByAuthorYearFuzzy(span, references)
        }

        return emptyList()
    }

    /**
     * Link by GROBID TEI target (highest accuracy).
     *
     * Process:
     * 1. Match PDF citation to GROBID citation by page + bbox + text similarity
     * 2. Use GROBID citation's target_xml_id to find GROBID reference
     * 3. Map GROBID reference to PDF reference by matching number/authors/title
     */
    private fun linkByGrobidTarget(
        span: RawCitationSpan,
        references: List<Reference>,
        grobidCitations: List<GrobidCitationSpan>,
        grobidReferences: List<GrobidReference>
    ): List<CitationLinkResult> {
        // Step 1: Match PDF citation to GROBID citation
        val matchedGrobid = matchGrobidToPdfCitation(span, grobidCitations) ?: return emptyList()

        // Check if GROBID citation has target link
        val targetXmlId = matchedGrobid.targetXmlId
        if (targetXmlId.isNullOrBlank()) return emptyList()

        // Only process biblio citations (not figure/formula)
        if (matchedGrobid.refType != "biblio") return emptyList()

        // Step 2: Find GROBID reference by xml_id
        val grobidRef = grobidReferences.find { it.xmlId == targetXmlId } ?: return emptyList()

        // Step 3: Map GROBID reference to PDF reference
        val matchedReferences = matchGrobidReferenceToReference(grobidRef, references)
        if (matchedReferences.isEmpty()) return emptyList()

        return matchedReferences.map { ref ->
            CitationLinkResult(ref, span, "grobid_target", 0.98)
        }
    }

    /**
     * Match a PDF citation span to a GROBID citation span.
     *
     * Uses:
     * - Same page number
     * - Text similarity > 0.8
     * - Bounding box overlap > 0.5
     */
    private fun matchGrobidToPdfCitation(
        pdfSpan: RawCitationSpan,
        grobidSpans: List<GrobidCitationSpan>
    ): GrobidCitationSpan? {
        return grobidSpans
            .filter { it.pageNum == pdfSpan.pageNum }
            .find { grobid ->
                val textSim = textSimilarity(grobid.rawText, pdfSpan.rawText)
                // Bbox overlap not available since GrobidCitationSpan doesn't store bbox
                // Just use text similarity
                textSim > 0.8
            }
    }

    /**
     * Calculate text similarity (simple normalized Levenshtein distance).
     */
    private fun textSimilarity(text1: String, text2: String): Double {
        val cleaned1 = text1.trim().lowercase()
        val cleaned2 = text2.trim().lowercase()

        // Exact match
        if (cleaned1 == cleaned2) return 1.0

        // Substring match (one contains the other)
        if (cleaned1.contains(cleaned2) || cleaned2.contains(cleaned1)) return 0.9

        // Levenshtein distance
        val maxLen = max(cleaned1.length, cleaned2.length)
        if (maxLen == 0) return 1.0

        val distance = levenshteinDistance(cleaned1, cleaned2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Compute Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Match a GROBID reference to a PDF reference.
     *
     * Matching criteria:
     * - Number match (if GROBID xml_id contains number like "b12" -> ref #12)
     * - Author match (first author surname)
     * - Year match
     * - Title match
     */
    private fun matchGrobidReferenceToReference(
        grobidRef: GrobidReference,
        references: List<Reference>
    ): List<Reference> {
        // Try to extract number from xml_id (e.g., "#b12" -> 12)
        val grobidNumber = extractNumberFromXmlId(grobidRef.xmlId)

        // If we have a number, try exact match first
        if (grobidNumber != null) {
            val exactMatch = references.find { it.number == grobidNumber }
            if (exactMatch != null) {
                return listOf(exactMatch)
            }
        }

        // Fallback: match by authors/year/title
        val candidates = references.filter { ref ->
            val authorMatch = if (grobidRef.authors != null && ref.authors != null) {
                authorsOverlap(grobidRef.authors, ref.authors) > 0.5
            } else false

            val yearMatch = grobidRef.year != null && ref.year != null && grobidRef.year == ref.year

            val titleMatch = if (grobidRef.title != null && ref.title != null) {
                textSimilarity(grobidRef.title, ref.title) > 0.7
            } else false

            authorMatch || yearMatch || titleMatch
        }

        return candidates
    }

    /**
     * Extract number from GROBID xml_id.
     * Examples: "#b12" -> 12, "b5" -> 5
     */
    private fun extractNumberFromXmlId(xmlId: String): Int? {
        val match = Regex("""[^\d]*(\d+)""").find(xmlId)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Calculate overlap between two author strings.
     * Returns a score from 0.0 to 1.0.
     */
    private fun authorsOverlap(authors1: String, authors2: String): Double {
        val names1 = authors1.lowercase().split(Regex(""",|\s+and\s+|\s+&\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val names2 = authors2.lowercase().split(Regex(""",|\s+and\s+|\s+&\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (names1.isEmpty() || names2.isEmpty()) return 0.0

        val overlaps = names1.count { name1 ->
            names2.any { name2 ->
                name1.contains(name2) || name2.contains(name1)
            }
        }

        return overlaps.toDouble() / max(names1.size, names2.size)
    }

    /**
     * Link by annotation destination (GoTo page/position).
     *
     * The annotation points to a specific location in the document.
     * We find the reference entry closest to that location.
     */
    private fun linkByAnnotationDestination(
        span: RawCitationSpan,
        references: List<Reference>,
        referencesStartPage: Int?
    ): List<CitationLinkResult> {
        val destPage = span.destPage ?: return emptyList()

        // Check if destination is in references section
        if (referencesStartPage != null && destPage < referencesStartPage) {
            // Destination is not in references - might be figure/table link
            return emptyList()
        }

        // Find references on the destination page
        val refsOnPage = references.filter { it.pageNum == destPage }

        if (refsOnPage.isEmpty()) {
            // Try adjacent pages (sometimes destinations are slightly off)
            val nearbyRefs = references.filter {
                it.pageNum in (destPage - 1)..(destPage + 1)
            }
            if (nearbyRefs.isEmpty()) return emptyList()

            // Use numeric fallback if we have the number
            val numericLinks = linkByNumericMatch(span, references)
            if (numericLinks.isNotEmpty()) {
                return numericLinks.map { it.copy(method = "annotation_numeric_fallback") }
            }

            // Return all nearby refs with lower confidence
            return nearbyRefs.map { ref ->
                CitationLinkResult(ref, span, "annotation_page_nearby", 0.5)
            }
        }

        // If single reference on page, high confidence
        if (refsOnPage.size == 1) {
            return listOf(CitationLinkResult(refsOnPage[0], span, "annotation_goto", 0.95))
        }

        // Multiple refs on page: try numeric matching first
        val numericLinks = linkByNumericMatch(span, references)
        if (numericLinks.isNotEmpty()) {
            // Verify that linked ref is actually on the dest page
            val matchingOnPage = numericLinks.filter { link ->
                refsOnPage.any { it.id == link.reference.id }
            }
            if (matchingOnPage.isNotEmpty()) {
                return matchingOnPage.map { it.copy(method = "annotation_numeric", confidence = 0.92) }
            }
            // Numeric match but not on expected page - still return but with lower confidence
            return numericLinks.map { it.copy(method = "annotation_numeric_offset", confidence = 0.75) }
        }

        // Fallback: return all refs on page with lower confidence
        return refsOnPage.map { ref ->
            CitationLinkResult(ref, span, "annotation_page_only", 0.6)
        }
    }

    /**
     * Link numeric citations by extracting and matching reference numbers.
     *
     * Handles formats like:
     * - [1] -> Reference #1
     * - [1,2,3] -> References #1, #2, #3
     * - [1-5] -> References #1 through #5
     */
    private fun linkByNumericMatch(
        span: RawCitationSpan,
        references: List<Reference>
    ): List<CitationLinkResult> {
        val numbers = extractCitationNumbers(span.rawText)
        if (numbers.isEmpty()) return emptyList()

        val refMap = references.associateBy { it.number }

        return numbers.mapNotNull { num ->
            refMap[num]?.let { ref ->
                CitationLinkResult(ref, span, "numeric", 0.95)
            }
        }
    }

    /**
     * Extract reference numbers from citation text.
     *
     * Examples:
     * - "[1]" -> [1]
     * - "[1,2,3]" -> [1, 2, 3]
     * - "[1-5]" -> [1, 2, 3, 4, 5]
     * - "[1, 3-5, 8]" -> [1, 3, 4, 5, 8]
     */
    private fun extractCitationNumbers(text: String): List<Int> {
        val numbers = mutableListOf<Int>()
        val cleaned = text.replace(Regex("""[\[\]()]"""), "")

        // Split by comma
        for (part in cleaned.split(",")) {
            val trimmed = part.trim()

            // Check for range (e.g., "1-5" or "1–5")
            val rangeMatch = Regex("""(\d+)\s*[-–]\s*(\d+)""").find(trimmed)
            if (rangeMatch != null) {
                val start = rangeMatch.groupValues[1].toIntOrNull() ?: continue
                val end = rangeMatch.groupValues[2].toIntOrNull() ?: continue
                if (start <= end && end - start < 50) { // Sanity check
                    numbers.addAll(start..end)
                }
                continue
            }

            // Single number
            val num = trimmed.toIntOrNull()
            if (num != null && num in 1..9999) {
                numbers.add(num)
            }
        }

        return numbers.distinct()
    }

    /**
     * Link author-year citations by fuzzy matching.
     *
     * Matches citations like "(Smith, 2024)" or "(Smith et al., 2024)"
     * to references containing the author surname and year.
     */
    private fun linkByAuthorYearFuzzy(
        span: RawCitationSpan,
        references: List<Reference>
    ): List<CitationLinkResult> {
        val parsed = parseAuthorYear(span.rawText) ?: return emptyList()
        val (surname, year) = parsed

        // Find candidates matching author and year
        val candidates = references.filter { ref ->
            val authorsMatch = ref.authors?.lowercase()?.contains(surname.lowercase()) == true
            val yearMatch = year == null || ref.year == year
            authorsMatch && yearMatch
        }

        if (candidates.isEmpty()) return emptyList()

        // Score candidates
        return candidates.map { ref ->
            val confidence = computeAuthorYearConfidence(surname, year, ref)
            CitationLinkResult(ref, span, "author_year_fuzzy", confidence)
        }.sortedByDescending { it.confidence }.take(1) // Return best match only
    }

    /**
     * Parse author-year citation to extract surname and year.
     *
     * Handles:
     * - (Smith, 2024)
     * - (Smith et al., 2024)
     * - (Smith & Jones, 2024)
     */
    private fun parseAuthorYear(text: String): Pair<String, Int?>? {
        // Pattern: (Surname[...], Year)
        val match = Regex("""([A-Z][a-z]+).*?(\d{4})""").find(text)
            ?: return null

        val surname = match.groupValues[1]
        val year = match.groupValues[2].toIntOrNull()

        return surname to year
    }

    /**
     * Compute confidence score for author-year match.
     */
    private fun computeAuthorYearConfidence(
        surname: String,
        year: Int?,
        ref: Reference
    ): Double {
        var confidence = 0.5

        // Check if surname matches first author
        val authors = ref.authors?.lowercase() ?: ""
        val firstAuthorMatch = authors.substringBefore(",").contains(surname.lowercase())
        if (firstAuthorMatch) {
            confidence += 0.25
        } else if (authors.contains(surname.lowercase())) {
            confidence += 0.15
        }

        // Year match
        if (year != null && ref.year == year) {
            confidence += 0.2
        }

        return confidence.coerceAtMost(0.85)
    }
}

/**
 * Result of linking a citation span to a reference.
 */
data class CitationLinkResult(
    val reference: Reference,
    val span: RawCitationSpan,
    val method: String,
    val confidence: Double
) {
    fun copy(
        method: String = this.method,
        confidence: Double = this.confidence
    ) = CitationLinkResult(reference, span, method, confidence)
}
