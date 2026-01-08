package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.Author
import com.potero.domain.model.Paper
import com.potero.domain.model.Tag
import com.potero.domain.repository.PaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Implementation of PaperRepository using SQLDelight
 */
class PaperRepositoryImpl(
    private val database: PoteroDatabase
) : PaperRepository {

    private val paperQueries = database.paperQueries
    private val authorQueries = database.authorQueries
    private val tagQueries = database.tagQueries

    override fun observeAll(): Flow<List<Paper>> = flow {
        emit(getAll().getOrDefault(emptyList()))
    }

    override fun observeById(id: String): Flow<Paper?> = flow {
        emit(getById(id).getOrNull())
    }

    override suspend fun getAll(): Result<List<Paper>> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.selectAll().executeAsList().map { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun getById(id: String): Result<Paper?> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.selectById(id).executeAsOneOrNull()?.let { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun getByDoi(doi: String): Result<Paper?> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.selectByDoi(doi).executeAsOneOrNull()?.let { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun getByArxivId(arxivId: String): Result<Paper?> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.selectByArxivId(arxivId).executeAsOneOrNull()?.let { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun searchByTitle(query: String): Result<List<Paper>> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.searchByTitle(query).executeAsList().map { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun getByConference(conference: String): Result<List<Paper>> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.selectByConference(conference).executeAsList().map { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun getByYearRange(startYear: Int, endYear: Int): Result<List<Paper>> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.selectByYearRange(startYear.toLong(), endYear.toLong()).executeAsList().map { dbPaper ->
                dbPaper.toDomain(
                    authors = getAuthorsForPaper(dbPaper.id),
                    tags = getTagsForPaper(dbPaper.id)
                )
            }
        }
    }

    override suspend fun insert(paper: Paper): Result<Paper> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()

            paperQueries.insert(
                id = paper.id,
                title = paper.title,
                abstract_ = paper.abstract,
                doi = paper.doi,
                arxiv_id = paper.arxivId,
                url = paper.url,
                year = paper.year?.toLong(),
                conference = paper.conference,
                citations_count = paper.citationsCount.toLong(),
                pdf_path = paper.pdfPath,
                thumbnail_path = paper.thumbnailPath,
                has_blog_view = if (paper.hasBlogView) 1L else 0L,
                created_at = now,
                updated_at = now
            )

            // Insert authors
            paper.authors.forEachIndexed { index, author ->
                insertOrUpdateAuthor(author)
                authorQueries.linkPaperAuthor(paper.id, author.id, index.toLong())
            }

            // Insert tags
            paper.tags.forEach { tag ->
                insertOrUpdateTag(tag)
                tagQueries.linkPaperTag(paper.id, tag.id)
            }

            paper.copy(
                createdAt = Instant.fromEpochMilliseconds(now),
                updatedAt = Instant.fromEpochMilliseconds(now)
            )
        }
    }

    override suspend fun update(paper: Paper): Result<Paper> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()

            paperQueries.update(
                title = paper.title,
                abstract_ = paper.abstract,
                doi = paper.doi,
                arxiv_id = paper.arxivId,
                url = paper.url,
                year = paper.year?.toLong(),
                conference = paper.conference,
                citations_count = paper.citationsCount.toLong(),
                pdf_path = paper.pdfPath,
                thumbnail_path = paper.thumbnailPath,
                has_blog_view = if (paper.hasBlogView) 1L else 0L,
                updated_at = now,
                id = paper.id
            )

            // Update authors
            authorQueries.unlinkAllFromPaper(paper.id)
            paper.authors.forEachIndexed { index, author ->
                insertOrUpdateAuthor(author)
                authorQueries.linkPaperAuthor(paper.id, author.id, index.toLong())
            }

            // Update tags
            tagQueries.unlinkAllFromPaper(paper.id)
            paper.tags.forEach { tag ->
                insertOrUpdateTag(tag)
                tagQueries.linkPaperTag(paper.id, tag.id)
            }

            paper.copy(updatedAt = Instant.fromEpochMilliseconds(now))
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Foreign key cascades will handle PaperAuthor and PaperTag
            paperQueries.delete(id)
        }
    }

    override suspend fun count(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            paperQueries.countAll().executeAsOne()
        }
    }

    override suspend fun setAuthors(paperId: String, authors: List<Author>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authorQueries.unlinkAllFromPaper(paperId)
            authors.forEachIndexed { index, author ->
                insertOrUpdateAuthor(author)
                authorQueries.linkPaperAuthor(paperId, author.id, index.toLong())
            }
        }
    }

    override suspend fun getAuthors(paperId: String): Result<List<Author>> = withContext(Dispatchers.IO) {
        runCatching {
            getAuthorsForPaper(paperId)
        }
    }

    override suspend fun setTags(paperId: String, tags: List<Tag>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.unlinkAllFromPaper(paperId)
            tags.forEach { tag ->
                insertOrUpdateTag(tag)
                tagQueries.linkPaperTag(paperId, tag.id)
            }
        }
    }

    override suspend fun getTags(paperId: String): Result<List<Tag>> = withContext(Dispatchers.IO) {
        runCatching {
            getTagsForPaper(paperId)
        }
    }

    override suspend fun addTag(paperId: String, tagId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.linkPaperTag(paperId, tagId)
        }
    }

    override suspend fun removeTag(paperId: String, tagId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.unlinkPaperTag(paperId, tagId)
        }
    }

    // Helper functions

    private fun getAuthorsForPaper(paperId: String): List<Author> {
        return authorQueries.selectByPaper(paperId).executeAsList().mapIndexed { index, dbAuthor ->
            Author(
                id = dbAuthor.id,
                name = dbAuthor.name,
                affiliation = dbAuthor.affiliation,
                order = index
            )
        }
    }

    private fun getTagsForPaper(paperId: String): List<Tag> {
        return tagQueries.selectByPaper(paperId).executeAsList().map { dbTag ->
            Tag(
                id = dbTag.id,
                name = dbTag.name,
                color = dbTag.color
            )
        }
    }

    private fun insertOrUpdateAuthor(author: Author) {
        val existing = authorQueries.selectById(author.id).executeAsOneOrNull()
        val now = Clock.System.now().toEpochMilliseconds()

        if (existing == null) {
            authorQueries.insert(
                id = author.id,
                name = author.name,
                affiliation = author.affiliation,
                created_at = now,
                updated_at = now
            )
        } else {
            authorQueries.update(
                name = author.name,
                affiliation = author.affiliation,
                updated_at = now,
                id = author.id
            )
        }
    }

    private fun insertOrUpdateTag(tag: Tag) {
        val existing = tagQueries.selectById(tag.id).executeAsOneOrNull()

        if (existing == null) {
            tagQueries.insert(
                id = tag.id,
                name = tag.name,
                color = tag.color,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
        } else {
            tagQueries.update(
                name = tag.name,
                color = tag.color,
                id = tag.id
            )
        }
    }

    // Extension function to convert DB model to domain model
    private fun com.potero.db.Paper.toDomain(
        authors: List<Author>,
        tags: List<Tag>
    ): Paper {
        return Paper(
            id = this.id,
            title = this.title,
            abstract = this.abstract_,
            doi = this.doi,
            arxivId = this.arxiv_id,
            url = this.url,
            year = this.year?.toInt(),
            conference = this.conference,
            citationsCount = this.citations_count.toInt(),
            pdfPath = this.pdf_path,
            thumbnailPath = this.thumbnail_path,
            hasBlogView = this.has_blog_view == 1L,
            authors = authors,
            tags = tags,
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            updatedAt = Instant.fromEpochMilliseconds(this.updated_at)
        )
    }
}
