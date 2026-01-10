import type {
	ApiResponse,
	Paper,
	Tag,
	ChatMessage,
	ChatSession,
	ResearchNote,
	ResearchNoteWithLinks,
	BacklinkInfo,
	RelatedPaperCandidate,
	ComparisonTableWithData,
	ColumnDefinition,
	ComparisonTable
} from '$lib/types';

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

	async downloadPdf(paperId: string): Promise<ApiResponse<{ pdfPath: string; message: string }>> {
		return this.request('POST', `/papers/${paperId}/download-pdf`);
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
		sessionId?: string,
		files?: ChatFileAttachment[]
	): Promise<ApiResponse<{ messageId: string; sessionId: string; content: string }>> {
		return this.request('POST', '/chat/message', {
			message,
			paperId,
			sessionId,
			files: files ?? []
		});
	}

	async uploadChatFile(file: File): Promise<ApiResponse<{ files: ChatFileAttachment[] }>> {
		const formData = new FormData();
		formData.append('file', file);

		try {
			const response = await fetch(`${this.directUploadUrl}/chat/upload`, {
				method: 'POST',
				body: formData
			});
			return (await response.json()) as ApiResponse<{ files: ChatFileAttachment[] }>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'UPLOAD_ERROR',
					message: error instanceof Error ? error.message : 'File upload failed'
				}
			};
		}
	}

	async getChatSessions(paperId?: string): Promise<ApiResponse<ChatSession[]>> {
		const params = paperId ? `?paperId=${paperId}` : '';
		return this.request('GET', `/chat/sessions${params}`);
	}

	async getChatHistory(sessionId: string): Promise<ApiResponse<ChatMessage[]>> {
		return this.request('GET', `/chat/sessions/${sessionId}/messages`);
	}

	async createChatSession(
		title: string,
		paperId?: string
	): Promise<ApiResponse<ChatSession>> {
		return this.request('POST', '/chat/sessions', { title, paperId });
	}

	async deleteChatSession(sessionId: string): Promise<ApiResponse<{ deletedSessionId: string }>> {
		return this.request('DELETE', `/chat/sessions/${sessionId}`);
	}

	/**
	 * Send a chat message with SSE streaming support.
	 *
	 * Yields events as they arrive:
	 * - session: { sessionId }
	 * - start: {}
	 * - delta: { content }
	 * - tool_call: { toolName }
	 * - tool_result: { toolName, success, error }
	 * - done: { messageId, content, toolExecutions }
	 * - error: { message }
	 *
	 * @param message User message
	 * @param paperId Optional paper ID
	 * @param sessionId Optional session ID
	 * @param onEvent Callback for each SSE event
	 */
	async sendMessageStream(
		message: string,
		paperId: string | undefined,
		sessionId: string | undefined,
		onEvent: (event: {
			type: 'session' | 'start' | 'delta' | 'tool_call' | 'tool_result' | 'done' | 'error';
			data: unknown;
		}) => void
	): Promise<void> {
		// Build query parameters
		const params = new URLSearchParams();
		params.append('message', message);
		if (paperId) params.append('paperId', paperId);
		if (sessionId) params.append('sessionId', sessionId);

		const url = `${this.baseUrl}/chat/stream?${params.toString()}`;

		const response = await fetch(url);
		if (!response.body) {
			throw new Error('No response body');
		}

		const reader = response.body.getReader();
		const decoder = new TextDecoder();
		let buffer = '';

		try {
			while (true) {
				const { done, value } = await reader.read();
				if (done) break;

				buffer += decoder.decode(value, { stream: true });

				// Process complete SSE messages
				const lines = buffer.split('\n');
				buffer = lines.pop() || ''; // Keep incomplete line in buffer

				let currentEvent: string | null = null;
				let currentData = '';

				for (const line of lines) {
					if (line.startsWith('event:')) {
						currentEvent = line.substring(6).trim();
					} else if (line.startsWith('data:')) {
						currentData = line.substring(5).trim();
					} else if (line === '' && currentEvent && currentData) {
						// Complete event - parse and emit
						try {
							const data = JSON.parse(currentData);
							onEvent({
								type: currentEvent as any,
								data
							});
						} catch (e) {
							console.error('[API] Failed to parse SSE data:', currentData, e);
						}

						currentEvent = null;
						currentData = '';
					}
				}
			}
		} finally {
			reader.releaseLock();
		}
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

	async getAPIConfigs(): Promise<ApiResponse<APIConfigDto[]>> {
		return this.request('GET', '/settings/apis');
	}

	async updateAPIConfig(
		id: string,
		enabled: boolean,
		apiKey?: string
	): Promise<ApiResponse<{ updated: boolean }>> {
		return this.request('PUT', `/settings/apis/${id}`, {
			enabled,
			apiKey
		});
	}

	async saveSSOToken(
		accessToken: string,
		siteName?: string,
		expiresAt?: number
	): Promise<ApiResponse<Settings>> {
		return this.request('PUT', '/settings', {
			ssoAccessToken: accessToken,
			ssoSiteName: siteName,
			ssoTokenExpiry: expiresAt
		});
	}

	/**
	 * Trigger SSO login flow
	 * - In Electron: Opens modal window with auto token extraction
	 * - In Browser: Redirects to SSO login page (manual token entry)
	 */
	async loginSSO(): Promise<{
		success: boolean;
		accessToken?: string;
		expiresIn?: number | null;
		error?: string;
	}> {
		// Check if running in Electron
		if (typeof window !== 'undefined' && window.electronAPI?.isElectron) {
			try {
				const result = await window.electronAPI.loginSSO();
				return result;
			} catch (error) {
				return {
					success: false,
					error: error instanceof Error ? error.message : 'SSO login failed'
				};
			}
		} else {
			// Browser environment: redirect to SSO login
			// Callback will be handled by /auth/callback route
			window.location.href = 'https://genai.postech.ac.kr/auth/login';
			return { success: true }; // Will redirect, so this won't be reached
		}
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

	// Re-extract PDF preprocessing data (force OCR and text extraction)
	async reextractPaper(paperId: string): Promise<ApiResponse<ReextractJobResponse>> {
		try {
			const response = await fetch(`${this.directUploadUrl}/upload/re-extract/${paperId}`, {
				method: 'POST'
			});
			return (await response.json()) as ApiResponse<ReextractJobResponse>;
		} catch (error) {
			return {
				success: false,
				error: {
					code: 'REEXTRACT_ERROR',
					message: error instanceof Error ? error.message : 'Failed to re-extract PDF'
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

	// GROBID References API - GROBID-extracted references with enhanced metadata
	async getGrobidReferences(paperId: string): Promise<ApiResponse<GrobidReference[]>> {
		return this.request('GET', `/papers/${paperId}/grobid-references`);
	}

	// Narratives API - Paper-to-Narrative Engine
	async getNarratives(paperId: string): Promise<ApiResponse<Narrative[]>> {
		return this.request('GET', `/papers/${paperId}/narratives`);
	}

	async getNarrative(
		paperId: string,
		style: NarrativeStyle,
		language: NarrativeLanguage
	): Promise<ApiResponse<Narrative>> {
		return this.request('GET', `/papers/${paperId}/narratives/${style}/${language}`);
	}

	async generateNarratives(
		paperId: string,
		request?: NarrativeGenerationRequest
	): Promise<ApiResponse<NarrativeGenerationResponse>> {
		return this.request('POST', `/papers/${paperId}/narratives/generate`, request ?? {});
	}

	async deleteNarratives(paperId: string): Promise<ApiResponse<{ deleted: boolean }>> {
		return this.request('DELETE', `/papers/${paperId}/narratives`);
	}

	async getNarrativeStyles(): Promise<ApiResponse<NarrativeOptionsResponse['styles']>> {
		return this.request('GET', '/narratives/styles');
	}

	async getNarrativeLanguages(): Promise<ApiResponse<NarrativeOptionsResponse['languages']>> {
		return this.request('GET', '/narratives/languages');
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

	// Research Notes
	async getNotes(): Promise<ApiResponse<ResearchNote[]>> {
		return this.request('GET', '/notes');
	}

	async getStandaloneNotes(): Promise<ApiResponse<ResearchNote[]>> {
		return this.request('GET', '/notes/standalone');
	}

	async getNotesByPaper(paperId: string): Promise<ApiResponse<ResearchNote[]>> {
		return this.request('GET', `/notes/paper/${paperId}`);
	}

	async getNote(id: string): Promise<ApiResponse<ResearchNoteWithLinks>> {
		return this.request('GET', `/notes/${id}`);
	}

	async createNote(data: {
		title: string;
		content: string;
		paperId?: string | null;
	}): Promise<ApiResponse<ResearchNote>> {
		return this.request('POST', '/notes', data);
	}

	async updateNote(
		id: string,
		data: { title: string; content: string; paperId?: string | null }
	): Promise<ApiResponse<ResearchNote>> {
		return this.request('PATCH', `/notes/${id}`, data);
	}

	async deleteNote(id: string): Promise<ApiResponse<{ deletedId: string }>> {
		return this.request('DELETE', `/notes/${id}`);
	}

	async getBacklinks(noteId: string): Promise<ApiResponse<BacklinkInfo[]>> {
		return this.request('GET', `/notes/${noteId}/backlinks`);
	}

	async searchNotes(query: string): Promise<ApiResponse<ResearchNote[]>> {
		return this.request('GET', `/notes/search?q=${encodeURIComponent(query)}`);
	}

	async generateNoteTemplate(paperId: string): Promise<ApiResponse<{ title: string; template: string }>> {
		return this.request('POST', `/notes/generate-template?paperId=${paperId}`);
	}

	// Related Work & Comparison Tables

	/**
	 * Search for related papers using multiple strategies
	 */
	async searchRelatedPapers(
		paperId: string,
		limit: number = 20,
		forceRefresh: boolean = false
	): Promise<ApiResponse<RelatedPaperCandidate[]>> {
		return this.request('POST', `/papers/${paperId}/related/find`, {
			limit,
			forceRefresh
		});
	}

	/**
	 * Get cached related papers
	 */
	async getRelatedPapers(
		paperId: string,
		limit?: number
	): Promise<ApiResponse<RelatedPaperCandidate[]>> {
		const params = limit ? `?limit=${limit}` : '';
		return this.request('GET', `/papers/${paperId}/related${params}`);
	}

	/**
	 * Generate comparison table for selected papers
	 */
	async generateComparison(
		paperId: string,
		relatedPaperIds: string[],
		title: string,
		description?: string,
		columns?: ColumnDefinition[],
		generateNarrative: boolean = true
	): Promise<ApiResponse<ComparisonTableWithData>> {
		return this.request('POST', `/papers/${paperId}/comparisons/generate`, {
			relatedPaperIds,
			title,
			description,
			columns: columns ?? [],
			generateNarrative
		});
	}

	/**
	 * List all comparison tables for a paper
	 */
	async getComparisonTables(paperId: string): Promise<ApiResponse<ComparisonTable[]>> {
		return this.request('GET', `/papers/${paperId}/comparisons`);
	}

	/**
	 * Get specific comparison table with full data
	 */
	async getComparison(
		paperId: string,
		tableId: string
	): Promise<ApiResponse<ComparisonTableWithData>> {
		return this.request('GET', `/papers/${paperId}/comparisons/${tableId}`);
	}

	/**
	 * Delete comparison table
	 */
	async deleteComparison(
		paperId: string,
		tableId: string
	): Promise<ApiResponse<{ deleted: boolean }>> {
		return this.request('DELETE', `/papers/${paperId}/comparisons/${tableId}`);
	}

	/**
	 * Search comparison tables by text (title, description, narrative content, key insights)
	 */
	async searchComparisonTables(
		query: string,
		limit: number = 10
	): Promise<ApiResponse<ComparisonTable[]>> {
		return this.request('GET', `/comparisons/search?q=${encodeURIComponent(query)}&limit=${limit}`);
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
	// SSO Authentication for POSTECH GenAI file upload
	ssoConfigured: boolean;
	ssoTokenExpiresAt: number | null;
	ssoSiteName: string;
	// PDF Download options
	enableSciHub?: boolean;
	// Reference Extraction Engines
	grobidEnabled?: boolean;
	pdftotextEnabled?: boolean;
	ocrEnabled?: boolean;
}

export interface APIConfigDto {
	id: string;
	name: string;
	enabled: boolean;
	requiresKey: boolean;
	hasKey: boolean;
	keyMasked: string | null;
	description: string;
	keyRegistrationUrl: string | null;
	category: string;
}

export interface ChatFileAttachment {
	id: string;
	name: string;
	url: string;
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
 * Response for reextract job submission
 */
export interface ReextractJobResponse {
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
	linkedReferences?: LinkedReference[];  // Full reference info from backend
}

/**
 * Linked reference with full metadata (from backend linking)
 */
export interface LinkedReference {
	id: string;
	number: number;
	authors: string | null;
	title: string | null;
	venue: string | null;
	year: number | null;
	doi: string | null;
	searchQuery: string;
	linkMethod: string;
	linkConfidence: number;
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

/**
 * GROBID-extracted reference with metadata
 */
export interface GrobidReference {
	id: string;
	paperId: string;
	xmlId: string;
	authors: string | null;
	title: string | null;
	venue: string | null;
	year: number | null;
	doi: string | null;
	arxivId: string | null;
	pageNum: number | null;
	confidence: number;
}

// ============================================
// Narrative Types (Paper-to-Narrative Engine)
// ============================================

/**
 * Narrative style options
 */
export type NarrativeStyle = 'BLOG' | 'NEWS' | 'REDDIT';

/**
 * Narrative language options
 */
export type NarrativeLanguage = 'KOREAN' | 'ENGLISH';

/**
 * Figure explanation within a narrative
 */
export interface NarrativeFigureExplanation {
	figureId: string;
	label: string;
	originalCaption: string | null;
	explanation: string;
	relevance: string;
}

/**
 * Concept explanation for technical terms
 */
export interface NarrativeConceptExplanation {
	term: string;
	definition: string;
	analogy: string | null;
	relatedTerms: string[];
}

/**
 * Generated narrative for a paper
 * Note: Backend returns style as uppercase string (BLOG, NEWS, REDDIT)
 * and language as code (ko, en)
 */
export interface Narrative {
	id: string;
	paperId: string;
	style: NarrativeStyle; // Backend returns: "BLOG", "NEWS", "REDDIT"
	language: string; // Backend returns language code: "ko", "en"
	title: string;
	content: string;
	summary: string;
	figureExplanations: NarrativeFigureExplanation[];
	conceptExplanations: NarrativeConceptExplanation[];
	estimatedReadTime: number;
	createdAt: string;
}

/**
 * Request to generate narratives
 */
export interface NarrativeGenerationRequest {
	styles?: NarrativeStyle[];
	languages?: NarrativeLanguage[];
	regenerate?: boolean;
	includeConceptExplanations?: boolean;
}

/**
 * Response from narrative generation start
 */
export interface NarrativeGenerationResponse {
	jobId: string;
	status: string;
	message: string;
}

/**
 * Progress update for narrative generation
 */
export interface NarrativeGenerationProgress {
	paperId: string;
	totalNarratives: number;
	completedNarratives: number;
	currentStage: string;
	currentStyle?: NarrativeStyle;
	currentLanguage?: NarrativeLanguage;
}

/**
 * Available styles/languages response
 */
export interface NarrativeOptionsResponse {
	styles: { name: NarrativeStyle; displayName: string; description: string }[];
	languages: { code: string; name: NarrativeLanguage; displayName: string }[];
}

export const api = new ApiClient();
