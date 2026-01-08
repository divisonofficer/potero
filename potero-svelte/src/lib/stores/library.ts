import { writable, derived } from 'svelte/store';
import type { Paper, ViewStyle, SortBy, Tag } from '$lib/types';
import { api, type UploadAnalysisResponse, type SearchResult } from '$lib/api/client';

// Core state
export const papers = writable<Paper[]>([]);
export const tags = writable<Tag[]>([]);
export const isLoading = writable(false);
export const error = writable<string | null>(null);

// Upload analysis state - for showing search results dialog
export interface PendingUploadAnalysis {
	paperId: string;
	searchQuery: string;
	searchResults: SearchResult[];
}
export const pendingUploadAnalysis = writable<PendingUploadAnalysis | null>(null);

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

/**
 * Load papers from the API
 */
export async function loadPapers() {
	isLoading.set(true);
	error.set(null);

	const result = await api.listPapers();

	if (result.success && result.data) {
		papers.set(result.data);
	} else {
		error.set(result.error?.message ?? 'Failed to load papers');
	}

	isLoading.set(false);
}

/**
 * Load tags from the API
 */
export async function loadTags() {
	const result = await api.listTags();

	if (result.success && result.data) {
		tags.set(result.data);
	}
}

/**
 * Import a paper by DOI
 */
export async function importByDoi(doi: string): Promise<Paper | null> {
	isLoading.set(true);
	error.set(null);

	const result = await api.importByDoi(doi);

	if (result.success && result.data) {
		// Add to papers list
		papers.update((list) => [...list, result.data!]);
		isLoading.set(false);
		return result.data;
	} else {
		error.set(result.error?.message ?? 'Failed to import paper');
		isLoading.set(false);
		return null;
	}
}

/**
 * Import a paper by arXiv ID
 */
export async function importByArxiv(arxivId: string): Promise<Paper | null> {
	isLoading.set(true);
	error.set(null);

	const result = await api.importByArxiv(arxivId);

	if (result.success && result.data) {
		// Add to papers list
		papers.update((list) => [...list, result.data!]);
		isLoading.set(false);
		return result.data;
	} else {
		error.set(result.error?.message ?? 'Failed to import paper');
		isLoading.set(false);
		return null;
	}
}

/**
 * Delete a paper
 */
export async function deletePaper(id: string): Promise<boolean> {
	const result = await api.deletePaper(id);

	if (result.success) {
		papers.update((list) => list.filter((p) => p.id !== id));
		return true;
	} else {
		error.set(result.error?.message ?? 'Failed to delete paper');
		return false;
	}
}

/**
 * Upload a PDF file and create a new paper
 * Returns the analysis response which may contain search results for user confirmation
 */
export async function uploadPdf(
	file: File,
	title?: string,
	skipAnalysis?: boolean
): Promise<UploadAnalysisResponse | null> {
	isLoading.set(true);
	error.set(null);

	const result = await api.uploadPdf(file, title, skipAnalysis);

	if (result.success && result.data) {
		const analysisResult = result.data;

		// If there are search results that need user confirmation, store them
		if (analysisResult.needsUserConfirmation && analysisResult.searchResults.length > 0) {
			pendingUploadAnalysis.set({
				paperId: analysisResult.paperId,
				searchQuery: analysisResult.pdfMetadataTitle || analysisResult.title,
				searchResults: analysisResult.searchResults
			});
		}

		// Reload papers to get the new entry
		await loadPapers();
		isLoading.set(false);
		return analysisResult;
	} else {
		error.set(result.error?.message ?? 'Failed to upload PDF');
		isLoading.set(false);
		return null;
	}
}

/**
 * Clear pending upload analysis (after user confirms or cancels)
 */
export function clearPendingUploadAnalysis() {
	pendingUploadAnalysis.set(null);
}

/**
 * Upload multiple PDF files
 * Returns number of successful uploads and any pending analysis for user confirmation
 */
export async function uploadPdfs(files: FileList | File[]): Promise<{
	successCount: number;
	pendingAnalyses: PendingUploadAnalysis[];
}> {
	isLoading.set(true);
	error.set(null);

	let successCount = 0;
	let lastError: string | null = null;
	const pendingAnalyses: PendingUploadAnalysis[] = [];

	const pdfFiles = Array.from(files).filter(
		(f) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')
	);

	console.log(`Uploading ${pdfFiles.length} PDF files...`);

	for (const file of pdfFiles) {
		console.log(`Uploading: ${file.name} (${Math.round(file.size / 1024)}KB)`);
		try {
			const result = await api.uploadPdf(file);
			console.log('Upload result:', result);
			if (result.success && result.data) {
				successCount++;
				// Collect pending analyses for later display
				if (result.data.needsUserConfirmation && result.data.searchResults.length > 0) {
					pendingAnalyses.push({
						paperId: result.data.paperId,
						searchQuery: result.data.pdfMetadataTitle || result.data.title,
						searchResults: result.data.searchResults
					});
				}
			} else {
				lastError = result.error?.message ?? 'Upload failed';
				console.error('Upload error:', lastError);
			}
		} catch (e) {
			lastError = e instanceof Error ? e.message : 'Upload failed';
			console.error('Upload exception:', e);
		}
	}

	// Reload papers after all uploads
	if (successCount > 0) {
		await loadPapers();
	}

	// Set the first pending analysis for display (others will be handled later)
	if (pendingAnalyses.length > 0) {
		pendingUploadAnalysis.set(pendingAnalyses[0]);
	}

	if (lastError && successCount === 0) {
		error.set(lastError);
	}

	isLoading.set(false);
	return { successCount, pendingAnalyses };
}

/**
 * Initialize the library (load papers and tags)
 */
export async function initializeLibrary() {
	await Promise.all([loadPapers(), loadTags()]);
}
