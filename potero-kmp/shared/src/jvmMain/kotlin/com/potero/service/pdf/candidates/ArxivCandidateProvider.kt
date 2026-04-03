package com.potero.service.pdf.candidates

import com.potero.domain.model.Paper
import com.potero.service.pdf.PdfCandidate
import com.potero.service.pdf.PdfCandidateProvider
import com.potero.service.pdf.PdfSource
import com.potero.service.pdf.PaperIdentity

class ArxivCandidateProvider : PdfCandidateProvider {
    override val source = PdfSource.ARXIV

    override suspend fun findCandidates(identity: PaperIdentity, paper: Paper): List<PdfCandidate> {
        val arxivId = identity.normalizedArxivId ?: return emptyList()
        val url = "https://arxiv.org/pdf/$arxivId.pdf"
        return listOf(
            PdfCandidate(
                url = url,
                source = PdfSource.ARXIV,
                confidence = 0.95,
                directPdfHint = true,
                notes = listOf("arXiv canonical PDF for $arxivId")
            )
        )
    }
}
