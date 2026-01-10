<script lang="ts">
	import { onMount } from 'svelte';
	import { papers, fuzzySearchPapers, importFromSearchResult } from '$lib/stores/library';
	import { openPaper, openNote, openAuthorProfile, openRelatedWork } from '$lib/stores/tabs';
	import { notes } from '$lib/stores/notes';
	import { api, type SearchResult } from '$lib/api/client';
	import type { Paper, ResearchNote, AuthorProfile, ComparisonTable } from '$lib/types';
	import { Search, X, ArrowRight, Globe, FileText, BookOpen, User, Table } from 'lucide-svelte';

	interface Props {
		onClose: () => void;
	}

	let { onClose }: Props = $props();

	type SearchTab = 'local' | 'online';

	let searchQuery = $state('');
	let activeTab = $state<SearchTab>('local');

	// Local search results
	let paperResults = $state<Paper[]>([]);
	let noteResults = $state<ResearchNote[]>([]);
	let authorResults = $state<string[]>([]);
	let comparisonResults = $state<ComparisonTable[]>([]);

	// Online search results
	let onlineResults = $state<SearchResult[]>([]);
	let isSearchingOnline = $state(false);

	let selectedIndex = $state(0);
	let inputEl: HTMLInputElement;
	let onlineSearchTimer: ReturnType<typeof setTimeout> | null = null;

	// Search local content (papers, notes, authors, comparison tables)
	$effect(() => {
		if (searchQuery.length >= 2) {
			// Search papers
			paperResults = fuzzySearchPapers($papers, searchQuery).slice(0, 5);

			// Search notes
			const lowerQuery = searchQuery.toLowerCase();
			noteResults = $notes
				.filter((note) => {
					const titleMatch = note.title.toLowerCase().includes(lowerQuery);
					const contentMatch = note.content.toLowerCase().includes(lowerQuery);
					return titleMatch || contentMatch;
				})
				.slice(0, 3);

			// Search authors (unique authors from papers)
			const allAuthors = new Set<string>();
			$papers.forEach((paper) => {
				paper.authors.forEach((author) => {
					if (author.toLowerCase().includes(lowerQuery)) {
						allAuthors.add(author);
					}
				});
			});
			authorResults = Array.from(allAuthors).slice(0, 3);

			// Search comparison tables
			api.searchComparisonTables(searchQuery, 3).then((result) => {
				if (result.success && result.data) {
					comparisonResults = result.data;
				}
			});
		} else {
			paperResults = [];
			noteResults = [];
			authorResults = [];
			comparisonResults = [];
			onlineResults = [];
		}
		selectedIndex = 0; // Reset selection when results change
	});

	// Auto-trigger online search in online tab
	$effect(() => {
		// Clear previous timer
		if (onlineSearchTimer) {
			clearTimeout(onlineSearchTimer);
		}

		// Only search online if:
		// 1. In online tab
		// 2. Query is long enough (3+ chars)
		// 3. Not already searching or have results
		if (activeTab === 'online' && searchQuery.length >= 3 && !isSearchingOnline && onlineResults.length === 0) {
			onlineSearchTimer = setTimeout(async () => {
				isSearchingOnline = true;
				const results = await api.searchOnline(searchQuery, 'semantic');
				if (results.success && results.data) {
					onlineResults = results.data.slice(0, 10);
				}
				isSearchingOnline = false;
			}, 500); // Debounce 500ms
		}

		// Clean up timer on unmount
		return () => {
			if (onlineSearchTimer) {
				clearTimeout(onlineSearchTimer);
			}
		};
	});

	// Reset online results when switching tabs
	$effect(() => {
		if (activeTab === 'local') {
			onlineResults = [];
		}
	});

	// Get total number of results based on active tab
	const totalResults = $derived(() => {
		if (activeTab === 'local') {
			return (
				paperResults.length +
				noteResults.length +
				authorResults.length +
				comparisonResults.length
			);
		} else {
			return onlineResults.length;
		}
	});

	async function handleEnter() {
		if (activeTab === 'local') {
			// Local tab navigation
			let offset = 0;

			// Papers
			if (selectedIndex < offset + paperResults.length) {
				openPaper(paperResults[selectedIndex - offset]);
				onClose();
				return;
			}
			offset += paperResults.length;

			// Notes
			if (selectedIndex < offset + noteResults.length) {
				openNote(noteResults[selectedIndex - offset]);
				onClose();
				return;
			}
			offset += noteResults.length;

			// Comparison Tables
			if (selectedIndex < offset + comparisonResults.length) {
				const comparison = comparisonResults[selectedIndex - offset];
				// Find the source paper and open related work tab
				const sourcePaper = $papers.find((p) => p.id === comparison.sourcePaperId);
				if (sourcePaper) {
					openRelatedWork(sourcePaper);
				}
				onClose();
				return;
			}
			offset += comparisonResults.length;

			// Authors
			if (selectedIndex < offset + authorResults.length) {
				const authorName = authorResults[selectedIndex - offset];
				const authorPapers = $papers.filter((p) => p.authors.includes(authorName));
				const authorProfile: AuthorProfile = {
					name: authorName,
					affiliation: null,
					publications: authorPapers.length,
					citations: 0,
					hIndex: 0,
					i10Index: 0,
					overview: null,
					researchInterests: [],
					recentPapers: authorPapers
				};
				openAuthorProfile(authorProfile);
				onClose();
				return;
			}
		} else {
			// Online tab - import paper
			if (selectedIndex < onlineResults.length) {
				await importFromSearchResult(onlineResults[selectedIndex]);
				onClose();
			}
		}
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			onClose();
		} else if (e.key === 'ArrowDown') {
			e.preventDefault();
			const maxIndex = totalResults() - 1;
			selectedIndex = Math.min(selectedIndex + 1, maxIndex);
		} else if (e.key === 'ArrowUp') {
			e.preventDefault();
			selectedIndex = Math.max(selectedIndex - 1, 0);
		} else if (e.key === 'Enter') {
			e.preventDefault();
			handleEnter();
		}
	}

	function switchTab(tab: SearchTab) {
		activeTab = tab;
		selectedIndex = 0;

		// Trigger online search if switching to online tab with query
		if (tab === 'online' && searchQuery.length >= 3 && onlineResults.length === 0 && !isSearchingOnline) {
			setTimeout(async () => {
				isSearchingOnline = true;
				const results = await api.searchOnline(searchQuery, 'semantic');
				if (results.success && results.data) {
					onlineResults = results.data.slice(0, 10);
				}
				isSearchingOnline = false;
			}, 100);
		}
	}

	function handleBackdropClick(e: MouseEvent) {
		if (e.target === e.currentTarget) {
			onClose();
		}
	}

	onMount(() => {
		inputEl?.focus();
	});
