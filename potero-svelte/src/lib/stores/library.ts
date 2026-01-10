import { writable, derived } from "svelte/store";
import type { Paper, ViewStyle, SortBy, Tag } from "$lib/types";
import {
  api,
  type UploadAnalysisResponse,
  type SearchResult,
  type AutoTagResponse,
  type TagSuggestion,
} from "$lib/api/client";
import { triggerJobRefresh, triggerJobAutoExpand } from "$lib/stores/jobs";

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
export const pendingUploadAnalysis = writable<PendingUploadAnalysis | null>(
  null
);

// Filter state
export const searchQuery = writable("");
export const selectedConference = writable<string | null>(null);
export const selectedYear = writable<number | null>(null);
export const selectedSubjects = writable<string[]>([]);

// View state
export const viewStyle = writable<ViewStyle>("grid");
export const sortBy = writable<SortBy>("recent");

// Online search state
export const onlineSearchResults = writable<SearchResult[]>([]);
export const isSearchingOnline = writable(false);
let onlineSearchDebounceTimer: ReturnType<typeof setTimeout> | null = null;

// Derived: filtered and sorted papers
export const filteredPapers = derived(
  [
    papers,
    searchQuery,
    selectedConference,
    selectedYear,
    selectedSubjects,
    sortBy,
  ],
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
      result = result.filter((p) =>
        $subjects.some((s) => p.subject.includes(s))
      );
    }

    // Sort
    switch ($sortBy) {
      case "citations":
        result = [...result].sort((a, b) => b.citations - a.citations);
        break;
      case "title":
        result = [...result].sort((a, b) => a.title.localeCompare(b.title));
        break;
      case "recent":
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

// Online search - triggered when local results are few
export async function searchOnlineIfNeeded(
  query: string,
  localResultCount: number
) {
  // Clear previous timer
  if (onlineSearchDebounceTimer) {
    clearTimeout(onlineSearchDebounceTimer);
  }

  // Clear results if query is short or local results are sufficient
  if (query.length < 3 || localResultCount >= 3) {
    onlineSearchResults.set([]);
    isSearchingOnline.set(false);
    return;
  }

  // Debounce: wait 500ms before searching
  onlineSearchDebounceTimer = setTimeout(async () => {
    isSearchingOnline.set(true);

    const results = await searchOnline(query);
    console.log(results);
    onlineSearchResults.set(results);

    isSearchingOnline.set(false);
  }, 500);
}

// Clear online search results
export function clearOnlineSearchResults() {
  onlineSearchResults.set([]);
  isSearchingOnline.set(false);
  if (onlineSearchDebounceTimer) {
    clearTimeout(onlineSearchDebounceTimer);
    onlineSearchDebounceTimer = null;
  }
}

// Actions
export function clearFilters() {
  searchQuery.set("");
  clearOnlineSearchResults();
  selectedConference.set(null);
  selectedYear.set(null);
  selectedSubjects.set([]);
}

/**
 * Load papers from the API
 * @param showLoading - If true, shows full-screen loading indicator (default true for initial load)
 */
export async function loadPapers(showLoading: boolean = true) {
  if (showLoading) {
    isLoading.set(true);
  }
  error.set(null);

  const result = await api.listPapers();

  if (result.success && result.data) {
    papers.set(result.data);
  } else {
    error.set(result.error?.message ?? "Failed to load papers");
  }

  if (showLoading) {
    isLoading.set(false);
  }
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
    triggerJobAutoExpand(); // Show job panel for analysis progress
    // Add to papers list
    papers.update((list) => [...list, result.data!]);
    isLoading.set(false);
    return result.data;
  } else {
    error.set(result.error?.message ?? "Failed to import paper");
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
    triggerJobAutoExpand(); // Show job panel for analysis progress
    // Add to papers list
    papers.update((list) => [...list, result.data!]);
    isLoading.set(false);
    return result.data;
  } else {
    error.set(result.error?.message ?? "Failed to import paper");
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
    error.set(result.error?.message ?? "Failed to delete paper");
    return false;
  }
}

