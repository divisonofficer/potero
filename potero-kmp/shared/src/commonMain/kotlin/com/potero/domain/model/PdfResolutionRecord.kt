package com.potero.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PdfResolutionRecord(
    val id: String,
    val paperId: String,
    val source: String,           // 'arxiv', 'openalex', 'unpaywall', 'cvf', 'semantic_scholar', 'scihub', 'direct_url'
    val candidateUrl: String?,
    val status: String,           // 'success', 'failed', 'skipped'
    val failureReason: String?,   // 'NOT_FOUND', 'FORBIDDEN', 'INVALID_PDF', 'NETWORK_ERROR', 'TIMEOUT'
    val httpStatus: Int?,
    val resolvedAt: Instant
)
