package com.potero.service.relatedwork

import com.potero.domain.model.*
import com.potero.domain.repository.ComparisonTableRepository
import com.potero.domain.repository.PaperRepository
import com.potero.service.llm.LLMService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Service for generating and managing comparison tables
 */
class ComparisonService(
    private val comparisonRepository: ComparisonTableRepository,
    private val paperRepository: PaperRepository,
    private val metricExtractionService: MetricExtractionService,
    private val llmService: LLMService
) {

    /**
     * Generate a comparison table with LLM-suggested columns
     */
    suspend fun generateComparisonTable(
        sourcePaperId: String,
        relatedPaperIds: List<String>,
        request: ComparisonTableRequest
    ): Result<ComparisonTableWithData> = runCatching {

        val now = Clock.System.now()
        val tableId = UUID.randomUUID().toString()

        // Step 1: Suggest columns if not provided
        val columns = if (request.columns.isEmpty()) {
            suggestColumns(sourcePaperId, relatedPaperIds).getOrThrow()
        } else {
            request.columns
        }

        // Step 2: Create table structure
        val table = ComparisonTable(
            id = tableId,
            sourcePaperId = sourcePaperId,
            title = request.title,
            description = request.description,
            columns = emptyList(), // Will be populated separately
            generationMethod = if (request.columns.isEmpty()) GenerationMethod.LLM_SUGGESTED else GenerationMethod.MANUAL,
            createdAt = now,
            updatedAt = now
        )

        // Save table to database
        comparisonRepository.insertTable(table).getOrThrow()

        // Step 3: Create and save columns
        val comparisonColumns = columns.mapIndexed { index, columnDef ->
            val columnId = UUID.randomUUID().toString()
            ComparisonColumn(
                id = columnId,
                tableId = tableId,
                name = columnDef.name,
                description = columnDef.description,
                dataType = columnDef.dataType,
                order = index,
                createdAt = now
            )
        }

        comparisonColumns.forEach { column ->
            comparisonRepository.insertColumn(column).getOrThrow()
        }

        // Step 4: Extract metrics for all papers (parallel)
        val allPaperIds = listOf(sourcePaperId) + relatedPaperIds
        val entries = extractMetricsForAllPapers(tableId, allPaperIds, comparisonColumns)

        // Step 5: Generate narrative summary (if requested)
        val narrative = if (request.generateNarrative) {
            generateNarrative(tableId, allPaperIds, comparisonColumns, entries).getOrNull()
        } else null

        // Step 6: Load papers for response
        val papers = allPaperIds.mapNotNull { paperId ->
            paperRepository.getById(paperId).getOrNull()
        }

        // Group entries by paperId -> columnId
        val entriesMap = entries.groupBy { it.paperId }
            .mapValues { (_, paperEntries) ->
                paperEntries.associateBy { it.columnId }
            }

        ComparisonTableWithData(
            table = table.copy(columns = comparisonColumns),
            entries = entriesMap,
            papers = papers,
            narrative = narrative
        )
    }

    /**
     * Use LLM to suggest domain-specific comparison columns
     */
    private suspend fun suggestColumns(
        sourcePaperId: String,
        relatedPaperIds: List<String>
    ): Result<List<ColumnDefinition>> = runCatching {

        val sourcePaper = paperRepository.getById(sourcePaperId).getOrNull()
            ?: throw IllegalArgumentException("Source paper not found")

        // Get a sample related paper for context
        val sampleRelatedPaper = if (relatedPaperIds.isNotEmpty()) {
            paperRepository.getById(relatedPaperIds.first()).getOrNull()
        } else null

        val prompt = buildColumnSuggestionPrompt(sourcePaper, sampleRelatedPaper)
        val response = llmService.chat(prompt).getOrThrow()

        // Parse LLM response into column definitions
        parseColumnSuggestions(response)
    }

    /**
     * Build LLM prompt for column suggestion
     */
    private fun buildColumnSuggestionPrompt(sourcePaper: Paper, samplePaper: Paper?): String {
        val field = sourcePaper.conference ?: "computer science"

        return """
You are a research assistant analyzing academic papers in the field of $field.

Source paper:
Title: ${sourcePaper.title}
Abstract: ${sourcePaper.abstract ?: "Not available"}

${samplePaper?.let {
            """
Sample related paper:
Title: ${it.title}
Abstract: ${it.abstract ?: "Not available"}
"""
        } ?: ""}

Suggest 5-7 key metrics/characteristics that would be valuable for comparing these papers with related work.

Focus on:
- Research methodology (e.g., "Approach", "Method", "Algorithm")
- Evaluation metrics (e.g., "Accuracy", "Performance", "Dataset")
- Technical contributions (e.g., "Key Innovation", "Novelty")
- Context (e.g., "Year", "Venue", "Citation Count")

Return ONLY a JSON array with this exact structure (no markdown code blocks):
[
  {
    "name": "Dataset",
    "description": "Datasets used for evaluation",
    "dataType": "LIST"
  },
  {
    "name": "Main Contribution",
    "description": "Primary technical innovation",
    "dataType": "TEXT"
  }
]

Valid dataType values: TEXT, NUMBER, BOOLEAN, LIST, DATE, CITATION_COUNT

Your JSON response:
        """.trimIndent()
    }

    /**
     * Parse LLM column suggestion response
     */
    private fun parseColumnSuggestions(response: String): List<ColumnDefinition> {
        return try {
            // Remove markdown code blocks if present
            val jsonText = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val suggestions = Json.decodeFromString<List<ColumnSuggestionDto>>(jsonText)

            suggestions.map { dto ->
                ColumnDefinition(
                    name = dto.name,
                    description = dto.description,
                    dataType = ColumnDataType.valueOf(dto.dataType.uppercase())
                )
            }
        } catch (e: Exception) {
            println("[ComparisonService] Failed to parse column suggestions: ${e.message}")
            println("[ComparisonService] Response was: $response")

            // Fallback: default columns
            listOf(
                ColumnDefinition("Method", "Research method or approach", ColumnDataType.TEXT),
                ColumnDefinition("Dataset", "Datasets used for evaluation", ColumnDataType.LIST),
                ColumnDefinition("Key Result", "Main experimental result", ColumnDataType.TEXT),
                ColumnDefinition("Year", "Publication year", ColumnDataType.DATE),
                ColumnDefinition("Citations", "Citation count", ColumnDataType.CITATION_COUNT)
            )
        }
    }

    /**
     * Extract metrics for all papers in parallel
     */
    private suspend fun extractMetricsForAllPapers(
        tableId: String,
        paperIds: List<String>,
        columns: List<ComparisonColumn>
    ): List<ComparisonEntry> = coroutineScope {

        val now = Clock.System.now()

        // Create async jobs for each (paper, column) pair
        val jobs = paperIds.flatMap { paperId ->
            columns.map { column ->
                async {
                    val extracted = metricExtractionService.extractMetric(
                        paperId = paperId,
                        metricName = column.name,
                        metricDescription = column.description,
                        dataType = column.dataType
                    ).getOrElse {
                        ExtractedMetric(value = "N/A", confidence = 0.0, source = "error")
                    }

                    val entry = ComparisonEntry(
                        id = UUID.randomUUID().toString(),
                        tableId = tableId,
                        paperId = paperId,
                        columnId = column.id,
                        value = extracted.value,
                        confidence = extracted.confidence,
                        extractionSource = extracted.source,
                        createdAt = now,
                        updatedAt = now
                    )

                    // Save to database
                    comparisonRepository.insertEntry(entry).getOrThrow()

                    entry
                }
            }
        }

        // Wait for all extractions to complete
        jobs.awaitAll()
    }

    /**
     * Generate LLM narrative summary of comparison table
     */
    private suspend fun generateNarrative(
        tableId: String,
        paperIds: List<String>,
        columns: List<ComparisonColumn>,
        entries: List<ComparisonEntry>
    ): Result<ComparisonNarrative> = runCatching {

        // Load papers
        val papers = paperIds.mapNotNull { paperId ->
            paperRepository.getById(paperId).getOrNull()
        }

        // Build narrative prompt
        val prompt = buildNarrativePrompt(papers, columns, entries)
        val response = llmService.chat(prompt).getOrThrow()

        // Extract key insights (first few sentences or bullet points)
        val insights = extractKeyInsights(response)

        val now = Clock.System.now()
        val narrative = ComparisonNarrative(
            id = UUID.randomUUID().toString(),
            tableId = tableId,
            content = response,
            keyInsights = insights,
            createdAt = now,
            updatedAt = now
        )

        // Save to database
        comparisonRepository.insertNarrative(narrative).getOrThrow()

        narrative
    }

    /**
     * Build prompt for narrative generation
     */
    private fun buildNarrativePrompt(
        papers: List<Paper>,
        columns: List<ComparisonColumn>,
        entries: List<ComparisonEntry>
    ): String {
        // Format entries as a table
        val tableText = buildString {
            appendLine("Comparison Table:")
            appendLine()

            // Header row
            append("Paper")
            columns.forEach { column ->
                append(" | ${column.name}")
            }
            appendLine()

            // Separator row
            append("---")
            columns.forEach { append(" | ---") }
            appendLine()

            // Data rows
            papers.forEach { paper ->
                append(paper.title.take(50))
                columns.forEach { column ->
                    val entry = entries.find { it.paperId == paper.id && it.columnId == column.id }
                    append(" | ${entry?.value ?: "N/A"}")
                }
                appendLine()
            }
        }

        return """
You are a research assistant analyzing a comparison of academic papers.

$tableText

Generate a concise narrative summary (2-3 paragraphs) that:
1. Highlights the main similarities and differences between the papers
2. Identifies any trends or patterns in the data
3. Points out notable gaps or unique contributions

Write in a clear, academic style suitable for a literature review.

Your summary:
        """.trimIndent()
    }

    /**
     * Extract key insights from narrative (first 3-5 sentences)
     */
    private fun extractKeyInsights(narrative: String): List<String> {
        return narrative
            .split(Regex("[.!?]\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 20 }
            .take(5)
    }

    /**
     * Get all comparison tables for a source paper
     */
    suspend fun getTablesBySourcePaper(sourcePaperId: String): Result<List<ComparisonTable>> {
        return comparisonRepository.getTablesBySourcePaper(sourcePaperId)
    }

    /**
     * Get a specific comparison table with all data
     */
    suspend fun getTableWithData(tableId: String): Result<ComparisonTableWithData?> {
        return comparisonRepository.getTableWithData(tableId)
    }

    /**
     * Delete a comparison table
     */
    suspend fun deleteTable(tableId: String): Result<Unit> {
        return comparisonRepository.deleteTable(tableId)
    }
}

// DTOs
@Serializable
data class ComparisonTableRequest(
    val title: String,
    val description: String? = null,
    val columns: List<ColumnDefinition> = emptyList(),
    val generateNarrative: Boolean = true
)

@Serializable
data class ColumnDefinition(
    val name: String,
    val description: String?,
    val dataType: ColumnDataType
)

@Serializable
private data class ColumnSuggestionDto(
    val name: String,
    val description: String,
    val dataType: String
)
