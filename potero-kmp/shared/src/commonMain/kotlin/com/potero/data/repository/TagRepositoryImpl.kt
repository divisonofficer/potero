package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.Tag
import com.potero.domain.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Implementation of TagRepository using SQLDelight
 */
class TagRepositoryImpl(
    private val database: PoteroDatabase
) : TagRepository {

    private val tagQueries = database.tagQueries

    override fun observeAll(): Flow<List<Tag>> = flow {
        emit(getAll().getOrDefault(emptyList()))
    }

    override suspend fun getAll(): Result<List<Tag>> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.selectAll().executeAsList().map { dbTag ->
                Tag(
                    id = dbTag.id,
                    name = dbTag.name,
                    color = dbTag.color ?: "#6366f1"
                )
            }
        }
    }

    override suspend fun getById(id: String): Result<Tag?> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.selectById(id).executeAsOneOrNull()?.let { dbTag ->
                Tag(
                    id = dbTag.id,
                    name = dbTag.name,
                    color = dbTag.color ?: "#6366f1"
                )
            }
        }
    }

    override suspend fun getByName(name: String): Result<Tag?> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.selectByName(name).executeAsOneOrNull()?.let { dbTag ->
                Tag(
                    id = dbTag.id,
                    name = dbTag.name,
                    color = dbTag.color ?: "#6366f1"
                )
            }
        }
    }

    override suspend fun insert(tag: Tag): Result<Tag> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.insert(
                id = tag.id,
                name = tag.name,
                color = tag.color,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
            tag
        }
    }

    override suspend fun update(tag: Tag): Result<Tag> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.update(
                name = tag.name,
                color = tag.color,
                id = tag.id
            )
            tag
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.delete(id)
        }
    }

    override suspend fun getTagsWithCounts(): Result<List<Pair<Tag, Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            tagQueries.selectAllWithCount().executeAsList().map { row ->
                Pair(
                    Tag(
                        id = row.id,
                        name = row.name,
                        color = row.color ?: "#6366f1"
                    ),
                    row.paper_count.toInt()
                )
            }
        }
    }
}
