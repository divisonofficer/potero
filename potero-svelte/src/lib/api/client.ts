import type { ApiResponse, Paper, Tag, ChatMessage, ChatSession } from '$lib/types';

/**
 * API client for communicating with the Kotlin backend
 */
class ApiClient {
	private baseUrl: string;
	private directUploadUrl: string;

	constructor() {
		// In development, Vite proxies /api to the Ktor server
		// In production (Electron), we might use a different URL
		this.baseUrl = '/api';

		// For file uploads, use direct connection to backend to avoid WSL proxy issues
		// When running in browser, detect if we're accessing via WSL IP and use direct backend URL
		if (typeof window !== 'undefined') {
			const host = window.location.hostname;
			// If accessing via IP (WSL), connect directly to backend on same host
			if (host !== 'localhost' && host !== '127.0.0.1') {
				this.directUploadUrl = `http://${host}:8080/api`;
			} else {
				this.directUploadUrl = 'http://127.0.0.1:8080/api';
			}
		} else {
			this.directUploadUrl = '/api';
		}
	}

	async request<T>(
		method: string,
		path: string,
		body?: unknown
	): Promise<ApiResponse<T>> {
		try {
			const response = await fetch(`${this.baseUrl}${path}`, {
				method,
				headers: body ? { 'Content-Type': 'application/json' } : {},
				body: body ? JSON.stringify(body) : undefined
			});

			const result = await response.json();
			return result as ApiResponse<T>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'NETWORK_ERROR',
					message: error instanceof Error ? error.message : 'Network request failed'
				}
			};
		}
	}

	// Papers
	async listPapers(): Promise<ApiResponse<Paper[]>> {
		return this.request('GET', '/papers');
	}

	async getPaper(id: string): Promise<ApiResponse<Paper>> {
		return this.request('GET', `/papers/${id}`);
	}

	async createPaper(paper: Partial<Paper>): Promise<ApiResponse<Paper>> {
		return this.request('POST', '/papers', paper);
	}

	async updatePaper(id: string, paper: Partial<Paper>): Promise<ApiResponse<Paper>> {
		return this.request('PATCH', `/papers/${id}`, paper);
	}

	async deletePaper(id: string): Promise<ApiResponse<{ deletedId: string }>> {
		return this.request('DELETE', `/papers/${id}`);
	}

	async importByDoi(doi: string): Promise<ApiResponse<Paper>> {
		return this.request('POST', '/papers/import/doi', { doi });
	}

	async importByArxiv(arxivId: string): Promise<ApiResponse<Paper>> {
		return this.request('POST', '/papers/import/arxiv', { arxivId });
	}

	// Tags
	async listTags(): Promise<ApiResponse<Tag[]>> {
		return this.request('GET', '/tags');
	}

	async createTag(name: string, color: string): Promise<ApiResponse<Tag>> {
		return this.request('POST', '/tags', { name, color });
	}

	async assignTags(paperId: string, tags: string[]): Promise<ApiResponse<unknown>> {
		return this.request('POST', `/papers/${paperId}/tags`, { tags });
	}

	// Chat
	async sendMessage(
		message: string,
		paperId?: string,
		sessionId?: string
	): Promise<ApiResponse<{ messageId: string; sessionId: string; content: string }>> {
		return this.request('POST', '/chat/message', {
			message,
			paperId,
			sessionId
		});
	}

	async getChatSessions(paperId?: string): Promise<ApiResponse<ChatSession[]>> {
		const params = paperId ? `?paperId=${paperId}` : '';
		return this.request('GET', `/chat/sessions${params}`);
	}

	async getChatHistory(sessionId: string): Promise<ApiResponse<ChatMessage[]>> {
		return this.request('GET', `/chat/sessions/${sessionId}/messages`);
	}

	// Search
	async search(query: string, filters?: object): Promise<ApiResponse<unknown>> {
		return this.request('POST', '/search', { query, ...filters });
	}

	async getSuggestions(query: string): Promise<ApiResponse<unknown>> {
		return this.request('GET', `/search/suggest?q=${encodeURIComponent(query)}`);
	}

	// Settings
	async getSettings(): Promise<ApiResponse<Settings>> {
		return this.request('GET', '/settings');
	}

	async updateSettings(settings: Partial<Settings>): Promise<ApiResponse<Settings>> {
		return this.request('PUT', '/settings', settings);
	}

	// File Upload - uses direct backend URL to avoid proxy issues with large files
	// Now returns UploadAnalysisResponse with PDF analysis results
	async uploadPdf(
		file: File,
		title?: string,
		skipAnalysis?: boolean
	): Promise<ApiResponse<UploadAnalysisResponse>> {
		const formData = new FormData();
		formData.append('file', file);
		if (title) {
			formData.append('title', title);
		}
		if (skipAnalysis) {
			formData.append('skipAnalysis', 'true');
		}

		try {
			console.log(`Uploading to: ${this.directUploadUrl}/upload/pdf`);
			const response = await fetch(`${this.directUploadUrl}/upload/pdf`, {
				method: 'POST',
				body: formData
			});
			return (await response.json()) as ApiResponse<UploadAnalysisResponse>;
		} catch (error) {
			console.error('Upload fetch error:', error);
			return {
				success: false,
				error: {
					code: 'UPLOAD_ERROR',
					message: error instanceof Error ? error.message : 'Upload failed'
				}
			};
		}
	}

	// Confirm metadata selection for a paper
	async confirmMetadata(request: ConfirmMetadataRequest): Promise<ApiResponse<UploadResponse>> {
		try {
			const response = await fetch(`${this.directUploadUrl}/upload/confirm`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(request)
			});
			return (await response.json()) as ApiResponse<UploadResponse>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'CONFIRM_ERROR',
					message: error instanceof Error ? error.message : 'Failed to confirm metadata'
				}
			};
		}
	}

	async uploadPdfForPaper(paperId: string, file: File): Promise<ApiResponse<UploadResponse>> {
		const formData = new FormData();
		formData.append('file', file);

		try {
			const response = await fetch(`${this.directUploadUrl}/upload/pdf/${paperId}`, {
				method: 'POST',
				body: formData
			});
			return (await response.json()) as ApiResponse<UploadResponse>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'UPLOAD_ERROR',
					message: error instanceof Error ? error.message : 'Upload failed'
				}
			};
		}
	}

	// Re-analyze an existing paper's PDF (async job)
	async reanalyzePaper(paperId: string): Promise<ApiResponse<ReanalyzeJobResponse>> {
		try {
			const response = await fetch(`${this.directUploadUrl}/upload/reanalyze/${paperId}`, {
				method: 'POST'
			});
			return (await response.json()) as ApiResponse<ReanalyzeJobResponse>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'REANALYZE_ERROR',
					message: error instanceof Error ? error.message : 'Failed to re-analyze paper'
				}
			};
		}
	}

	// Analyze paper with LLM (extract title, abstract, Korean translation, thumbnail)
	async analyzePaperWithLLM(paperId: string): Promise<ApiResponse<LLMAnalysisResponse>> {
		try {
			const response = await fetch(`${this.directUploadUrl}/upload/analyze/${paperId}`, {
				method: 'POST'
			});
			return (await response.json()) as ApiResponse<LLMAnalysisResponse>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'ANALYZE_ERROR',
					message: error instanceof Error ? error.message : 'Failed to analyze paper with LLM'
				}
			};
		}
	}

	// Get thumbnail URL for a paper
	getThumbnailUrl(paperId: string): string {
		return `${this.directUploadUrl}/upload/thumbnail/${paperId}`;
	}

	// Auto-tag a paper using LLM (async - returns job ID immediately)
	async autoTagPaper(paperId: string): Promise<ApiResponse<AutoTagJobResponse>> {
		return this.request('POST', `/papers/${paperId}/tags/auto`);
	}

	// Check auto-tag job status
	async getAutoTagJobStatus(paperId: string, jobId: string): Promise<ApiResponse<AutoTagJobStatus>> {
		return this.request('GET', `/papers/${paperId}/tags/auto/${jobId}`);
	}

	// Poll for auto-tag completion
	async waitForAutoTag(
		paperId: string,
		jobId: string,
		onProgress?: (status: string) => void,
		maxWaitMs: number = 60000,
		pollIntervalMs: number = 1000
	): Promise<ApiResponse<AutoTagResponse>> {
		const startTime = Date.now();

		while (Date.now() - startTime < maxWaitMs) {
			const result = await this.getAutoTagJobStatus(paperId, jobId);

			if (!result.success || !result.data) {
				return {
					success: false,
					error: result.error || { code: 'POLL_ERROR', message: 'Failed to get job status' }
				};
			}

			const status = result.data;
			onProgress?.(status.status);

			if (status.status === 'completed' && status.result) {
				return { success: true, data: status.result };
			}

			if (status.status === 'failed') {
				return {
					success: false,
					error: { code: 'AUTO_TAG_FAILED', message: status.error || 'Auto-tagging failed' }
				};
			}

			// Still processing, wait and poll again
			await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
		}

		return {
			success: false,
			error: { code: 'TIMEOUT', message: 'Auto-tagging timed out' }
		};
	}

	// Get tag suggestions for a paper without applying
	async suggestTags(paperId: string): Promise<ApiResponse<TagSuggestion[]>> {
		return this.request('POST', `/papers/${paperId}/tags/suggest`);
	}

	// Merge similar tags (admin operation)
	async mergeSimilarTags(): Promise<ApiResponse<{ mergedCount: number; message: string }>> {
		return this.request('POST', '/tags/merge');
	}

	// Online search for papers
	async searchOnline(
		query: string,
		engine: 'semantic' | 'scholar' = 'semantic'
	): Promise<ApiResponse<SearchResult[]>> {
		return this.request(
			'GET',
			`/search/online?q=${encodeURIComponent(query)}&engine=${engine}`
		);
	}

	// LLM logs and status (uses /api/llm prefix)
	async getLLMLogs(limit: number = 50, purpose?: string): Promise<ApiResponse<LLMLogEntry[]>> {
		const params = new URLSearchParams({ limit: limit.toString() });
		if (purpose) params.append('purpose', purpose);
		return this.request('GET', `/llm/logs?${params}`);
	}

	async getLLMLogDetail(logId: string): Promise<ApiResponse<LLMLogDetail>> {
		return this.request('GET', `/llm/logs/${logId}`);
	}

	async getLLMStats(): Promise<ApiResponse<LLMUsageStats>> {
		return this.request('GET', '/llm/stats');
	}

	async getLLMStatus(): Promise<ApiResponse<LLMStatus>> {
		return this.request('GET', '/llm/status');
	}

	async clearLLMLogs(): Promise<ApiResponse<{ cleared: boolean }>> {
		return this.request('DELETE', '/llm/logs');
	}

	// Jobs API - Background task management
	async getJobs(includeCompleted: boolean = true): Promise<ApiResponse<JobDto[]>> {
		return this.request('GET', `/jobs?includeCompleted=${includeCompleted}`);
	}

	async getActiveJobs(): Promise<ApiResponse<JobDto[]>> {
		return this.request('GET', '/jobs/active');
	}

	async getJob(jobId: string): Promise<ApiResponse<JobDto>> {
		return this.request('GET', `/jobs/${jobId}`);
	}

	async getJobsForPaper(paperId: string): Promise<ApiResponse<JobDto[]>> {
		return this.request('GET', `/jobs/paper/${paperId}`);
	}

	async cancelJob(jobId: string): Promise<ApiResponse<{ cancelled: boolean; jobId: string }>> {
		return this.request('POST', `/jobs/${jobId}/cancel`);
	}

	async clearCompletedJobs(): Promise<ApiResponse<{ cleared: boolean }>> {
		return this.request('DELETE', '/jobs/completed');
	}

	// Authors API - Semantic Scholar integration
	async searchAuthors(query: string): Promise<ApiResponse<AuthorSearchResult[]>> {
		return this.request('GET', `/authors/search?q=${encodeURIComponent(query)}`);
	}

	async lookupAuthor(name: string, affiliation?: string): Promise<ApiResponse<AuthorProfileResponse>> {
		const params = new URLSearchParams({ name });
		if (affiliation) params.append('affiliation', affiliation);
		return this.request('GET', `/authors/lookup?${params.toString()}`);
	}

	async getAuthorById(authorId: string): Promise<ApiResponse<AuthorProfileResponse>> {
		return this.request('GET', `/authors/${authorId}`);
	}

	// Find paper by DOI (returns null if not found, doesn't create)
	async findPaperByDoi(doi: string): Promise<ApiResponse<Paper | null>> {
		return this.request('GET', `/papers/find/doi?doi=${encodeURIComponent(doi)}`);
	}

	// Find paper by arXiv ID (returns null if not found, doesn't create)
	async findPaperByArxiv(arxivId: string): Promise<ApiResponse<Paper | null>> {
		return this.request('GET', `/papers/find/arxiv?arxivId=${encodeURIComponent(arxivId)}`);
	}

	// Import paper by downloading PDF from URL
	async importFromUrl(request: ImportFromUrlRequest): Promise<ApiResponse<UploadAnalysisResponse>> {
		try {
			const response = await fetch(`${this.directUploadUrl}/upload/from-url`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(request)
			});
			return (await response.json()) as ApiResponse<UploadAnalysisResponse>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'IMPORT_ERROR',
					message: error instanceof Error ? error.message : 'Failed to import from URL'
				}
			};
		}
	}

	// References API - Backend-based references extraction
	// Returns ReferencesAnalysisResult with references array and metadata
	async getReferences(paperId: string): Promise<ApiResponse<ReferencesAnalysisResult>> {
		return this.request('GET', `/papers/${paperId}/references`);
	}

	async getReferenceByNumber(paperId: string, number: number): Promise<ApiResponse<Reference>> {
		return this.request('GET', `/papers/${paperId}/references/${number}`);
	}

	async analyzeReferences(paperId: string): Promise<ApiResponse<ReferencesAnalysisResult>> {
		return this.request('POST', `/papers/${paperId}/references/analyze`);
	}

	async deleteReferences(paperId: string): Promise<ApiResponse<{ deleted: boolean }>> {
		return this.request('DELETE', `/papers/${paperId}/references`);
	}

	async searchReferences(
		query: string,
		type: 'title' | 'authors' = 'title'
	): Promise<ApiResponse<Reference[]>> {
		return this.request('GET', `/references/search?q=${encodeURIComponent(query)}&type=${type}`);
	}

	// Citations API - In-text citation extraction and linking
	async getCitations(paperId: string): Promise<ApiResponse<CitationSpan[]>> {
		return this.request('GET', `/papers/${paperId}/citations`);
	}

	async getCitationsByPage(paperId: string, pageNum: number): Promise<ApiResponse<CitationSpan[]>> {
		return this.request('GET', `/papers/${paperId}/citations/page/${pageNum}`);
	}

	async getCitation(paperId: string, spanId: string): Promise<ApiResponse<CitationSpan>> {
		return this.request('GET', `/papers/${paperId}/citations/${spanId}`);
	}

	async getCitationReferences(paperId: string, spanId: string): Promise<ApiResponse<Reference[]>> {
		return this.request('GET', `/papers/${paperId}/citations/${spanId}/references`);
	}

	async extractCitations(paperId: string): Promise<ApiResponse<CitationExtractionResponse>> {
		return this.request('POST', `/papers/${paperId}/citations/extract`);
	}

	async deleteCitations(paperId: string): Promise<ApiResponse<{ deleted: boolean }>> {
		return this.request('DELETE', `/papers/${paperId}/citations`);
	}

	async getCitationStats(paperId: string): Promise<ApiResponse<CitationStats>> {
		return this.request('GET', `/papers/${paperId}/citations/stats`);
	}

	async getCitationsForReference(referenceId: string): Promise<ApiResponse<CitationSpan[]>> {
		return this.request('GET', `/references/${referenceId}/citations`);
	}

	// Bulk Re-analyze API
	async bulkReanalyze(request: BulkReanalyzeRequest): Promise<ApiResponse<BulkReanalyzeResponse>> {
		return this.request('POST', '/upload/bulk-reanalyze', request);
	}

	async bulkReanalyzePreview(
		request: BulkReanalyzeRequest
	): Promise<ApiResponse<BulkReanalyzePreview>> {
		return this.request('POST', '/upload/bulk-reanalyze/preview', request);
	}
}

