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
    val order: Int = 0
)

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
