import { writable, derived, get } from 'svelte/store';
import { papers } from './library';
import type { Paper } from '$lib/types';

// Source selection types
export type SourceType = 'all' | 'recent' | 'favorites' | 'unread' | 'tag' | 'author' | 'journal' | 'submissions' | 'notes';

export interface AppState {
	// Sidebar selection
	selectedSource: SourceType;
	selectedSourceId?: string; // tag id, author name, journal name, etc.

	// Paper browser selection
	selectedPaperIds: string[];

	// View settings
	viewMode: 'list' | 'compact' | 'grid';
	sortBy: 'added' | 'year' | 'citations' | 'title';
	sortDirection: 'asc' | 'desc';
	searchQuery: string;

	// Panel visibility
	showInspector: boolean;
	showSidebar: boolean;

	// Viewer state
	viewerPaperId?: string;
}

const initialState: AppState = {
	selectedSource: 'all',
	selectedSourceId: undefined,
	selectedPaperIds: [],
	viewMode: 'list',
	sortBy: 'added',
	sortDirection: 'desc',
	searchQuery: '',
	showInspector: true,
	showSidebar: true,
	viewerPaperId: undefined
};

// Main app state store
export const appState = writable<AppState>(initialState);

// Helper functions for state updates
export function selectSource(source: SourceType, sourceId?: string) {
	appState.update((state) => ({
		...state,
		selectedSource: source,
		selectedSourceId: sourceId,
		selectedPaperIds: [] // Clear paper selection when changing source
	}));
}

export function selectPaper(paperId: string, multi = false) {
	appState.update((state) => {
		if (multi) {
			// Multi-select: toggle paper in selection
			const newSelection = state.selectedPaperIds.includes(paperId)
				? state.selectedPaperIds.filter((id) => id !== paperId)
				: [...state.selectedPaperIds, paperId];
			return { ...state, selectedPaperIds: newSelection };
		} else {
			// Single select: replace selection
			return { ...state, selectedPaperIds: [paperId] };
		}
	});
}

export function clearPaperSelection() {
	appState.update((state) => ({
		...state,
		selectedPaperIds: []
	}));
}

export function selectAllPapers(paperIds: string[]) {
	appState.update((state) => ({
		...state,
		selectedPaperIds: paperIds
	}));
}

export function setViewMode(mode: 'list' | 'compact' | 'grid') {
	appState.update((state) => ({ ...state, viewMode: mode }));
}

export function setSortBy(sortBy: 'added' | 'year' | 'citations' | 'title') {
	appState.update((state) => ({ ...state, sortBy }));
}

export function toggleSortDirection() {
	appState.update((state) => ({
		...state,
		sortDirection: state.sortDirection === 'asc' ? 'desc' : 'asc'
	}));
}

export function setSearchQuery(query: string) {
	appState.update((state) => ({ ...state, searchQuery: query }));
}

export function toggleInspector() {
	appState.update((state) => ({ ...state, showInspector: !state.showInspector }));
}

export function toggleSidebar() {
	appState.update((state) => ({ ...state, showSidebar: !state.showSidebar }));
}

export function openViewer(paperId: string) {
	appState.update((state) => ({ ...state, viewerPaperId: paperId }));
}

export function closeViewer() {
	appState.update((state) => ({ ...state, viewerPaperId: undefined }));
}

// Derived stores

// Filtered papers based on source selection
export const filteredPapers = derived([appState, papers], ([$appState, $papers]) => {
	let result = [...$papers];

	// Filter by source
	switch ($appState.selectedSource) {
		case 'all':
			// No filter
			break;
		case 'recent':
			// Sort by date added and take last 30
			result = result.slice(0, 30);
			break;
		case 'favorites':
			result = result.filter((p) => p.favorite);
			break;
		case 'unread':
			result = result.filter((p) => !p.read);
			break;
		case 'tag':
			if ($appState.selectedSourceId) {
				result = result.filter((p) => p.subject?.includes($appState.selectedSourceId!));
			}
			break;
		case 'author':
			if ($appState.selectedSourceId) {
				result = result.filter((p) => p.authors?.includes($appState.selectedSourceId!));
			}
			break;
		case 'journal':
			if ($appState.selectedSourceId) {
				result = result.filter((p) => p.venue === $appState.selectedSourceId);
			}
			break;
		default:
			break;
	}

	// Filter by search query
	if ($appState.searchQuery.trim()) {
		const query = $appState.searchQuery.toLowerCase();
		result = result.filter(
			(p) =>
				p.title.toLowerCase().includes(query) ||
				p.authors.some((a) => a.toLowerCase().includes(query)) ||
				p.abstract?.toLowerCase().includes(query)
		);
	}

	// Sort
	result.sort((a, b) => {
		let comparison = 0;
		switch ($appState.sortBy) {
			case 'year':
				comparison = (a.year || 0) - (b.year || 0);
				break;
			case 'citations':
				comparison = (a.citations || 0) - (b.citations || 0);
				break;
			case 'title':
				comparison = a.title.localeCompare(b.title);
				break;
			case 'added':
			default:
				// Assuming papers are already sorted by date added (newest first)
				comparison = 0;
				break;
		}
		return $appState.sortDirection === 'asc' ? comparison : -comparison;
	});

	return result;
});

// Selected paper (first selected)
export const selectedPaper = derived([appState, papers], ([$appState, $papers]) => {
	if ($appState.selectedPaperIds.length === 0) return null;
	return $papers.find((p) => p.id === $appState.selectedPaperIds[0]) || null;
});

// Aggregate data for sidebar
export const sidebarData = derived(papers, ($papers) => {
	// Count papers
	const totalCount = $papers.length;
	const recentCount = Math.min($papers.length, 30);
	const favoriteCount = $papers.filter((p) => p.favorite).length;

	// Extract unique tags with counts
	const tagCounts = new Map<string, number>();
	$papers.forEach((p) => {
		p.subject?.forEach((tag) => {
			tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
		});
	});
	const tags = Array.from(tagCounts.entries())
		.map(([name, count]) => ({ id: name, name, count }))
		.sort((a, b) => b.count - a.count);

	// Extract unique authors with counts
	const authorCounts = new Map<string, number>();
	$papers.forEach((p) => {
		p.authors?.forEach((author) => {
			authorCounts.set(author, (authorCounts.get(author) || 0) + 1);
		});
	});
	const authors = Array.from(authorCounts.entries())
		.map(([name, count]) => ({ name, count }))
		.sort((a, b) => b.count - a.count);

	// Extract unique journals with counts
	const journalCounts = new Map<string, number>();
	$papers.forEach((p) => {
		if (p.venue) {
			journalCounts.set(p.venue, (journalCounts.get(p.venue) || 0) + 1);
		}
	});
	const journals = Array.from(journalCounts.entries())
		.map(([name, count]) => ({ name, count }))
		.sort((a, b) => b.count - a.count);

	return {
		paperCount: totalCount,
		recentCount,
		favoriteCount,
		tags,
		authors,
		journals
	};
});
