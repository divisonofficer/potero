package com.potero.service.narrative

import com.potero.domain.model.PdfPageText

/**
 * Enriches claims with actual evidence snippets from paper text.
 *
 * Problem: LLM generates claims but evidence snippets might be hallucinated.
 * Solution: Search actual paper text for relevant snippets that support each claim.
 *
 * This ensures narratives are grounded in the paper's actual content, not generic statements.
 */
object EvidenceEnricher {

    /**
     * Data class for text snippet with relevance score
     */
    data class TextSnippet(
        val text: String,
        val pageNum: Int,
        val similarity: Double
    )

    /**
     * Evidence snippet with source information
     */
    data class EvidenceSnippet(
        val text: String,
        val pageNum: Int,
        val confidence: Double
    )

    /**
     * Find relevant text snippets from paper pages that support a claim.
     *
     * Uses keyword overlap scoring (simple and fast, no LLM needed).
     *
     * @param claimStatement The claim to find evidence for
     * @param pages All pages of the paper
     * @param maxSnippets Maximum number of snippets to return
     * @return List of evidence snippets sorted by relevance
     */
    fun findEvidenceSnippets(
        claimStatement: String,
        pages: List<PdfPageText>,
        maxSnippets: Int = 2
    ): List<EvidenceSnippet> {
        // Extract keywords from claim
        val queryKeywords = extractKeywords(claimStatement)

        if (queryKeywords.isEmpty()) {
            return emptyList()
        }

        // Search all pages for relevant sentences
        val candidates = pages.flatMap { page ->
            val sentences = page.textContent.split(Regex("[.!?]\\s+"))

            sentences.mapNotNull { sentence ->
                if (sentence.trim().length < 20) return@mapNotNull null // Skip too short

                val sentenceKeywords = extractKeywords(sentence)
                val overlap = queryKeywords.intersect(sentenceKeywords).size

                if (overlap > 0) {
                    TextSnippet(
                        text = sentence.trim(),
                        pageNum = page.pageNum,
                        similarity = overlap.toDouble() / queryKeywords.size
                    )
                } else {
                    null
                }
            }
        }

        // Return top N most relevant snippets
        return candidates
            .filter { it.similarity > 0.3 } // At least 30% keyword overlap
            .sortedByDescending { it.similarity }
            .take(maxSnippets)
            .map {
                EvidenceSnippet(
                    text = it.text,
                    pageNum = it.pageNum,
                    confidence = it.similarity
                )
            }
    }

    /**
     * Extract meaningful keywords from text for matching.
     *
     * Strategy:
     * - Convert to lowercase
     * - Remove stop words
     * - Extract words longer than 3 characters
     * - Prioritize technical terms (capitalized, numbers, %)
     */
    private fun extractKeywords(text: String): Set<String> {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "should", "could", "may", "might", "must", "can", "this",
            "that", "these", "those", "it", "its", "which", "what", "where",
            "when", "who", "how", "why", "we", "our", "they", "their", "them"
        )

        // Split into words, filter and normalize
        val words = text
            .lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { word ->
                word.length > 3 &&  // At least 4 characters
                !stopWords.contains(word) &&
                word.any { it.isLetter() }  // Contains at least one letter
            }
            .toSet()

        // Also extract technical patterns
        val technicalTerms = extractTechnicalTerms(text)

        return words + technicalTerms
    }

    /**
     * Extract technical terms that might not follow normal word rules.
     * Examples: "ResNet-50", "3D", "HDR", "92.7%"
     */
    private fun extractTechnicalTerms(text: String): Set<String> {
        val terms = mutableSetOf<String>()

        // Percentage numbers (92.7%, 15%)
        Regex("\\d+\\.?\\d*%").findAll(text).forEach {
            terms.add(it.value.lowercase())
        }

        // Technical acronyms (2-5 uppercase letters)
        Regex("\\b[A-Z]{2,5}\\b").findAll(text).forEach {
            terms.add(it.value.lowercase())
        }

        // Hyphenated terms (ResNet-50, state-of-the-art)
        Regex("\\w+-\\w+").findAll(text).forEach {
            terms.add(it.value.lowercase())
        }

        // Numbers with units (300ms, 15GB, 92.7)
        Regex("\\d+\\.?\\d*\\s*(?:ms|GB|MB|s|m|kg|%)").findAll(text).forEach {
            terms.add(it.value.lowercase().replace(Regex("\\s+"), ""))
        }

        return terms
    }

    /**
     * Count keyword overlap between query and target text.
     */
    private fun countKeywordOverlap(text: String, keywords: Set<String>): Int {
        val textKeywords = extractKeywords(text)
        return textKeywords.intersect(keywords).size
    }
}
