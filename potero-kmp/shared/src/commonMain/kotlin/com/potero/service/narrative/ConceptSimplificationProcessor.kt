package com.potero.service.narrative

import com.potero.domain.model.ConceptExplanation
import com.potero.service.llm.LLMService
import com.potero.service.llm.LLMLogger
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Stage 3: Concept Simplification
 *
 * Purpose: Generate accessible explanations for prerequisite concepts.
 * These explanations are reused across all narrative styles.
 */
class ConceptSimplificationProcessor(
    private val llmService: LLMService,
    private val llmLogger: LLMLogger
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    /**
     * Process concepts to generate explanations
     *
     * @param concepts List of technical concepts to explain
     * @param paperContext Brief context about the paper for relevance
     * @param narrativeId ID of the narrative these explanations belong to
     */
    suspend fun process(
        concepts: List<String>,
        paperContext: String,
        narrativeId: String
    ): Result<List<ConceptExplanation>> = runCatching {
        if (concepts.isEmpty()) {
            return@runCatching emptyList()
        }

        val prompt = buildSimplificationPrompt(concepts, paperContext)

        val startTime = System.currentTimeMillis()
        val llmResult = llmService.chat(prompt)
        val endTime = System.currentTimeMillis()

        llmResult.fold(
            onSuccess = { response ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_concept_simplification",
                    inputPrompt = prompt,
                    outputResponse = response,
                    durationMs = endTime - startTime,
                    success = true
                )

                parseConceptExplanations(response, narrativeId)
            },
            onFailure = { error ->
                llmLogger.log(
                    provider = llmService.provider,
                    purpose = "narrative_concept_simplification",
                    inputPrompt = prompt,
                    outputResponse = null,
                    durationMs = endTime - startTime,
                    success = false,
                    errorMessage = error.message
                )
                throw error
            }
        )
    }

    private fun buildSimplificationPrompt(
        concepts: List<String>,
        paperContext: String
    ): String = """
You are an expert at explaining complex technical concepts to general audiences.

## Concepts to Explain
${concepts.mapIndexed { i, c -> "${i + 1}. $c" }.joinToString("\n")}

## Context
These concepts appear in a paper about: ${paperContext.take(500)}

## Your Task
For each concept, provide:
1. A clear, jargon-free definition (1-2 sentences, under 50 words)
2. A relatable analogy if helpful (from everyday experience)
3. Related terms readers might encounter

Respond in JSON:
{
    "concepts": [
        {
            "term": "Transformer",
            "definition": "A type of neural network that excels at understanding relationships between different parts of data, like words in a sentence.",
            "analogy": "Like a very attentive reader who considers how every word in a sentence relates to every other word.",
            "relatedTerms": ["attention mechanism", "BERT", "GPT"]
        },
        {
            "term": "Gradient Descent",
            "definition": "An optimization technique that iteratively adjusts parameters to minimize errors, like finding the lowest point in a valley.",
            "analogy": "Imagine rolling a ball down a hill - it naturally finds the lowest point by following the steepest path downward.",
            "relatedTerms": ["learning rate", "backpropagation", "optimization"]
        }
    ]
}

Guidelines:
- Avoid circular definitions (don't use the term to define itself)
- Use everyday language that a high school student could understand
- Analogies should be from common experience (not other technical fields)
- Keep definitions under 50 words
- Include 2-4 related terms
- If a concept is too basic (like "algorithm"), provide a brief refresher anyway

Respond with ONLY the JSON object, no additional text.
""".trimIndent()

    private fun parseConceptExplanations(
        response: String,
        narrativeId: String
    ): List<ConceptExplanation> {
        return try {
            var jsonText = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            jsonText = jsonText.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()

            val jsonStart = jsonText.indexOf('{')
            val jsonEnd = jsonText.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonText = jsonText.substring(jsonStart, jsonEnd + 1)
            }

            val parsed = json.decodeFromString<ConceptsResponseJson>(jsonText)
            val now = Clock.System.now()

            parsed.concepts?.map { c ->
                ConceptExplanation(
                    id = UUID.randomUUID().toString(),
                    narrativeId = narrativeId,
                    term = c.term ?: "Unknown",
                    definition = c.definition ?: "Definition not available",
                    analogy = c.analogy,
                    relatedTerms = c.relatedTerms ?: emptyList(),
                    createdAt = now
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("[ConceptSimplification] Failed to parse LLM response: ${e.message}")
            println("[ConceptSimplification] Response was: $response")
            emptyList()
        }
    }
}

@Serializable
private data class ConceptsResponseJson(
    val concepts: List<ConceptJson>? = null
)

@Serializable
private data class ConceptJson(
    val term: String? = null,
    val definition: String? = null,
    val analogy: String? = null,
    val relatedTerms: List<String>? = null
)
