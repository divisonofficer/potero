import { writable, derived, get } from 'svelte/store';
import type { ResearchNote, ResearchNoteWithLinks } from '$lib/types';
import { api } from '$lib/api/client';
import { toast } from './toast';

// Core state
export const notes = writable<ResearchNote[]>([]);
export const currentNote = writable<ResearchNoteWithLinks | null>(null);
export const isLoading = writable(false);
export const error = writable<string | null>(null);

// Search state
export const searchQuery = writable('');

// Derived: filtered notes based on search
export const filteredNotes = derived(
	[notes, searchQuery],
	([$notes, $query]) => {
		if (!$query.trim()) return $notes;

		const lowerQuery = $query.toLowerCase();
		return $notes.filter(
			(n) =>
				n.title.toLowerCase().includes(lowerQuery) ||
				n.content.toLowerCase().includes(lowerQuery)
		);
	}
);

/**
 * Load all notes from the server
 */
export async function loadNotes(): Promise<void> {
	isLoading.set(true);
	error.set(null);

	try {
		const result = await api.getNotes();
		if (result.success && result.data) {
			notes.set(result.data);
		} else {
			const errorMsg = typeof result.error === 'string' ? result.error : result.error?.message || 'Failed to load notes';
			error.set(errorMsg);
			toast.error(errorMsg);
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Failed to load notes';
		error.set(errorMsg);
		toast.error(errorMsg);
	} finally {
		isLoading.set(false);
	}
}

/**
 * Load a specific note with links and backlinks
 */
export async function loadNote(id: string): Promise<void> {
	isLoading.set(true);
	error.set(null);

	try {
		const result = await api.getNote(id);
		if (result.success && result.data) {
			currentNote.set(result.data);
		} else {
			const errorMsg = typeof result.error === 'string' ? result.error : result.error?.message || 'Failed to load note';
			error.set(errorMsg);
			toast.error(errorMsg);
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Failed to load note';
		error.set(errorMsg);
		toast.error(errorMsg);
	} finally {
		isLoading.set(false);
	}
}

/**
 * Create a new note
 */
export async function createNote(
	title: string,
	content: string,
	paperId?: string | null
): Promise<ResearchNote | null> {
	isLoading.set(true);
	error.set(null);

	try {
		const result = await api.createNote({ title, content, paperId });
		if (result.success && result.data) {
			// Refresh the notes list
			await loadNotes();
			toast.success('Note created');
			return result.data;
		} else {
			const errorMsg = typeof result.error === 'string' ? result.error : result.error?.message || 'Failed to create note';
			error.set(errorMsg);
			toast.error(errorMsg);
			return null;
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Failed to create note';
		error.set(errorMsg);
		toast.error(errorMsg);
		return null;
	} finally {
		isLoading.set(false);
	}
}

/**
 * Update an existing note
 */
export async function updateNote(
	id: string,
	title: string,
	content: string,
	paperId?: string | null
): Promise<boolean> {
	isLoading.set(true);
	error.set(null);

	try {
		const result = await api.updateNote(id, { title, content, paperId });
		if (result.success) {
			// Refresh the notes list
			await loadNotes();
			// Refresh current note if it's the one being updated
			const current = get(currentNote);
			if (current?.note.id === id) {
				await loadNote(id);
			}
			toast.success('Note saved');
			return true;
		} else {
			const errorMsg = typeof result.error === 'string' ? result.error : result.error?.message || 'Failed to save note';
			error.set(errorMsg);
			toast.error(errorMsg);
			return false;
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Failed to save note';
		error.set(errorMsg);
		toast.error(errorMsg);
		return false;
	} finally {
		isLoading.set(false);
	}
}

/**
 * Delete a note
 */
export async function deleteNote(id: string): Promise<boolean> {
	try {
		const result = await api.deleteNote(id);
		if (result.success) {
			// Refresh the notes list
			await loadNotes();
			// Clear current note if it's the one being deleted
			const current = get(currentNote);
			if (current?.note.id === id) {
				currentNote.set(null);
			}
			toast.success('Note deleted');
			return true;
		} else {
			const errorMsg = typeof result.error === 'string' ? result.error : result.error?.message || 'Failed to delete note';
			toast.error(errorMsg);
			return false;
		}
	} catch (e) {
		const errorMsg = e instanceof Error ? e.message : 'Failed to delete note';
		toast.error(errorMsg);
		return false;
	}
}

/**
 * Search notes
 */
export async function searchNotes(query: string): Promise<ResearchNote[]> {
	if (!query.trim()) {
		return get(notes);
	}

	try {
		const result = await api.searchNotes(query);
		if (result.success && result.data) {
			return result.data;
		}
		return [];
	} catch (e) {
		console.error('Failed to search notes:', e);
		return [];
	}
}

/**
 * Load notes for a specific paper
 */
export async function loadPaperNotes(paperId: string): Promise<ResearchNote[]> {
	try {
		const result = await api.getNotesByPaper(paperId);
		if (result.success && result.data) {
			return result.data;
		}
		return [];
	} catch (e) {
		console.error('Failed to load paper notes:', e);
		return [];
	}
}
