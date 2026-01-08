package com.potero.service.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Extracts thumbnails from PDF files.
 *
 * Strategy:
 * 1. First, try to find and extract figures/images from the first few pages
 * 2. If no suitable image found, render the first page as thumbnail
 */
class PdfThumbnailExtractor {

    companion object {
        private const val THUMBNAIL_WIDTH = 400
        private const val THUMBNAIL_HEIGHT = 300
        private const val MIN_IMAGE_SIZE = 100 // Minimum dimension for extracted images
        private const val MAX_PAGES_TO_SCAN = 5
    }

    /**
     * Extract thumbnail from PDF and save it to the specified output path.
     * Returns the path if successful, null otherwise.
     */
    fun extractThumbnail(pdfPath: String, outputPath: String): String? {
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
            return null
        }

        return try {
            Loader.loadPDF(pdfFile).use { document ->
                // Strategy 1: Try to find a good figure/image
                val extractedImage = findBestImage(document)

                if (extractedImage != null) {
                    // Found a good image, scale and save it
                    val scaledImage = scaleImage(extractedImage, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    saveImage(scaledImage, outputPath)
                } else {
                    // Strategy 2: Render first page as thumbnail
                    val renderer = PDFRenderer(document)
                    val pageImage = renderer.renderImageWithDPI(0, 72f) // First page at 72 DPI
                    val scaledImage = scaleImage(pageImage, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    saveImage(scaledImage, outputPath)
                }
            }
        } catch (e: Exception) {
            println("[ThumbnailExtractor] Error: ${e.message}")
            null
        }
    }

    /**
     * Extract thumbnail and return as base64 encoded string (for API response).
     */
    fun extractThumbnailAsBase64(pdfPath: String): String? {
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
            return null
        }

        return try {
            Loader.loadPDF(pdfFile).use { document ->
                val image = findBestImage(document) ?: run {
                    val renderer = PDFRenderer(document)
                    renderer.renderImageWithDPI(0, 72f)
                }

                val scaledImage = scaleImage(image, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                imageToBase64(scaledImage)
            }
        } catch (e: Exception) {
            println("[ThumbnailExtractor] Error: ${e.message}")
            null
        }
    }

    /**
     * Find the best image from the first few pages.
     * Prefers larger images that might be figures.
     */
    private fun findBestImage(document: PDDocument): BufferedImage? {
        val pagesToScan = minOf(MAX_PAGES_TO_SCAN, document.numberOfPages)

        var bestImage: BufferedImage? = null
        var bestScore = 0

        for (pageIndex in 0 until pagesToScan) {
            val page = document.getPage(pageIndex)
            val images = extractImagesFromPage(page, document)

            for (image in images) {
                val score = scoreImage(image, pageIndex)
                if (score > bestScore) {
                    bestScore = score
                    bestImage = image
                }
            }
        }

        return bestImage
    }

    /**
     * Extract all images from a PDF page.
     */
    private fun extractImagesFromPage(page: PDPage, document: PDDocument): List<BufferedImage> {
        val images = mutableListOf<BufferedImage>()

        try {
            val resources = page.resources
            resources?.xObjectNames?.forEach { name ->
                try {
                    val xObject = resources.getXObject(name)
                    if (xObject is PDImageXObject) {
                        val image = xObject.image
                        if (image != null) {
                            images.add(image)
                        }
                    }
                } catch (e: Exception) {
                    // Skip problematic images
                }
            }
        } catch (e: Exception) {
            // Skip if page has no resources
        }

        return images
    }

    /**
     * Score an image based on its suitability as a thumbnail.
     * Higher score = better candidate.
     */
    private fun scoreImage(image: BufferedImage, pageIndex: Int): Int {
        val width = image.width
        val height = image.height

        // Skip too small images (likely icons or decorations)
        if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
            return 0
        }

        var score = 0

        // Prefer larger images
        val area = width * height
        score += when {
            area > 200000 -> 100  // Large figures
            area > 50000 -> 50   // Medium figures
            area > 10000 -> 25   // Small figures
            else -> 10
        }

        // Prefer images from earlier pages (page 2-3 usually have main figures)
        score += when (pageIndex) {
            0 -> 10  // First page might have logo
            1, 2 -> 30 // Usually has main figure
            3, 4 -> 20
            else -> 5
        }

        // Prefer images with good aspect ratio (not too wide/tall)
        val aspectRatio = width.toFloat() / height.toFloat()
        if (aspectRatio in 0.5f..2.0f) {
            score += 20
        }

        return score
    }

    /**
     * Scale image to fit within the specified dimensions while maintaining aspect ratio.
     */
    private fun scaleImage(original: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        val originalWidth = original.width
        val originalHeight = original.height

        // Calculate scale factor
        val widthScale = maxWidth.toFloat() / originalWidth
        val heightScale = maxHeight.toFloat() / originalHeight
        val scale = minOf(widthScale, heightScale)

        val newWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        val newHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        graphics.drawImage(original, 0, 0, newWidth, newHeight, null)
        graphics.dispose()

        return scaled
    }

    /**
     * Save image to file.
     */
    private fun saveImage(image: BufferedImage, outputPath: String): String? {
        return try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            ImageIO.write(image, "png", outputFile)
            outputPath
        } catch (e: Exception) {
            println("[ThumbnailExtractor] Failed to save: ${e.message}")
            null
        }
    }

    /**
     * Convert image to base64 string.
     */
    private fun imageToBase64(image: BufferedImage): String {
        val baos = java.io.ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray())
    }
}
