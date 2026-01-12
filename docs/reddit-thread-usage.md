# Reddit Thread Narrative - Usage Guide

## Overview

The Reddit Thread feature converts academic papers into interactive Reddit-style discussions with:
- **OP (Original Post)**: Paper summary in casual Reddit style
- **7 Role-based Comments**: AI personas (Skeptic, Implementer, Reviewer #2, ELI5, etc.)
- **OP Replies**: AI answers to each comment using evidence from the paper
- **Interactive User Comments**: Users can add comments and get AI replies
- **Claim-based Evidence**: All statements reference specific claims to prevent hallucination

## Architecture

```
Paper (PDF)
    ↓
GROBID Extraction (Figures, Tables, Formulas)
    ↓
Structural Understanding (Claims, Limitations, Related Papers)
    ↓
Reddit Thread Generation (OP + 7 Role Comments + Replies)
    ↓
JSON Storage in Narrative.content
    ↓
UI Rendering / Interactive Comments / Export
```

## Quick Start

### 1. Generate Reddit Thread Narrative

```kotlin
// Using NarrativeEngineService (recommended)
val request = NarrativeGenerationRequest(
    paperId = "paper123",
    styles = listOf(NarrativeStyle.REDDIT),
    languages = listOf(NarrativeLanguage.KOREAN, NarrativeLanguage.ENGLISH)
)

val narratives = narrativeEngine.generate(request).getOrThrow()

// Reddit Thread is stored as JSON in narrative.content
val redditNarrative = narratives.first { it.style == NarrativeStyle.REDDIT }
```

### 2. Get Reddit Thread

```kotlin
// Using RedditThreadService
val redditThreadService = RedditThreadService(narrativeRepository, styleRenderingProcessor)

// Get by narrative ID
val thread = redditThreadService.getRedditThread(narrativeId).getOrThrow()

// Get by paper ID
val (narrative, thread) = redditThreadService.getRedditThreadByPaper(
    paperId = "paper123",
    language = NarrativeLanguage.KOREAN
).getOrThrow()!!

println("OP Score: ${thread.originalPost.score}")
println("Comments: ${thread.comments.size}")
```

### 3. Add User Comment

```kotlin
// User adds a comment to the thread
val userComment = "코드는 공개되어 있나요?"

val (updatedNarrative, updatedThread) = redditThreadService.addUserComment(
    narrativeId = narrativeId,
    userComment = userComment,
    structural = structural,  // From StructuralUnderstandingProcessor
    parentId = null  // Top-level comment
).getOrThrow()

// Updated thread now includes:
// - User's comment
// - AI's reply using claim evidence
```

### 4. Export to Reddit Markdown

```kotlin
// Export to actual Reddit format
val markdown = redditThreadService.exportToMarkdown(narrativeId).getOrThrow()

// Copy to clipboard or save to file
File("reddit_post.md").writeText(markdown)

// Can now paste directly to Reddit!
```

### 5. Get Thread Statistics

```kotlin
val stats = redditThreadService.getThreadStats(narrativeId).getOrThrow()

println("""
Thread Statistics:
- OP Score: ${stats.opScore}
- Total Comments: ${stats.totalComments}
- Top-level: ${stats.topLevelComments}
- User Comments: ${stats.userComments}
- AI Replies: ${stats.aiReplies}
- Roles: ${stats.rolesPresent.joinToString(", ")}
- Total Score: ${stats.totalScore}
- Avg Comment Score: ${stats.avgCommentScore}
""".trimIndent())
```

## Reddit Thread Structure

### RedditThread Model

```kotlin
@Serializable
data class RedditThread(
    val originalPost: RedditPost,
    val comments: List<RedditPost>
)

@Serializable
data class RedditPost(
    val id: String,
    val parentId: String? = null,  // null for OP
    val role: RedditRole? = null,  // null for OP and User
    val author: String,  // "OP", "User", "skeptic_user", etc.
    val content: String,  // Markdown content
    val claimReferences: List<Int> = emptyList(),  // [Claim #0], [Claim #1], ...
    val depth: Int = 0,  // 0=OP, 1=top-level, 2=reply
    val order: Int = 0,  // Sibling order
    val score: Int = 0  // Upvote simulation
)
```

### 7 AI Roles

1. **SKEPTIC** - Challenges methodology and asks for more evidence
2. **IMPLEMENTER** - Asks about code, reproducibility, hyperparameters
3. **REVIEWER_2** - Academic critique (ablations, statistical significance)
4. **ELI5** - Requests simple explanations of technical concepts
5. **RELATED_PAPER** - Mentions related work and comparisons
6. **COMPARATIVE_CRITIC** - Compares with baseline methods
7. **ALTERNATIVE_VIEW** - Proposes alternative interpretations

## Claim System

### Claims in StructuralUnderstanding

Claims are extracted during Stage 1 (Structural Understanding):

```kotlin
@Serializable
data class Claim(
    val statement: String,  // "The method achieves 35% variance reduction"
    val type: ClaimType,  // EMPIRICAL_RESULT
    val evidence: Evidence,
    val confidence: ClaimConfidence  // HIGH
)

@Serializable
data class Evidence(
    val type: EvidenceType,  // FIGURE, TABLE, TEXT_QUOTE, etc.
    val snippet: String,  // 50-200 char quote from paper
    val figureId: String? = null,
    val tableId: String? = null,
    val formulaId: String? = null,
    val sectionReference: String? = null
)
```

### Claim References in Content

All Reddit posts reference claims using `[Claim #X]` notation:

```markdown
**TL;DR**
- This paper improves neural rendering by 35% [Claim #2]
- Works with any BRDF without retraining [Claim #3]
- Beats current state-of-the-art on specular scenes [Claim #5]
```

### Getting Referenced Claims

```kotlin
val claims = redditThreadService.getThreadClaims(narrativeId, structural).getOrThrow()

claims.forEach { claim ->
    println("Claim: ${claim.statement}")
    println("Evidence: ${claim.evidence.snippet}")
    println("Confidence: ${claim.confidence}")
}
```

## Export Formats

### 1. Reddit Markdown

```kotlin
val markdown = thread.toRedditMarkdown()
```

Output:
```markdown
# Paper Title: Neural Rendering with Learned Importance Sampling

**TL;DR**
- Achieves 35% variance reduction [Claim #2]
- Works without retraining [Claim #3]
...

*Posted by u/OP | 812 points*

---

## Comment by u/skeptic_user [SKEPTIC] | 128 points

Did you test this on real-world BRDFs? [Claim #5] mentions synthetic scenes...

> **Reply by u/OP**
>
> Great question! [Claim #6] shows results on MERL database.

---
```

### 2. Plain Text Summary

```kotlin
val summary = thread.toPlainTextSummary()
```

Output:
```
Reddit Thread: Neural Rendering with Learned Importance Sampling
OP Score: 812
Comments: 14
Replies: 7
Roles: SKEPTIC, IMPLEMENTER, REVIEWER_2, ELI5, RELATED_PAPER, COMPARATIVE_CRITIC, ALTERNATIVE_VIEW
```

### 3. JSON

```kotlin
val json = Json.encodeToString<RedditThread>(thread)
```

## API Integration Example

### REST API Endpoints

```kotlin
// GET /api/papers/{paperId}/reddit?language=ko
suspend fun getRedditThread(paperId: String, language: String): RedditThreadResponse {
    val lang = if (language == "ko") NarrativeLanguage.KOREAN else NarrativeLanguage.ENGLISH

    val (narrative, thread) = redditThreadService.getRedditThreadByPaper(
        paperId = paperId,
        language = lang
    ).getOrThrow() ?: throw NotFoundException("Reddit thread not found")

    return RedditThreadResponse(
        narrativeId = narrative.id,
        thread = thread,
        createdAt = narrative.createdAt,
        updatedAt = narrative.updatedAt
    )
}

// POST /api/papers/{paperId}/reddit/comments
suspend fun addComment(
    paperId: String,
    request: AddCommentRequest
): AddCommentResponse {
    // Get structural understanding (cached)
    val structural = structuralUnderstandingProcessor.getFromCache(paperId)
        ?: throw BadRequestException("Paper not processed")

    // Get narrative
    val narrative = narrativeRepository.getByPaperStyleLanguage(
        paperId = paperId,
        style = NarrativeStyle.REDDIT,
        language = request.language
    ).getOrThrow() ?: throw NotFoundException("Narrative not found")

    // Add user comment
    val (updatedNarrative, updatedThread) = redditThreadService.addUserComment(
        narrativeId = narrative.id,
        userComment = request.userComment,
        structural = structural,
        parentId = request.parentId
    ).getOrThrow()

    return AddCommentResponse(
        thread = updatedThread,
        userComment = updatedThread.comments.findLast { it.author == "User" }!!,
        aiReply = updatedThread.comments.last()
    )
}

// GET /api/papers/{paperId}/reddit/export
suspend fun exportMarkdown(paperId: String): String {
    return redditThreadService.exportToMarkdown(narrativeId).getOrThrow()
}
```

### Request/Response Models

```kotlin
@Serializable
data class AddCommentRequest(
    val userComment: String,
    val parentId: String? = null,
    val language: NarrativeLanguage = NarrativeLanguage.KOREAN
)

@Serializable
data class AddCommentResponse(
    val thread: RedditThread,
    val userComment: RedditPost,
    val aiReply: RedditPost
)

@Serializable
data class RedditThreadResponse(
    val narrativeId: String,
    val thread: RedditThread,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

## UI Integration (Svelte Example)

### RedditThreadView.svelte

```svelte
<script lang="ts">
  import { onMount } from 'svelte';
  import type { RedditThread, RedditPost } from '$lib/types';

  export let paperId: string;

  let thread: RedditThread | null = null;
  let loading = true;
  let newComment = '';

  onMount(async () => {
    const res = await fetch(`/api/papers/${paperId}/reddit?language=ko`);
    const data = await res.json();
    thread = data.thread;
    loading = false;
  });

  async function handleAddComment() {
    const res = await fetch(`/api/papers/${paperId}/reddit/comments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userComment: newComment })
    });

    const data = await res.json();
    thread = data.thread;
    newComment = '';
  }
</script>

{#if loading}
  <div>Loading...</div>
{:else if thread}
  <!-- OP -->
  <div class="reddit-post op">
    <div class="score">{thread.originalPost.score}</div>
    <div class="content">
      <div class="author">u/{thread.originalPost.author}</div>
      {@html marked(thread.originalPost.content)}
    </div>
  </div>

  <!-- Comments -->
  {#each thread.comments.filter(c => c.depth === 1) as comment}
    <div class="reddit-comment" style="margin-left: 20px">
      <div class="score">{comment.score}</div>
      <div class="content">
        <div class="author role-{comment.role}">
          u/{comment.author} {#if comment.role}[{comment.role}]{/if}
        </div>
        {@html marked(comment.content)}

        <!-- Replies -->
        {#each thread.comments.filter(c => c.parentId === comment.id) as reply}
          <div class="reply" style="margin-left: 20px">
            <div class="author">u/{reply.author}</div>
            {@html marked(reply.content)}
          </div>
        {/each}
      </div>
    </div>
  {/each}

  <!-- Add Comment Form -->
  <div class="add-comment">
    <textarea bind:value={newComment} placeholder="Add your comment..."></textarea>
    <button on:click={handleAddComment}>Submit</button>
  </div>
{/if}

<style>
  .reddit-post {
    display: flex;
    background: white;
    border: 1px solid #ccc;
    border-radius: 4px;
    padding: 16px;
    margin-bottom: 16px;
  }

  .score {
    min-width: 40px;
    text-align: center;
    font-weight: bold;
    color: #ff4500;
  }

  .author {
    font-weight: bold;
    margin-bottom: 8px;
  }

  .role-SKEPTIC { color: #e74c3c; }
  .role-IMPLEMENTER { color: #3498db; }
  .role-REVIEWER_2 { color: #9b59b6; }
  .role-ELI5 { color: #2ecc71; }
</style>
```

## Performance Considerations

### LLM Calls

Reddit Thread generation makes **9 LLM calls** per narrative:
1. OP generation (1 call)
2. 7 role-based comments (7 calls)
3. 7 OP replies (7 calls in parallel)

Total: **15 calls** (1 + 7 + 7)

### Caching Strategy

```kotlin
// Cache StructuralUnderstanding to avoid re-extracting claims
val cacheKey = "structural_${paperId}"
val cached = narrativeRepository.getCache(paperId, "structural", currentTime).getOrNull()

if (cached != null) {
    structural = Json.decodeFromString(cached.data)
} else {
    structural = structuralUnderstandingProcessor.process(...).getOrThrow()
    narrativeRepository.insertOrReplaceCache(
        NarrativeCache(
            id = cacheKey,
            paperId = paperId,
            stage = "structural",
            data = Json.encodeToString(structural),
            createdAt = currentTime,
            expiresAt = currentTime + 86400000  // 24 hours
        )
    )
}
```

## Testing

### Unit Test Example

```kotlin
class RedditThreadServiceTest {
    @Test
    fun `should add user comment and get AI reply`() = runTest {
        val service = RedditThreadService(mockRepository, mockProcessor)

        val (narrative, thread) = service.addUserComment(
            narrativeId = "narrative123",
            userComment = "Is the code available?",
            structural = mockStructural
        ).getOrThrow()

        // Verify user comment was added
        val userComment = thread.comments.findLast { it.author == "User" }
        assertNotNull(userComment)
        assertEquals("Is the code available?", userComment?.content)

        // Verify AI reply was generated
        val aiReply = thread.comments.last()
        assertEquals("OP", aiReply.author)
        assertEquals(userComment?.id, aiReply.parentId)
        assertTrue(aiReply.content.isNotBlank())
    }
}
```

## Troubleshooting

### Common Issues

1. **"Narrative is not REDDIT style"**
   - Ensure you're using `NarrativeStyle.REDDIT` when generating
   - Check `narrative.style` field

2. **JSON parsing errors**
   - Verify `narrative.content` is valid JSON
   - Check for malformed LLM output

3. **Missing claims**
   - Ensure StructuralUnderstanding was generated with claims extraction
   - Check that claims list is not empty

4. **Interactive comments not working**
   - Verify StructuralUnderstanding is available (not null)
   - Check that narrative.language matches the request language

## Best Practices

1. **Always validate StructuralUnderstanding**
   ```kotlin
   if (structural.claims.isEmpty()) {
       throw IllegalStateException("No claims extracted")
   }
   ```

2. **Use RedditThreadService for all Reddit operations**
   - Don't parse JSON manually
   - Let the service handle serialization

3. **Cache StructuralUnderstanding**
   - Expensive to generate (LLM call)
   - Reuse for interactive comments

4. **Handle LLM failures gracefully**
   - All generation methods have fallbacks
   - Check logs for detailed error messages

5. **Validate claim references**
   ```kotlin
   val maxClaimIndex = structural.claims.size - 1
   val invalidRefs = post.claimReferences.filter { it > maxClaimIndex }
   if (invalidRefs.isNotEmpty()) {
       logger.warn("Invalid claim references: $invalidRefs")
   }
   ```

## Next Steps

- Implement API endpoints in your backend
- Create UI components for rendering threads
- Add user authentication for comment tracking
- Implement real-time updates with WebSockets
- Add moderation features for user comments
