import { writable } from 'svelte/store';

// Signal to trigger job refresh
// Incrementing this value causes JobStatusPanel to immediately fetch jobs
export const jobRefreshTrigger = writable(0);

// Signal to auto-expand job panel when new job starts
// Incrementing this value causes JobStatusPanel to expand
export const jobAutoExpandTrigger = writable(0);

/**
 * Call this after creating a job to immediately show it in JobStatusPanel
 */
export function triggerJobRefresh() {
	jobRefreshTrigger.update((n) => n + 1);
}

/**
 * Call this after creating a job to auto-expand JobStatusPanel
 */
export function triggerJobAutoExpand() {
	jobAutoExpandTrigger.update((n) => n + 1);
	triggerJobRefresh();
}
