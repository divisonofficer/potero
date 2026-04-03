package com.potero.domain.repository

import com.potero.domain.model.PdfResolutionRecord

interface PdfResolutionRepository {
    suspend fun insert(record: PdfResolutionRecord): Result<Unit>
    suspend fun getByPaperId(paperId: String): Result<List<PdfResolutionRecord>>
    suspend fun getLastSuccess(paperId: String): Result<PdfResolutionRecord?>
    suspend fun deleteByPaperId(paperId: String): Result<Unit>
}
