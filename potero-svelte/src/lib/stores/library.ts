import { writable, derived } from 'svelte/store';
import type { Paper, ViewStyle, SortBy, SearchFilters } from '$lib/types';

// Core state
export const papers = writable<Paper[]>([]);
export const isLoading = writable(false);
export const error = writable<string | null>(null);

// Filter state
export const searchQuery = writable('');
export const selectedConference = writable<string | null>(null);
export const selectedYear = writable<number | null>(null);
export const selectedSubjects = writable<string[]>([]);

// View state
export const viewStyle = writable<ViewStyle>('grid');
export const sortBy = writable<SortBy>('recent');

// Derived: filtered and sorted papers
export const filteredPapers = derived(
	[papers, searchQuery, selectedConference, selectedYear, selectedSubjects, sortBy],
	([$papers, $query, $conference, $year, $subjects, $sortBy]) => {
		let result = $papers;

		// Filter by search query
		if ($query) {
			const lowerQuery = $query.toLowerCase();
			result = result.filter(
				(p) =>
					p.title.toLowerCase().includes(lowerQuery) ||
					p.authors.some((a) => a.toLowerCase().includes(lowerQuery)) ||
					p.abstract?.toLowerCase().includes(lowerQuery)
			);
		}

		// Filter by conference
		if ($conference) {
			result = result.filter((p) => p.conference === $conference);
		}

		// Filter by year
		if ($year) {
			result = result.filter((p) => p.year === $year);
		}

		// Filter by subjects
		if ($subjects.length > 0) {
			result = result.filter((p) => $subjects.some((s) => p.subject.includes(s)));
		}

		// Sort
		switch ($sortBy) {
			case 'citations':
				result = [...result].sort((a, b) => b.citations - a.citations);
				break;
			case 'title':
				result = [...result].sort((a, b) => a.title.localeCompare(b.title));
				break;
			case 'recent':
			default:
				result = [...result].sort((a, b) => (b.year ?? 0) - (a.year ?? 0));
				break;
		}

		return result;
	}
);

// Derived: available filters (facets)
export const availableConferences = derived(papers, ($papers) => {
	const counts = new Map<string, number>();
	$papers.forEach((p) => {
		if (p.conference) {
			counts.set(p.conference, (counts.get(p.conference) ?? 0) + 1);
		}
	});
	return Array.from(counts.entries())
		.map(([name, count]) => ({ name, count }))
		.sort((a, b) => b.count - a.count);
});

export const availableYears = derived(papers, ($papers) => {
	const counts = new Map<number, number>();
	$papers.forEach((p) => {
		if (p.year) {
			counts.set(p.year, (counts.get(p.year) ?? 0) + 1);
		}
	});
	return Array.from(counts.entries())
		.map(([year, count]) => ({ year, count }))
		.sort((a, b) => b.year - a.year);
});

export const availableSubjects = derived(papers, ($papers) => {
	const counts = new Map<string, number>();
	$papers.forEach((p) => {
		p.subject.forEach((s) => {
			counts.set(s, (counts.get(s) ?? 0) + 1);
		});
	});
	return Array.from(counts.entries())
		.map(([name, count]) => ({ name, count }))
		.sort((a, b) => b.count - a.count);
});

// Actions
export function clearFilters() {
	searchQuery.set('');
	selectedConference.set(null);
	selectedYear.set(null);
	selectedSubjects.set([]);
}
