package com.potero.service.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Extracts figures from PDFs using PDFBox when GROBID is not available.
 *
 * Fallback strategy:
 * 1. Extract all embedded images from PDF
 * 2. Filter out small images (likely icons/logos)
 * 3. Score images based on size, aspect ratio, and page location
 * 4. Save top-quality images as figures
 *
 * Note: Without GROBID, we don't have:
 * - Figure captions (would need Vision API or page text analysis)
 * - Accurate bounding boxes (use image position approximation)
 * - Figure-to-text linking
 *
 * To improve caption quality, consider:
 * - Option 1: Pass pageTextProvider to extract page text and infer caption
 * - Option 2: Use Vision API to analyze the image itself
 */
class PdfBoxFigureExtractor(
    private val pageTextProvider: (suspend (String, Int) -> String?)? = null
) {

    companion object {
        private const val MIN_IMAGE_SIZE = 150 // Minimum width/height for figures
        private const val MAX_FIGURES_PER_PAGE = 5 // Avoid extracting too many images
    }

    /**
     * Extract figures from PDF without GROBID metadata.
     * Returns list of extracted figure info with file paths.
     * If pageTextProvider is available, attempts to infer captions from page text.
     */
    suspend fun extractFigures(
        pdfPath: String,
        paperId: String,
        outputDir: String
    ): Result<List<ExtractedFigure>> = runCatching {
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
            throw IllegalArgumentException("PDF file not found: $pdfPath")
        }

        val outDir = File(outputDir)
        outDir.mkdirs()

        val extractedFigures = mutableListOf<ExtractedFigure>()

        Loader.loadPDF(pdfFile).use { document ->
            var globalFigureNum = 1

            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                val pageNum = pageIndex + 1

                // Get page text for caption inference (if provider available)
                val pageText = try {
                    pageTextProvider?.invoke(paperId, pageNum)
                } catch (e: Exception) {
                    println("[PdfBoxFigureExtractor] Failed to get page text: ${e.message}")
                    null
                }

                // Extract images from this page
                val images = extractImagesFromPage(page, document, pageNum)

                // Score and filter images
                val qualityImages = images
                    .filter { it.score > 50 } // Only good quality images
                    .sortedByDescending { it.score }
                    .take(MAX_FIGURES_PER_PAGE)

                // Save as figures with deterministic IDs
                qualityImages.forEachIndexed { imgIndex, imageData ->
                    // Create deterministic ID based on paper + page + image index
                    val figureId = "${paperId}_pdfbox_p${pageNum}_img${imgIndex}"
                    val outputPath = File(outputDir, "$figureId.png").absolutePath

                    // Try to infer caption from page text
                    val caption = pageText?.let {
                        extractCaptionFromPageText(it, globalFigureNum)
                    }

                    try {
                        ImageIO.write(imageData.image, "png", File(outputPath))

                        extractedFigures.add(
                            ExtractedFigure(
                                id = figureId,
                                paperId = paperId,
                                pageNum = pageNum,
                                label = "Figure $globalFigureNum",
                                caption = caption, // Inferred from page text
                                imagePath = outputPath,
                                confidence = calculateConfidence(imageData.score)
                            )
                        )
                        globalFigureNum++
                    } catch (e: Exception) {
                        println("[PdfBoxFigureExtractor] Failed to save image: ${e.message}")
                    }
                }
            }
        }

        extractedFigures
    }

    /**
     * Extract all images from a PDF page with quality scoring.
     */
    private fun extractImagesFromPage(
        page: PDPage,
        document: PDDocument,
        pageNum: Int
    ): List<ImageData> {
        val images = mutableListOf<ImageData>()

        try {
            val resources = page.resources
            resources?.xObjectNames?.forEach { name ->
                try {
                    val xObject = resources.getXObject(name)
                    if (xObject is PDImageXObject) {
                        val image = xObject.image
                        if (image != null) {
                            val score = scoreImage(image, pageNum)
                            if (score > 0) {
                                images.add(ImageData(image, score, pageNum))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip problematic images
                }
            }
        } catch (e: Exception) {
            // Page has no resources or error accessing them
        }

        return images
    }

    /**
     * Score an image based on its suitability as a figure.
     * Higher score = better candidate.
     */
    private fun scoreImage(image: BufferedImage, pageNum: Int): Int {
        val width = image.width
        val height = image.height

        // Skip too small images (likely icons, logos, decorations)
        if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
            return 0
        }

        var score = 0

        // Prefer larger images (more likely to be figures)
        val area = width * height
        score += when {
            area > 500000 -> 150  // Very large figures
            area > 200000 -> 100  // Large figures
            area > 100000 -> 75   // Medium figures
            area > 50000 -> 50    // Small figures
            else -> 25
        }

        // Prefer images from pages 2-5 (usually have main figures)
        score += when (pageNum) {
            1 -> 20  // First page (might have logo/abstract figure)
            2, 3 -> 50 // Usually has main figures
            4, 5 -> 40
            in 6..10 -> 30
            else -> 10
        }

        // Prefer images with reasonable aspect ratio (not too wide/tall)
        val aspectRatio = width.toFloat() / height.toFloat()
        if (aspectRatio in 0.3f..3.0f) {
            score += 30
        }

        // Penalize very wide images (likely headers/footers)
        if (aspectRatio > 5.0f || aspectRatio < 0.2f) {
            score -= 50
        }

        return maxOf(0, score)
    }

    /**
     * Extract figure caption from page text using pattern matching.
     * Looks for patterns like "Figure N: caption text" or "Fig. N. caption text"
     */
    private fun extractCaptionFromPageText(pageText: String, figureNum: Int): String? {
        if (pageText.isBlank()) return null

        // Common patterns for figure captions
        val patterns = listOf(
            // "Figure 1: Caption text here."
            Regex("Figure\\s+$figureNum[:\\.]\\s*([^\\n\\.]+[\\.]?)", RegexOption.IGNORE_CASE),
            // "Fig. 1: Caption text here."
            Regex("Fig\\.?\\s+$figureNum[:\\.]\\s*([^\\n\\.]+[\\.]?)", RegexOption.IGNORE_CASE),
            // "FIG. 1. Caption text here."
            Regex("FIG\\.?\\s+$figureNum[:\\.]\\s*([^\\n\\.]+[\\.]?)")
        )

        for (pattern in patterns) {
            val match = pattern.find(pageText)
            if (match != null && match.groupValues.size > 1) {
                val caption = match.groupValues[1].trim()
                // Return if caption is reasonable length (not too short/long)
                if (caption.length in 10..300) {
                    return caption
                }
            }
        }

        return null
    }

    /**
     * Convert quality score to confidence level.
     */
    private fun calculateConfidence(score: Int): Double {
        return when {
            score > 150 -> 0.90
            score > 100 -> 0.80
            score > 75 -> 0.70
            score > 50 -> 0.60
            else -> 0.50
        }
    }

    private data class ImageData(
        val image: BufferedImage,
        val score: Int,
        val pageNum: Int
    )
}

/**
 * Result of PDFBox figure extraction
 */
data class ExtractedFigure(
    val id: String,
    val paperId: String,
    val pageNum: Int,
    val label: String,
    val caption: String?,
    val imagePath: String,
    val confidence: Double
)
