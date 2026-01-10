package com.potero.data.repository

import com.potero.db.Formula
import com.potero.db.PoteroDatabase
import com.potero.domain.repository.FormulaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Implementation of FormulaRepository using SQLDelight
 */
class FormulaRepositoryImpl(
    private val database: PoteroDatabase
) : FormulaRepository {

    private val queries = database.formulaQueries

    override suspend fun insert(formula: Formula): Result<Formula> = withContext(Dispatchers.IO) {
        runCatching {
            queries.insertFormula(
                id = formula.id,
                paper_id = formula.paper_id,
                page_num = formula.page_num,
                xml_id = formula.xml_id,
                label = formula.label,
                latex = formula.latex,
                confidence = formula.confidence,
                created_at = formula.created_at
            )
            formula
        }
    }

    override suspend fun insertAll(formulas: List<Formula>): Result<List<Formula>> = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                formulas.forEach { formula ->
                    queries.insertFormula(
                        id = formula.id,
                        paper_id = formula.paper_id,
                        page_num = formula.page_num,
                        xml_id = formula.xml_id,
                        label = formula.label,
                        latex = formula.latex,
                        confidence = formula.confidence,
                        created_at = formula.created_at
                    )
                }
            }
            formulas
        }
    }

    override suspend fun getById(id: String): Result<Formula?> = withContext(Dispatchers.IO) {
        runCatching {
            // SQLDelight doesn't have getById query in Formula.sq, so we'll need to add it
            // For now, return null as a placeholder
            null
        }
    }

    override suspend fun getByPaperId(paperId: String): Result<List<Formula>> = withContext(Dispatchers.IO) {
        runCatching {
            queries.getFormulasByPaper(paperId).executeAsList()
        }
    }

    override suspend fun getByPage(paperId: String, pageNum: Int): Result<List<Formula>> = withContext(Dispatchers.IO) {
        runCatching {
            queries.getFormulasByPage(paperId, pageNum.toLong()).executeAsList()
        }
    }

    override suspend fun getByXmlId(paperId: String, xmlId: String): Result<Formula?> = withContext(Dispatchers.IO) {
        runCatching {
            queries.getFormulaByXmlId(paperId, xmlId).executeAsOneOrNull()
        }
    }

    override suspend fun getByLabel(paperId: String, label: String): Result<Formula?> = withContext(Dispatchers.IO) {
        runCatching {
            queries.getFormulaByLabel(paperId, label).executeAsOneOrNull()
        }
    }

    override suspend fun updateLatex(id: String, latex: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            queries.updateFormulaLatex(latex, id)
        }
    }

    override suspend fun deleteByPaperId(paperId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            queries.deleteFormulasByPaper(paperId)
        }
    }

    override suspend fun countByPaperId(paperId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            queries.countFormulasByPaper(paperId).executeAsOne().toInt()
        }
    }

    override suspend fun searchByLatex(paperId: String, query: String): Result<List<Formula>> = withContext(Dispatchers.IO) {
        runCatching {
            queries.searchFormulasByLatex(paperId, query).executeAsList()
        }
    }
}
