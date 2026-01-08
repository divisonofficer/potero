/**
 * Core data types for Potero
 */

export interface Paper {
	id: string;
	title: string;
	authors: string[];
	year: number | null;
	conference: string | null;
	subject: string[]; // Tags
	abstract: string | null;
	pdfUrl: string | null;
	thumbnailUrl: string | null;
	citations: number;
	doi: string | null;
	hasBlogView: boolean;
}

export interface Author {
	id: string;
	name: string;
	affiliation: string | null;
}

export interface Tag {
	name: string;
	color: string;
	count: number;
}

export interface Note {
	id: string;
	paperId: string;
	content: string;
	type: 'general' | 'annotation' | 'highlight';
	pageNumber: number | null;
	position: NotePosition | null;
	color: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface NotePosition {
	x: number;
	y: number;
	width: number;
	height: number;
}

export interface ChatMessage {
	id: string;
	sessionId: string;
	role: 'user' | 'assistant';
	content: string;
	model: string | null;
	createdAt: string;
}

export interface ChatSession {
	id: string;
	paperId: string | null;
	title: string;
	messageCount: number;
	lastMessage: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface Tab {
	id: string;
	type: 'home' | 'viewer' | 'settings';
	title: string;
	paper?: Paper;
	// PDF viewer state (persisted across tab switches)
	viewerState?: PdfViewerState;
}

export interface PdfViewerState {
	scrollTop: number;
	scrollLeft: number;
	currentPage: number;
	scale: number;
	viewMode: 'single' | 'scroll';
}

export type ViewStyle = 'grid' | 'list' | 'compact';
export type SortBy = 'recent' | 'citations' | 'title';

export interface SearchFilters {
	year?: { min?: number; max?: number };
	conference?: string[];
	subject?: string[];
}

export interface ApiResponse<T> {
	success: boolean;
	data?: T;
	error?: {
		code: string;
		message: string;
	};
}
