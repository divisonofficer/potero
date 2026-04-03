package com.potero.service.pdf.candidates

import com.potero.domain.model.Paper
import com.potero.service.metadata.IdentifierNormalizer
import com.potero.service.metadata.OpenAlexResolver
import com.potero.service.pdf.PdfCandidate
import com.potero.service.pdf.PdfCandidateProvider
import com.potero.service.pdf.PdfSource
import com.potero.service.pdf.PaperIdentity

class OpenAlexCandidateProvider(
    private val resolver: OpenAlexResolver
) : PdfCandidateProvider {
    override val source = PdfSource.OPENALEX

    override suspend fun findCandidates(identity: PaperIdentity, paper: Paper): List<PdfCandidate> {
        return try {
            val query = identity.normalizedDoi ?: paper.title
            val metadata = resolver.resolve(query).getOrNull() ?: return emptyList()
            val pdfUrl = metadata.pdfUrl ?: return emptyList()

            val titleSim = IdentifierNormalizer.titleSimilarity(metadata.title, paper.title)
            val confidence = when {
                identity.normalizedDoi != null -> 0.90  // DOI exact match
                titleSim > 0.85 -> 0.75
                titleSim > 0.60 -> 0.55
                else -> 0.30
            }

            listOf(
                PdfCandidate(
                    url = pdfUrl,
                    source = PdfSource.OPENALEX,
                    confidence = confidence,
                    directPdfHint = false,
                    notes = listOf("title_sim=%.2f".format(titleSim))
                )
            )
        } catch (e: Exception) {
            println("[OpenAlexCandidateProvider] Failed: ${e.message}")
            emptyList()
        }
    }
}
