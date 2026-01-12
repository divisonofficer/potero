<script lang="ts">
	import type { Readable } from 'svelte/store';
	import type { Paper } from '$lib/types';
	import { List, Grid, LayoutGrid, RefreshCw, Plus, Search } from 'lucide-svelte';

	interface Props {
		papers: Readable<Paper[]>;
		selectedPaperIds: string[];
		viewMode?: 'list' | 'compact' | 'grid';
		sortBy?: 'added' | 'year' | 'citations' | 'title';
		sortDirection?: 'asc' | 'desc';
		searchQuery?: string;
		isLoading?: boolean;
		onSelectPaper: (paperId: string, multi?: boolean) => void;
		onOpenPaper: (paper: Paper) => void;
		onDeletePaper?: (paper: Paper) => void;
		onViewModeChange?: (mode: 'list' | 'compact' | 'grid') => void;
		onSortChange?: (sortBy: 'added' | 'year' | 'citations' | 'title') => void;
		onToggleSortDirection?: () => void;
		onSearchChange?: (query: string) => void;
		onAddPaper?: () => void;
		onRefresh?: () => void;
	}

	let {
		papers,
		selectedPaperIds,
		viewMode = 'list',
		sortBy = 'added',
		sortDirection = 'desc',
		searchQuery = '',
		isLoading = false,
		onSelectPaper,
		onOpenPaper,
		onDeletePaper,
		onViewModeChange,
		onSortChange,
		onToggleSortDirection,
		onSearchChange,
		onAddPaper,
		onRefresh
	}: Props = $props();

	function isSelected(paperId: string): boolean {
		return selectedPaperIds.includes(paperId);
	}

	function handleClick(e: MouseEvent, paperId: string) {
		const multi = e.metaKey || e.ctrlKey || e.shiftKey;
		onSelectPaper(paperId, multi);
	}

	function handleDoubleClick(paper: Paper) {
		onOpenPaper(paper);
	}

	function formatAuthors(authors: string[]): string {
		if (!authors || authors.length === 0) return 'Unknown';
		if (authors.length === 1) return authors[0];
		if (authors.length === 2) return authors.join(' & ');
		return `${authors[0]} et al.`;
	}
</script>

