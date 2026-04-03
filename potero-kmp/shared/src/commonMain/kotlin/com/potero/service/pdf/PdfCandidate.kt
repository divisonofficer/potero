package com.potero.service.pdf

import com.potero.domain.model.Paper

enum class PdfSource(val label: String) {
    ARXIV("arxiv"),
    OPENALEX("openalex"),
    UNPAYWALL("unpaywall"),
    CVF("cvf"),
    SEMANTIC_SCHOLAR("semantic_scholar"),
    DIRECT_URL("direct_url"),
    SCIHUB("scihub"),
    DBLP("dblp")
}

/**
 * A candidate PDF URL from one source, with associated metadata for scoring.
 */
data class PdfCandidate(
    val url: String,
    val source: PdfSource,
    /** Initial confidence assigned by the provider (0.0–1.0) */
    val confidence: Double,
    /** True when the provider believes this URL is a direct PDF (not a landing page) */
    val directPdfHint: Boolean = false,
    /** Optional notes for debugging / UI display */
    val notes: List<String> = emptyList()
)

/**
 * Interface for components that supply PDF URL candidates from a single source.
 * Implementations must be non-blocking and handle their own errors internally,
 * returning emptyList() on failure rather than throwing.
 */
interface PdfCandidateProvider {
    val source: PdfSource
    suspend fun findCandidates(identity: PaperIdentity, paper: Paper): List<PdfCandidate>
}
