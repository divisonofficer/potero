package com.potero.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Relationship between a source paper and a related paper.
 * Stores metadata about why papers are related and their relevance.
 */
@Serializable
data class RelatedWork(
    val id: String,
    val sourcePaperId: String,
    val relatedPaperId: String,
    val relationshipType: RelationshipType,
    val relevanceScore: Double,  // 0.0-1.0
    val source: RelationshipSource,
    val reasoning: String? = null,  // LLM-generated reasoning
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class RelationshipType {
    CITATION,           // Cited by source paper
    REFERENCE,          // Source paper cites this
    CO_CITATION,        // Frequently cited together
    BIBLIOGRAPHIC_COUPLING,  // Share many references
    SEMANTIC_SIMILAR,   // Semantic Scholar recommendation
    AUTHOR_OVERLAP,     // Same authors
    TOPIC_SIMILAR      // Similar research topic
}

@Serializable
enum class RelationshipSource {
    SEMANTIC_SCHOLAR,   // From S2 recommendations API
    INTERNAL_CITATIONS, // From paper's reference list
    INTERNAL_LIBRARY,   // From user's local papers
    LLM_SUGGESTED      // LLM-identified relationship
}

/**
 * Comparison table for related papers.
 * Flexible schema allows domain-specific metrics.
 */
@Serializable
data class ComparisonTable(
    val id: String,
    val sourcePaperId: String,
    val title: String,
    val description: String? = null,
    val columns: List<ComparisonColumn> = emptyList(),
    val generationMethod: GenerationMethod,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class ComparisonColumn(
    val id: String,
    val tableId: String,
    val name: String,
    val description: String? = null,
    val dataType: ColumnDataType,
    val order: Int,
    val createdAt: Instant
)

@Serializable
enum class ColumnDataType {
    TEXT,
    NUMBER,
    BOOLEAN,
    LIST,
    DATE,
    CITATION_COUNT
}

@Serializable
enum class GenerationMethod {
    MANUAL,         // User-defined
    LLM_SUGGESTED,  // LLM extracted metrics
    TEMPLATE       // Pre-defined template
}

/**
 * Individual cell in comparison table.
 * Links paper + column to extracted value.
 */
@Serializable
data class ComparisonEntry(
    val id: String,
    val tableId: String,
    val paperId: String,
    val columnId: String,
    val value: String,  // JSON-encoded value
    val confidence: Double? = null,  // 0.0-1.0 for LLM extractions
    val extractionSource: String? = null,  // Where value came from
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * LLM-generated narrative summary of comparison table.
 */
@Serializable
data class ComparisonNarrative(
    val id: String,
    val tableId: String,
    val content: String,  // Markdown format
    val keyInsights: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Full comparison table with all entries and narrative.
 * Used for API responses.
 */
@Serializable
data class ComparisonTableWithData(
    val table: ComparisonTable,
    val entries: Map<String, Map<String, ComparisonEntry>>,  // paperId -> columnId -> entry
    val papers: List<Paper>,
    val narrative: ComparisonNarrative? = null
)
