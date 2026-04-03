package com.potero.service.pdf.candidates

import com.potero.domain.model.Paper
import com.potero.service.metadata.UnpaywallResolver
import com.potero.service.pdf.PdfCandidate
import com.potero.service.pdf.PdfCandidateProvider
import com.potero.service.pdf.PdfSource
import com.potero.service.pdf.PaperIdentity

class UnpaywallCandidateProvider(
    private val resolver: UnpaywallResolver
) : PdfCandidateProvider {
    override val source = PdfSource.UNPAYWALL

    override suspend fun findCandidates(identity: PaperIdentity, paper: Paper): List<PdfCandidate> {
        val doi = identity.normalizedDoi ?: return emptyList()
        return try {
            val url = resolver.findOpenAccessPdf(doi) ?: return emptyList()
            listOf(
                PdfCandidate(
                    url = url,
                    source = PdfSource.UNPAYWALL,
                    confidence = 0.80,
                    directPdfHint = false,
                    notes = listOf("doi=$doi")
                )
            )
        } catch (e: Exception) {
            println("[UnpaywallCandidateProvider] Failed: ${e.message}")
            emptyList()
        }
    }
}
