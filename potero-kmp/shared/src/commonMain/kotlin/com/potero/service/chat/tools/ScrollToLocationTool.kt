package com.potero.service.chat.tools

/**
 * Tool for scrolling PDF viewer to a specific location.
 *
 * Can scroll by:
 * - Page number
 * - Section name (e.g., "methodology", "conclusion")
 * - Keyword (searches for keyword and scrolls to first occurrence)
 *
 * Returns the target page number and optional y-position for the frontend.
 */
class ScrollToLocationTool : ChatTool {
    override val definition = ToolDefinition(
        name = "scroll_to_location",
        description = "Scroll the PDF viewer to a specific page, section, or keyword location",
        parameters = mapOf(
            "page" to ToolParameter(
                type = "number",
                description = "Page number to scroll to (1-indexed)",
                required = false
            ),
            "section" to ToolParameter(
                type = "string",
                description = "Section name to find (e.g., 'introduction', 'methodology', 'results', 'conclusion', 'references')",
                required = false
            ),
            "keyword" to ToolParameter(
                type = "string",
                description = "Keyword to search for and scroll to first occurrence",
                required = false
            )
        ),
        requiresPaper = true
    )

    override suspend fun execute(
        arguments: Map<String, Any>,
        context: ToolExecutionContext
    ): ToolResult {
        val page = arguments["page"] as? Int
        val section = arguments["section"] as? String
        val keyword = arguments["keyword"] as? String

        // Validate that at least one parameter is provided
        if (page == null && section == null && keyword == null) {
            return ToolResult.failure("At least one of 'page', 'section', or 'keyword' must be provided")
        }

        // Priority: page > section > keyword
        return when {
            page != null -> {
                // Scroll to specific page
                if (page < 1) {
                    ToolResult.failure("Page number must be >= 1")
                } else {
                    ToolResult.success(
                        data = mapOf(
                            "page" to page,
                            "type" to "page"
                        ),
                        metadata = mapOf(
                            "message" to "Scrolling to page $page"
                        )
                    )
                }
            }
            section != null -> {
                // Scroll to section (approximate page will be determined by frontend)
                val normalizedSection = section.lowercase().trim()
                ToolResult.success(
                    data = mapOf(
                        "section" to normalizedSection,
                        "type" to "section"
                    ),
                    metadata = mapOf(
                        "message" to "Scrolling to section: $normalizedSection"
                    )
                )
            }
            keyword != null -> {
                // Scroll to keyword (frontend will search and scroll)
                ToolResult.success(
                    data = mapOf(
                        "keyword" to keyword,
                        "type" to "keyword"
                    ),
                    metadata = mapOf(
                        "message" to "Scrolling to keyword: $keyword"
                    )
                )
            }
            else -> {
                ToolResult.failure("No valid scroll target provided")
            }
        }
    }
}
