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
	arxivId: string | null;
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
	type: 'home' | 'viewer' | 'settings' | 'author' | 'tag' | 'journal' | 'notes' | 'note-viewer';
	title: string;
	paper?: Paper;
	// PDF viewer state (persisted across tab switches)
	viewerState?: PdfViewerState;
	// Author profile data
	author?: AuthorProfile;
	// Tag details
	tag?: TagProfile;
	// Journal/venue details
	journal?: JournalProfile;
	// Research note (for note-viewer tab)
	note?: ResearchNote;
}

export interface AuthorProfile {
	name: string;
	affiliation: string | null;
	publications: number;
	citations: number;
	hIndex: number;
	i10Index: number;
	overview: string | null;
	researchInterests: string[];
	recentPapers: Paper[];
	// External links (from Semantic Scholar API)
	semanticScholarId?: string;
	googleScholarUrl?: string;
	semanticScholarUrl?: string;
	dblpUrl?: string;
	orcid?: string;
	homepage?: string;
}

export interface TagProfile {
	name: string;
	color: string;
	paperCount: number;
	papers: Paper[];
	relatedTags: string[];
}

export interface JournalProfile {
	name: string;
	abbreviation: string | null;
	paperCount: number;
	papers: Paper[];
	years: number[];
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

export interface ApiError {
	code: string;
	message: string;
}

export interface ApiResponse<T> {
	success: boolean;
	data?: T;
	// Error can be either a string (from backend) or an object (from frontend client)
	error?: string | ApiError;
}

/**
 * Helper to get error message from ApiResponse
 */
export function getErrorMessage(error: string | ApiError | undefined): string {
	if (!error) return 'Unknown error';
	if (typeof error === 'string') return error;
	return error.message;
}

// Research Notes (separate from PDF annotation Notes)

/**
 * Research note with markdown content and wiki-style links
 */
export interface ResearchNote {
	id: string;
	paperId: string | null; // null for standalone notes
	title: string;
	content: string; // Markdown with LaTeX and [[...]] links
	createdAt: string;
	updatedAt: string;
}

/**
 * Wiki-style link type
 */
export type NoteLinkType = 'NOTE' | 'PAPER';

/**
 * Parsed wiki-style link from note content
 */
export interface NoteLink {
	id: string;
	sourceNoteId: string;
	targetNoteId: string | null; // null if unresolved
	targetPaperId: string | null; // for [[paper:id]] links
	linkText: string;
	linkType: NoteLinkType;
	positionInContent: number;
	createdAt: string;
}

/**
 * Research note with outgoing links and backlinks
 */
export interface ResearchNoteWithLinks {
	note: ResearchNote;
	outgoingLinks: NoteLink[];
	backlinks: BacklinkInfo[];
}

/**
 * Information about a note that links to the current note
 */
export interface BacklinkInfo {
	noteId: string;
	noteTitle: string;
	linkText: string;
	createdAt: string;
}
