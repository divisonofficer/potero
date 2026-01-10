import { writable, derived, get } from 'svelte/store';
import type {
	RelatedPaperCandidate,
	ComparisonTableWithData,
	RelatedWorkQuery,
	ColumnDefinition
} from '$lib/types';
import { api } from '$lib/api/client';
import { toast } from './toast';

// Core state
export const currentQuery = writable<RelatedWorkQuery | null>(null);
export const candidates = writable<RelatedPaperCandidate[]>([]);
export const selectedCandidateIds = writable<string[]>([]);
export const currentComparison = writable<ComparisonTableWithData | null>(null);
export const isSearching = writable(false);
export const isGenerating = writable(false);
export const error = writable<string | null>(null);

// Filter state
export const yearRangeFilter = writable<[number, number] | null>(null);
export const minCitationsFilter = writable<number>(0);
export const venueFilter = writable<string[]>([]);

// Derived: filtered candidates
export const filteredCandidates = derived(
	[candidates, yearRangeFilter, minCitationsFilter, venueFilter],
	([$candidates, $yearRange, $minCitations, $venues]) => {
		let filtered = $candidates;

		if ($yearRange) {
			filtered = filtered.filter(
				(c) =>
					c.year && c.year >= $yearRange[0] && c.year <= $yearRange[1]
			);
		}

		if ($minCitations > 0) {
			filtered = filtered.filter((c) => c.citationsCount >= $minCitations);
		}

		if ($venues.length > 0) {
			filtered = filtered.filter(
				(c) => c.relationshipType && $venues.includes(c.relationshipType)
			);
		}

		return filtered;
	}
);

/**
 * Search for related papers
 */
export async function searchRelatedPapers(
	paperId: string,
	limit: number = 20,
	forceRefresh: boolean = false
): Promise<void> {
	isSearching.set(true);
	error.set(null);

	try {
		const result = await api.searchRelatedPapers(paperId, limit, forceRefresh);
		if (result.success && result.data) {
			candidates.set(result.data);
			toast.success(`Found ${result.data.length} related papers`);
		} else {
			const errorMsg =
				typeof result.error === 'string'
					? result.error
					: result.error?.message || 'Search failed';
			error.set(errorMsg);
			toast.error(errorMsg);
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Search failed';
		error.set(errorMsg);
		toast.error(errorMsg);
	} finally {
		isSearching.set(false);
	}
}

/**
 * Toggle selection of a candidate paper
 */
export function toggleCandidateSelection(paperId: string): void {
	selectedCandidateIds.update((ids) => {
		if (ids.includes(paperId)) {
			return ids.filter((id) => id !== paperId);
		} else {
			return [...ids, paperId];
		}
	});
}

/**
 * Select all filtered candidates
 */
export function selectAllCandidates(): void {
	const filtered = get(filteredCandidates);
	selectedCandidateIds.set(filtered.map((c) => c.paperId));
}

/**
 * Deselect all candidates
 */
export function deselectAllCandidates(): void {
	selectedCandidateIds.set([]);
}

/**
 * Generate comparison table
 */
export async function generateComparison(
	sourcePaperId: string,
	title: string,
	description?: string,
	columns?: ColumnDefinition[],
	generateNarrative: boolean = true
): Promise<ComparisonTableWithData | null> {
	const selectedIds = get(selectedCandidateIds);
	if (selectedIds.length === 0) {
		toast.error('Please select at least one paper');
		return null;
	}

	isGenerating.set(true);
	error.set(null);

	try {
		const result = await api.generateComparison(
			sourcePaperId,
			selectedIds,
			title,
			description,
			columns,
			generateNarrative
		);
		if (result.success && result.data) {
			currentComparison.set(result.data);
			toast.success('Comparison table generated');
			return result.data;
		} else {
			const errorMsg =
				typeof result.error === 'string'
					? result.error
					: result.error?.message || 'Generation failed';
			error.set(errorMsg);
			toast.error(errorMsg);
			return null;
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Generation failed';
		error.set(errorMsg);
		toast.error(errorMsg);
		return null;
	} finally {
		isGenerating.set(false);
	}
}

/**
 * Export comparison to file
 */
export function exportComparison(format: 'csv' | 'json' | 'markdown'): void {
	const comparison = get(currentComparison);
	if (!comparison) {
		toast.error('No comparison to export');
		return;
	}

	try {
		let content: string;
		let mimeType: string;
		let extension: string;

		switch (format) {
			case 'json':
				content = JSON.stringify(comparison, null, 2);
				mimeType = 'application/json';
				extension = 'json';
				break;

			case 'csv':
				content = convertToCSV(comparison);
				mimeType = 'text/csv';
				extension = 'csv';
				break;

			case 'markdown':
				content = convertToMarkdown(comparison);
				mimeType = 'text/markdown';
				extension = 'md';
				break;
		}

		const blob = new Blob([content], { type: mimeType });
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = `comparison-${comparison.table.title.replace(/\s+/g, '-')}.${extension}`;
		a.click();
		URL.revokeObjectURL(url);

		toast.success(`Exported as ${format.toUpperCase()}`);
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Export failed';
		toast.error(errorMsg);
	}
}

/**
 * Convert comparison to CSV format
 */
function convertToCSV(comparison: ComparisonTableWithData): string {
	const { table, entries, papers } = comparison;
	const lines: string[] = [];

	// Header row
	const headers = ['Paper', ...table.columns.map((col) => col.name)];
	lines.push(headers.map((h) => `"${h}"`).join(','));

	// Data rows
	papers.forEach((paper) => {
		const row = [paper.title];
		table.columns.forEach((col) => {
			const entry = entries[paper.id]?.[col.id];
			row.push(entry?.value || 'N/A');
		});
		lines.push(row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(','));
	});

	return lines.join('\n');
}

/**
 * Convert comparison to Markdown format
 */
function convertToMarkdown(comparison: ComparisonTableWithData): string {
	const { table, entries, papers, narrative } = comparison;
	let markdown = `# ${table.title}\n\n`;

	if (table.description) {
		markdown += `${table.description}\n\n`;
	}

	// Table
	markdown += '## Comparison Table\n\n';

	// Header row
	const headers = ['Paper', ...table.columns.map((col) => col.name)];
	markdown += `| ${headers.join(' | ')} |\n`;
	markdown += `| ${headers.map(() => '---').join(' | ')} |\n`;

	// Data rows
	papers.forEach((paper) => {
		const row = [paper.title.slice(0, 50)];
		table.columns.forEach((col) => {
			const entry = entries[paper.id]?.[col.id];
			row.push(entry?.value || 'N/A');
		});
		markdown += `| ${row.join(' | ')} |\n`;
	});

	// Narrative summary
	if (narrative) {
		markdown += `\n## Summary\n\n${narrative.content}\n`;

		if (narrative.keyInsights.length > 0) {
			markdown += `\n### Key Insights\n\n`;
			narrative.keyInsights.forEach((insight) => {
				markdown += `- ${insight}\n`;
			});
		}
	}

	return markdown;
}

/**
 * Reset all state
 */
export function resetRelatedWorkState(): void {
	currentQuery.set(null);
	candidates.set([]);
	selectedCandidateIds.set([]);
	currentComparison.set(null);
	error.set(null);
	yearRangeFilter.set(null);
	minCitationsFilter.set(0);
	venueFilter.set([]);
}
