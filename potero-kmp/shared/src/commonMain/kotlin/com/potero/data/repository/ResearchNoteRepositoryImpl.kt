package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.BacklinkInfo
import com.potero.domain.model.NoteLink
import com.potero.domain.model.NoteLinkType
import com.potero.domain.model.ResearchNote
import com.potero.domain.model.ResearchNoteWithLinks
import com.potero.domain.repository.ResearchNoteRepository
import com.potero.service.note.NoteLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Implementation of ResearchNoteRepository using SQLDelight
 */
class ResearchNoteRepositoryImpl(
    private val database: PoteroDatabase
) : ResearchNoteRepository {

    private val noteQueries = database.researchNoteQueries

    override fun observeAll(): Flow<List<ResearchNote>> = flow {
        emit(getAll().getOrDefault(emptyList()))
    }

    override fun observeById(id: String): Flow<ResearchNote?> = flow {
        emit(getById(id).getOrNull())
    }

    override fun observeByPaper(paperId: String): Flow<List<ResearchNote>> = flow {
        emit(getByPaper(paperId).getOrDefault(emptyList()))
    }

    override suspend fun getAll(): Result<List<ResearchNote>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.selectAll().executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getById(id: String): Result<ResearchNote?> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.selectById(id).executeAsOneOrNull()?.toDomain()
        }
    }

    override suspend fun getByPaper(paperId: String): Result<List<ResearchNote>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.selectByPaper(paperId).executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getStandalone(): Result<List<ResearchNote>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.selectStandalone().executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun insert(note: ResearchNote): Result<ResearchNote> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.insert(
                id = note.id,
                paper_id = note.paperId,
                title = note.title,
                content = note.content,
                created_at = note.createdAt.toEpochMilliseconds(),
                updated_at = note.updatedAt.toEpochMilliseconds()
            )
            note
        }
    }

    override suspend fun update(note: ResearchNote): Result<ResearchNote> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            noteQueries.update(
                title = note.title,
                content = note.content,
                paper_id = note.paperId,
                updated_at = now,
                id = note.id
            )
            note.copy(updatedAt = Instant.fromEpochMilliseconds(now))
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.delete(id)
        }
    }

    override suspend fun searchByTitle(query: String): Result<List<ResearchNote>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.searchByTitle(query).executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun searchByContent(query: String): Result<List<ResearchNote>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.searchByContent(query).executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getWithLinks(noteId: String): Result<ResearchNoteWithLinks?> = withContext(Dispatchers.IO) {
        runCatching {
            val note = noteQueries.selectById(noteId).executeAsOneOrNull()?.toDomain()
                ?: return@runCatching null

            val outgoingLinks = noteQueries.selectLinksBySourceNote(noteId)
                .executeAsList()
                .map { it.toDomainLink() }

            val backlinks = noteQueries.selectBacklinks(noteId)
                .executeAsList()
                .map { row ->
                    BacklinkInfo(
                        noteId = row.source_note_id,
                        noteTitle = row.title,
                        linkText = row.link_text,
                        createdAt = Instant.fromEpochMilliseconds(row.created_at)
                    )
                }

            ResearchNoteWithLinks(
                note = note,
                outgoingLinks = outgoingLinks,
                backlinks = backlinks
            )
        }
    }

    override suspend fun getBacklinks(noteId: String): Result<List<BacklinkInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.selectBacklinks(noteId)
                .executeAsList()
                .map { row ->
                    BacklinkInfo(
                        noteId = row.source_note_id,
                        noteTitle = row.title,
                        linkText = row.link_text,
                        createdAt = Instant.fromEpochMilliseconds(row.created_at)
                    )
                }
        }
    }

    override suspend fun getBacklinksToPaper(paperId: String): Result<List<BacklinkInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.selectBacklinksToPaper(paperId)
                .executeAsList()
                .map { row ->
                    BacklinkInfo(
                        noteId = row.source_note_id,
                        noteTitle = row.title,
                        linkText = row.link_text,
                        createdAt = Instant.fromEpochMilliseconds(row.created_at)
                    )
                }
        }
    }

    override suspend fun updateLinks(noteId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Delete old links
            noteQueries.deleteLinksForNote(noteId)

            // Parse new links from content
            val parsedLinks = NoteLinkParser.parse(content)
            val now = Clock.System.now().toEpochMilliseconds()

            parsedLinks.forEach { parsedLink ->
                val (linkType, targetNoteId, targetPaperId) = when {
                    NoteLinkParser.isPaperLink(parsedLink.text) -> {
                        val paperId = NoteLinkParser.extractPaperId(parsedLink.text)
                        Triple(NoteLinkType.PAPER, null, paperId)
                    }
                    else -> {
                        // Try to resolve note by title
                        val targetNote = noteQueries.findNoteByTitle(parsedLink.text)
                            .executeAsOneOrNull()
                        Triple(NoteLinkType.NOTE, targetNote?.id, null)
                    }
                }

                noteQueries.insertLink(
                    id = UUID.randomUUID().toString(),
                    source_note_id = noteId,
                    target_note_id = targetNoteId,
                    target_paper_id = targetPaperId,
                    link_text = parsedLink.text,
                    link_type = linkType.name,
                    position_in_content = parsedLink.position.toLong(),
                    created_at = now
                )
            }
        }
    }

    override suspend fun resolveLink(linkId: String, targetNoteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.updateLinkTarget(
                target_note_id = targetNoteId,
                id = linkId
            )
        }
    }

    override suspend fun findNoteByTitle(title: String): Result<ResearchNote?> = withContext(Dispatchers.IO) {
        runCatching {
            noteQueries.findNoteByTitle(title).executeAsOneOrNull()?.toDomain()
        }
    }

    // Extension functions to convert database models to domain models
    private fun com.potero.db.ResearchNote.toDomain() = ResearchNote(
        id = this.id,
        paperId = this.paper_id,
        title = this.title,
        content = this.content,
        createdAt = Instant.fromEpochMilliseconds(this.created_at),
        updatedAt = Instant.fromEpochMilliseconds(this.updated_at)
    )

    private fun com.potero.db.NoteLink.toDomainLink() = NoteLink(
        id = this.id,
        sourceNoteId = this.source_note_id,
        targetNoteId = this.target_note_id,
        targetPaperId = this.target_paper_id,
        linkText = this.link_text,
        linkType = NoteLinkType.valueOf(this.link_type),
        positionInContent = this.position_in_content.toInt(),
        createdAt = Instant.fromEpochMilliseconds(this.created_at)
    )
}
