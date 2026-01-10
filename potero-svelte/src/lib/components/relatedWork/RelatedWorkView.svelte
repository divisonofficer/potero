<script lang="ts">
import { RefreshCw, ChevronDown, ChevronRight, FileText } from 'lucide-svelte';
import type { Paper } from '$lib/types';
import {
	candidates,
	selectedCandidateIds,
	filteredCandidates,
	currentComparison,
	isSearching,
	isGenerating,
	error,
	searchRelatedPapers,
	generateComparison,
	exportComparison,
	selectAllCandidates,
	deselectAllCandidates
} from '$lib/stores/relatedWork';
import CandidateCard from './CandidateCard.svelte';
import FilterPanel from './FilterPanel.svelte';
import ComparisonTable from './ComparisonTable.svelte';
import NarrativeSummary from './NarrativeSummary.svelte';

interface Props {
	paper: Paper;
	tabId: string;
}

let { paper, tabId }: Props = $props();

// Local state
let showFilters = $state(true);
let showComparison = $state(false);
let comparisonTitle = $state(`Related Work Comparison: ${paper.title.slice(0, 40)}...`);

// Auto-search on mount
$effect(() => {
	searchRelatedPapers(paper.id, 20, false);
});

// Show comparison section when data is available
$effect(() => {
	if ($currentComparison) {
		showComparison = true;
	}
});

function handleGenerateComparison() {
	generateComparison(paper.id, comparisonTitle);
}

function handleRefresh() {
	searchRelatedPapers(paper.id, 20, true);
}

function toggleFilters() {
	showFilters = !showFilters;
}

function toggleComparison() {
	showComparison = !showComparison;
}
</script>

<div class="flex h-full flex-col bg-background">
	<!-- Header -->
	<div class="bg-gradient-to-r from-blue-600 to-indigo-600 p-4 text-white">
		<div class="flex items-center justify-between">
			<div class="flex-1">
				<h2 class="text-lg font-semibold">
					{paper.title.length > 60 ? paper.title.slice(0, 60) + '...' : paper.title}
				</h2>
				<p class="mt-1 text-sm text-blue-100">
					Finding related papers and generating comparison tables
				</p>
			</div>
			<button
				onclick={handleRefresh}
				disabled={$isSearching}
				class="ml-4 rounded-lg bg-white/20 px-3 py-2 text-sm font-medium transition-colors hover:bg-white/30 disabled:opacity-50"
			>
				<RefreshCw class={`h-4 w-4 ${$isSearching ? 'animate-spin' : ''}`} />
			</button>
		</div>
	</div>

	<!-- Error message -->
	{#if $error}
		<div class="border-l-4 border-red-500 bg-red-50 p-4 dark:bg-red-900/20">
			<p class="text-sm text-red-800 dark:text-red-200">{$error}</p>
		</div>
	{/if}

	<div class="flex-1 overflow-y-auto">
		<!-- Search Status -->
		<div class="border-b border-border bg-muted/30 p-4">
			<div class="flex items-center justify-between">
				<div>
					{#if $isSearching}
						<p class="text-sm font-medium text-muted-foreground">Searching for related papers...</p>
					{:else if $candidates.length > 0}
						<p class="text-sm font-medium">
							Found <span class="text-primary">{$filteredCandidates.length}</span> related papers
							{#if $filteredCandidates.length !== $candidates.length}
								(filtered from {$candidates.length})
							{/if}
						</p>
					{:else}
						<p class="text-sm text-muted-foreground">No related papers found</p>
					{/if}
				</div>

				{#if $candidates.length > 0}
					<div class="flex items-center gap-2">
						<button
							onclick={selectAllCandidates}
							class="text-xs text-muted-foreground hover:text-foreground"
						>
							Select All
						</button>
						<span class="text-muted-foreground">|</span>
						<button
							onclick={deselectAllCandidates}
							class="text-xs text-muted-foreground hover:text-foreground"
						>
							Clear
						</button>
					</div>
				{/if}
			</div>
		</div>

		<!-- Filter Panel (Collapsible) -->
		{#if $candidates.length > 0}
			<div class="border-b border-border">
				<button
					onclick={toggleFilters}
					class="flex w-full items-center justify-between bg-muted/20 px-4 py-3 hover:bg-muted/40"
				>
					<span class="text-sm font-medium">Filters</span>
					{#if showFilters}
						<ChevronDown class="h-4 w-4" />
					{:else}
						<ChevronRight class="h-4 w-4" />
					{/if}
				</button>

				{#if showFilters}
					<FilterPanel candidates={$candidates} />
				{/if}
			</div>
		{/if}

		<!-- Candidates List -->
		{#if $filteredCandidates.length > 0}
			<div class="p-4">
				<div class="mb-4 flex items-center justify-between">
					<h3 class="text-sm font-medium text-muted-foreground">
						Select papers to compare ({$selectedCandidateIds.length} selected)
					</h3>

					{#if $selectedCandidateIds.length > 0}
						<button
							onclick={handleGenerateComparison}
							disabled={$isGenerating}
							class="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
						>
							{#if $isGenerating}
								<RefreshCw class="h-4 w-4 animate-spin" />
								Generating...
							{:else}
								<FileText class="h-4 w-4" />
								Generate Comparison
							{/if}
						</button>
					{/if}
				</div>

				<div class="space-y-3">
					{#each $filteredCandidates as candidate (candidate.paperId)}
						<CandidateCard {candidate} />
					{/each}
				</div>
			</div>
		{/if}

		<!-- Comparison Section -->
		{#if $currentComparison && showComparison}
			<div class="border-t border-border">
				<button
					onclick={toggleComparison}
					class="flex w-full items-center justify-between bg-muted/20 px-4 py-3 hover:bg-muted/40"
				>
					<span class="text-sm font-medium">Comparison Results</span>
					<ChevronDown class="h-4 w-4" />
				</button>

				<div class="p-4">
					<ComparisonTable comparison={$currentComparison} />

					{#if $currentComparison.narrative}
						<div class="mt-6">
							<NarrativeSummary narrative={$currentComparison.narrative} />
						</div>
					{/if}
				</div>
			</div>
		{/if}
	</div>
</div>

<style>
	/* Custom scrollbar for dark mode */
	:global(.dark) div::-webkit-scrollbar {
		width: 8px;
	}

	:global(.dark) div::-webkit-scrollbar-track {
		background: transparent;
	}

	:global(.dark) div::-webkit-scrollbar-thumb {
		background: rgba(255, 255, 255, 0.2);
		border-radius: 4px;
	}

	:global(.dark) div::-webkit-scrollbar-thumb:hover {
		background: rgba(255, 255, 255, 0.3);
	}
</style>
