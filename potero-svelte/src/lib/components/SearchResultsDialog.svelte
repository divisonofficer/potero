<script lang="ts">
	import { api, type SearchResult, type ConfirmMetadataRequest } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';

	interface Props {
		paperId: string;
		searchQuery: string;
		results: SearchResult[];
		onConfirm: () => void;
		onCancel: () => void;
	}

	let { paperId, searchQuery, results, onConfirm, onCancel }: Props = $props();

	let isLoading = $state(false);
	let selectedResult = $state<SearchResult | null>(null);

	async function confirmSelection(result: SearchResult) {
		isLoading = true;
		selectedResult = result;

		const request: ConfirmMetadataRequest = {
			paperId,
			title: result.title,
			authors: result.authors,
			abstract: result.abstract ?? undefined,
			doi: result.doi ?? undefined,
			arxivId: result.arxivId ?? undefined,
			year: result.year ?? undefined,
			venue: result.venue ?? undefined,
			citationsCount: result.citationCount ?? undefined
		};

		const response = await api.confirmMetadata(request);

		if (response.success) {
			toast.success('Paper metadata updated');
			onConfirm();
		} else {
			toast.error(response.error?.message ?? 'Failed to update metadata');
		}

		isLoading = false;
	}

	function skipSelection() {
		toast.info('Paper saved without additional metadata');
		onCancel();
	}
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" role="dialog" aria-modal="true">
	<div class="flex max-h-[80vh] w-full max-w-2xl flex-col overflow-hidden rounded-lg bg-background shadow-xl">
		<!-- Header -->
		<div class="border-b p-4">
			<h2 class="text-lg font-semibold">Select Matching Paper</h2>
			<p class="mt-1 text-sm text-muted-foreground">
				We found similar papers for: "<span class="font-medium">{searchQuery}</span>"
			</p>
		</div>

		<!-- Results List -->
		<div class="flex-1 overflow-auto p-4">
			{#if results.length === 0}
				<p class="py-8 text-center text-muted-foreground">No matching papers found</p>
			{:else}
				<div class="space-y-3">
					{#each results as result}
						<button
							class="w-full rounded-lg border p-4 text-left transition-colors hover:bg-muted disabled:opacity-50 {selectedResult?.id === result.id ? 'border-primary bg-primary/5' : ''}"
							onclick={() => confirmSelection(result)}
							disabled={isLoading}
						>
							<h3 class="line-clamp-2 font-medium">{result.title}</h3>
							<p class="mt-1 text-sm text-muted-foreground">
								{result.authors.slice(0, 3).join(', ')}{result.authors.length > 3 ? ' et al.' : ''}
								{#if result.year}
									<span class="mx-1">·</span>
									{result.year}
								{/if}
							</p>
							{#if result.venue}
								<span class="mt-2 inline-block rounded bg-muted px-2 py-0.5 text-xs">
									{result.venue}
								</span>
							{/if}
							<div class="mt-2 flex items-center gap-4 text-xs text-muted-foreground">
								<span>{result.citationCount ?? 0} citations</span>
								{#if result.doi}
									<span class="text-primary">DOI</span>
								{/if}
								{#if result.pdfUrl}
									<span class="text-green-600">PDF Available</span>
								{/if}
							</div>
							{#if result.abstract}
								<p class="mt-2 line-clamp-2 text-xs text-muted-foreground">
									{result.abstract}
								</p>
							{/if}
						</button>
					{/each}
				</div>
			{/if}
		</div>

		<!-- Footer -->
		<div class="flex justify-end gap-2 border-t p-4">
			<button
				class="rounded-md px-4 py-2 text-sm hover:bg-muted"
				onclick={skipSelection}
				disabled={isLoading}
			>
				Skip (Keep current info)
			</button>
		</div>
	</div>
</div>
