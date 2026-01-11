<script lang="ts">
	import { ChevronDown, Check } from 'lucide-svelte';
	import type { ReviewRound } from '$lib/types';

	interface Props {
		rounds: ReviewRound[];
		selectedRound: number;
		onRoundChange: (roundNumber: number) => void;
	}

	let { rounds, selectedRound, onRoundChange }: Props = $props();

	let isOpen = $state(false);
	let dropdownRef = $state<HTMLDivElement | null>(null);

	let selectedRoundData = $derived(rounds.find((r) => r.roundNumber === selectedRound));

	function handleClickOutside(event: MouseEvent) {
		if (dropdownRef && !dropdownRef.contains(event.target as Node)) {
			isOpen = false;
		}
	}

	function formatDate(dateString: string): string {
		return new Date(dateString).toLocaleDateString('en-US', {
			month: 'short',
			day: 'numeric',
			year: 'numeric'
		});
	}

	$effect(() => {
		if (isOpen) {
			document.addEventListener('click', handleClickOutside);
		} else {
			document.removeEventListener('click', handleClickOutside);
		}

		return () => {
			document.removeEventListener('click', handleClickOutside);
		};
	});
</script>

<div class="relative" bind:this={dropdownRef}>
	<button
		class="flex items-center gap-2 rounded-lg border bg-white px-4 py-2 text-sm font-medium hover:bg-muted transition-colors"
		onclick={() => (isOpen = !isOpen)}
		aria-expanded={isOpen}
		aria-haspopup="listbox"
	>
		<span class="text-muted-foreground">Round</span>
		<span
			class="inline-flex items-center rounded bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
		>
			{selectedRound} Round
		</span>
		<ChevronDown class="h-4 w-4 text-muted-foreground transition-transform {isOpen ? 'rotate-180' : ''}" />
	</button>

	{#if isOpen}
		<div
			class="absolute left-0 top-full mt-1 w-64 rounded-lg border bg-white shadow-lg z-50"
			role="listbox"
			aria-label="Select review round"
		>
			{#each rounds as round}
				<button
					class="w-full px-4 py-3 text-left text-sm hover:bg-muted transition-colors flex items-center justify-between
                           {round.roundNumber === selectedRound ? 'bg-muted/50' : ''}"
					onclick={() => {
						onRoundChange(round.roundNumber);
						isOpen = false;
					}}
					role="option"
					aria-selected={round.roundNumber === selectedRound}
				>
					<div>
						<div class="flex items-center gap-2">
							<span class="font-medium">Round {round.roundNumber}</span>
							<span
								class="inline-flex items-center rounded px-1.5 py-0.5 text-xs capitalize
                                       {round.status === 'active'
									? 'bg-green-100 text-green-700'
									: 'bg-gray-100 text-gray-700'}"
							>
								{round.status}
							</span>
						</div>
						<p class="text-xs text-muted-foreground mt-0.5">
							Started: {formatDate(round.startDate)}
							{#if round.endDate}
								- Ended: {formatDate(round.endDate)}
							{/if}
						</p>
					</div>
					{#if round.roundNumber === selectedRound}
						<Check class="h-4 w-4 text-primary" />
					{/if}
				</button>
			{/each}
		</div>
	{/if}
</div>
