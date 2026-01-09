package com.potero.domain.repository

import com.potero.domain.model.GrobidCitationSpan
import com.potero.domain.model.GrobidReference

/**
 * Repository for GROBID-extracted citation and reference data.
 * Stores high-quality TEI XML parsing results with target links.
 */
interface GrobidRepository {

    // === Citation Span Operations ===

    /**
     * Insert a single GROBID citation span
     */
    suspend fun insertCitationSpan(span: GrobidCitationSpan): Result<GrobidCitationSpan>

    /**
     * Insert multiple GROBID citation spans in a transaction
     */
    suspend fun insertAllCitationSpans(spans: List<GrobidCitationSpan>): Result<List<GrobidCitationSpan>>

    /**
     * Get all citation spans for a paper
     */
    suspend fun getCitationSpansByPaperId(paperId: String): Result<List<GrobidCitationSpan>>

    /**
     * Get citation spans for a specific page
     */
    suspend fun getCitationSpansByPage(paperId: String, pageNum: Int): Result<List<GrobidCitationSpan>>

    /**
     * Get citation spans by type (biblio, figure, formula)
     */
    suspend fun getCitationSpansByType(paperId: String, refType: String): Result<List<GrobidCitationSpan>>

    /**
     * Get citation spans that link to a specific target
     */
    suspend fun getCitationSpansByTarget(targetXmlId: String): Result<List<GrobidCitationSpan>>

    /**
     * Delete all citation spans for a paper
     */
    suspend fun deleteCitationSpansByPaperId(paperId: String): Result<Unit>

    /**
     * Count citation spans for a paper
     */
    suspend fun countCitationSpansByPaperId(paperId: String): Result<Int>

    // === Reference Operations ===

    /**
     * Insert a single GROBID reference
     */
    suspend fun insertReference(reference: GrobidReference): Result<GrobidReference>

    /**
     * Insert multiple GROBID references in a transaction
     */
    suspend fun insertAllReferences(references: List<GrobidReference>): Result<List<GrobidReference>>

    /**
     * Get all references for a paper
     */
    suspend fun getReferencesByPaperId(paperId: String): Result<List<GrobidReference>>

    /**
     * Get a reference by its xml_id
     */
    suspend fun getReferenceByXmlId(paperId: String, xmlId: String): Result<GrobidReference?>

    /**
     * Get references by page number
     */
    suspend fun getReferencesByPage(paperId: String, pageNum: Int): Result<List<GrobidReference>>

    /**
     * Get a reference by DOI
     */
    suspend fun getReferenceByDoi(doi: String): Result<GrobidReference?>

    /**
     * Delete all references for a paper
     */
    suspend fun deleteReferencesByPaperId(paperId: String): Result<Unit>

    /**
     * Count references for a paper
     */
    suspend fun countReferencesByPaperId(paperId: String): Result<Int>

    /**
     * Search references by title
     */
    suspend fun searchReferencesByTitle(paperId: String, title: String): Result<List<GrobidReference>>
}
