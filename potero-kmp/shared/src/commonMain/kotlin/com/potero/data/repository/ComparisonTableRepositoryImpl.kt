package com.potero.data.repository

import com.potero.db.PoteroDatabase
import com.potero.domain.model.*
import com.potero.domain.repository.ComparisonTableRepository
import com.potero.domain.repository.PaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Implementation of ComparisonTableRepository using SQLDelight
 */
class ComparisonTableRepositoryImpl(
    private val database: PoteroDatabase,
    private val paperRepository: PaperRepository
) : ComparisonTableRepository {

    private val tableQueries = database.comparisonTableQueries

    override suspend fun getTablesBySourcePaper(sourcePaperId: String): Result<List<ComparisonTable>> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.selectTablesBySourcePaper(sourcePaperId)
                    .executeAsList()
                    .map { dbTable ->
                        val columns = tableQueries.selectColumnsByTable(dbTable.id)
                            .executeAsList()
                            .map { it.toDomainColumn() }
                        dbTable.toDomain(columns)
                    }
            }
        }

    override suspend fun getTableById(tableId: String): Result<ComparisonTable?> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.selectTableById(tableId).executeAsOneOrNull()?.let { dbTable ->
                    val columns = tableQueries.selectColumnsByTable(tableId)
                        .executeAsList()
                        .map { it.toDomainColumn() }
                    dbTable.toDomain(columns)
                }
            }
        }

    override suspend fun getTableWithData(tableId: String): Result<ComparisonTableWithData?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dbTable = tableQueries.selectTableById(tableId).executeAsOneOrNull()
                    ?: return@runCatching null

                // Get columns
                val columns = tableQueries.selectColumnsByTable(tableId)
                    .executeAsList()
                    .map { it.toDomainColumn() }

                val table = dbTable.toDomain(columns)

                // Get entries
                val dbEntries = tableQueries.selectEntriesByTable(tableId).executeAsList()
                val entries = dbEntries.map { it.toDomainEntry() }

                // Group entries by paperId -> columnId
                val entriesMap = mutableMapOf<String, MutableMap<String, ComparisonEntry>>()
                entries.forEach { entry ->
                    entriesMap.getOrPut(entry.paperId) { mutableMapOf() }[entry.columnId] = entry
                }

                // Get all unique paper IDs
                val paperIds = entries.map { it.paperId }.distinct()
                val papers = paperIds.mapNotNull { paperId ->
                    paperRepository.getById(paperId).getOrNull()
                }

                // Get narrative
                val narrative = tableQueries.selectNarrativeByTable(tableId)
                    .executeAsOneOrNull()
                    ?.toDomainNarrative()

                ComparisonTableWithData(
                    table = table,
                    entries = entriesMap,
                    papers = papers,
                    narrative = narrative
                )
            }
        }

    override suspend fun insertTable(table: ComparisonTable): Result<ComparisonTable> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.insertTable(
                    id = table.id,
                    source_paper_id = table.sourcePaperId,
                    title = table.title,
                    description = table.description,
                    generation_method = table.generationMethod.name,
                    created_at = table.createdAt.toEpochMilliseconds(),
                    updated_at = table.updatedAt.toEpochMilliseconds()
                )
                table
            }
        }

    override suspend fun updateTable(table: ComparisonTable): Result<ComparisonTable> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.updateTable(
                    title = table.title,
                    description = table.description,
                    updated_at = table.updatedAt.toEpochMilliseconds(),
                    id = table.id
                )
                table
            }
        }

    override suspend fun deleteTable(tableId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.deleteTableById(tableId)
            }
        }

    override suspend fun deleteTablesBySourcePaper(sourcePaperId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.deleteTablesBySourcePaper(sourcePaperId)
            }
        }

    override suspend fun getColumnsByTable(tableId: String): Result<List<ComparisonColumn>> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.selectColumnsByTable(tableId)
                    .executeAsList()
                    .map { it.toDomainColumn() }
            }
        }

    override suspend fun insertColumn(column: ComparisonColumn): Result<ComparisonColumn> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.insertColumn(
                    id = column.id,
                    table_id = column.tableId,
                    name = column.name,
                    description = column.description,
                    data_type = column.dataType.name,
                    column_order = column.order.toLong(),
                    created_at = column.createdAt.toEpochMilliseconds()
                )
                column
            }
        }

    override suspend fun getEntriesByTable(tableId: String): Result<List<ComparisonEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.selectEntriesByTable(tableId)
                    .executeAsList()
                    .map { it.toDomainEntry() }
            }
        }

    override suspend fun getEntriesByPaperAndTable(
        tableId: String,
        paperId: String
    ): Result<List<ComparisonEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            tableQueries.selectEntriesByPaperAndTable(tableId, paperId)
                .executeAsList()
                .map { it.toDomainEntry() }
        }
    }

    override suspend fun insertEntry(entry: ComparisonEntry): Result<ComparisonEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.insertEntry(
                    id = entry.id,
                    table_id = entry.tableId,
                    paper_id = entry.paperId,
                    column_id = entry.columnId,
                    value_ = entry.value,
                    confidence = entry.confidence,
                    extraction_source = entry.extractionSource,
                    created_at = entry.createdAt.toEpochMilliseconds(),
                    updated_at = entry.updatedAt.toEpochMilliseconds()
                )
                entry
            }
        }

    override suspend fun getNarrativeByTable(tableId: String): Result<ComparisonNarrative?> =
        withContext(Dispatchers.IO) {
            runCatching {
                tableQueries.selectNarrativeByTable(tableId)
                    .executeAsOneOrNull()
                    ?.toDomainNarrative()
            }
        }

    override suspend fun insertNarrative(narrative: ComparisonNarrative): Result<ComparisonNarrative> =
        withContext(Dispatchers.IO) {
            runCatching {
                val keyInsightsJson = Json.encodeToString(narrative.keyInsights)
                tableQueries.insertNarrative(
                    id = narrative.id,
                    table_id = narrative.tableId,
                    content = narrative.content,
                    key_insights = keyInsightsJson,
                    created_at = narrative.createdAt.toEpochMilliseconds(),
                    updated_at = narrative.updatedAt.toEpochMilliseconds()
                )
                narrative
            }
        }

    // Helper extension functions
    private fun com.potero.db.ComparisonTable.toDomain(columns: List<ComparisonColumn>): ComparisonTable {
        return ComparisonTable(
            id = id,
            sourcePaperId = source_paper_id,
            title = title,
            description = description,
            columns = columns,
            generationMethod = GenerationMethod.valueOf(generation_method),
            createdAt = Instant.fromEpochMilliseconds(created_at),
            updatedAt = Instant.fromEpochMilliseconds(updated_at)
        )
    }

    private fun com.potero.db.ComparisonColumn.toDomainColumn(): ComparisonColumn {
        return ComparisonColumn(
            id = id,
            tableId = table_id,
            name = name,
            description = description,
            dataType = ColumnDataType.valueOf(data_type),
            order = column_order.toInt(),
            createdAt = Instant.fromEpochMilliseconds(created_at)
        )
    }

    private fun com.potero.db.ComparisonEntry.toDomainEntry(): ComparisonEntry {
        return ComparisonEntry(
            id = id,
            tableId = table_id,
            paperId = paper_id,
            columnId = column_id,
            value = value_,
            confidence = confidence,
            extractionSource = extraction_source,
            createdAt = Instant.fromEpochMilliseconds(created_at),
            updatedAt = Instant.fromEpochMilliseconds(updated_at)
        )
    }

    private fun com.potero.db.ComparisonNarrative.toDomainNarrative(): ComparisonNarrative {
        val insights = key_insights?.let { json ->
            Json.decodeFromString<List<String>>(json)
        } ?: emptyList()

        return ComparisonNarrative(
            id = id,
            tableId = table_id,
            content = content,
            keyInsights = insights,
            createdAt = Instant.fromEpochMilliseconds(created_at),
            updatedAt = Instant.fromEpochMilliseconds(updated_at)
        )
    }
}
