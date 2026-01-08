package com.potero.service.llm

import com.potero.domain.model.Paper
import com.potero.domain.repository.PaperRepository

/**
 * Use case for chatting about a paper with LLM
 * Uses text extraction approach for MVP (file upload requires SSO)
 */
class ChatWithPaperUseCase(
    private val llmService: LLMService,
    private val paperRepository: PaperRepository,
    private val pdfTextExtractor: PdfTextExtractor? = null
) {
    /**
     * Chat about a paper with optional full text context
     */
    suspend fun chat(
        paperId: String,
        userMessage: String,
        includeFullText: Boolean = false
    ): Result<String> {
        val paper = paperRepository.getById(paperId).getOrNull()
            ?: return Result.failure(IllegalArgumentException("Paper not found: $paperId"))

        val context = buildPaperContext(paper, includeFullText)
        val prompt = buildPrompt(context, userMessage)

        return llmService.chat(prompt)
    }

    /**
     * Chat with custom context (selected text, etc.)
     */
    suspend fun chatWithContext(
        paperId: String?,
        userMessage: String,
        selectedText: String? = null
    ): Result<String> {
        val context = buildString {
            if (paperId != null) {
                val paper = paperRepository.getById(paperId).getOrNull()
                if (paper != null) {
                    append("Paper: ${paper.title}\n")
                    append("Authors: ${paper.formattedAuthors}\n")
                    if (paper.abstract != null) {
                        append("Abstract: ${paper.abstract}\n")
                    }
                }
            }

            if (selectedText != null) {
                append("\nSelected text from paper:\n\"$selectedText\"\n")
            }
        }

        val prompt = if (context.isNotBlank()) {
            """
            |Context:
            |$context
            |
            |User question: $userMessage
            """.trimMargin()
        } else {
            userMessage
        }

        return llmService.chat(prompt)
    }

    private suspend fun buildPaperContext(paper: Paper, includeFullText: Boolean): String {
        return buildString {
            append("Paper Information:\n")
            append("Title: ${paper.title}\n")
            append("Authors: ${paper.formattedAuthors}\n")

            paper.year?.let { append("Year: $it\n") }
            paper.conference?.let { append("Conference: $it\n") }
            paper.doi?.let { append("DOI: $it\n") }

            if (paper.abstract != null) {
                append("\nAbstract:\n${paper.abstract}\n")
            }

            if (includeFullText && paper.pdfPath != null && pdfTextExtractor != null) {
                try {
                    val fullText = pdfTextExtractor.extractText(paper.pdfPath)
                    if (fullText.isNotBlank()) {
                        append("\nFull Paper Text:\n$fullText\n")
                    }
                } catch (e: Exception) {
                    // Failed to extract text, continue without it
                }
            }
        }
    }

    private fun buildPrompt(context: String, userMessage: String): String {
        return """
            |You are a research assistant helping to understand academic papers.
            |
            |$context
            |
            |User question: $userMessage
            |
            |Please provide a helpful, accurate response based on the paper information above.
        """.trimMargin()
    }
}

/**
 * Interface for PDF text extraction
 * Implemented in platform-specific code (JVM uses PDFBox)
 */
interface PdfTextExtractor {
    /**
     * Extract all text from a PDF file
     */
    suspend fun extractText(pdfPath: String): String

    /**
     * Extract text from specific pages
     */
    suspend fun extractText(pdfPath: String, startPage: Int, endPage: Int): String
}