// Types

/**
 * Author search result from Semantic Scholar
 */
export interface AuthorSearchResult {
	id: string;
	name: string;
	affiliations: string[];
	paperCount: number | null;
	citationCount: number | null;
	hIndex: number | null;
}

/**
 * Detailed author profile with external links
 */
export interface AuthorProfileResponse {
	name: string;
	affiliations: string[];
	paperCount: number;
	citationCount: number;
	hIndex: number | null;
	i10Index: number | null;
	homepage: string | null;
	orcid: string | null;
	semanticScholarId: string | null;
	googleScholarUrl: string | null;
	dblpUrl: string | null;
	semanticScholarUrl: string | null;
}
export interface Settings {
	llmApiKey: string | null;
	llmProvider: string;
	pdfStoragePath: string | null;
	theme: string;
	semanticScholarApiKey: string | null;
}

export interface UploadResponse {
	paperId: string;
	fileName: string;
	filePath: string;
	title: string;
}

/**
 * Search result from Semantic Scholar or other sources
 */
export interface SearchResult {
	id: string;
	title: string;
	authors: string[];
	year: number | null;
	venue: string | null;
	citationCount: number | null;
	abstract: string | null;
	pdfUrl: string | null;
	doi: string | null;
	arxivId: string | null;
	source: string; // "semantic_scholar", "google_scholar", etc.
}

