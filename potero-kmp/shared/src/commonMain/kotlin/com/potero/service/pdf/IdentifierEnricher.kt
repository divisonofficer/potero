package com.potero.service.pdf

import com.potero.domain.model.Paper

/**
 * Enriches a Paper with normalized and remote-resolved identifiers,
 * producing a PaperIdentity used throughout the PDF discovery pipeline.
 */
interface IdentifierEnricher {
    suspend fun enrich(paper: Paper): PaperIdentity
}