<div class="flex h-full flex-col overflow-hidden">
	<!-- Toolbar -->
	<div class="flex items-center gap-2 border-b border-border/30 px-3 py-2 bg-[hsl(var(--glass-bg))] backdrop-blur-[var(--blur-sm)]">
		<!-- Search -->
		<div class="relative flex-1 max-w-xs">
			<Search class="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
			<input
				type="text"
				placeholder="Filter papers..."
				value={searchQuery}
				oninput={(e) => onSearchChange?.(e.currentTarget.value)}
				class="h-8 w-full rounded-md border border-border/50 bg-background/50 pl-8 pr-3 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
			/>
		</div>

		<!-- Sort -->
		<select
			class="h-8 rounded-md border border-border/50 bg-background/50 px-2 text-sm focus:border-primary focus:outline-none"
			value={sortBy}
			onchange={(e) => onSortChange?.(e.currentTarget.value as 'added' | 'year' | 'citations' | 'title')}
		>
			<option value="added">Date Added</option>
			<option value="year">Year</option>
			<option value="citations">Citations</option>
			<option value="title">Title</option>
		</select>

		<!-- View Mode Toggle -->
		<div class="flex items-center gap-0.5 rounded-md border border-border/50 p-0.5">
			<button
				class="rounded p-1.5 transition-colors {viewMode === 'list' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}"
				onclick={() => onViewModeChange?.('list')}
				title="List view"
			>
				<List class="h-4 w-4" />
			</button>
			<button
				class="rounded p-1.5 transition-colors {viewMode === 'compact' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}"
				onclick={() => onViewModeChange?.('compact')}
				title="Compact view"
			>
				<LayoutGrid class="h-4 w-4" />
			</button>
			<button
				class="rounded p-1.5 transition-colors {viewMode === 'grid' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}"
				onclick={() => onViewModeChange?.('grid')}
				title="Grid view"
			>
				<Grid class="h-4 w-4" />
			</button>
		</div>

		<!-- Refresh Button -->
		<button
			class="rounded p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
			onclick={onRefresh}
			title="Refresh library"
		>
			<RefreshCw class="h-4 w-4 {isLoading ? 'animate-spin' : ''}" />
		</button>

		<!-- Add Button -->
		<button
			class="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors flex items-center gap-1"
			onclick={onAddPaper}
		>
			<Plus class="h-4 w-4" />
			Add
		</button>
	</div>

	<!-- Paper List -->
	<div class="flex-1 overflow-y-auto">
		{#if isLoading}
			<div class="flex h-full flex-col items-center justify-center p-6 text-center">
				<div class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
				<p class="mt-2 text-sm text-muted-foreground">Loading papers...</p>
			</div>
		{:else if $papers.length === 0}
			<div class="flex h-full flex-col items-center justify-center p-6 text-center">
				<div class="rounded-full bg-muted/50 p-4 mb-4">
					<svg class="h-8 w-8 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
					</svg>
				</div>
				<h3 class="text-sm font-medium text-foreground">No papers found</h3>
				<p class="mt-1 text-xs text-muted-foreground">
					Add papers by dropping PDFs or using the Add button
				</p>
				<button
					class="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
					onclick={onAddPaper}
				>
					Add Paper
				</button>
			</div>
		{:else if viewMode === 'list'}
			<!-- List View -->
			<div class="divide-y divide-border/20">
				{#each $papers as paper (paper.id)}
					<button
						class="w-full px-4 py-3 text-left transition-colors hover:bg-[hsl(var(--sidebar-item-hover))] {isSelected(paper.id) ? 'bg-[hsl(var(--sidebar-item-active))]' : ''}"
						onclick={(e) => handleClick(e, paper.id)}
						ondblclick={() => handleDoubleClick(paper)}
					>
						<div class="flex items-start gap-3">
							<div class="flex-1 min-w-0">
								<h3 class="font-medium text-sm leading-tight line-clamp-2 {isSelected(paper.id) ? 'text-primary' : 'text-foreground'}">
									{paper.title}
								</h3>
								<p class="mt-1 text-xs text-muted-foreground line-clamp-1">
									{formatAuthors(paper.authors)}
									{#if paper.year}
										<span class="mx-1">·</span>
										{paper.year}
									{/if}
									{#if paper.venue}
										<span class="mx-1">·</span>
										<span class="truncate">{paper.venue}</span>
									{/if}
								</p>
								{#if paper.abstract}
									<p class="mt-1.5 text-xs text-muted-foreground/70 line-clamp-2">
										{paper.abstract}
									</p>
								{/if}
							</div>
							<div class="shrink-0 text-right">
								{#if paper.citations && paper.citations > 0}
									<span class="text-xs font-medium text-muted-foreground">
										{paper.citations.toLocaleString()}
									</span>
								{/if}
							</div>
						</div>
					</button>
				{/each}
			</div>
		{:else if viewMode === 'compact'}
			<!-- Compact View -->
			<div class="divide-y divide-border/20">
				{#each $papers as paper (paper.id)}
					<button
						class="w-full px-4 py-2 text-left transition-colors hover:bg-[hsl(var(--sidebar-item-hover))] {isSelected(paper.id) ? 'bg-[hsl(var(--sidebar-item-active))]' : ''}"
						onclick={(e) => handleClick(e, paper.id)}
						ondblclick={() => handleDoubleClick(paper)}
					>
						<div class="flex items-center gap-3">
							<div class="flex-1 min-w-0">
								<h3 class="font-medium text-sm leading-tight truncate {isSelected(paper.id) ? 'text-primary' : 'text-foreground'}">
									{paper.title}
								</h3>
							</div>
							<div class="shrink-0 flex items-center gap-3 text-xs text-muted-foreground">
								<span>{formatAuthors(paper.authors)}</span>
								{#if paper.year}
									<span>{paper.year}</span>
								{/if}
								{#if paper.citations && paper.citations > 0}
									<span class="font-medium">{paper.citations}</span>
								{/if}
							</div>
						</div>
					</button>
				{/each}
			</div>
		{:else}
			<!-- Grid View -->
			<div class="grid grid-cols-2 gap-3 p-4 lg:grid-cols-3">
				{#each $papers as paper (paper.id)}
					<button
						class="rounded-xl p-4 text-left transition-all glass hover:shadow-glass {isSelected(paper.id) ? 'ring-2 ring-primary shadow-glass' : ''}"
						onclick={(e) => handleClick(e, paper.id)}
						ondblclick={() => handleDoubleClick(paper)}
					>
						<h3 class="font-medium text-sm leading-tight line-clamp-3 {isSelected(paper.id) ? 'text-primary' : 'text-foreground'}">
							{paper.title}
						</h3>
						<p class="mt-2 text-xs text-muted-foreground line-clamp-1">
							{formatAuthors(paper.authors)}
						</p>
						<div class="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
							{#if paper.year}
								<span>{paper.year}</span>
							{/if}
							{#if paper.citations && paper.citations > 0}
								<span>·</span>
								<span>{paper.citations} citations</span>
							{/if}
						</div>
					</button>
				{/each}
			</div>
		{/if}
	</div>
</div>
