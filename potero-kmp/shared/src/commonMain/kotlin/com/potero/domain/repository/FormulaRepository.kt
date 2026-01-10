package com.potero.domain.repository

import com.potero.db.Formula

/**
 * Repository for formulas extracted from PDFs (by GROBID).
 * Stores formula labels, LaTeX content, and metadata.
 */
interface FormulaRepository {

    /**
     * Insert a single formula
     */
    suspend fun insert(formula: Formula): Result<Formula>

    /**
     * Insert multiple formulas in a transaction
     */
    suspend fun insertAll(formulas: List<Formula>): Result<List<Formula>>

    /**
     * Get a formula by ID
     */
    suspend fun getById(id: String): Result<Formula?>

    /**
     * Get all formulas for a paper
     */
    suspend fun getByPaperId(paperId: String): Result<List<Formula>>

    /**
     * Get formulas for a specific page
     */
    suspend fun getByPage(paperId: String, pageNum: Int): Result<List<Formula>>

    /**
     * Get formula by xml_id
     */
    suspend fun getByXmlId(paperId: String, xmlId: String): Result<Formula?>

    /**
     * Get formula by label (e.g., "(1)")
     */
    suspend fun getByLabel(paperId: String, label: String): Result<Formula?>

    /**
     * Update formula LaTeX content
     */
    suspend fun updateLatex(id: String, latex: String): Result<Unit>

    /**
     * Delete all formulas for a paper
     */
    suspend fun deleteByPaperId(paperId: String): Result<Unit>

    /**
     * Count formulas for a paper
     */
    suspend fun countByPaperId(paperId: String): Result<Int>

    /**
     * Search formulas by LaTeX content
     */
    suspend fun searchByLatex(paperId: String, query: String): Result<List<Formula>>
}
