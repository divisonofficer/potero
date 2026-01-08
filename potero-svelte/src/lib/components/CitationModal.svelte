<script lang="ts">
	import { api, type SearchResult } from '$lib/api/client';

	interface Props {
		query: string;
		onClose: () => void;
		onImport?: (result: SearchResult) => void;
	}

	let { query, onClose, onImport }: Props = $props();

	let results = $state<SearchResult[]>([]);
	let isLoading = $state(true);
	let error = $state<string | null>(null);

	$effect(() => {
		searchCitation();
	});

	async function searchCitation() {
		if (!query || query.length < 3) {
			isLoading = false;
			error = 'Query too short';
			return;
		}

		isLoading = true;
		error = null;

		try {
			const response = await api.searchOnline(query, 'semantic');
			if (response.success && response.data) {
				results = response.data;
			} else {
				error = response.error?.message || 'Search failed';
			}
		} catch (e) {
			error = e instanceof Error ? e.message : 'Search failed';
		} finally {
			isLoading = false;
		}
	}

	function handleImport(result: SearchResult) {
		onImport?.(result);
		onClose();
	}

	function handleBackdropClick(e: MouseEvent) {
		if (e.target === e.currentTarget) {
			onClose();
		}
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			onClose();
		}
	}
</script>

<svelte:window onkeydown={handleKeydown} />

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
	onclick={handleBackdropClick}
>
	<div class="w-full max-w-2xl max-h-[80vh] overflow-hidden rounded-xl bg-white shadow-2xl dark:bg-neutral-800 flex flex-col">
		<!-- Header -->
		<div class="flex items-center justify-between border-b px-6 py-4 dark:border-neutral-700">
			<div>
				<h2 class="text-lg font-semibold text-neutral-900 dark:text-white">Citation Lookup</h2>
				<p class="text-sm text-neutral-500 dark:text-neutral-400 mt-0.5 line-clamp-1">
					Searching: "{query}"
				</p>
			</div>
			<button
				onclick={onClose}
				class="rounded-lg p-2 text-neutral-500 hover:bg-neutral-100 dark:hover:bg-neutral-700"
				title="Close"
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M18 6L6 18M6 6l12 12" />
				</svg>
			</button>
		</div>

		<!-- Content -->
		<div class="flex-1 overflow-y-auto p-6">
			{#if isLoading}
				<div class="flex flex-col items-center justify-center py-12">
					<div class="h-8 w-8 animate-spin rounded-full border-2 border-blue-500 border-t-transparent"></div>
					<p class="mt-3 text-sm text-neutral-500">Searching Semantic Scholar...</p>
				</div>
			{:else if error}
				<div class="flex flex-col items-center justify-center py-12 text-neutral-500">
					<svg class="h-12 w-12 mb-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
					</svg>
					<p>{error}</p>
					<button
						onclick={searchCitation}
						class="mt-4 rounded-md bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-600"
					>
						Retry
					</button>
				</div>
			{:else if results.length === 0}
				<div class="flex flex-col items-center justify-center py-12 text-neutral-500">
					<svg class="h-12 w-12 mb-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
					</svg>
					<p>No results found</p>
					<p class="text-sm mt-1">Try a different search query</p>
				</div>
			{:else}
				<div class="space-y-4">
					{#each results as result}
						<div class="rounded-lg border border-neutral-200 dark:border-neutral-700 p-4 hover:bg-neutral-50 dark:hover:bg-neutral-700/50 transition-colors">
							<h3 class="font-medium text-neutral-900 dark:text-white line-clamp-2">
								{result.title}
							</h3>
							<p class="text-sm text-neutral-600 dark:text-neutral-400 mt-1 line-clamp-1">
								{result.authors.slice(0, 3).join(', ')}{result.authors.length > 3 ? ' et al.' : ''}
							</p>
							<div class="flex flex-wrap items-center gap-2 mt-2 text-xs">
								{#if result.year}
									<span class="rounded bg-neutral-100 dark:bg-neutral-700 px-2 py-0.5">
										{result.year}
									</span>
								{/if}
								{#if result.venue}
									<span class="rounded bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 px-2 py-0.5 line-clamp-1 max-w-48">
										{result.venue}
									</span>
								{/if}
								{#if result.citationCount != null}
									<span class="text-neutral-500 dark:text-neutral-400">
										{result.citationCount.toLocaleString()} citations
									</span>
								{/if}
							</div>

							{#if result.abstract}
								<p class="text-sm text-neutral-600 dark:text-neutral-400 mt-3 line-clamp-3">
									{result.abstract}
								</p>
							{/if}

							<!-- Actions -->
							<div class="flex items-center gap-2 mt-4 pt-3 border-t border-neutral-100 dark:border-neutral-700">
								{#if result.pdfUrl}
									<a
										href={result.pdfUrl}
										target="_blank"
										rel="noopener noreferrer"
										class="flex items-center gap-1 rounded-md bg-red-100 dark:bg-red-900/30 px-3 py-1.5 text-sm text-red-700 dark:text-red-300 hover:bg-red-200 dark:hover:bg-red-900/50"
									>
										<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<path d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
										</svg>
										PDF
									</a>
								{/if}
								{#if result.doi}
									<a
										href="https://doi.org/{result.doi}"
										target="_blank"
										rel="noopener noreferrer"
										class="flex items-center gap-1 rounded-md bg-neutral-100 dark:bg-neutral-700 px-3 py-1.5 text-sm text-neutral-700 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-600"
									>
										<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<path d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
										</svg>
										DOI
									</a>
								{/if}
								<a
									href="https://www.semanticscholar.org/paper/{result.id}"
									target="_blank"
									rel="noopener noreferrer"
									class="flex items-center gap-1 rounded-md bg-neutral-100 dark:bg-neutral-700 px-3 py-1.5 text-sm text-neutral-700 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-600"
								>
									<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9" />
									</svg>
									Semantic Scholar
								</a>
								{#if onImport}
									<button
										onclick={() => handleImport(result)}
										class="ml-auto flex items-center gap-1 rounded-md bg-blue-500 px-3 py-1.5 text-sm text-white hover:bg-blue-600"
									>
										<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<path d="M12 4v16m8-8H4" />
										</svg>
										Add to Library
									</button>
								{/if}
							</div>
						</div>
					{/each}
				</div>
			{/if}
		</div>
	</div>
</div>
