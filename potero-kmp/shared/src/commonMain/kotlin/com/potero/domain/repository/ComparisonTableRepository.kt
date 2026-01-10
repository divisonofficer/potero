package com.potero.domain.repository

import com.potero.domain.model.*

/**
 * Repository for managing comparison tables and their data
 */
interface ComparisonTableRepository {

    // Table operations
    /**
     * Get all comparison tables for a source paper
     */
    suspend fun getTablesBySourcePaper(sourcePaperId: String): Result<List<ComparisonTable>>

    /**
     * Get a specific comparison table by ID
     */
    suspend fun getTableById(tableId: String): Result<ComparisonTable?>

    /**
     * Get a comparison table with all associated data (columns, entries, papers, narrative)
     */
    suspend fun getTableWithData(tableId: String): Result<ComparisonTableWithData?>

    /**
     * Insert a new comparison table
     */
    suspend fun insertTable(table: ComparisonTable): Result<ComparisonTable>

    /**
     * Update an existing comparison table
     */
    suspend fun updateTable(table: ComparisonTable): Result<ComparisonTable>

    /**
     * Delete a comparison table (cascades to columns, entries, narrative)
     */
    suspend fun deleteTable(tableId: String): Result<Unit>

    /**
     * Delete all comparison tables for a source paper
     */
    suspend fun deleteTablesBySourcePaper(sourcePaperId: String): Result<Unit>

    // Column operations
    /**
     * Get all columns for a table
     */
    suspend fun getColumnsByTable(tableId: String): Result<List<ComparisonColumn>>

    /**
     * Insert a new column
     */
    suspend fun insertColumn(column: ComparisonColumn): Result<ComparisonColumn>

    // Entry operations
    /**
     * Get all entries for a table
     */
    suspend fun getEntriesByTable(tableId: String): Result<List<ComparisonEntry>>

    /**
     * Get entries for a specific paper in a table
     */
    suspend fun getEntriesByPaperAndTable(
        tableId: String,
        paperId: String
    ): Result<List<ComparisonEntry>>

    /**
     * Insert or update a comparison entry
     */
    suspend fun insertEntry(entry: ComparisonEntry): Result<ComparisonEntry>

    // Narrative operations
    /**
     * Get narrative summary for a table
     */
    suspend fun getNarrativeByTable(tableId: String): Result<ComparisonNarrative?>

    /**
     * Insert or update a narrative summary
     */
    suspend fun insertNarrative(narrative: ComparisonNarrative): Result<ComparisonNarrative>

    // Search operations
    /**
     * Search comparison tables by text (title, description, narrative content, key insights)
     */
    suspend fun searchByText(query: String, limit: Int = 10): Result<List<ComparisonTable>>
}
