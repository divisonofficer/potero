<script lang="ts">
	import { ChevronDown, ChevronRight, Sigma, Image, X, FileText, AlignLeft, List, Link } from 'lucide-svelte';
	import { fly } from 'svelte/transition';
	import type { CitationSpan, GrobidReference } from '$lib/api/client';

	interface OutlineItem {
		id: string;
		type: 'equation' | 'figure' | 'table' | 'section' | 'reference' | 'algorithm';
		title: string;
		content?: string;
		page: number;
		yPosition?: number;
	}

	interface Props {
		sections?: OutlineItem[];
		figures?: OutlineItem[];
		tables?: OutlineItem[];
		equations?: OutlineItem[];
		references?: OutlineItem[];
		citations?: CitationSpan[];
		grobidReferences?: GrobidReference[];
		onClose: () => void;
		onItemClick: (item: OutlineItem) => void;
		onCitationClick?: (citation: CitationSpan) => void;
		onReferenceClick?: (reference: GrobidReference) => void;
	}

	let {
		sections = [],
		figures = [],
		tables = [],
		equations = [],
		references = [],
		citations = [],
		grobidReferences = [],
		onClose,
		onItemClick,
		onCitationClick,
		onReferenceClick
	}: Props = $props();

	// Debug logging
	$effect(() => {
		console.log('[FloatingOutline] Props updated:', {
			citations: citations.length,
			grobidReferences: grobidReferences.length,
			sections: sections.length,
			figures: figures.length,
			tables: tables.length,
			equations: equations.length,
			references: references.length
		});
	});

	let showSections = $state(true);
	let showFigures = $state(true);
	let showTables = $state(true);
	let showEquations = $state(true);
	let showReferences = $state(true);
	let showCitations = $state(false);
	let showGrobidReferences = $state(false);
</script>

<div
	transition:fly={{ x: 300, duration: 200 }}
	class="fixed right-6 top-24 w-80 bg-white/95 dark:bg-gray-900/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-gray-200 dark:border-gray-700 overflow-hidden max-h-[calc(100vh-150px)] flex flex-col z-40"
