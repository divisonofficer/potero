package com.potero.service.narrative

/**
 * Extracts representative sections from academic papers for narrative generation.
 *
 * Problem: Using first 8000 chars only captures Introduction, missing core Method/Results.
 * Solution: Section-based sampling - extract key portions from Abstract, Intro, Method, Results, Conclusion.
 *
 * This dramatically improves Stage 1 (Structural Understanding) accuracy by ensuring
 * the LLM sees the paper's actual contributions and findings.
 */
object SectionSampler {

    private const val MAX_SAMPLED_LENGTH = 15000 // Increased from 8000

    /**
     * Extract representative sections from paper pages.
     *
     * Strategy:
     * - Abstract: Full (usually 1000-1500 chars)
     * - Introduction: First 1500 chars
     * - Method: 2-3 chunks of 2000 chars each
     * - Results: 2-3 chunks of 2000 chars each
     * - Conclusion: First 1000 chars
     *
     * Total: ~10,000-15,000 chars with actual paper content
     */
    fun extractRepresentativeSections(fullText: String): String {
        if (fullText.length <= MAX_SAMPLED_LENGTH) {
            // Short paper, use all
            return fullText
        }

        // Detect section boundaries
        val sections = detectSections(fullText)

        // Build sampled text from detected sections
        val sampledText = buildString {
            // Abstract (full)
            sections["abstract"]?.let { section ->
                append("## ABSTRACT\n")
                append(section)
                append("\n\n")
            }

            // Introduction (partial)
            sections["introduction"]?.let { section ->
                append("## INTRODUCTION\n")
                append(section.take(1500))
                if (section.length > 1500) append("\n[...]\n")
                append("\n\n")
            }

            // Method (key chunks)
            sections["method"]?.let { section ->
                append("## METHOD\n")
                val chunks = splitIntoChunks(section, chunkSize = 2000)
                chunks.take(3).forEachIndexed { idx, chunk ->
                    if (idx > 0) append("[...]\n")
                    append(chunk)
                    append("\n")
                }
                if (chunks.size > 3) append("[...]\n")
                append("\n")
            }

            // Results (key chunks)
            sections["results"]?.let { section ->
                append("## RESULTS\n")
                val chunks = splitIntoChunks(section, chunkSize = 2000)
                chunks.take(3).forEachIndexed { idx, chunk ->
                    if (idx > 0) append("[...]\n")
                    append(chunk)
                    append("\n")
                }
                if (chunks.size > 3) append("[...]\n")
                append("\n")
            }

            // Conclusion (partial)
            sections["conclusion"]?.let { section ->
                append("## CONCLUSION\n")
                append(section.take(1000))
                if (section.length > 1000) append("\n[...]\n")
                append("\n\n")
            }
        }

        // Fallback if no sections detected
        if (sampledText.length < 1000) {
            println("[SectionSampler] Warning: Section detection failed, using first ${MAX_SAMPLED_LENGTH} chars")
            return fullText.take(MAX_SAMPLED_LENGTH)
        }

        return sampledText.take(MAX_SAMPLED_LENGTH)
    }

    /**
     * Detect section boundaries in paper text.
     *
     * Uses heuristics to find common academic paper sections:
     * - Numbered sections (1. Introduction, 2. Method, ...)
     * - ALL CAPS headers (ABSTRACT, INTRODUCTION, ...)
     * - Title case headers with newlines
     */
    private fun detectSections(text: String): Map<String, String> {
        val sectionMarkers = mapOf(
            "abstract" to listOf(
                "Abstract", "ABSTRACT",
                "A B S T R A C T"  // Sometimes spaced
            ),
            "introduction" to listOf(
                "Introduction", "INTRODUCTION",
                "1. Introduction", "1 Introduction",
                "I. Introduction", "1. INTRODUCTION"
            ),
            "method" to listOf(
                "Method", "Methodology", "Approach", "Methods",
                "METHOD", "METHODOLOGY", "APPROACH",
                "2. Method", "3. Method", "3. Methodology",
                "II. Method", "III. Methodology"
            ),
            "results" to listOf(
                "Results", "Experiments", "Evaluation", "Experimental Results",
                "RESULTS", "EXPERIMENTS", "EVALUATION",
                "4. Results", "5. Experiments", "4. Experimental Results",
                "IV. Results", "V. Experiments"
            ),
            "conclusion" to listOf(
                "Conclusion", "Discussion", "Conclusions", "Discussion and Conclusion",
                "CONCLUSION", "DISCUSSION",
                "6. Conclusion", "7. Discussion", "5. Conclusion",
                "VI. Conclusion", "VII. Discussion"
            )
        )

        val detected = mutableMapOf<String, String>()

        for ((sectionName, markers) in sectionMarkers) {
            for (marker in markers) {
                // Try to find section header
                val regex = Regex(
                    "^\\s*${Regex.escape(marker)}\\s*$",
                    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
                )

                val match = regex.find(text)
                if (match != null) {
                    val startIdx = match.range.last + 1

                    // Find end of this section (next section start or end of text)
                    val nextSectionStart = findNextSectionStart(text, startIdx, sectionMarkers.values.flatten())
                    val endIdx = nextSectionStart ?: text.length

                    // Extract section content
                    val sectionContent = text.substring(startIdx, endIdx.coerceAtMost(text.length)).trim()

                    if (sectionContent.isNotEmpty()) {
                        detected[sectionName] = sectionContent
                        break // Found this section, move to next
                    }
                }
            }
        }

        return detected
    }

    /**
     * Find the start position of the next section after currentPos.
     */
    private fun findNextSectionStart(
        text: String,
        currentPos: Int,
        allMarkers: List<String>
    ): Int? {
        var closestPos: Int? = null

        for (marker in allMarkers) {
            val regex = Regex(
                "^\\s*${Regex.escape(marker)}\\s*$",
                setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
            )

            val match = regex.find(text, currentPos)
            if (match != null) {
                val matchPos = match.range.first
                if (closestPos == null || matchPos < closestPos) {
                    closestPos = matchPos
                }
            }
        }

        return closestPos
    }

    /**
     * Split section text into chunks of approximately chunkSize.
     * Tries to split on paragraph boundaries when possible.
     */
    private fun splitIntoChunks(text: String, chunkSize: Int): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var currentPos = 0

        while (currentPos < text.length) {
            // Try to find paragraph break near chunk boundary
            val idealEnd = (currentPos + chunkSize).coerceAtMost(text.length)

            // Look for double newline (paragraph break) within ±200 chars of ideal end
            val searchStart = (idealEnd - 200).coerceAtLeast(currentPos)
            val searchEnd = (idealEnd + 200).coerceAtMost(text.length)

            var actualEnd = idealEnd

            // Find nearest paragraph break
            val paragraphBreak = text.indexOf("\n\n", searchStart)
            if (paragraphBreak in searchStart until searchEnd) {
                actualEnd = paragraphBreak + 2
            }

            // Extract chunk
            val chunk = text.substring(currentPos, actualEnd).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }

            currentPos = actualEnd
        }

        return chunks
    }

    /**
     * Fallback: Just use first N chars (current behavior)
     */
    fun extractFirstChars(text: String, maxChars: Int = 8000): String {
        return text.take(maxChars)
    }
}
