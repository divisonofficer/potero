import { writable, derived } from 'svelte/store';
import type { Tab, Paper, PdfViewerState } from '$lib/types';

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
