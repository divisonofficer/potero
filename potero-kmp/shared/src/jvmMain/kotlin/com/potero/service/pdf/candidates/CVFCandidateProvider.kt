package com.potero.service.pdf.candidates

import com.potero.domain.model.Paper
import com.potero.service.metadata.CVFOpenAccessResolver
import com.potero.service.pdf.PdfCandidate
import com.potero.service.pdf.PdfCandidateProvider
import com.potero.service.pdf.PdfSource
import com.potero.service.pdf.PaperIdentity

class CVFCandidateProvider(
    private val resolver: CVFOpenAccessResolver
) : PdfCandidateProvider {
    override val source = PdfSource.CVF

    override suspend fun findCandidates(identity: PaperIdentity, paper: Paper): List<PdfCandidate> {
        // Use enriched venueHint if conference field is blank
        val venue = paper.conference?.takeIf { it.isNotBlank() }
            ?: identity.venueHint
            ?: return emptyList()

        if (!resolver.isCVFConference(venue)) return emptyList()

        return try {
            val url = resolver.findPdf(
                title = paper.title,
                year = paper.year ?: identity.year,
                venue = venue,
                doi = identity.normalizedDoi,
                authors = paper.authors.map { it.name }
            ) ?: return emptyList()

            listOf(
                PdfCandidate(
                    url = url,
                    source = PdfSource.CVF,
                    confidence = 0.85,
                    directPdfHint = true,
                    notes = listOf("venue=$venue")
                )
            )
        } catch (e: Exception) {
            println("[CVFCandidateProvider] Failed: ${e.message}")
            emptyList()
        }
    }
}
