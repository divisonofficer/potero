package com.potero.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Paper(
    val id: String,
    val title: String,
    val abstract: String? = null,
    val abstractKorean: String? = null,
    val doi: String? = null,
    val arxivId: String? = null,
    val url: String? = null,
    val year: Int? = null,
    val conference: String? = null,
    val citationsCount: Int = 0,
    val pdfPath: String? = null,
    val thumbnailPath: String? = null,
    val hasBlogView: Boolean = false,
    val authors: List<Author> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val formattedAuthors: String
        get() = authors.joinToString(", ") { it.name }

    val formattedCitation: String
        get() = buildString {
            append(formattedAuthors)
            append(" (${year ?: "n.d."}). ")
            append(title)
            conference?.let { append(". $it") }
            doi?.let { append(". https://doi.org/$it") }
        }
}

@Serializable
data class Author(
    val id: String,
    val name: String,
    val affiliation: String? = null,
    val order: Int = 0,
    val googleScholarId: String? = null,
    val semanticScholarId: String? = null
) {
    /**
     * Get the Google Scholar profile URL if available
     */
    val googleScholarUrl: String?
        get() = googleScholarId?.let { "https://scholar.google.com/citations?user=$it" }

    /**
     * Get the Semantic Scholar profile URL if available
     */
    val semanticScholarUrl: String?
        get() = semanticScholarId?.let { "https://www.semanticscholar.org/author/$it" }
}

@Serializable
data class Tag(
    val id: String,
    val name: String,
    val color: String = "#6366f1"
)

@Serializable
data class Note(
    val id: String,
    val paperId: String,
    val content: String,
    val type: NoteType = NoteType.GENERAL,
    val pageNumber: Int? = null,
    val positionX: Float? = null,
    val positionY: Float? = null,
    val width: Float? = null,
    val height: Float? = null,
    val color: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class NoteType {
    GENERAL,
    ANNOTATION,
    HIGHLIGHT
}

@Serializable
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val paperId: String? = null,
    val role: ChatRole,
    val content: String,
    val model: String? = null,
    val createdAt: Instant
)

@Serializable
enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Serializable
data class ChatSession(
    val id: String,
    val paperId: String? = null,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messageCount: Int = 0,
    val lastMessage: String? = null
)

/**
 * A reference entry from a paper's References/Bibliography section.
 * Used for citation lookup and linking related papers.
 */
@Serializable
data class Reference(
    val id: String,
    val paperId: String,
    val number: Int,
    val rawText: String,
    val authors: String? = null,
    val title: String? = null,
    val venue: String? = null,
    val year: Int? = null,
    val doi: String? = null,
    val pageNum: Int = 0,
    val createdAt: Instant
) {
    /**
     * Get a search query for looking up this reference
     */
    val searchQuery: String
        get() = title ?: authors ?: rawText.take(100)
}

/**
 * Bounding box in PDF coordinates (origin at bottom-left).
 */
@Serializable
data class BoundingBox(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double
) {
    val width: Double get() = x2 - x1
    val height: Double get() = y2 - y1
    val centerX: Double get() = (x1 + x2) / 2
    val centerY: Double get() = (y1 + y2) / 2
}

/**
 * Citation style type
 */
@Serializable
enum class CitationStyle {
    NUMERIC,      // [1], [1,2,3], [1-5]
    AUTHOR_YEAR,  // (Smith et al., 2024)
    UNKNOWN
}

/**
 * How the citation was detected
 */
@Serializable
enum class CitationProvenance {
    ANNOTATION,  // From PDF link annotation (highest confidence)
    PATTERN      // From text pattern matching
}

/**
 * An in-text citation span with its bounding box.
 * Represents clickable citation references like [1], [1,2,3], (Smith et al., 2024).
 */
@Serializable
data class CitationSpan(
    val id: String,
    val paperId: String,
    val pageNum: Int,
    val bbox: BoundingBox,
    val rawText: String,
    val style: CitationStyle = CitationStyle.NUMERIC,
    val provenance: CitationProvenance = CitationProvenance.PATTERN,
    val confidence: Double = 0.5,
    val destPage: Int? = null,
    val destY: Double? = null,
    val createdAt: Instant
)

/**
 * A link between a CitationSpan and a Reference entry.
 * Represents the edge: "citation [1] refers to reference entry #1"
 */
@Serializable
data class CitationLink(
    val id: String,
    val citationSpanId: String,
    val referenceId: String,
    val linkMethod: String,
    val confidence: Double = 0.5,
    val createdAt: Instant
)

/**
 * Citation extraction result with statistics
 */
@Serializable
data class CitationExtractionStats(
    val totalSpans: Int,
    val annotationSpans: Int,
    val patternSpans: Int,
    val linkedCount: Int,
    val avgConfidence: Double
)

/**
 * GROBID Citation Span - extracted from TEI XML.
 * Contains GROBID-specific fields like xml_id and target_xml_id for high-quality linking.
 */
@Serializable
data class GrobidCitationSpan(
    val id: String,
    val paperId: String,
    val pageNum: Int,
    val rawText: String,
    val xmlId: String? = null,
    val refType: String,  // 'biblio', 'figure', 'formula'
    val targetXmlId: String? = null,  // Links to GrobidReference.xmlId
    val confidence: Double = 0.95,
    val createdAt: Instant
)

/**
 * GROBID Reference - bibliography entry extracted from TEI XML.
 * Contains raw TEI XML and structured metadata.
 */
@Serializable
data class GrobidReference(
    val id: String,
    val paperId: String,
    val xmlId: String,  // e.g., "#b12"
    val rawTei: String? = null,  // Full <biblStruct> XML
    val authors: String? = null,
    val title: String? = null,
    val venue: String? = null,
    val year: Int? = null,
    val doi: String? = null,
    val arxivId: String? = null,
    val pageNum: Int? = null,
    val confidence: Double = 0.95,
    val createdAt: Instant
) {
    /**
     * Get a search query for looking up this reference
     */
    val searchQuery: String
        get() = title ?: authors ?: rawTei?.take(100) ?: ""
}
