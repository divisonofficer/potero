package com.potero.service.pdf

import com.potero.domain.model.Paper
import com.potero.service.metadata.IdentifierNormalizer
import com.potero.service.metadata.OpenAlexResolver
import com.potero.service.metadata.SemanticScholarResolver

/**
 * Enriches a Paper identity by:
 * 1. Normalizing existing doi/arxivId from the DB
 * 2. If doi is missing, querying OpenAlex by title to recover it
 * 3. If arxivId is missing, checking if OpenAlex/Semantic Scholar return one
 * 4. Inferring CVF conference from title keywords if venue field is empty
 */
class DefaultIdentifierEnricher(
    private val openAlexResolver: OpenAlexResolver? = null,
    private val semanticScholarResolver: SemanticScholarResolver? = null
) : IdentifierEnricher {

    private val CVF_KEYWORDS = mapOf(
        "CVPR" to listOf("cvpr", "computer vision and pattern recognition"),
        "ICCV" to listOf("iccv", "international conference on computer vision"),
        "WACV" to listOf("wacv", "winter conference on applications of computer vision"),
        "ECCV" to listOf("eccv", "european conference on computer vision")
    )

    override suspend fun enrich(paper: Paper): PaperIdentity {
        // Start with what we have in the DB
        var identity = PaperIdentity.fromPaper(paper)

        // Try to recover missing DOI via OpenAlex title search
        if (identity.normalizedDoi == null && openAlexResolver != null) {
            try {
                val query = buildSearchQuery(paper)
                val metadata = openAlexResolver.resolve(query).getOrNull()
                if (metadata != null) {
                    val recoveredDoi = IdentifierNormalizer.normalizeDoi(metadata.doi)
                    val recoveredArxivId = IdentifierNormalizer.normalizeArxivId(metadata.arxivId)
                        ?: identity.normalizedArxivId
                    identity = identity.copy(
                        normalizedDoi = recoveredDoi ?: identity.normalizedDoi,
                        normalizedArxivId = recoveredArxivId
                    )
                    println("[IdentifierEnricher] OpenAlex recovered doi=${recoveredDoi}, arxivId=${recoveredArxivId}")
                }
            } catch (e: Exception) {
                println("[IdentifierEnricher] OpenAlex enrichment failed: ${e.message}")
            }
        }

        // If still no arxivId and SS is available, try to recover it
        if (identity.normalizedArxivId == null && semanticScholarResolver != null) {
            try {
                val results = semanticScholarResolver.search(paper.title, limit = 3)
                val best = results.firstOrNull { result ->
                    IdentifierNormalizer.titleSimilarity(result.title, paper.title) > 0.80
                }
                val recoveredArxivId = IdentifierNormalizer.normalizeArxivId(
                    best?.externalIds?.arxivId
                )
                if (recoveredArxivId != null) {
                    identity = identity.copy(normalizedArxivId = recoveredArxivId)
                    println("[IdentifierEnricher] SemanticScholar recovered arxivId=$recoveredArxivId")
                }
            } catch (e: Exception) {
                println("[IdentifierEnricher] SemanticScholar enrichment failed: ${e.message}")
            }
        }

        // Infer CVF conference from conference field or title when venue is blank
        if (!identity.isCvfConference) {
            val haystack = listOfNotNull(paper.conference, paper.title).joinToString(" ").lowercase()
            val inferredVenue = CVF_KEYWORDS.entries.firstOrNull { (_, keywords) ->
                keywords.any { haystack.contains(it) }
            }?.key
            if (inferredVenue != null) {
                identity = identity.copy(venueHint = inferredVenue, isCvfConference = true)
                println("[IdentifierEnricher] Inferred CVF venue: $inferredVenue")
            }
        }

        return identity
    }

    private fun buildSearchQuery(paper: Paper): String {
        // For OpenAlex: prefer DOI if partially present, else use title
        return paper.doi ?: paper.title
    }
}