/**
 * Resolved metadata from DOI/arXiv lookup
 */
export interface ResolvedMetadata {
	title: string;
	authors: string[];
	abstract: string | null;
	doi: string | null;
	arxivId: string | null;
	year: number | null;
	venue: string | null;
	pdfUrl: string | null;
	citationsCount: number | null;
}

/**
 * Extended upload response with analysis results
 */
export interface UploadAnalysisResponse {
	paperId: string;
	fileName: string;
	filePath: string;
	title: string;
	// Analysis results
	detectedDoi: string | null;
	detectedArxivId: string | null;
	pdfMetadataTitle: string | null;
	pdfMetadataAuthor: string | null;
	// If DOI/arXiv found and resolved automatically
	autoResolved: boolean;
	resolvedMetadata: ResolvedMetadata | null;
	// If no identifier found, search results for user selection
	searchResults: SearchResult[];
	needsUserConfirmation: boolean;
}

/**
 * Request to confirm metadata selection
 */
export interface ConfirmMetadataRequest {
	paperId: string;
	title: string;
	authors: string[];
	abstract?: string;
	doi?: string;
	arxivId?: string;
	year?: number;
	venue?: string;
	citationsCount?: number;
}

/**
 * Request to import paper by downloading PDF from URL
 */
export interface ImportFromUrlRequest {
	pdfUrl: string;
	title?: string;
	authors?: string[];
	abstract?: string;
	doi?: string;
	arxivId?: string;
	year?: number;
	venue?: string;
	citationsCount?: number;
}

