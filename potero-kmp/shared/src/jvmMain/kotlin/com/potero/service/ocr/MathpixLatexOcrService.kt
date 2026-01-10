package com.potero.service.ocr

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/**
 * LaTeX OCR implementation using Mathpix API.
 *
 * Mathpix provides high-accuracy OCR for mathematical equations.
 * API: https://docs.mathpix.com/
 *
 * Requires:
 * - Mathpix App ID (mathpix.app_id setting)
 * - Mathpix App Key (mathpix.app_key setting)
 *
 * Free tier: 1000 requests/month
 */
class MathpixLatexOcrService(
    private val httpClient: HttpClient,
    private val appIdProvider: () -> String?,
    private val appKeyProvider: () -> String?
) : LatexOcrService {

    companion object {
        private const val MATHPIX_API_URL = "https://api.mathpix.com/v3/text"
        private const val MAX_IMAGE_SIZE_MB = 5
        private const val ENGINE_NAME = "Mathpix"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extractLatex(imagePath: String): Result<LatexOcrResult> {
        return runCatching {
            if (!isAvailable()) {
                throw LatexOcrException("Mathpix not configured. Please set mathpix.app_id and mathpix.app_key")
            }

            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                throw LatexOcrException("Image file not found: $imagePath")
            }

            // Check file size
            val sizeInMB = imageFile.length() / (1024.0 * 1024.0)
            if (sizeInMB > MAX_IMAGE_SIZE_MB) {
                throw LatexOcrException("Image too large: ${sizeInMB}MB (max ${MAX_IMAGE_SIZE_MB}MB)")
            }

            val imageBytes = imageFile.readBytes()
            val format = imageFile.extension.lowercase()

            extractLatex(imageBytes, format).getOrThrow()
        }
    }

    override suspend fun extractLatex(imageBytes: ByteArray, format: String): Result<LatexOcrResult> {
        return runCatching {
            if (!isAvailable()) {
                throw LatexOcrException("Mathpix not configured")
            }

            val appId = appIdProvider() ?: throw LatexOcrException("Mathpix app_id not set")
            val appKey = appKeyProvider() ?: throw LatexOcrException("Mathpix app_key not set")

            // Encode image to base64
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)
            val mimeType = when (format.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                else -> "image/png"
            }

            // Prepare request
            val requestBody = MathpixRequest(
                src = "data:$mimeType;base64,$base64Image",
                formats = listOf("latex_styled"),  // Get LaTeX with styling
                metadata = MathpixRequestMetadata(
                    include_asciimath = false,
                    include_latex = true,
                    include_mathml = false,
                    include_tsv = false
                )
            )

            // Call Mathpix API
            val response: MathpixResponse = httpClient.post(MATHPIX_API_URL) {
                headers {
                    append("app_id", appId)
                    append("app_key", appKey)
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            // Parse response
            if (response.error != null) {
                throw LatexOcrException("Mathpix API error: ${response.error}")
            }

            val latex = response.latex_styled ?: response.text ?: ""
            if (latex.isBlank()) {
                throw LatexOcrException("Mathpix returned empty LaTeX")
            }

            LatexOcrResult(
                latex = latex.trim(),
                confidence = response.confidence,
                requestId = response.request_id,
                engine = ENGINE_NAME,
                isPrinted = response.is_printed,
                isHandwritten = response.is_handwritten
            )
        }
    }

    override fun isAvailable(): Boolean {
        val appId = appIdProvider()
        val appKey = appKeyProvider()
        return !appId.isNullOrBlank() && !appKey.isNullOrBlank()
    }

    override fun getEngineName(): String = ENGINE_NAME

    // === Mathpix API Models ===

    @Serializable
    private data class MathpixRequest(
        val src: String,  // data:image/png;base64,<base64>
        val formats: List<String> = listOf("latex_styled"),
        val metadata: MathpixRequestMetadata? = null
    )

    @Serializable
    private data class MathpixRequestMetadata(
        val include_asciimath: Boolean = false,
        val include_latex: Boolean = true,
        val include_mathml: Boolean = false,
        val include_tsv: Boolean = false
    )

    @Serializable
    private data class MathpixResponse(
        val text: String? = null,
        val latex_styled: String? = null,
        val confidence: Double? = null,
        val confidence_rate: Double? = null,
        val is_printed: Boolean? = null,
        val is_handwritten: Boolean? = null,
        val auto_rotate_confidence: Double? = null,
        val auto_rotate_degrees: Int? = null,
        val request_id: String? = null,
        val error: String? = null
    )
}

/**
 * Disabled LaTeX OCR service (used when Mathpix is not configured)
 */
object DisabledLatexOcrService : LatexOcrService {

    override suspend fun extractLatex(imagePath: String): Result<LatexOcrResult> {
        return Result.failure(LatexOcrException("LaTeX OCR is disabled. Configure Mathpix API keys to enable."))
    }

    override suspend fun extractLatex(imageBytes: ByteArray, format: String): Result<LatexOcrResult> {
        return Result.failure(LatexOcrException("LaTeX OCR is disabled"))
    }

    override fun isAvailable(): Boolean = false

    override fun getEngineName(): String = "Disabled"
}
