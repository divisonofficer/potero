package com.potero.service.pdf

import com.potero.domain.model.Paper
import com.potero.service.metadata.IdentifierNormalizer

/**
 * Normalized, enriched identity of a paper used throughout the PDF discovery pipeline.
 * Built by IdentifierEnricher from a raw Paper domain object.
 */
data class PaperIdentity(
    val paperId: String,
    val normalizedTitle: String,
    val normalizedDoi: String? = null,
    val normalizedArxivId: String? = null,
    val year: Int? = null,
    val firstAuthorLastName: String? = null,
    val venueHint: String? = null,   // e.g. "CVPR", "ICCV", "NeurIPS"
    val isCvfConference: Boolean = false
) {
    companion object {
        private val CVF_CONFERENCES = setOf("CVPR", "ICCV", "WACV", "ECCV")

        /**
         * Build a PaperIdentity directly from a Paper without remote enrichment.
         * Used as a fast baseline; DefaultIdentifierEnricher adds remote lookups.
         */
        fun fromPaper(paper: Paper): PaperIdentity {
            val normalizedDoi = IdentifierNormalizer.normalizeDoi(paper.doi)
            val normalizedArxivId = IdentifierNormalizer.normalizeArxivId(paper.arxivId)
            val venueHint = CVF_CONFERENCES.find {
                paper.conference?.contains(it, ignoreCase = true) == true
            }
            return PaperIdentity(
                paperId = paper.id,
                normalizedTitle = IdentifierNormalizer.normalizeTitle(paper.title),
                normalizedDoi = normalizedDoi,
                normalizedArxivId = normalizedArxivId,
                year = paper.year,
                firstAuthorLastName = IdentifierNormalizer.firstAuthorLastName(paper.authors),
                venueHint = venueHint,
                isCvfConference = venueHint != null
            )
        }
    }
}
