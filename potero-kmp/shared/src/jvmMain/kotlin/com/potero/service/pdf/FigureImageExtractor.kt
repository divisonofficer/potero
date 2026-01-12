package com.potero.service.pdf

import com.potero.service.grobid.TEIBoundingBox
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Extracts figure images from PDF files based on GROBID bounding boxes.
 *
 * Strategy:
 * 1. Render the PDF page at high DPI (300) for quality
 * 2. Crop the rendered image to the bounding box coordinates
 * 3. Add 10% padding to account for bbox inaccuracies
 * 4. Save as PNG with original quality
 */
class FigureImageExtractor {

    companion object {
        private const val DPI = 300f // High quality for figures
        private const val BBOX_PADDING_PERCENT = 0.10 // 10% padding around bbox
    }

    /**
     * Extract figure region from PDF and save as PNG image.
     *
     * @param pdfPath Path to the PDF file
     * @param bbox Bounding box coordinates in PDF coordinate system
     * @param outputPath Where to save the extracted PNG
     * @return Result with output path on success, exception on failure
     */
    fun extractFigureImage(
        pdfPath: String,
        bbox: TEIBoundingBox,
        outputPath: String
    ): Result<String> {
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
            return Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
        }

        return try {
            Loader.loadPDF(pdfFile).use { document ->
                val pageIndex = bbox.pageNum - 1 // Convert to 0-indexed

                if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                    return Result.failure(IllegalArgumentException("Invalid page number: ${bbox.pageNum}"))
                }

                // Render entire page at high DPI
                val renderer = PDFRenderer(document)
                val fullPageImage = renderer.renderImageWithDPI(pageIndex, DPI)

                // Get page dimensions to convert PDF coordinates to image coordinates
                val page = document.getPage(pageIndex)
                val pageHeight = page.mediaBox.height
                val pageWidth = page.mediaBox.width

                // Convert PDF bbox (bottom-left origin) to image coords (top-left origin)
                val cropRect = convertBboxToImageCoords(
                    bbox = bbox,
                    pageWidth = pageWidth.toDouble(),
                    pageHeight = pageHeight.toDouble(),
                    imageWidth = fullPageImage.width,
                    imageHeight = fullPageImage.height
                )

                // Crop image to bbox
                val croppedImage = cropImage(fullPageImage, cropRect)

                // Save as PNG
                saveImage(croppedImage, outputPath)

                Result.success(outputPath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert PDF bounding box coordinates to image pixel coordinates.
     *
     * PDF coords: origin at bottom-left, Y increases upward
     * Image coords: origin at top-left, Y increases downward
     */
    private fun convertBboxToImageCoords(
        bbox: TEIBoundingBox,
        pageWidth: Double,
        pageHeight: Double,
        imageWidth: Int,
        imageHeight: Int
    ): Rectangle {
        // Scale factor from PDF points to image pixels
        val scaleX = imageWidth / pageWidth
        val scaleY = imageHeight / pageHeight

        // PDF coordinates (bottom-left origin)
        val pdfX1 = bbox.x1
        val pdfY1 = bbox.y1
        val pdfX2 = bbox.x2
        val pdfY2 = bbox.y2

        // Calculate bbox width and height in PDF coords
        val pdfWidth = pdfX2 - pdfX1
        val pdfHeight = pdfY2 - pdfY1

        // Add padding (10% on each side)
        val paddingX = pdfWidth * BBOX_PADDING_PERCENT
        val paddingY = pdfHeight * BBOX_PADDING_PERCENT

        val paddedX1 = (pdfX1 - paddingX).coerceAtLeast(0.0)
        val paddedY1 = (pdfY1 - paddingY).coerceAtLeast(0.0)
        val paddedX2 = (pdfX2 + paddingX).coerceAtMost(pageWidth)
        val paddedY2 = (pdfY2 + paddingY).coerceAtMost(pageHeight)

        // Convert to image coordinates (top-left origin)
        // Y coordinate: flip from bottom to top
        val imageX = (paddedX1 * scaleX).toInt().coerceAtLeast(0)
        val imageY = ((pageHeight - paddedY2) * scaleY).toInt().coerceAtLeast(0)
        val imageWidth = ((paddedX2 - paddedX1) * scaleX).toInt().coerceAtLeast(1)
        val imageHeight = ((paddedY2 - paddedY1) * scaleY).toInt().coerceAtLeast(1)

        // Clamp to image bounds
        val clampedX = imageX.coerceIn(0, imageWidth - 1)
        val clampedY = imageY.coerceIn(0, imageHeight - 1)
        val clampedWidth = imageWidth.coerceIn(1, imageWidth - clampedX)
        val clampedHeight = imageHeight.coerceIn(1, imageHeight - clampedY)

        return Rectangle(clampedX, clampedY, clampedWidth, clampedHeight)
    }

    /**
     * Crop a BufferedImage to the specified rectangle.
     */
    private fun cropImage(source: BufferedImage, cropRect: Rectangle): BufferedImage {
        // Validate crop rectangle
        val validX = cropRect.x.coerceIn(0, source.width - 1)
        val validY = cropRect.y.coerceIn(0, source.height - 1)
        val validWidth = cropRect.width.coerceIn(1, source.width - validX)
        val validHeight = cropRect.height.coerceIn(1, source.height - validY)

        return source.getSubimage(validX, validY, validWidth, validHeight)
    }

    /**
     * Save image to PNG file.
     */
    private fun saveImage(image: BufferedImage, outputPath: String): String {
        val outputFile = File(outputPath)
        val parentDir = outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            val created = parentDir.mkdirs()
            if (!created && !parentDir.exists()) {
                throw java.io.IOException("Failed to create directory: ${parentDir.absolutePath}")
            }
        }
        java.io.FileOutputStream(outputFile).use { fos ->
            ImageIO.write(image, "png", fos)
        }
        return outputPath
    }
}