>
	<!-- Header -->
	<div class="px-4 py-3 bg-gradient-to-r from-indigo-600 to-purple-600 flex items-center justify-between">
		<div class="flex items-center gap-2">
			<List class="w-4 h-4 text-white" />
			<h3 class="font-semibold text-white">Outline</h3>
		</div>
		<button
			onclick={onClose}
			class="p-1 hover:bg-white/20 rounded transition-colors text-white"
		>
			<X class="w-4 h-4" />
		</button>
	</div>

	<!-- Content -->
	<div class="flex-1 overflow-y-auto p-4 space-y-3">
		<!-- Sections -->
		{#if sections.length > 0}
			<div>
				<button
					onclick={() => showSections = !showSections}
					class="w-full flex items-center justify-between px-3 py-2 bg-gray-50 dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<AlignLeft class="w-4 h-4 text-gray-600 dark:text-gray-400" />
						<span class="font-medium text-gray-900 dark:text-gray-100 text-sm">
							Sections ({sections.length})
						</span>
					</div>
					{#if showSections}
						<ChevronDown class="w-4 h-4 text-gray-600 dark:text-gray-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-gray-600 dark:text-gray-400" />
					{/if}
				</button>

				{#if showSections}
					<div class="mt-2 space-y-1 ml-2">
						{#each sections as sec}
							<button
								onclick={() => onItemClick(sec)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
											{sec.title}
										</p>
										{#if sec.content}
											<p class="text-xs text-gray-500 dark:text-gray-400 mt-1 truncate">
												{sec.content}
											</p>
										{/if}
									</div>
									<span class="text-xs text-gray-400 flex-shrink-0">
										p.{sec.page}
									</span>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- Figures -->
		{#if figures.length > 0}
			<div>
				<button
					onclick={() => showFigures = !showFigures}
					class="w-full flex items-center justify-between px-3 py-2 bg-blue-50 dark:bg-blue-900/30 hover:bg-blue-100 dark:hover:bg-blue-900/50 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<Image class="w-4 h-4 text-blue-600 dark:text-blue-400" />
						<span class="font-medium text-blue-900 dark:text-blue-100 text-sm">
							Figures ({figures.length})
						</span>
					</div>
					{#if showFigures}
						<ChevronDown class="w-4 h-4 text-blue-600 dark:text-blue-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-blue-600 dark:text-blue-400" />
					{/if}
				</button>

				{#if showFigures}
					<div class="mt-2 space-y-1 ml-2">
						{#each figures as fig (fig.id)}
							<button
								onclick={() => onItemClick(fig)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-blue-50 dark:hover:bg-blue-900/30 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
											{fig.title}
										</p>
										{#if fig.content}
											<p class="text-xs text-gray-500 dark:text-gray-400 mt-1 truncate">
												{fig.content}
											</p>
										{/if}
									</div>
									<span class="text-xs text-gray-400 flex-shrink-0">
										p.{fig.page}
									</span>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- Tables -->
		{#if tables.length > 0}
			<div>
				<button
					onclick={() => showTables = !showTables}
					class="w-full flex items-center justify-between px-3 py-2 bg-emerald-50 dark:bg-emerald-900/30 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<svg class="w-4 h-4 text-emerald-600 dark:text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
							<rect x="3" y="3" width="18" height="18" rx="2" />
							<line x1="3" y1="9" x2="21" y2="9" />
							<line x1="3" y1="15" x2="21" y2="15" />
							<line x1="9" y1="3" x2="9" y2="21" />
							<line x1="15" y1="3" x2="15" y2="21" />
						</svg>
						<span class="font-medium text-emerald-900 dark:text-emerald-100 text-sm">
							Tables ({tables.length})
						</span>
					</div>
					{#if showTables}
						<ChevronDown class="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
					{/if}
				</button>

				{#if showTables}
					<div class="mt-2 space-y-1 ml-2">
						{#each tables as table}
							<button
								onclick={() => onItemClick(table)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors">
											{table.title}
										</p>
										{#if table.content}
											<p class="text-xs text-gray-500 dark:text-gray-400 mt-1 truncate">
												{table.content}
											</p>
										{/if}
									</div>
									<span class="text-xs text-gray-400 flex-shrink-0">
										p.{table.page}
									</span>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- Equations -->
		{#if equations.length > 0}
			<div>
				<button
					onclick={() => showEquations = !showEquations}
					class="w-full flex items-center justify-between px-3 py-2 bg-purple-50 dark:bg-purple-900/30 hover:bg-purple-100 dark:hover:bg-purple-900/50 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<Sigma class="w-4 h-4 text-purple-600 dark:text-purple-400" />
						<span class="font-medium text-purple-900 dark:text-purple-100 text-sm">
							Equations ({equations.length})
						</span>
					</div>
					{#if showEquations}
						<ChevronDown class="w-4 h-4 text-purple-600 dark:text-purple-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-purple-600 dark:text-purple-400" />
					{/if}
				</button>

				{#if showEquations}
					<div class="mt-2 space-y-1 ml-2">
						{#each equations as eq}
							<button
								onclick={() => onItemClick(eq)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-purple-50 dark:hover:bg-purple-900/30 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-purple-600 dark:group-hover:text-purple-400 transition-colors">
											{eq.title}
										</p>
										{#if eq.content}
											<p class="text-xs text-gray-500 dark:text-gray-400 font-mono mt-1 truncate">
												{eq.content}
											</p>
										{/if}
									</div>
									<span class="text-xs text-gray-400 flex-shrink-0">
										p.{eq.page}
									</span>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- Citations -->
		{#if citations.length > 0 && (grobidReferences.length + references.length) < 1}
			<div>
				<button
					onclick={() => showCitations = !showCitations}
					class="w-full flex items-center justify-between px-3 py-2 bg-emerald-50 dark:bg-emerald-900/30 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<Link class="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
						<span class="font-medium text-emerald-900 dark:text-emerald-100 text-sm">
							Citations ({citations.length})
						</span>
					</div>
					{#if showCitations}
						<ChevronDown class="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
					{/if}
				</button>

				{#if showCitations}
					<div class="mt-2 space-y-1 ml-2">
						{#each citations as citation}
							<button
								onclick={() => onCitationClick?.(citation)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors">
											{citation.rawText}
										</p>
										<p class="text-xs text-emerald-600 dark:text-emerald-400 mt-1">
											{citation.linkedRefIds.length} reference{citation.linkedRefIds.length > 1 ? 's' : ''}
										</p>
									</div>
									<span class="text-xs text-gray-400 flex-shrink-0">
										p.{citation.pageNum}
									</span>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- GROBID References -->
		{#if grobidReferences.length > 0}
			<div>
				<button
					onclick={() => showGrobidReferences = !showGrobidReferences}
					class="w-full flex items-center justify-between px-3 py-2 bg-gray-50 dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<FileText class="w-4 h-4 text-gray-600 dark:text-gray-400" />
						<span class="font-medium text-gray-900 dark:text-gray-100 text-sm">
							References ({grobidReferences.length})
						</span>
					</div>
					{#if showGrobidReferences}
						<ChevronDown class="w-4 h-4 text-gray-600 dark:text-gray-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-gray-600 dark:text-gray-400" />
					{/if}
				</button>

				{#if showGrobidReferences}
					<div class="mt-2 space-y-1 ml-2">
						{#each grobidReferences as ref}
							<button
								onclick={() => onReferenceClick?.(ref)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-gray-600 dark:group-hover:text-gray-300 transition-colors line-clamp-1">
											[{ref.xmlId.replace(/^(llm-ref-|b|#b)/g, '')}]: {ref.title || ref.authors || 'Untitled'}
										</p>
										{#if ref.authors}
											<p class="text-xs text-gray-500 dark:text-gray-400 mt-1 line-clamp-1">
												{ref.authors}
											</p>
										{/if}
										{#if ref.venue && ref.year}
											<p class="text-xs text-gray-400 mt-1">
												{ref.venue}, {ref.year}
											</p>
										{/if}
									</div>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- References (Legacy) -->
		{#if references.length > 0}
			<div>
				<button
					onclick={() => showReferences = !showReferences}
					class="w-full flex items-center justify-between px-3 py-2 bg-gray-50 dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
				>
					<div class="flex items-center gap-2">
						<FileText class="w-4 h-4 text-gray-600 dark:text-gray-400" />
						<span class="font-medium text-gray-900 dark:text-gray-100 text-sm">
							References (Legacy) ({references.length})
						</span>
					</div>
					{#if showReferences}
						<ChevronDown class="w-4 h-4 text-gray-600 dark:text-gray-400" />
					{:else}
						<ChevronRight class="w-4 h-4 text-gray-600 dark:text-gray-400" />
					{/if}
				</button>

				{#if showReferences}
					<div class="mt-2 space-y-1 ml-2">
						{#each references as ref}
							<button
								onclick={() => onItemClick(ref)}
								class="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors group"
							>
								<div class="flex items-start justify-between gap-2">
									<div class="flex-1 min-w-0">
										<p class="text-sm font-medium text-gray-900 dark:text-gray-100 group-hover:text-gray-600 dark:group-hover:text-gray-300 transition-colors">
											{ref.title}
										</p>
										{#if ref.content}
											<p class="text-xs text-gray-500 dark:text-gray-400 mt-1 truncate">
												{ref.content}
											</p>
										{/if}
									</div>
									<span class="text-xs text-gray-400 flex-shrink-0">
										p.{ref.page}
									</span>
								</div>
							</button>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<!-- Empty state -->
		{#if sections.length === 0 && figures.length === 0 && tables.length === 0 && equations.length === 0 && references.length === 0}
			<div class="text-center py-8">
				<List class="w-12 h-12 mx-auto text-gray-300 dark:text-gray-600 mb-3" />
				<p class="text-sm text-gray-500 dark:text-gray-400">
					Outline is being generated...
				</p>
				<p class="text-xs text-gray-400 dark:text-gray-500 mt-1">
					This may take a moment for large documents
				</p>
			</div>
		{/if}

		<!-- Quick Stats -->
		{#if figures.length > 0 || tables.length > 0 || equations.length > 0}
			<div class="mt-4 p-3 bg-gradient-to-r from-indigo-50 to-purple-50 dark:from-indigo-900/30 dark:to-purple-900/30 rounded-lg border border-indigo-200 dark:border-indigo-800">
				<div class="grid grid-cols-3 gap-3 text-center">
					<div>
						<div class="text-xl font-bold text-blue-600 dark:text-blue-400">{figures.length}</div>
						<div class="text-xs text-gray-600 dark:text-gray-400">Figures</div>
					</div>
					<div>
						<div class="text-xl font-bold text-emerald-600 dark:text-emerald-400">{tables.length}</div>
						<div class="text-xs text-gray-600 dark:text-gray-400">Tables</div>
					</div>
					<div>
						<div class="text-xl font-bold text-purple-600 dark:text-purple-400">{equations.length}</div>
						<div class="text-xs text-gray-600 dark:text-gray-400">Equations</div>
					</div>
				</div>
			</div>
		{/if}
	</div>
</div>

<style>
	@keyframes slideIn {
		from {
			opacity: 0;
			transform: translateX(-10px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}
</style>