</script>

<svelte:window onkeydown={handleKeydown} />

<!-- Modal Backdrop -->
<div
	class="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm pt-20"
	onclick={handleBackdropClick}
	role="dialog"
	aria-modal="true"
	aria-label="Quick search"
>
	<!-- Search Box -->
	<div
		class="w-full max-w-2xl rounded-xl bg-background shadow-2xl"
		onclick={(e) => e.stopPropagation()}
	>
		<!-- Search Input -->
		<div class="flex items-center gap-3 border-b px-4 py-3">
			<Search class="h-5 w-5 text-muted-foreground" />
			<input
				bind:this={inputEl}
				bind:value={searchQuery}
				placeholder="Search everything..."
				class="flex-1 bg-transparent text-lg outline-none"
				aria-label="Search papers, notes, and authors"
			/>
			{#if isSearchingOnline}
				<div
					class="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent"
					aria-label="Searching online"
				></div>
			{/if}
			<button onclick={onClose} class="rounded p-1 hover:bg-muted" aria-label="Close search">
				<X class="h-5 w-5" />
			</button>
		</div>

		<!-- Tabs -->
		<div class="flex border-b">
			<button
				class="flex-1 px-4 py-2 text-sm font-medium transition-colors {activeTab === 'local'
					? 'border-b-2 border-primary text-primary'
					: 'text-muted-foreground hover:text-foreground'}"
				onclick={() => switchTab('local')}
			>
				Local
			</button>
			<button
				class="flex-1 px-4 py-2 text-sm font-medium transition-colors {activeTab === 'online'
					? 'border-b-2 border-primary text-primary'
					: 'text-muted-foreground hover:text-foreground'}"
				onclick={() => switchTab('online')}
			>
				Online
			</button>
		</div>

		<!-- Results -->
		<div class="max-h-[60vh] overflow-y-auto">
			{#if activeTab === 'local'}
				<!-- Papers -->
				{#if paperResults.length > 0}
					<div
						class="border-b bg-muted/30 px-4 py-2 text-xs font-semibold uppercase text-muted-foreground"
					>
						Papers
					</div>
					{#each paperResults as paper, i}
						<button
							class="w-full text-left px-4 py-3 hover:bg-muted transition-colors
								{selectedIndex === i ? 'bg-muted' : ''}"
							onclick={() => {
								openPaper(paper);
								onClose();
							}}
						>
							<div class="flex items-start gap-3">
								<FileText class="h-5 w-5 text-primary shrink-0 mt-0.5" />
								<div class="flex-1 min-w-0">
									<div class="font-medium line-clamp-1">{paper.title}</div>
									<div class="text-sm text-muted-foreground line-clamp-1">
										{paper.authors.slice(0, 2).join(', ')}
										{#if paper.year} · {paper.year}{/if}
									</div>
								</div>
							</div>
						</button>
					{/each}
				{/if}

				<!-- Notes -->
				{#if noteResults.length > 0}
					<div
						class="border-b bg-muted/30 px-4 py-2 text-xs font-semibold uppercase text-muted-foreground"
					>
						Notes
					</div>
					{#each noteResults as note, i}
						<button
							class="w-full text-left px-4 py-3 hover:bg-muted transition-colors
								{selectedIndex === paperResults.length + i ? 'bg-muted' : ''}"
							onclick={() => {
								openNote(note);
								onClose();
							}}
						>
							<div class="flex items-start gap-3">
								<BookOpen class="h-5 w-5 text-purple-500 shrink-0 mt-0.5" />
								<div class="flex-1 min-w-0">
									<div class="font-medium line-clamp-1">{note.title}</div>
									<div class="text-sm text-muted-foreground line-clamp-2">
										{note.content.substring(0, 100)}...
									</div>
								</div>
							</div>
						</button>
					{/each}
				{/if}

				<!-- Comparison Tables -->
				{#if comparisonResults.length > 0}
					<div
						class="border-b bg-muted/30 px-4 py-2 text-xs font-semibold uppercase text-muted-foreground"
					>
						Comparison Tables
					</div>
					{#each comparisonResults as comparison, i}
						<button
							class="w-full text-left px-4 py-3 hover:bg-muted transition-colors
								{selectedIndex === paperResults.length + noteResults.length + i ? 'bg-muted' : ''}"
							onclick={() => {
								const sourcePaper = $papers.find((p) => p.id === comparison.sourcePaperId);
								if (sourcePaper) {
									openRelatedWork(sourcePaper);
								}
								onClose();
							}}
						>
							<div class="flex items-start gap-3">
								<Table class="h-5 w-5 text-purple-500 shrink-0 mt-0.5" />
								<div class="flex-1 min-w-0">
									<div class="font-medium line-clamp-1">{comparison.title}</div>
									{#if comparison.description}
										<div class="text-sm text-muted-foreground line-clamp-1">
											{comparison.description}
										</div>
									{/if}
									<div class="text-xs text-muted-foreground mt-1">
										{new Date(comparison.createdAt).toLocaleDateString()}
									</div>
								</div>
							</div>
						</button>
					{/each}
				{/if}

				<!-- Authors -->
				{#if authorResults.length > 0}
					<div
						class="border-b bg-muted/30 px-4 py-2 text-xs font-semibold uppercase text-muted-foreground"
					>
						Authors
					</div>
					{#each authorResults as author, i}
						<button
							class="w-full text-left px-4 py-3 hover:bg-muted transition-colors
								{selectedIndex === paperResults.length + noteResults.length + comparisonResults.length + i ? 'bg-muted' : ''}"
							onclick={() => {
								const authorPapers = $papers.filter((p) => p.authors.includes(author));
								const authorProfile: AuthorProfile = {
									name: author,
									affiliation: null,
									publications: authorPapers.length,
									citations: 0,
									hIndex: 0,
									i10Index: 0,
									overview: null,
									researchInterests: [],
									recentPapers: authorPapers
								};
								openAuthorProfile(authorProfile);
								onClose();
							}}
						>
							<div class="flex items-start gap-3">
								<User class="h-5 w-5 text-blue-500 shrink-0 mt-0.5" />
								<div class="flex-1 min-w-0">
									<div class="font-medium line-clamp-1">{author}</div>
									<div class="text-sm text-muted-foreground">
										{$papers.filter((p) => p.authors.includes(author)).length} papers
									</div>
								</div>
							</div>
						</button>
					{/each}
				{/if}

				<!-- Empty state for local tab -->
				{#if searchQuery.length >= 2 && paperResults.length === 0 && noteResults.length === 0 && authorResults.length === 0}
					<div class="px-4 py-8 text-center text-muted-foreground">
						No local results found
					</div>
				{/if}
			{:else}
				<!-- Online Results -->
				{#if onlineResults.length > 0}
					<div
						class="border-b bg-muted/30 px-4 py-2 text-xs font-semibold uppercase text-muted-foreground"
					>
						Online Results
					</div>
					{#each onlineResults as result, i}
						<button
							class="w-full text-left px-4 py-3 hover:bg-muted transition-colors
								{selectedIndex === i ? 'bg-muted' : ''}"
							onclick={async () => {
								await importFromSearchResult(result);
								onClose();
							}}
						>
							<div class="flex items-start gap-3">
								<Globe class="h-5 w-5 text-muted-foreground shrink-0 mt-0.5" />
								<div class="flex-1 min-w-0">
									<div class="font-medium line-clamp-1">{result.title}</div>
									<div class="text-sm text-muted-foreground line-clamp-1">
										{result.authors.slice(0, 2).join(', ')}
										{#if result.year} · {result.year}{/if}
										{#if result.citationCount} · {result.citationCount} citations{/if}
									</div>
								</div>
								<ArrowRight class="h-4 w-4 text-muted-foreground" />
							</div>
						</button>
					{/each}
				{:else if isSearchingOnline}
					<div class="px-4 py-8 text-center text-muted-foreground">
						<div class="flex flex-col items-center gap-2">
							<div class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
							<span>Searching online databases...</span>
						</div>
					</div>
				{:else if searchQuery.length >= 3}
					<div class="px-4 py-8 text-center text-muted-foreground">
						No online results found
					</div>
				{:else if searchQuery.length < 3}
					<div class="px-4 py-8 text-center text-muted-foreground">
						Type at least 3 characters to search online
					</div>
				{/if}
			{/if}

			<!-- Initial State -->
			{#if searchQuery.length < 2}
				<div class="px-4 py-8 text-center text-muted-foreground">
					<div class="flex flex-col items-center gap-2">
						<Search class="h-12 w-12 text-muted-foreground/50" />
						<span>Type to search papers, notes, and authors</span>
					</div>
				</div>
			{/if}
		</div>

		<!-- Footer -->
		<div class="border-t px-4 py-2 text-xs text-muted-foreground">
			<div class="flex items-center gap-4">
				<span>↑↓ Navigate</span>
				<span>Enter Open</span>
				<span>Esc Close</span>
				{#if activeTab === 'local'}
					<span class="ml-auto">
						{paperResults.length} papers, {noteResults.length} notes, {authorResults.length} authors
					</span>
				{:else if onlineResults.length > 0}
					<span class="ml-auto">{onlineResults.length} online results</span>
				{/if}
			</div>
		</div>
	</div>
</div>
