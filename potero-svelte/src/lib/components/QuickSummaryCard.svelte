<script lang="ts">
	import { api, type QuickSummary } from '$lib/api/client';
	import { Sparkles, Clock, RefreshCw } from 'lucide-svelte';

	interface Props {
		paperId: string;
		onGenerateNarrative?: () => void;
	}

	let { paperId, onGenerateNarrative }: Props = $props();

	let summary = $state<QuickSummary | null>(null);
	let isLoading = $state(false);
	let notFound = $state(false);

	$effect(() => {
		if (paperId) loadSummary(paperId);
	});

	async function loadSummary(id: string) {
		isLoading = true;
		notFound = false;
		summary = null;

		const result = await api.getQuickSummary(id);
		if (result.success && result.data) {
			summary = result.data;
		} else {
			notFound = true;
		}
		isLoading = false;
	}
</script>

<div class="rounded-lg border border-border bg-muted/30 px-3 py-2.5">
	<div class="mb-1.5 flex items-center gap-1.5">
		<Sparkles size={12} class="text-amber-500" />
		<span class="text-xs font-medium text-muted-foreground">AI Summary</span>
	</div>

	{#if isLoading}
		<div class="flex items-center gap-2 py-1">
			<div class="h-3 w-3 animate-spin rounded-full border-2 border-muted-foreground border-t-transparent"></div>
			<span class="text-xs text-muted-foreground">Loading...</span>
		</div>
	{:else if summary}
		<p class="line-clamp-4 text-xs leading-relaxed text-foreground/80">{summary.summary}</p>
		{#if summary.estimatedReadTime}
			<div class="mt-1.5 flex items-center gap-1 text-[10px] text-muted-foreground">
				<Clock size={10} />
				<span>{summary.estimatedReadTime} min read</span>
			</div>
		{/if}
	{:else if notFound}
		<div class="flex items-center justify-between">
			<span class="text-xs text-muted-foreground">Summary not generated yet</span>
			{#if onGenerateNarrative}
				<button
					class="flex items-center gap-1 rounded px-2 py-1 text-[11px] text-primary hover:bg-primary/10"
					onclick={onGenerateNarrative}
				>
					<RefreshCw size={10} />
					Generate
				</button>
			{/if}
		</div>
	{/if}
</div>
