package com.potero.service.metadata

import com.potero.domain.model.Author

/**
 * Utility for normalizing paper identifiers and metadata fields.
 * Used across resolvers and the PDF discovery pipeline for consistent comparison.
 */
object IdentifierNormalizer {

    private val DOI_PREFIXES = listOf("https://doi.org/", "http://doi.org/", "doi:", "DOI:")
    private val ARXIV_PREFIXES = listOf("arXiv:", "arxiv:", "https://arxiv.org/abs/", "https://arxiv.org/pdf/")
    private val ARXIV_VERSION_REGEX = Regex("""v\d+$""")
    private val ARXIV_ID_REGEX = Regex("""(\d{4}\.\d{4,5})(v\d+)?|([a-z-]+/\d{7})""")

    /**
     * Normalize a DOI to its bare form: "10.xxxx/yyyy"
     * Returns null if input is null or not a DOI.
     */
    fun normalizeDoi(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var cleaned = raw.trim()
        for (prefix in DOI_PREFIXES) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
                break
            }
        }
        // Must start with "10." to be a valid DOI
        return if (cleaned.startsWith("10.")) cleaned else null
    }

    /**
     * Normalize an arXiv ID to its canonical versionless form: "1234.56789"
     * Accepts "arXiv:1234.56789v2", "https://arxiv.org/abs/1234.56789", bare IDs.
     * Returns null if no valid arXiv ID found.
     */
    fun normalizeArxivId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var cleaned = raw.trim()
        for (prefix in ARXIV_PREFIXES) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
                break
            }
        }
        cleaned = cleaned.removeSuffix(".pdf")
        val match = ARXIV_ID_REGEX.find(cleaned) ?: return null
        // Strip version suffix (v1, v2, ...)
        return ARXIV_VERSION_REGEX.replace(match.value, "")
    }

    /**
     * Normalize a paper title for fuzzy comparison:
     * lowercase, trim, collapse whitespace, remove non-alphanumeric punctuation.
     */
    fun normalizeTitle(raw: String): String {
        return raw
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Extract the last name of the first author.
     * Handles "First Last", "Last, First" formats.
     */
    fun firstAuthorLastName(authors: List<Author>): String? {
        val first = authors.firstOrNull()?.name ?: return null
        return if (first.contains(",")) {
            first.substringBefore(",").trim()
        } else {
            first.split(" ").last().trim()
        }
    }

    /**
     * Calculate Jaccard similarity between two normalized titles.
     * Returns 0.0–1.0.
     */
    fun titleSimilarity(a: String, b: String): Double {
        val wordsA = normalizeTitle(a).split(" ").filter { it.length > 2 }.toSet()
        val wordsB = normalizeTitle(b).split(" ").filter { it.length > 2 }.toSet()
        if (wordsA.isEmpty() && wordsB.isEmpty()) return 1.0
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        return if (union > 0) intersection.toDouble() / union else 0.0
    }
}
