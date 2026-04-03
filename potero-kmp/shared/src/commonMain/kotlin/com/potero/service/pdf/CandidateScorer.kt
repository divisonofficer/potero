package com.potero.service.pdf

/**
 * Scores a PdfCandidate given the enriched PaperIdentity.
 * Higher score = better candidate to try first.
 *
 * Scoring factors:
 * - Base confidence from provider
 * - +0.15 if directPdfHint (provider believes URL is a direct PDF)
 * - Source-specific adjustments
 */
class CandidateScorer {

    fun score(candidate: PdfCandidate, identity: PaperIdentity): Double {
        var score = candidate.confidence

        if (candidate.directPdfHint) score += 0.15

        // Source-level priors
        score += when (candidate.source) {
            PdfSource.ARXIV -> 0.10             // extremely reliable
            PdfSource.CVF -> 0.05               // institutional, reliable
            PdfSource.OPENALEX -> 0.00
            PdfSource.UNPAYWALL -> 0.00
            PdfSource.SEMANTIC_SCHOLAR -> -0.05 // currently prone to 403
            PdfSource.DIRECT_URL -> 0.05
            PdfSource.SCIHUB -> -0.10           // gray area, last resort
            PdfSource.DBLP -> -0.10             // ee often landing page
        }

        return score.coerceIn(0.0, 1.0)
    }
}
