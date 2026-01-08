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

	private async request<T>(
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
}

// Types
export interface Settings {
	llmApiKey: string | null;
	llmProvider: string;
	pdfStoragePath: string | null;
	theme: string;
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

export const api = new ApiClient();
