package com.potero.service.metadata

import kotlinx.serialization.Serializable

/**
 * Interface for resolving paper metadata from various sources
 */
interface MetadataResolver {
    /**
     * Check if this resolver can handle the given identifier
     */
    fun canResolve(identifier: String): Boolean

    /**
     * Resolve metadata from the identifier
     */
    suspend fun resolve(identifier: String): Result<ResolvedMetadata>
}

/**
 * Resolved paper metadata
 */
@Serializable
data class ResolvedMetadata(
    val title: String,
    val authors: List<ResolvedAuthor> = emptyList(),
    val abstract: String? = null,
    val doi: String? = null,
    val arxivId: String? = null,
    val year: Int? = null,
    val month: Int? = null,
    val venue: String? = null,
    val venueType: String? = null, // "conference", "journal", "preprint"
    val publisher: String? = null,
    val pdfUrl: String? = null,
    val citationsCount: Int? = null,
    val keywords: List<String> = emptyList()
)

/**
 * Author information from resolved metadata
 */
@Serializable
data class ResolvedAuthor(
    val name: String,
    val affiliation: String? = null,
    val orcid: String? = null
)

/**
 * Exception for metadata resolution errors
 */
class MetadataResolutionException(
    message: String,
    val identifier: String,
    cause: Throwable? = null
) : Exception(message, cause)
