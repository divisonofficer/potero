<script lang="ts">
import { Download, ExternalLink } from 'lucide-svelte';
import type { ComparisonTableWithData } from '$lib/types';
import { exportComparison } from '$lib/stores/relatedWork';
import { openPaper } from '$lib/stores/tabs';

interface Props {
	comparison: ComparisonTableWithData;
}

let { comparison }: Props = $props();

function getConfidenceColor(confidence: number | undefined): string {
	if (!confidence) return '';
	if (confidence < 0.3) return 'text-red-600 dark:text-red-400';
	if (confidence < 0.6) return 'text-orange-600 dark:text-orange-400';
	return '';
}

function handleExport(format: 'csv' | 'json' | 'markdown') {
	exportComparison(format);
}

function handlePaperClick(paperId: string) {
	const paper = comparison.papers.find((p) => p.id === paperId);
	if (paper) {
		openPaper(paper);
	}
}
</script>

<div class="space-y-4">
	<!-- Table Header with Export Options -->
	<div class="flex items-center justify-between">
		<div>
			<h3 class="font-semibold text-foreground">{comparison.table.title}</h3>
			{#if comparison.table.description}
				<p class="mt-1 text-sm text-muted-foreground">{comparison.table.description}</p>
			{/if}
		</div>

		<!-- Export Dropdown -->
		<div class="flex items-center gap-2">
			<button
				onclick={() => handleExport('csv')}
				class="flex items-center gap-2 rounded border border-border bg-background px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted"
			>
				<Download class="h-4 w-4" />
				CSV
			</button>
			<button
				onclick={() => handleExport('json')}
				class="flex items-center gap-2 rounded border border-border bg-background px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted"
			>
				<Download class="h-4 w-4" />
				JSON
			</button>
			<button
				onclick={() => handleExport('markdown')}
				class="flex items-center gap-2 rounded border border-border bg-background px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted"
			>
				<Download class="h-4 w-4" />
				Markdown
			</button>
		</div>
	</div>

	<!-- Scrollable Table Container -->
	<div class="relative max-h-[600px] overflow-auto rounded-lg border border-border">
		<table class="w-full border-collapse text-sm">
			<!-- Sticky Header -->
			<thead class="sticky top-0 z-10 bg-background shadow-sm">
				<tr class="border-b-2 border-border">
					<th
						class="sticky left-0 z-20 min-w-[250px] bg-background px-4 py-3 text-left font-semibold"
					>
						Paper
					</th>
					{#each comparison.table.columns as column}
						<th class="min-w-[150px] bg-background px-4 py-3 text-left font-semibold">
							<div>
								<div>{column.name}</div>
								{#if column.description}
									<div class="mt-1 text-xs font-normal text-muted-foreground">
										{column.description}
									</div>
								{/if}
							</div>
						</th>
					{/each}
				</tr>
			</thead>

			<!-- Table Body -->
			<tbody>
				{#each comparison.papers as paper, idx}
					<tr
						class="border-b border-border transition-colors hover:bg-muted/30 {idx % 2 === 1
							? 'bg-muted/10'
							: ''}"
					>
						<!-- Fixed First Column (Paper Title) -->
						<td class="sticky left-0 z-10 bg-background px-4 py-3">
							<button
								onclick={() => handlePaperClick(paper.id)}
								class="group flex items-start gap-2 text-left hover:text-primary"
							>
								<span class="flex-1 font-medium group-hover:underline">
									{paper.title.length > 60 ? paper.title.slice(0, 60) + '...' : paper.title}
								</span>
								<ExternalLink
									class="h-4 w-4 flex-shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
								/>
							</button>
							<div class="mt-1 text-xs text-muted-foreground">
								{paper.authors.slice(0, 2).join(', ')}
								{#if paper.authors.length > 2}
									et al.
								{/if}
								• {paper.year ?? 'N/A'}
							</div>
						</td>

						<!-- Dynamic Columns -->
						{#each comparison.table.columns as column}
							{@const entry = comparison.entries[paper.id]?.[column.id]}
							<td
								class="px-4 py-3 {getConfidenceColor(entry?.confidence)} {idx % 2 === 1
									? 'bg-muted/10'
									: 'bg-background'}"
							>
								{#if entry}
									<div>
										<div class="whitespace-pre-wrap">{entry.value}</div>
										{#if entry.confidence !== undefined && entry.confidence < 0.8}
											<div class="mt-1 text-xs text-muted-foreground">
												Confidence: {Math.round(entry.confidence * 100)}%
											</div>
										{/if}
									</div>
								{:else}
									<span class="italic text-muted-foreground">N/A</span>
								{/if}
							</td>
						{/each}
					</tr>
				{/each}
			</tbody>
		</table>
	</div>

	<!-- Legend for Confidence Colors -->
	<div class="flex items-center gap-4 text-xs text-muted-foreground">
		<span>Confidence indicators:</span>
		<div class="flex items-center gap-2">
			<div class="h-3 w-3 rounded bg-red-600 dark:bg-red-400"></div>
			<span>&lt; 30%</span>
		</div>
		<div class="flex items-center gap-2">
			<div class="h-3 w-3 rounded bg-orange-600 dark:bg-orange-400"></div>
			<span>30-60%</span>
		</div>
		<div class="flex items-center gap-2">
			<div class="h-3 w-3 rounded bg-foreground"></div>
			<span>&gt; 60%</span>
		</div>
	</div>

	<!-- Table Info -->
	<div class="text-xs text-muted-foreground">
		<p>
			Generated: {new Date(comparison.table.createdAt).toLocaleString()}
			• Method: {comparison.table.generationMethod.replace(/_/g, ' ')}
		</p>
	</div>
</div>

<style>
	/* Ensure sticky elements stay on top of scrolling content */
	thead th {
		position: sticky;
		top: 0;
	}

	tbody td:first-child {
		position: sticky;
		left: 0;
	}

	/* Add shadow to fixed column when scrolling */
	tbody td:first-child::after {
		content: '';
		position: absolute;
		top: 0;
		right: 0;
		bottom: 0;
		width: 1px;
		background: linear-gradient(
			to right,
			rgba(0, 0, 0, 0.1),
			transparent
		);
	}
</style>
