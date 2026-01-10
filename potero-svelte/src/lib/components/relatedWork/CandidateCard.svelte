<script lang="ts">
import { Square, CheckSquare, Quote, ExternalLink } from 'lucide-svelte';
import type { RelatedPaperCandidate } from '$lib/types';
import { selectedCandidateIds, toggleCandidateSelection } from '$lib/stores/relatedWork';

interface Props {
	candidate: RelatedPaperCandidate;
}

let { candidate }: Props = $props();

// Derived state for selection
let isSelected = $derived($selectedCandidateIds.includes(candidate.paperId));

// Format authors display
let authorsDisplay = $derived(() => {
	if (candidate.authors.length === 0) return 'Unknown authors';
	if (candidate.authors.length <= 3) return candidate.authors.join(', ');
	return `${candidate.authors.slice(0, 3).join(', ')} et al.`;
});

// Relevance score as percentage
let relevancePercentage = $derived(Math.round(candidate.relevanceScore * 100));

// Relationship type display
let relationshipColor = $derived(() => {
	switch (candidate.relationshipType) {
		case 'CITATION':
			return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300';
		case 'REFERENCE':
			return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
		case 'SEMANTIC_SIMILAR':
			return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300';
		case 'CO_CITATION':
			return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
		default:
			return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-300';
	}
});

function handleToggle() {
	toggleCandidateSelection(candidate.paperId);
}
</script>

<button
	onclick={handleToggle}
	class="group w-full rounded-lg border-2 p-4 text-left transition-all {isSelected
		? 'border-primary bg-primary/10'
		: 'border-border bg-card hover:bg-muted/50 hover:shadow-md'}"
>
	<div class="flex items-start gap-3">
		<!-- Checkbox -->
		<div class="mt-1 flex-shrink-0">
			{#if isSelected}
				<CheckSquare class="h-5 w-5 text-primary" />
			{:else}
				<Square class="h-5 w-5 text-muted-foreground group-hover:text-primary" />
			{/if}
		</div>

		<!-- Content -->
		<div class="flex-1 space-y-2">
			<!-- Title -->
			<h3 class="line-clamp-2 font-semibold text-foreground">
				{candidate.title}
			</h3>

			<!-- Authors -->
			<p class="text-sm text-muted-foreground">
				{authorsDisplay()}
			</p>

			<!-- Metadata badges -->
			<div class="flex flex-wrap items-center gap-2">
				<!-- Year -->
				{#if candidate.year}
					<span class="rounded bg-muted px-2 py-1 text-xs font-medium">
						{candidate.year}
					</span>
				{/if}

				<!-- Citations -->
				<span class="rounded bg-muted px-2 py-1 text-xs font-medium">
					<Quote class="mr-1 inline h-3 w-3" />
					{candidate.citationsCount} citations
				</span>

				<!-- Relationship type -->
				<span class="rounded px-2 py-1 text-xs font-medium {relationshipColor()}">
					{candidate.relationshipType.replace(/_/g, ' ')}
				</span>

				<!-- Source badge -->
				<span class="rounded bg-muted px-2 py-1 text-xs font-medium text-muted-foreground">
					via {candidate.source.replace(/_/g, ' ')}
				</span>

				<!-- DOI link -->
				{#if candidate.doi}
					<a
						href="https://doi.org/{candidate.doi}"
						target="_blank"
						rel="noopener noreferrer"
						onclick={(e) => e.stopPropagation()}
						class="inline-flex items-center gap-1 rounded bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-blue-300"
					>
						DOI
						<ExternalLink class="h-3 w-3" />
					</a>
				{/if}
			</div>

			<!-- Relevance score with progress indicator -->
			<div class="flex items-center gap-2">
				<div class="flex-1">
					<div class="h-2 w-full overflow-hidden rounded-full bg-muted">
						<div
							class="h-full bg-primary transition-all"
							style="width: {relevancePercentage}%"
						></div>
					</div>
				</div>
				<span class="text-xs font-medium text-muted-foreground">
					{relevancePercentage}% relevant
				</span>
			</div>

			<!-- Reasoning (if available) -->
			{#if candidate.reasoning}
				<p class="text-xs italic text-muted-foreground">
					{candidate.reasoning}
				</p>
			{/if}
		</div>
	</div>
</button>
