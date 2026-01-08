package com.potero.service.pdf

import com.potero.domain.model.Reference

/**
 * Links citation spans to reference entries.
 *
 * Linking strategies (in priority order):
 * 1. Annotation destination: GoTo links pointing to reference page
 * 2. Numeric matching: [n] -> Reference entry #n (deterministic)
 * 3. Author-year fuzzy: (Smith, 2024) -> Reference with matching author/year
 */
class CitationLinker {

    /**
     * Link citation spans to references.
     *
     * @param spans Raw citation spans from CitationExtractor
     * @param references Reference entries from the paper
     * @param referencesStartPage First page of references section
     * @return List of citation links with confidence scores
     */
    fun link(
        spans: List<RawCitationSpan>,
        references: List<Reference>,
        referencesStartPage: Int?
    ): List<CitationLinkResult> {
        if (references.isEmpty()) return emptyList()

        return spans.flatMap { span ->
            linkSpan(span, references, referencesStartPage)
        }
    }

    /**
     * Link a single citation span to references.
     */
    private fun linkSpan(
        span: RawCitationSpan,
        references: List<Reference>,
        referencesStartPage: Int?
    ): List<CitationLinkResult> {
        return when {
            // Priority 1: Annotation with destination
            span.provenance == "annotation" && span.destPage != null -> {
                linkByAnnotationDestination(span, references, referencesStartPage)
            }
            // Priority 2: Numeric citation
            span.style == "numeric" -> {
                linkByNumericMatch(span, references)
            }
            // Priority 3: Author-year citation
            span.style == "author_year" -> {
                linkByAuthorYearFuzzy(span, references)
            }
            else -> emptyList()
        }
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
