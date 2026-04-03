package com.potero.service.pdf

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

enum class PdfFailureReason {
    NOT_FOUND,
    FORBIDDEN,
    HTML_INSTEAD_OF_PDF,
    INVALID_CONTENT_TYPE,
    NETWORK_ERROR,
    TIMEOUT
}

sealed class VerificationResult {
    data class Success(
        val finalUrl: String,
        val contentLength: Long?
    ) : VerificationResult()

    data class Failure(
        val reason: PdfFailureReason,
        val httpStatus: Int? = null,
        val detail: String? = null
    ) : VerificationResult()
}

/**
 * Verifies that a URL actually serves a PDF before attempting a full download.
 *
 * Strategy:
 * 1. HEAD request — check status and Content-Type
 * 2. If HEAD fails or returns ambiguous type, Range GET bytes=0-1023 and check %PDF magic
 * 3. HTML response → Failure(HTML_INSTEAD_OF_PDF)
 */
class PdfUrlVerifier(private val httpClient: HttpClient) {

    private val BROWSER_HEADERS = mapOf(
        HttpHeaders.UserAgent to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        HttpHeaders.Accept to "application/pdf,*/*;q=0.9",
        "Referer" to "https://www.google.com/"
    )

    suspend fun verify(url: String): VerificationResult {
        // Step 1: HEAD
        return try {
            val headResult = tryHead(url)
            when (headResult) {
                is VerificationResult.Success -> headResult
                is VerificationResult.Failure -> {
                    // HEAD rejected or ambiguous — try Range GET
                    if (headResult.reason == PdfFailureReason.INVALID_CONTENT_TYPE ||
                        headResult.httpStatus == null) {
                        tryRangeGet(url)
                    } else {
                        headResult
                    }
                }
            }
        } catch (e: Exception) {
            VerificationResult.Failure(PdfFailureReason.NETWORK_ERROR, detail = e.message)
        }
    }

    private suspend fun tryHead(url: String): VerificationResult {
        return try {
            val response = httpClient.request(url) {
                method = HttpMethod.Head
                BROWSER_HEADERS.forEach { (k, v) -> header(k, v) }
            }

            when (response.status.value) {
                in 200..299 -> {
                    val contentType = response.contentType()?.contentType ?: ""
                    when {
                        contentType.contains("pdf", ignoreCase = true) ->
                            VerificationResult.Success(url, response.contentLength())
                        contentType.contains("html", ignoreCase = true) ->
                            VerificationResult.Failure(PdfFailureReason.HTML_INSTEAD_OF_PDF,
                                httpStatus = response.status.value, detail = contentType)
                        contentType.isEmpty() || contentType == "application/octet-stream" ->
                            // Ambiguous — fall through to Range GET
                            VerificationResult.Failure(PdfFailureReason.INVALID_CONTENT_TYPE,
                                httpStatus = null, detail = "ambiguous content-type: $contentType")
                        else ->
                            VerificationResult.Failure(PdfFailureReason.INVALID_CONTENT_TYPE,
                                httpStatus = response.status.value, detail = contentType)
                    }
                }
                403 -> VerificationResult.Failure(PdfFailureReason.FORBIDDEN, httpStatus = 403)
                404 -> VerificationResult.Failure(PdfFailureReason.NOT_FOUND, httpStatus = 404)
                else -> VerificationResult.Failure(PdfFailureReason.NETWORK_ERROR,
                    httpStatus = response.status.value)
            }
        } catch (e: Exception) {
            // Many servers don't support HEAD — treat as ambiguous
            VerificationResult.Failure(PdfFailureReason.INVALID_CONTENT_TYPE, detail = e.message)
        }
    }

    private suspend fun tryRangeGet(url: String): VerificationResult {
        return try {
            val response = httpClient.request(url) {
                method = HttpMethod.Get
                BROWSER_HEADERS.forEach { (k, v) -> header(k, v) }
                header(HttpHeaders.Range, "bytes=0-1023")
            }

            when (response.status.value) {
                200, 206 -> {
                    val bytes = response.bodyAsBytes()
                    if (isPdf(bytes)) {
                        VerificationResult.Success(url, null)
                    } else {
                        val preview = bytes.take(20).toByteArray().toString(Charsets.UTF_8)
                        VerificationResult.Failure(PdfFailureReason.HTML_INSTEAD_OF_PDF,
                            httpStatus = response.status.value,
                            detail = "magic bytes: ${preview.take(10)}")
                    }
                }
                403 -> VerificationResult.Failure(PdfFailureReason.FORBIDDEN, httpStatus = 403)
                404 -> VerificationResult.Failure(PdfFailureReason.NOT_FOUND, httpStatus = 404)
                else -> VerificationResult.Failure(PdfFailureReason.NETWORK_ERROR,
                    httpStatus = response.status.value)
            }
        } catch (e: Exception) {
            VerificationResult.Failure(PdfFailureReason.NETWORK_ERROR, detail = e.message)
        }
    }

    private fun isPdf(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x25.toByte() &&  // %
               bytes[1] == 0x50.toByte() &&  // P
               bytes[2] == 0x44.toByte() &&  // D
               bytes[3] == 0x46.toByte()     // F
    }
}