/**
 * Upload a PDF file and create a new paper
 * Returns the analysis response which may contain search results for user confirmation
 * Automatically starts auto-tagging in the background after successful upload
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
    if (
      analysisResult.needsUserConfirmation &&
      analysisResult.searchResults.length > 0
    ) {
      pendingUploadAnalysis.set({
        paperId: analysisResult.paperId,
        searchQuery: analysisResult.pdfMetadataTitle || analysisResult.title,
        searchResults: analysisResult.searchResults,
      });
    }

    // Reload papers to get the new entry (without full-screen loading)
    await loadPapers(false);
    isLoading.set(false);

    // Auto-tag the paper in background (fire and forget)
    // This will show progress in JobStatusPanel
    api.autoTagPaper(analysisResult.paperId).then(async (tagResult) => {
      // Trigger immediate job panel refresh
      triggerJobRefresh();
      if (tagResult.success) {
        // Reload tags and papers after auto-tagging completes (silently)
        await loadTags();
        await loadPapers(false);
      }
    });
    // Also trigger refresh immediately when the request starts
    triggerJobRefresh();

    return analysisResult;
  } else {
    error.set(result.error?.message ?? "Failed to upload PDF");
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
 * Automatically starts auto-tagging for each uploaded paper in the background
 */
export async function uploadPdfs(files: FileList | File[]): Promise<{
  successCount: number;
  pendingAnalyses: PendingUploadAnalysis[];
}> {
  // Don't set global isLoading - upload happens in background
  error.set(null);

  let successCount = 0;
  let lastError: string | null = null;
  const pendingAnalyses: PendingUploadAnalysis[] = [];
  const uploadedPaperIds: string[] = [];

  const pdfFiles = Array.from(files).filter(
    (f) => f.type === "application/pdf" || f.name.toLowerCase().endsWith(".pdf")
  );

  console.log(`Uploading ${pdfFiles.length} PDF files...`);

  for (const file of pdfFiles) {
    console.log(`Uploading: ${file.name} (${Math.round(file.size / 1024)}KB)`);
    try {
      const result = await api.uploadPdf(file);
      console.log("Upload result:", result);
      if (result.success && result.data) {
        successCount++;
        uploadedPaperIds.push(result.data.paperId);

        // Auto-expand job panel on first successful upload to show analysis progress
        if (successCount === 1) {
          triggerJobAutoExpand();
        }

        // Collect pending analyses for later display
        if (
          result.data.needsUserConfirmation &&
          result.data.searchResults.length > 0
        ) {
          pendingAnalyses.push({
            paperId: result.data.paperId,
            searchQuery: result.data.pdfMetadataTitle || result.data.title,
            searchResults: result.data.searchResults,
          });
        }
      } else {
        lastError = result.error?.message ?? "Upload failed";
        console.error("Upload error:", lastError);
      }
    } catch (e) {
      lastError = e instanceof Error ? e.message : "Upload failed";
      console.error("Upload exception:", e);
    }
  }

  // Reload papers after all uploads (without full-screen loading)
  if (successCount > 0) {
    await loadPapers(false);
  }

  // Set the first pending analysis for display (others will be handled later)
  if (pendingAnalyses.length > 0) {
    pendingUploadAnalysis.set(pendingAnalyses[0]);
  }

  if (lastError && successCount === 0) {
    error.set(lastError);
  }

  isLoading.set(false);

  // Auto-tag all uploaded papers in background (fire and forget)
  // Each will show as a separate job in JobStatusPanel
  for (const paperId of uploadedPaperIds) {
    api.autoTagPaper(paperId).then(async (tagResult) => {
      triggerJobRefresh();
      if (tagResult.success) {
        // Reload tags and papers after each auto-tagging completes
        await loadTags();
        await loadPapers();
      }
    });
  }
  // Trigger refresh immediately when jobs start
  if (uploadedPaperIds.length > 0) {
    triggerJobRefresh();
  }

  return { successCount, pendingAnalyses };
}

/**
 * Initialize the library (load papers and tags)
 */
export async function initializeLibrary() {
  await Promise.all([loadPapers(), loadTags()]);
}

/**
 * Re-analyze an existing paper's PDF to update metadata (async background job)
 * Returns the job ID - progress can be tracked via JobStatusPanel
 */
export async function reanalyzePaper(paperId: string): Promise<string | null> {
  error.set(null);

  const result = await api.reanalyzePaper(paperId);

  if (result.success && result.data) {
    // Trigger job panel refresh to show the new job
    triggerJobRefresh();
    return result.data.jobId;
  } else {
    error.set(result.error?.message ?? "Failed to start re-analysis");
    return null;
  }
}

/**
 * Re-extract PDF preprocessing data (force OCR and text extraction)
 */