/**
 * Tag suggestion from LLM analysis
 */
export interface TagSuggestion {
	name: string;
	existingTagId: string | null;
	existingTagName: string | null;
	isNew: boolean;
}

/**
 * Response from LLM-based PDF analysis
 */
export interface LLMAnalysisResponse {
	paperId: string;
	title: string | null;
	authors: string[];
	abstract: string | null;
	abstractKorean: string | null;
	thumbnailPath: string | null;
}

/**
 * Response when starting an auto-tag job
 */
export interface AutoTagJobResponse {
	jobId: string;
	paperId: string;
	status: string;
}

/**
 * Response from async re-analyze job submission
 */
export interface ReanalyzeJobResponse {
	jobId: string;
	paperId: string;
	message: string;
}

/**
 * Status of an auto-tag job
 */
export interface AutoTagJobStatus {
	jobId: string;
	paperId: string;
	status: string; // 'processing' | 'completed' | 'failed'
	result: AutoTagResponse | null;
	error: string | null;
}

/**
 * Response from auto-tagging a paper
 */
export interface AutoTagResponse {
	paperId: string;
	suggestedTags: TagSuggestion[];
	assignedTags: {
		id: string;
		name: string;
		color: string;
		count: number;
	}[];
}

/**
 * LLM log entry (summarized)
 */
