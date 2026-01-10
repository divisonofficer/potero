package com.potero.service.note

/**
 * Result of parsing a single [[...]] link from markdown content
 */
data class ParsedLink(
    val text: String,      // Content inside [[...]]
    val position: Int,     // Character offset in markdown
    val fullMatch: String  // Full [[...]] string
)

/**
 * Parser for wiki-style [[...]] links in markdown content.
 * Extracts note references and paper references.
 */
object NoteLinkParser {
    // Regex to match [[...]] patterns
    private val LINK_REGEX = """\[\[([^\]]+)\]\]""".toRegex()

    /**
     * Parse all [[...]] links from markdown content
     *
     * @param content Markdown content to parse
     * @return List of parsed links with positions
     */
    fun parse(content: String): List<ParsedLink> {
        val links = mutableListOf<ParsedLink>()

        LINK_REGEX.findAll(content).forEach { match ->
            links.add(
                ParsedLink(
                    text = match.groupValues[1].trim(),
                    position = match.range.first,
                    fullMatch = match.value
                )
            )
        }

        return links
    }

    /**
     * Check if link text is a paper reference ([[paper:id]])
     *
     * @param linkText Text inside [[...]]
     * @return true if this is a paper reference
     */
    fun isPaperLink(linkText: String): Boolean {
        return linkText.startsWith("paper:")
    }

    /**
     * Extract paper ID from paper link
     *
     * @param linkText Text inside [[...]]
     * @return Paper ID or null if not a paper link
     */
    fun extractPaperId(linkText: String): String? {
        return if (isPaperLink(linkText)) {
            linkText.removePrefix("paper:")
        } else null
    }
}