export async function reextractPaper(paperId: string): Promise<string | null> {
  error.set(null);

  const result = await api.reextractPaper(paperId);

  if (result.success && result.data) {
    // Trigger job panel refresh to show the new job
    triggerJobRefresh();
    return result.data.jobId;
  } else {
    error.set(result.error?.message ?? "Failed to start re-extraction");
    return null;
  }
}

/**
 * Auto-tag a paper using LLM analysis
 */
export async function autoTagPaper(
  paperId: string
): Promise<AutoTagResponse | null> {
  isLoading.set(true);
  error.set(null);

  const result = await api.autoTagPaper(paperId);

  if (result.success && result.data) {
    // Reload tags to include any newly created tags
    await loadTags();
    // Reload papers to get updated tag associations
    await loadPapers();
    isLoading.set(false);
    return result.data;
  } else {
    error.set(result.error?.message ?? "Failed to auto-tag paper");
    isLoading.set(false);
    return null;
  }
}

/**
 * Get tag suggestions for a paper without applying
 */
export async function suggestTagsForPaper(
  paperId: string
): Promise<TagSuggestion[] | null> {
  const result = await api.suggestTags(paperId);

  if (result.success && result.data) {
    return result.data;
  } else {
    error.set(result.error?.message ?? "Failed to get tag suggestions");
    return null;
  }
}

/**
 * Merge similar tags (admin operation)
 */
export async function mergeSimilarTags(): Promise<number> {
  isLoading.set(true);
  error.set(null);

  const result = await api.mergeSimilarTags();

  if (result.success && result.data) {
    // Reload tags after merge
    await loadTags();
    isLoading.set(false);
    return result.data.mergedCount;
  } else {
    error.set(result.error?.message ?? "Failed to merge tags");
    isLoading.set(false);
    return 0;
  }
}

/**
 * Search for papers online (Semantic Scholar)
 */
export async function searchOnline(
  query: string,
  engine: "semantic" | "scholar" = "semantic"
): Promise<SearchResult[]> {
  const result = await api.searchOnline(query, engine);

  if (result.success && result.data) {
    return result.data;
  } else {
    return [];
  }
}

/**
 * Import a paper from online search result
 */
export async function importFromSearchResult(
  searchResult: SearchResult
): Promise<Paper | null> {
  isLoading.set(true);
  error.set(null);

  // Try to import by DOI first, then arXiv
  if (searchResult.doi) {
    const result = await api.importByDoi(searchResult.doi);
    if (result.success && result.data) {
      triggerJobAutoExpand(); // Show job panel for analysis progress
      await loadPapers();
      isLoading.set(false);
      return result.data;
    }
  }

  if (searchResult.arxivId) {
    const result = await api.importByArxiv(searchResult.arxivId);
    if (result.success && result.data) {
      triggerJobAutoExpand(); // Show job panel for analysis progress
      await loadPapers();
      isLoading.set(false);
      return result.data;
    }
  }

  // Fall back to creating paper manually from search result
  const result = await api.createPaper({
    title: searchResult.title,
    authors: searchResult.authors,
    abstract: searchResult.abstract ?? undefined,
    year: searchResult.year ?? undefined,
    conference: searchResult.venue ?? undefined,
    doi: searchResult.doi ?? undefined,
    arxivId: searchResult.arxivId ?? undefined,
    citations: searchResult.citationCount ?? 0,
    pdfUrl: searchResult.pdfUrl ?? undefined,
  });

  if (result.success && result.data) {
    triggerJobAutoExpand(); // Show job panel for analysis progress
    await loadPapers();
    isLoading.set(false);
    return result.data;
  }

  error.set(result.error?.message ?? "Failed to import paper");
  isLoading.set(false);
  return null;
}

/**
 * Fuzzy search papers by query string
 * Returns papers sorted by relevance score
 */
export function fuzzySearchPapers(papers: Paper[], query: string): Paper[] {
  const lowerQuery = query.toLowerCase();
  const terms = lowerQuery.split(/\s+/);

  return papers
    .map((paper) => {
      let score = 0;
      const searchText = `${paper.title} ${paper.authors.join(" ")} ${paper.abstract || ""}`.toLowerCase();

      // Score based on term matches
      terms.forEach((term) => {
        if (searchText.includes(term)) {
          score += 1;
          // Bonus for title match
          if (paper.title.toLowerCase().includes(term)) {
            score += 2;
          }
        }
      });

      return { paper, score };
    })
    .filter(({ score }) => score > 0)
    .sort((a, b) => b.score - a.score)
    .map(({ paper }) => paper);
}