export interface LLMLogEntry {
	id: string;
	timestamp: string;
	provider: string;
	purpose: string;
	inputPromptPreview: string;
	inputTokensEstimate: number;
	outputResponsePreview: string | null;
	outputTokensEstimate: number | null;
	durationMs: number;
	success: boolean;
	errorMessage: string | null;
	paperId: string | null;
	paperTitle: string | null;
}

/**
 * LLM log entry (detailed)
 */
export interface LLMLogDetail {
	id: string;
	timestamp: string;
	provider: string;
	purpose: string;
	inputPrompt: string;
	inputTokensEstimate: number;
	outputResponse: string | null;
	outputTokensEstimate: number | null;
	durationMs: number;
	success: boolean;
	errorMessage: string | null;
	paperId: string | null;
	paperTitle: string | null;
}

/**
 * LLM usage statistics
 */
export interface LLMUsageStats {
	totalCalls: number;
	successfulCalls: number;
	failedCalls: number;
	totalInputTokensEstimate: number;
	totalOutputTokensEstimate: number;
	averageDurationMs: number;
	callsByPurpose: Record<string, number>;
	callsByProvider: Record<string, number>;
}

/**
 * LLM service status
 */
export interface LLMStatus {
	configured: boolean;
	provider: string;
	endpoint: string;
}

