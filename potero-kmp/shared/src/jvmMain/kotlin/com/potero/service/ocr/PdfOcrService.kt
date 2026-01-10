package com.potero.service.ocr

import com.potero.domain.repository.SettingsKeys
import com.potero.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import javax.imageio.ImageIO

/**
 * PDF OCR Service using Tesseract.
 *
 * Provides OCR capabilities for scanned/image-based PDFs or PDFs with garbled text extraction.
 * This is an independent service that can be used by multiple components.
 *
 * Requirements:
 * - Tesseract must be installed on the system (tesseract command)
 * - Language data files must be available (eng, kor, etc.)
 *
 * Install on Ubuntu/Debian:
 * ```bash
 * sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor
 * ```
 */
class PdfOcrService(
    private val settingsRepository: SettingsRepository
) {

    /**
     * OCR configuration loaded from settings
     */
    private suspend fun getConfig(): OcrConfig {
        // Default: disabled (requires Tesseract installation)
        val enabled = settingsRepository.get(SettingsKeys.OCR_ENABLED)
            .getOrNull()?.equals("true", ignoreCase = true) ?: false
        val language = settingsRepository.get(SettingsKeys.OCR_LANGUAGE).getOrNull() ?: "eng"
        val dpi = settingsRepository.get(SettingsKeys.OCR_DPI).getOrNull()?.toIntOrNull() ?: 300
        val engineMode = settingsRepository.get(SettingsKeys.OCR_ENGINE_MODE).getOrNull()?.toIntOrNull() ?: 3

        return OcrConfig(
            enabled = enabled,
            language = language,
            dpi = dpi,
            engineMode = engineMode
        )
    }

    /**
     * Check if Tesseract is installed and available
     */
    suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .start()

                val exitCode = process.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                println("[OCR] Tesseract not available: ${e.message}")
                false
            }
        }
    }

    /**
     * OCR a PDF file and extract text from all pages.
     *
     * @param pdfPath Path to PDF file
     * @param startPage First page to OCR (1-indexed), null for all pages
     * @param endPage Last page to OCR (1-indexed), null for all pages
     * @return Extracted text with page markers, or Result.failure if OCR failed
     */
    suspend fun ocrPdf(
        pdfPath: String,
        startPage: Int? = null,
        endPage: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getConfig()

            if (!config.enabled) {
                throw OcrException("OCR is disabled in settings")
            }

            if (!isAvailable()) {
                throw OcrException("Tesseract is not installed or not available in PATH")
            }

            println("[OCR] Starting OCR: $pdfPath (language=${config.language}, dpi=${config.dpi})")

            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                throw OcrException("PDF file not found: $pdfPath")
            }

            Loader.loadPDF(pdfFile).use { document ->
                val renderer = PDFRenderer(document)
                val totalPages = document.numberOfPages

                val start = startPage ?: 1
                val end = endPage ?: totalPages

                println("[OCR] Processing pages $start-$end of $totalPages")

                val textBuilder = StringBuilder()

                for (pageIndex in (start - 1) until minOf(end, totalPages)) {
                    val pageNum = pageIndex + 1
                    println("[OCR] Processing page $pageNum...")

                    // Step 1: Render PDF page to image
                    val image = renderer.renderImageWithDPI(pageIndex, config.dpi.toFloat())

                    // Step 2: Save image to temp file
                    val tempImageFile = File.createTempFile("ocr-page-$pageNum-", ".png")
                    tempImageFile.deleteOnExit()
                    ImageIO.write(image, "PNG", tempImageFile)

                    // Step 3: Run Tesseract OCR
                    val pageText = runTesseract(tempImageFile, config)

                    // Step 4: Append to result
                    textBuilder.append("<<<PAGE $pageNum>>>\n")
                    textBuilder.append(pageText)
                    textBuilder.append("\n\n")

                    // Clean up temp file
                    tempImageFile.delete()
                }

                val result = textBuilder.toString()
                println("[OCR] Completed: extracted ${result.length} chars from ${end - start + 1} pages")

                result
            }
        }
    }

    /**
     * OCR a single page of a PDF.
     *
     * @param pdfPath Path to PDF file
     * @param pageNum Page number (1-indexed)
     * @return Extracted text from the page
     */
    suspend fun ocrPage(pdfPath: String, pageNum: Int): Result<String> {
        return ocrPdf(pdfPath, startPage = pageNum, endPage = pageNum)
    }

    /**
     * Run Tesseract OCR on an image file.
     *
     * @param imageFile Image file to OCR
     * @param config OCR configuration
     * @return Extracted text
     */
    private fun runTesseract(imageFile: File, config: OcrConfig): String {
        // Create temp file for OCR output (Tesseract adds .txt extension)
        val outputBase = File.createTempFile("tesseract-output-", "")
        outputBase.deleteOnExit()
        val outputFile = File(outputBase.absolutePath + ".txt")
        outputFile.deleteOnExit()

        try {
            // Build Tesseract command
            val command = buildList {
                add("tesseract")
                add(imageFile.absolutePath)
                add(outputBase.absolutePath)
                add("-l")
                add(config.language)
                add("--oem")
                add(config.engineMode.toString())
                add("--psm")
                add("3") // PSM 3: Fully automatic page segmentation (default)
            }

            println("[OCR] Running: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Capture output for debugging
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                println("[OCR] Tesseract failed with exit code $exitCode")
                println("[OCR] Output: $output")
                throw OcrException("Tesseract failed with exit code $exitCode")
            }

            // Read OCR result
            val text = if (outputFile.exists()) {
                outputFile.readText()
            } else {
                println("[OCR] Warning: Output file not created by Tesseract")
                ""
            }

            // Clean up
            outputBase.delete()
            outputFile.delete()

            return text
        } catch (e: Exception) {
            outputBase.delete()
            outputFile.delete()
            throw e
        }
    }
}

/**
 * OCR configuration
 */
data class OcrConfig(
    val enabled: Boolean,
    val language: String,
    val dpi: Int,
    val engineMode: Int
)

/**
 * OCR-specific exception
 */
class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)
