<script lang="ts">
import { X } from 'lucide-svelte';
import type { RelatedPaperCandidate } from '$lib/types';
import { yearRangeFilter, minCitationsFilter, venueFilter } from '$lib/stores/relatedWork';

interface Props {
	candidates: RelatedPaperCandidate[];
}

let { candidates }: Props = $props();

// Local state for filter inputs
let yearMin = $state<number | null>(null);
let yearMax = $state<number | null>(null);
let minCitations = $state(0);
let selectedRelationships = $state<string[]>([]);

// Extract unique relationship types from candidates
let uniqueRelationships = $derived(
	Array.from(new Set(candidates.map((c) => c.relationshipType))).sort()
);

// Extract year range from candidates
let candidateYearRange = $derived(() => {
	const years = candidates.map((c) => c.year).filter((y): y is number => y !== null);
	if (years.length === 0) return null;
	return {
		min: Math.min(...years),
		max: Math.max(...years)
	};
});

// Update stores when local state changes
$effect(() => {
	yearRangeFilter.set(yearMin && yearMax ? [yearMin, yearMax] : null);
});

$effect(() => {
	minCitationsFilter.set(minCitations);
});

$effect(() => {
	venueFilter.set(selectedRelationships);
});

function clearFilters() {
	yearMin = null;
	yearMax = null;
	minCitations = 0;
	selectedRelationships = [];
}

function toggleRelationship(relationship: string) {
	if (selectedRelationships.includes(relationship)) {
		selectedRelationships = selectedRelationships.filter((r) => r !== relationship);
	} else {
		selectedRelationships = [...selectedRelationships, relationship];
	}
}
</script>

<div class="space-y-4 bg-muted/10 p-4">
	<!-- Year Range Filter -->
	<div>
		<label class="mb-2 block text-sm font-medium">Publication Year</label>
		<div class="flex items-center gap-2">
			<input
				type="number"
				bind:value={yearMin}
				placeholder={candidateYearRange()?.min.toString() ?? 'Min'}
				min={candidateYearRange()?.min}
				max={yearMax ?? candidateYearRange()?.max}
				class="w-24 rounded border border-border bg-background px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
			/>
			<span class="text-muted-foreground">to</span>
			<input
				type="number"
				bind:value={yearMax}
				placeholder={candidateYearRange()?.max.toString() ?? 'Max'}
				min={yearMin ?? candidateYearRange()?.min}
				max={candidateYearRange()?.max}
				class="w-24 rounded border border-border bg-background px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
			/>
		</div>
	</div>

	<!-- Citations Filter -->
	<div>
		<label for="min-citations" class="mb-2 block text-sm font-medium">Minimum Citations</label>
		<input
			id="min-citations"
			type="number"
			bind:value={minCitations}
			min="0"
			placeholder="0"
			class="w-32 rounded border border-border bg-background px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
		/>
	</div>

	<!-- Relationship Type Filter -->
	{#if uniqueRelationships.length > 0}
		<div>
			<label class="mb-2 block text-sm font-medium">Relationship Type</label>
			<div class="flex flex-wrap gap-2">
				{#each uniqueRelationships as relationship}
					<button
						onclick={() => toggleRelationship(relationship)}
						class="rounded border px-3 py-1.5 text-sm transition-colors {selectedRelationships.includes(
							relationship
						)
							? 'border-primary bg-primary text-primary-foreground'
							: 'border-border bg-background hover:bg-muted'}"
					>
						{relationship.replace(/_/g, ' ')}
					</button>
				{/each}
			</div>
		</div>
	{/if}

	<!-- Clear Filters Button -->
	<div class="flex justify-end">
		<button
			onclick={clearFilters}
			class="flex items-center gap-2 rounded bg-muted px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted/80 hover:text-foreground"
		>
			<X class="h-4 w-4" />
			Clear Filters
		</button>
	</div>

	<!-- Active Filters Summary -->
	{#if yearMin || yearMax || minCitations > 0 || selectedRelationships.length > 0}
		<div class="border-t border-border pt-3">
			<p class="text-xs font-medium text-muted-foreground">Active Filters:</p>
			<div class="mt-2 flex flex-wrap gap-2">
				{#if yearMin || yearMax}
					<span class="rounded bg-primary/10 px-2 py-1 text-xs font-medium text-primary">
						Year: {yearMin ?? '?'} - {yearMax ?? '?'}
					</span>
				{/if}
				{#if minCitations > 0}
					<span class="rounded bg-primary/10 px-2 py-1 text-xs font-medium text-primary">
						Citations ≥ {minCitations}
					</span>
				{/if}
				{#each selectedRelationships as relationship}
					<span class="rounded bg-primary/10 px-2 py-1 text-xs font-medium text-primary">
						{relationship.replace(/_/g, ' ')}
					</span>
				{/each}
			</div>
		</div>
	{/if}
</div>