/**
 * Reference entry from a paper's References/Bibliography section
 * Matches backend ReferenceDto
 */
export interface Reference {
	id: string;
	paperId: string;
	number: number;
	rawText: string;
	authors: string | null;
	title: string | null;
	venue: string | null;
	year: number | null;
	doi: string | null;
	pageNum: number;
	searchQuery?: string;  // Backend returns searchQuery instead of createdAt
	createdAt?: string;    // For frontend-created references
}

/**
 * Result from backend references analysis
 * Matches backend ReferencesResponse
 */
export interface ReferencesAnalysisResult {
	paperId: string;
	totalCount: number;           // Backend uses totalCount
	referencesCount?: number;     // Alias for compatibility
	references: Reference[];
	referencesStartPage: number | null;
}

/**
 * Background job DTO
 */
export interface JobDto {
	id: string;
	type: string; // 'PDF_ANALYSIS' | 'AUTO_TAGGING' | 'METADATA_LOOKUP' | 'TAG_MERGE' | 'BULK_IMPORT'
	title: string;
	description: string;
	status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
	progress: number; // 0-100
	progressMessage: string;
	createdAt: string;
	startedAt: string | null;
	completedAt: string | null;
	error: string | null;
	paperId: string | null;
}

/**
 * Request for bulk re-analysis
 */
export interface BulkReanalyzeRequest {
	criteria?: string[]; // "missing_thumbnail", "missing_venue", "missing_doi", "missing_abstract", "missing_year", "all"
	paperIds?: string[]; // If specified, only these papers (overrides criteria)
}

/**
 * Response for bulk re-analysis job
 */
export interface BulkReanalyzeResponse {
	jobId: string;
	papersQueued: number;
	message: string;
}

/**
 * Preview of papers that would be affected by bulk re-analysis
 */
export interface BulkReanalyzePreview {
	totalPapers: number;
	papers: BulkReanalyzePaperPreview[];
}

export interface BulkReanalyzePaperPreview {
	id: string;
	title: string;
	missingFields: string[];
}

/**
 * Bounding box in PDF coordinates (origin at bottom-left)
 */
export interface BoundingBox {
	x1: number;
	y1: number;
	x2: number;
	y2: number;
}

/**
 * In-text citation span with bounding box
 * Represents clickable citation references like [1], [1,2,3], (Smith et al., 2024)
 */
export interface CitationSpan {
	id: string;
	paperId: string;
	pageNum: number;
	bbox: BoundingBox;
	rawText: string;
	style: 'numeric' | 'author_year' | 'unknown';
	provenance: 'annotation' | 'pattern';
	confidence: number;
	destPage: number | null;
	destY: number | null;
	linkedRefIds: string[];
}

/**
 * Link between a citation span and a reference
 */
export interface CitationLink {
	id: string;
	citationSpanId: string;
	referenceId: string;
	linkMethod: string;
	confidence: number;
}

/**
 * Statistics for citation extraction
 */
export interface CitationStats {
	totalSpans: number;
	annotationSpans: number;
	patternSpans: number;
	linkedCount: number;
	avgConfidence: number;
}

/**
 * Response from citation extraction
 */
export interface CitationExtractionResponse {
	paperId: string;
	spans: CitationSpan[];
	links: CitationLink[];
	stats: CitationStats;
}

export const api = new ApiClient();
