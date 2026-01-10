package com.potero.service.ocr

/**
 * Service for converting mathematical equations in PDF images to LaTeX code.
 * Supports multiple OCR engines (Mathpix, pix2tex, etc.)
 */
interface LatexOcrService {

    /**
     * Extract LaTeX from an equation image.
     *
     * @param imagePath Path to the image file containing the equation
     * @return Result with LaTeX code and confidence score
     */
    suspend fun extractLatex(imagePath: String): Result<LatexOcrResult>

    /**
     * Extract LaTeX from raw image bytes.
     *
     * @param imageBytes Image data (PNG, JPEG, etc.)
     * @param format Image format (e.g., "png", "jpeg")
     * @return Result with LaTeX code and confidence score
     */
    suspend fun extractLatex(imageBytes: ByteArray, format: String = "png"): Result<LatexOcrResult>

    /**
     * Batch extract LaTeX from multiple images.
     *
     * @param imagePaths List of image file paths
     * @return List of results (same order as input)
     */
    suspend fun extractLatexBatch(imagePaths: List<String>): List<Result<LatexOcrResult>> {
        return imagePaths.map { extractLatex(it) }
    }

    /**
     * Check if the service is available (API key configured, dependencies met, etc.)
     */
    fun isAvailable(): Boolean

    /**
     * Get the name of the OCR engine (e.g., "Mathpix", "pix2tex")
     */
    fun getEngineName(): String
}

/**
 * Result of LaTeX OCR extraction
 */
data class LatexOcrResult(
    val latex: String,
    val confidence: Double? = null,  // 0-1 score
    val requestId: String? = null,  // For debugging/tracking
    val engine: String,
    val isPrinted: Boolean? = null,  // Whether text is printed (vs handwritten)
    val isHandwritten: Boolean? = null  // Whether text is handwritten
) {
    /**
     * Check if result has high confidence
     */
    val isHighConfidence: Boolean
        get() = confidence?.let { it >= 0.8 } ?: false
}

/**
 * Exception thrown when LaTeX OCR fails
 */
class LatexOcrException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
