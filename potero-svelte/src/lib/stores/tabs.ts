import { writable, derived, get } from 'svelte/store';
import type {
	Tab,
	Paper,
	PdfViewerState,
	AuthorProfile,
	TagProfile,
	JournalProfile,
	ResearchNote,
	RelatedWorkQuery
} from '$lib/types';

// Initial tab
const initialTabs: Tab[] = [{ id: 'home', type: 'home', title: 'Library' }];

// Core state
export const tabs = writable<Tab[]>(initialTabs);
export const activeTabId = writable('home');

// Chat panel visibility (toggle state)
export const isChatPanelOpen = writable(false);

export function toggleChatPanel() {
	isChatPanelOpen.update((v) => !v);
}

// Note panel state
export const isNotePanelOpen = writable(false);
export const notePanelPaperId = writable<string | null>(null);
export const notePanelNoteId = writable<string | null>(null);

export function openNotePanel(paperId?: string | null, noteId?: string | null) {
	notePanelPaperId.set(paperId ?? null);
	notePanelNoteId.set(noteId ?? null);
	isNotePanelOpen.set(true);
}

export function closeNotePanel() {
	isNotePanelOpen.set(false);
}

// Derived: active tab
export const activeTab = derived([tabs, activeTabId], ([$tabs, $activeTabId]) =>
	$tabs.find((t) => t.id === $activeTabId)
);

// Actions
export function openPaper(paper: Paper) {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'viewer' && t.paper?.id === paper.id);
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		// Create new tab
		const newTab: Tab = {
			id: `pdf-${paper.id}-${Date.now()}`,
			type: 'viewer',
			title: paper.title.length > 30 ? paper.title.slice(0, 30) + '...' : paper.title,
			paper
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

export function closeTab(tabId: string) {
	tabs.update(($tabs) => {
		// Don't close home tab
		if (tabId === 'home') return $tabs;

		const index = $tabs.findIndex((t) => t.id === tabId);
		if (index === -1) return $tabs;

		const newTabs = $tabs.filter((t) => t.id !== tabId);

		// If we closed the active tab, switch to the previous one
		activeTabId.update(($activeId) => {
			if ($activeId === tabId) {
				const newIndex = Math.min(index, newTabs.length - 1);
				return newTabs[newIndex]?.id ?? 'home';
			}
			return $activeId;
		});

		return newTabs;
	});
}

export function openSettings() {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'settings');
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: 'settings',
			type: 'settings',
			title: 'Settings'
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

export function goHome() {
	activeTabId.set('home');
}

/**
 * Update the paper data for a viewer tab
 */
export function updateTabPaper(tabId: string, paper: Paper) {
	tabs.update(($tabs) => {
		return $tabs.map((tab) => {
			if (tab.id === tabId && tab.type === 'viewer') {
				return {
					...tab,
					paper,
					title: paper.title.length > 30 ? paper.title.slice(0, 30) + '...' : paper.title
				};
			}
			return tab;
		});
	});
}

/**
 * Update the viewer state for a tab (scroll position, zoom, etc.)
 */
export function updateViewerState(tabId: string, state: Partial<PdfViewerState>) {
	tabs.update(($tabs) => {
		return $tabs.map((tab) => {
			if (tab.id === tabId && tab.type === 'viewer') {
				return {
					...tab,
					viewerState: {
						...tab.viewerState,
						...state
					} as PdfViewerState
				};
			}
			return tab;
		});
	});
}

/**
 * Get the viewer state for a tab
 */
export function getViewerState(tabId: string): PdfViewerState | undefined {
	let state: PdfViewerState | undefined;
	tabs.subscribe(($tabs) => {
		const tab = $tabs.find((t) => t.id === tabId);
		state = tab?.viewerState;
	})();
	return state;
}

/**
 * Open author profile tab
 */
export function openAuthorProfile(author: AuthorProfile) {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'author' && t.author?.name === author.name);
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: `author-${author.name.replace(/\s+/g, '-')}-${Date.now()}`,
			type: 'author',
			title: author.name.length > 20 ? author.name.slice(0, 20) + '...' : author.name,
			author
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

/**
 * Open tag profile tab
 */
export function openTagProfile(tag: TagProfile) {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'tag' && t.tag?.name === tag.name);
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: `tag-${tag.name.replace(/\s+/g, '-')}-${Date.now()}`,
			type: 'tag',
			title: tag.name,
			tag
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

/**
 * Open journal/venue profile tab
 */
export function openJournalProfile(journal: JournalProfile) {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'journal' && t.journal?.name === journal.name);
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: `journal-${journal.name.replace(/\s+/g, '-')}-${Date.now()}`,
			type: 'journal',
			title: journal.abbreviation || (journal.name.length > 20 ? journal.name.slice(0, 20) + '...' : journal.name),
			journal
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

/**
 * Open notes list tab
 */
export function openNotesList() {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'notes');
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: 'notes',
			type: 'notes',
			title: 'Notes'
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

/**
 * Open a specific research note in a tab
 */
export function openNote(note: ResearchNote) {
	tabs.update(($tabs) => {
		// Check if already open
		const existing = $tabs.find((t) => t.type === 'note-viewer' && t.note?.id === note.id);
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: `note-${note.id}`,
			type: 'note-viewer',
			title: note.title.length > 30 ? note.title.slice(0, 30) + '...' : note.title,
			note
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}

/**
 * Open related work investigation tab for a paper
 */
export function openRelatedWork(paper: Paper) {
	tabs.update(($tabs) => {
		// Check if already open for this paper
		const existing = $tabs.find(
			(t) => t.type === 'related-work' && t.paper?.id === paper.id
		);
		if (existing) {
			activeTabId.set(existing.id);
			return $tabs;
		}

		const newTab: Tab = {
			id: `related-work-${paper.id}-${Date.now()}`,
			type: 'related-work',
			title: `Related: ${paper.title.slice(0, 20)}...`,
			paper,
			relatedWorkQuery: {
				sourcePaperId: paper.id,
				sourcePaper: paper,
				searchMethod: 'semantic',
				maxResults: 20
			}
		};

		activeTabId.set(newTab.id);
		return [...$tabs, newTab];
	});
}
