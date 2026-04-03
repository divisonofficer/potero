package com.potero.service.pdf.candidates

import com.potero.domain.model.Paper
import com.potero.service.metadata.IdentifierNormalizer
import com.potero.service.metadata.SemanticScholarResolver
import com.potero.service.pdf.PdfCandidate
import com.potero.service.pdf.PdfCandidateProvider
import com.potero.service.pdf.PdfSource
import com.potero.service.pdf.PaperIdentity

class SemanticScholarCandidateProvider(
    private val resolver: SemanticScholarResolver
) : PdfCandidateProvider {
    override val source = PdfSource.SEMANTIC_SCHOLAR

    override suspend fun findCandidates(identity: PaperIdentity, paper: Paper): List<PdfCandidate> {
        return try {
            val results = resolver.search(paper.title, limit = 3)
            if (results.isEmpty()) return emptyList()

            val candidates = mutableListOf<PdfCandidate>()
            for (result in results) {
                val pdfUrl = result.openAccessPdf?.url ?: continue
                val titleSim = IdentifierNormalizer.titleSimilarity(result.title, paper.title)
                val doiMatch = identity.normalizedDoi != null &&
                    IdentifierNormalizer.normalizeDoi(result.externalIds?.doi) == identity.normalizedDoi
                val arxivMatch = identity.normalizedArxivId != null &&
                    IdentifierNormalizer.normalizeArxivId(result.externalIds?.arxivId) == identity.normalizedArxivId

                val confidence = when {
                    doiMatch -> 0.90
                    arxivMatch -> 0.90
                    titleSim > 0.85 -> 0.70
                    titleSim > 0.60 -> 0.50
                    else -> 0.25
                }

                candidates.add(
                    PdfCandidate(
                        url = pdfUrl,
                        source = PdfSource.SEMANTIC_SCHOLAR,
                        confidence = confidence,
                        directPdfHint = false,
                        notes = listOf("title_sim=%.2f".format(titleSim), "paperId=${result.paperId}")
                    )
                )
            }
            candidates
        } catch (e: Exception) {
            println("[SemanticScholarCandidateProvider] Failed: ${e.message}")
            emptyList()
        }
    }
}
