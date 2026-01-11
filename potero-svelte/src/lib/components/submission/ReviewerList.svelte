<script lang="ts">
	import type { Reviewer, ReviewRound } from '$lib/types';
	import ReviewerCard from './ReviewerCard.svelte';
	import RoundSelector from './RoundSelector.svelte';

	interface Props {
		rounds: ReviewRound[];
		currentRound: ReviewRound | null;
		selectedRoundNumber: number;
		onRoundChange: (roundNumber: number) => void;
	}

	let { rounds, currentRound, selectedRoundNumber, onRoundChange }: Props = $props();

	let activeTab = $state<'all' | 'queue' | 'individual'>('all');
	let expandedReviewerId = $state<string | null>(null);

	let reviewers = $derived(currentRound?.reviewers ?? []);

	let filteredReviewers = $derived(() => {
		switch (activeTab) {
			case 'queue':
				return reviewers.filter((r) => ['invited', 'accepted'].includes(r.status));
			case 'individual':
				return reviewers.filter((r) => r.status === 'completed');
			case 'all':
			default:
				return reviewers;
		}
	});

	let counts = $derived({
		all: reviewers.length,
		queue: reviewers.filter((r) => ['invited', 'accepted'].includes(r.status)).length,
		individual: reviewers.filter((r) => r.status === 'completed').length
	});

	function toggleReviewerExpand(id: string) {
		expandedReviewerId = expandedReviewerId === id ? null : id;
	}
</script>

<div class="h-full flex flex-col">
	<!-- Header with Round Selector -->
	<div class="flex items-center justify-between mb-4">
		<RoundSelector {rounds} selectedRound={selectedRoundNumber} {onRoundChange} />
	</div>

	<!-- Reviews panel -->
	<div class="flex-1 rounded-lg border bg-white overflow-hidden flex flex-col">
		<!-- Panel header -->
		<div class="flex items-center justify-between px-4 py-3 border-b bg-muted/30">
			<div class="flex items-center gap-2">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					width="18"
					height="18"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
					stroke-linejoin="round"
					class="text-primary"
				>
					<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
					<circle cx="9" cy="7" r="4" />
					<path d="M22 21v-2a4 4 0 0 0-3-3.87" />
					<path d="M16 3.13a4 4 0 0 1 0 7.75" />
				</svg>
				<span class="font-medium">Reviews</span>
			</div>

			<!-- Tabs -->
			<div class="flex items-center gap-1 bg-muted rounded-lg p-1">
				<button
					class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors
                           {activeTab === 'all' ? 'bg-white text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'}"
					onclick={() => (activeTab = 'all')}
				>
					All ({counts.all})
				</button>
				<button
					class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors
                           {activeTab === 'queue' ? 'bg-white text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'}"
					onclick={() => (activeTab = 'queue')}
				>
					Queue ({counts.queue})
				</button>
				<button
					class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors
                           {activeTab === 'individual' ? 'bg-white text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'}"
					onclick={() => (activeTab = 'individual')}
				>
					Individual ({counts.individual})
				</button>
			</div>
		</div>

		<!-- Table header -->
		<div class="flex items-center px-4 py-2 border-b text-xs text-muted-foreground font-medium">
			<span class="w-6">#</span>
			<span class="flex-1 ml-4">Reviewer Name</span>
			<span class="w-48 text-right">Response Due Date</span>
			<span class="w-8"></span>
		</div>

		<!-- Reviewer list -->
		<div class="flex-1 overflow-y-auto">
			{#if filteredReviewers().length === 0}
				<div class="flex items-center justify-center h-32 text-muted-foreground">
					No reviewers found
				</div>
			{:else}
				{#each filteredReviewers() as reviewer, i}
					<ReviewerCard
						{reviewer}
						index={i + 1}
						isExpanded={expandedReviewerId === reviewer.id}
						onToggleExpand={() => toggleReviewerExpand(reviewer.id)}
					/>
				{/each}
			{/if}
		</div>
	</div>
</div>
