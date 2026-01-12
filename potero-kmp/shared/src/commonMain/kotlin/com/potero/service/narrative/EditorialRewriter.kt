package com.potero.service.narrative

import com.potero.domain.model.NarrativeLanguage
import com.potero.domain.model.NarrativeSection
import com.potero.domain.model.NarrativeStyle
import com.potero.service.llm.LLMService

/**
 * 2-Pass Editorial Rewriting for Narrative Quality.
 *
 * Problem: Single-pass generation produces inconsistent quality (long paragraphs, redundancy, poor flow).
 * Solution: Draft → Editorial Pass → Final Content
 *
 * The editorial pass improves:
 * - Paragraph length (max 4-5 sentences)
 * - Flow and transitions
 * - Redundancy removal
 * - Term definition placement
 * - Evidence coverage
 *
 * Critical: Preserves immutable blocks (images, formulas) exactly.
 */
class EditorialRewriter(
    private val llmService: LLMService
) {

    /**
     * Immutable block that must not be modified during editing.
     */
    data class ImmutableBlock(
        val type: String,        // "image", "formula", "code"
        val content: String,     // Exact markdown text
        val placeholder: String, // Temporary placeholder during editing
        val startIndex: Int,
        val endIndex: Int
    )

    /**
     * Apply editorial pass to improve draft quality.
     *
     * @param draftContent Original draft markdown
     * @param outline Narrative outline to maintain structure
     * @param style Narrative style (for style-appropriate editing)
     * @param language Narrative language
     * @return Edited content with improved quality
     */
    suspend fun applyEditorialPass(
        draftContent: String,
        outline: List<NarrativeSection>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): Result<String> = runCatching {
        // Extract immutable blocks (images, formulas)
        val immutableBlocks = extractImmutableBlocks(draftContent)

        // Replace immutable blocks with placeholders
        var editableContent = draftContent
        immutableBlocks.forEach { block ->
            editableContent = editableContent.replace(block.content, block.placeholder)
        }

        // Build editorial prompt
        val prompt = buildEditorialPrompt(editableContent, outline, immutableBlocks, style, language)

        // Get edited content from LLM
        val result = llmService.chat(prompt)
        var editedContent = result.getOrThrow()

        // Restore immutable blocks
        immutableBlocks.forEach { block ->
            editedContent = editedContent.replace(block.placeholder, block.content)
        }

        // Validate that all immutable blocks are present
        val validation = validateImmutableBlocks(editedContent, immutableBlocks)
        if (!validation.success) {
            println("[EditorialRewriter] Validation failed: ${validation.error}")
            println("[EditorialRewriter] Falling back to original draft")
            return Result.success(draftContent) // Rollback to draft if validation fails
        }

        editedContent
    }

    /**
     * Extract immutable blocks that must not be modified.
     *
     * Types:
     * - Images: ![alt](url)
     * - Display formulas: $$...$$
     * - Inline formulas: $...$
     * - Code blocks: ```...```
     */
    private fun extractImmutableBlocks(markdown: String): List<ImmutableBlock> {
        val blocks = mutableListOf<ImmutableBlock>()
        var blockId = 0

        // 1. Images (![alt](url))
        Regex("!\\[([^]]*)\\]\\(([^)]+)\\)").findAll(markdown).forEach { match ->
            blocks.add(ImmutableBlock(
                type = "image",
                content = match.value,
                placeholder = "{{IMAGE_BLOCK_${blockId++}}}",
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }

        // 2. Display formulas ($$...$$)
        Regex("\\$\\$[^$]+?\\$\\$", RegexOption.DOT_MATCHES_ALL).findAll(markdown).forEach { match ->
            blocks.add(ImmutableBlock(
                type = "formula_display",
                content = match.value,
                placeholder = "{{FORMULA_BLOCK_${blockId++}}}",
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }

        // 3. Inline formulas ($...$, but not $$)
        // Be careful not to match display formulas
        Regex("(?<!\\$)\\$(?!\\$)([^$]+?)\\$(?!\\$)").findAll(markdown).forEach { match ->
            blocks.add(ImmutableBlock(
                type = "formula_inline",
                content = match.value,
                placeholder = "{{INLINE_FORMULA_${blockId++}}}",
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }

        // 4. Code blocks (```...```)
        Regex("```[^`]*```", RegexOption.DOT_MATCHES_ALL).findAll(markdown).forEach { match ->
            blocks.add(ImmutableBlock(
                type = "code",
                content = match.value,
                placeholder = "{{CODE_BLOCK_${blockId++}}}",
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }

        return blocks
    }

    /**
     * Build editorial rewrite prompt.
     */
    private fun buildEditorialPrompt(
        editableContent: String,
        outline: List<NarrativeSection>,
        immutableBlocks: List<ImmutableBlock>,
        style: NarrativeStyle,
        language: NarrativeLanguage
    ): String {
        val languageNote = if (language == NarrativeLanguage.KOREAN) {
            "편집된 결과를 한국어로 작성하세요. 모든 내용은 자연스러운 한국어여야 합니다."
        } else {
            "Write the edited content in English."
        }

        val immutableList = immutableBlocks.joinToString("\n") { block ->
            "- ${block.placeholder} (${block.type})"
        }

        return """
$languageNote

You are an editor improving a ${style.name.lowercase()} narrative for readability and flow.

## Original Draft
$editableContent

## Narrative Outline (maintain this structure)
${outline.joinToString("\n") { section ->
    "Section ${section.order}: ${section.heading} (${section.suggestedLength})"
}}

## Immutable Blocks (DO NOT MODIFY THESE PLACEHOLDERS)
$immutableList

These placeholders represent images, tables, and formulas.
Keep them EXACTLY as shown in the draft, in the same positions.

## Your Editorial Tasks

1. **Shorten long paragraphs**
   - Max 4-5 sentences per paragraph
   - Break up dense technical paragraphs

2. **Remove redundancy**
   - Check for repeated explanations
   - Consolidate similar points
   - Remove unnecessary filler words

3. **Improve flow**
   - Add transitional phrases between sections
   - Ensure each section leads naturally to the next
   - Move backwards references forward if needed

4. **Enhance readability**
   - Vary sentence length (mix short and medium sentences)
   - Use active voice where possible
   - Replace complex phrases with simpler alternatives

5. **Move term definitions forward**
   - If a technical term is used before being defined, move definition earlier
   - Define on first use, not later

6. **Check evidence coverage**
   - Each major section should reference at least 1 figure/table/result
   - No orphaned claims without support

## Editorial Constraints

**CRITICAL - DO NOT:**
- Modify or remove any {{IMAGE_BLOCK_X}}, {{FORMULA_BLOCK_X}}, or {{CODE_BLOCK_X}} placeholders
- Change the section heading structure (##)
- Add or remove major content (this is editing, not rewriting)
- Change technical accuracy or specific numbers
- Translate between languages

**DO:**
- Improve sentence structure and flow
- Shorten overly long paragraphs
- Add transitional phrases
- Remove redundancy
- Improve readability

Output the edited narrative in markdown format.
The placeholders will be replaced with actual content automatically.

Begin the edited version:
""".trimIndent()
    }

    /**
     * Validate that all immutable blocks are present in edited content.
     */
    private fun validateImmutableBlocks(
        editedContent: String,
        immutableBlocks: List<ImmutableBlock>
    ): ValidationResult {
        val missingBlocks = immutableBlocks.filter { block ->
            !editedContent.contains(block.placeholder)
        }

        if (missingBlocks.isNotEmpty()) {
            return ValidationResult(
                success = false,
                error = "Missing ${missingBlocks.size} immutable blocks: ${missingBlocks.map { it.type }}"
            )
        }

        return ValidationResult(success = true, error = null)
    }

    data class ValidationResult(
        val success: Boolean,
        val error: String?
    )
}
