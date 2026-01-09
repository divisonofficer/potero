<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { updateViewerState } from '$lib/stores/tabs';
	import type { PdfViewerState } from '$lib/types';
	import CitationModal from './CitationModal.svelte';
	import FloatingOutline from './FloatingOutline.svelte';
	import { api, type Reference, type CitationSpan } from '$lib/api/client';
	import { List } from 'lucide-svelte';

	interface Props {
		pdfUrl: string;
		paperId?: string; // Paper ID for backend API calls
		tabId?: string;
		initialState?: PdfViewerState;
		onPageChange?: (page: number, total: number) => void;
		onTextSelect?: (text: string) => void;
		onOpenPaper?: (paperId: string) => void;
	}

	let { pdfUrl, paperId, tabId, initialState, onPageChange, onTextSelect, onOpenPaper }: Props = $props();

	// Citation modal state
	let showCitationModal = $state(false);
	let citationQuery = $state('');

	// Outline panel state
	let showOutlinePanel = $state(false);

	let container: HTMLDivElement;
	let scrollContainer: HTMLDivElement;
	let currentPage = $state(initialState?.currentPage ?? 1);
	let totalPages = $state(0);
	let scale = $state(initialState?.scale ?? 1.2);
	let isLoading = $state(true);
	let error = $state<string | null>(null);
	let pageInputValue = $state(String(initialState?.currentPage ?? 1));
	let viewMode = $state<'single' | 'scroll'>(initialState?.viewMode ?? 'scroll');
	let pendingScrollRestore = $state(initialState ? { top: initialState.scrollTop, left: initialState.scrollLeft } : null);

	// PDF.js types
	type PDFDocumentProxy = import('pdfjs-dist').PDFDocumentProxy;
	type PDFPageProxy = import('pdfjs-dist').PDFPageProxy;
	type RenderTask = import('pdfjs-dist').RenderTask;

	let pdfDoc: PDFDocumentProxy | null = null;
	let renderTasks: Map<number, RenderTask> = new Map();
	let pdfjsLib: typeof import('pdfjs-dist') | null = null;
	let renderedPages: Set<number> = new Set();

	// References section detection (using backend API when available)
	let referencesStartPage = $state<number | null>(null);
	let parsedReferences = $state<Reference[]>([]);
	let isDetectingReferences = $state(false);
	let referencesSource = $state<'backend' | 'frontend' | null>(null);

	// Backend citation spans with precise bounding boxes
	let citationSpans = $state<CitationSpan[]>([]);
	let isLoadingCitations = $state(false);
	let citationsSource = $state<'backend' | 'pattern' | null>(null);

	async function initPdfJs() {
		if (pdfjsLib) return;

		// Dynamic import of pdfjs-dist (client-side only)
		pdfjsLib = await import('pdfjs-dist');

		// Set worker path
		pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
			'pdfjs-dist/build/pdf.worker.min.mjs',
			import.meta.url
		).toString();
	}

	async function loadPdf() {
		if (!pdfUrl) return;

		isLoading = true;
		error = null;
		renderedPages.clear();

		try {
			await initPdfJs();
			if (!pdfjsLib) throw new Error('PDF.js not initialized');

			// Handle local file paths - convert to blob URL via API
			let url = pdfUrl;
			if (pdfUrl.startsWith('/') || pdfUrl.startsWith('~')) {
				// Fetch PDF through backend API
				const response = await fetch(`/api/pdf/file?path=${encodeURIComponent(pdfUrl)}`);
				if (!response.ok) {
					throw new Error('Failed to load PDF file');
				}
				const blob = await response.blob();
				url = URL.createObjectURL(blob);
			}

			const loadingTask = pdfjsLib.getDocument(url);
			pdfDoc = await loadingTask.promise;
			totalPages = pdfDoc.numPages;

			// Restore page if available, otherwise start at 1
			if (initialState?.currentPage && initialState.currentPage <= totalPages) {
				currentPage = initialState.currentPage;
				pageInputValue = String(currentPage);
			} else {
				currentPage = 1;
				pageInputValue = '1';
			}

			// Wait for next tick before rendering
			await new Promise(resolve => setTimeout(resolve, 0));

			if (viewMode === 'scroll') {
				await renderAllPages();
			} else {
				await renderSinglePage(currentPage);
			}

			// Detect References section in background (don't block rendering)
			// DISABLED: Conflicts with backend reference extraction during debugging
			// detectReferencesSection();

			// Load backend citation spans (if available)
			loadCitationSpans();

			// Build figure/table index in background for navigation
			buildFigureIndex();
		} catch (e) {
			console.error('Failed to load PDF:', e);
			error = e instanceof Error ? e.message : 'Failed to load PDF';
		} finally {
			isLoading = false;
		}
	}

	/**
	 * Detect References/Bibliography section.
	 * First tries to load existing references from backend (saved from re-analysis),
	 * then falls back to analysis API, then to frontend PDF.js parsing.
	 * Can be called manually via "Refresh References" button.
	 */
	async function detectReferencesSection() {
		isDetectingReferences = true;
		referencesStartPage = null;
		parsedReferences = [];
		referencesSource = null;

		// Try backend API if we have a paperId
		if (paperId) {
			try {
				// Step 1: Try to get existing references (from previous re-analysis)
				console.log(`[PDF] Fetching existing references for paper: ${paperId}`);
				const existingResult = await api.getReferences(paperId);

				if (existingResult.success && existingResult.data) {
					const { references, referencesStartPage: existingStartPage, totalCount } = existingResult.data;

					if (totalCount > 0) {
						parsedReferences = references;
						referencesStartPage = existingStartPage;
						referencesSource = 'backend';
						isDetectingReferences = false;
						console.log(`[PDF] Loaded ${totalCount} existing references from backend`);

						if (references.length > 0 && scrollContainer) {
							reAnnotateReferencesPages();
						}
						return;
					}
				}

				// Step 2: No existing references, trigger analysis
				console.log(`[PDF] No existing references, analyzing...`);
				const analyzeResult = await api.analyzeReferences(paperId);

				if (analyzeResult.success && analyzeResult.data) {
					const { references, referencesStartPage: apiStartPage, totalCount } = analyzeResult.data;
					console.log(`[PDF] Backend analysis returned ${totalCount} references starting at page ${apiStartPage}`);
					if (totalCount > 0) {
						parsedReferences = references;
						referencesStartPage = apiStartPage;
						referencesSource = 'backend';
						isDetectingReferences = false;
						console.log(`[PDF] Backend found ${totalCount} references starting at page ${apiStartPage}`);

						if (references.length > 0 && scrollContainer) {
							reAnnotateReferencesPages();
						}
						return;
					}
				} else {
					console.warn('[PDF] Backend analysis failed:', analyzeResult.error);
				}
			} catch (e) {
				console.warn('[PDF] Backend API error, falling back to frontend:', e);
			}
		}

		// Fallback to frontend PDF.js parsing
		await detectReferencesSectionFrontend();
	}

	/**
	 * Frontend-only References detection using PDF.js
	 * Used as fallback when backend API is not available or fails
	 */
	async function detectReferencesSectionFrontend() {
		if (!pdfDoc) {
			isDetectingReferences = false;
			return;
		}

		const referencesSectionHeaders = [
			/\breferences\b/i,
			/\bbibliography\b/i,
			/\bworks cited\b/i,
			/\bliterature cited\b/i,
			/\bcited literature\b/i,
			/\breference list\b/i,
		];

		// Scan from last pages (references are usually at the end)
		const pagesToScan = Math.min(pdfDoc.numPages, 10);
		const startPage = Math.max(1, pdfDoc.numPages - pagesToScan + 1);

		for (let pageNum = startPage; pageNum <= pdfDoc.numPages; pageNum++) {
			try {
				const page = await pdfDoc.getPage(pageNum);
				const textContent = await page.getTextContent();
				const textItems = textContent.items as Array<{ str: string }>;
				const pageText = textItems.map(item => item.str).join(' ');

				// Check if this page contains a References section header
				for (const pattern of referencesSectionHeaders) {
					if (pattern.test(pageText.substring(0, 500))) { // Check near the top
						referencesStartPage = pageNum;
						referencesSource = 'frontend';
						console.log(`[PDF] Frontend: References section detected on page ${pageNum}`);

						// Parse references from this page onwards
						await parseReferencesFromPage(pageNum);
						return;
					}
				}
			} catch (e) {
				console.warn(`[PDF] Error scanning page ${pageNum}:`, e);
			}
		}

		console.log('[PDF] No References section detected');
		isDetectingReferences = false;
	}

	/**
	 * Parse individual references from the References section (frontend fallback)
	 */
	async function parseReferencesFromPage(startPage: number) {
		if (!pdfDoc) return;

		const refs: Reference[] = [];

		// Reference patterns: [1], 1., (1), etc.
		const refNumberPatterns = [
			/^\[(\d+)\]/,      // [1] Author...
			/^(\d+)\./,        // 1. Author...
			/^\((\d+)\)/,      // (1) Author...
		];

		for (let pageNum = startPage; pageNum <= pdfDoc.numPages; pageNum++) {
			try {
				const page = await pdfDoc.getPage(pageNum);
				const textContent = await page.getTextContent();
				const textItems = textContent.items as Array<{ str: string }>;

				// Join text items to form lines
				let currentLine = '';
				const lines: string[] = [];

				for (const item of textItems) {
					const text = item.str;
					if (text.trim() === '') {
						if (currentLine.trim()) {
							lines.push(currentLine.trim());
							currentLine = '';
						}
					} else {
						currentLine += text + ' ';
					}
				}
				if (currentLine.trim()) {
					lines.push(currentLine.trim());
				}

				// Parse each line for reference numbers
				for (const line of lines) {
					for (const pattern of refNumberPatterns) {
						const match = line.match(pattern);
						if (match) {
							const refNum = parseInt(match[1], 10);
							const refText = line.substring(match[0].length).trim();

							// Try to extract author and title
							// Common patterns: "Author. Title." or "Author, Title,"
							const parts = refText.split(/[.]\s*/);
							const authors = parts[0] || '';
							const title = parts[1] || refText;

							// Create Reference object compatible with backend format
							refs.push({
								id: `frontend-ref-${refNum}`,
								paperId: paperId || '',
								number: refNum,
								rawText: refText,
								authors: authors || null,
								title: title.substring(0, 100) || null, // Limit title length
								venue: null,
								year: null,
								doi: null,
								pageNum: pageNum,
								createdAt: new Date().toISOString()
							});
							break;
						}
					}
				}
			} catch (e) {
				console.warn(`[PDF] Error parsing references on page ${pageNum}:`, e);
			}
		}

		parsedReferences = refs;
		isDetectingReferences = false;
		console.log(`[PDF] Frontend parsed ${refs.length} references`);

		// Re-annotate pages in the References section to highlight reference entries
		if (refs.length > 0 && scrollContainer) {
			reAnnotateReferencesPages();
		}
	}

	/**
	 * Re-annotate text layers on References section pages after detection completes.
	 * This ensures reference entries are highlighted even if pages were rendered
	 * before the detection finished.
	 */
	function reAnnotateReferencesPages() {
		if (!scrollContainer || referencesStartPage === null) return;

		const pageWrappers = scrollContainer.querySelectorAll('.page-wrapper');
		for (const wrapper of pageWrappers) {
			const pageNum = parseInt((wrapper as HTMLElement).dataset.page || '0', 10);
			if (pageNum >= referencesStartPage) {
				const textLayer = wrapper.querySelector('.textLayer');
				if (textLayer) {
					// Re-annotate this text layer
					annotateTextLayer(textLayer as HTMLElement, pageNum);
				}
			}
		}
	}

	/**
	 * Load citation spans from backend API.
	 * These have precise bounding boxes extracted from PDF link annotations.
	 */
	async function loadCitationSpans() {
		if (!paperId) {
			citationsSource = 'pattern';
			return;
		}

		isLoadingCitations = true;
		citationSpans = [];

		try {
			// First try to get existing citations
			const result = await api.getCitations(paperId);

			if (result.success && result.data && result.data.length > 0) {
				citationSpans = result.data;
				citationsSource = 'backend';
				console.log(`[PDF] Loaded ${citationSpans.length} citation spans from backend`);

				// Render citation overlays on already-rendered pages
				renderCitationOverlaysOnAllPages();
				return;
			}

			// No existing citations, try extraction
			console.log(`[PDF] No existing citations, extracting...`);
			const extractResult = await api.extractCitations(paperId);

			if (extractResult.success && extractResult.data) {
				citationSpans = extractResult.data.spans;
				citationsSource = 'backend';
				console.log(`[PDF] Extracted ${citationSpans.length} citation spans (${extractResult.data.stats.annotationSpans} from annotations, ${extractResult.data.stats.patternSpans} from patterns)`);

				// Render citation overlays
				renderCitationOverlaysOnAllPages();
			} else {
				console.warn('[PDF] Citation extraction failed, using pattern-based detection');
				citationsSource = 'pattern';
			}
		} catch (e) {
			console.warn('[PDF] Citation API error, falling back to pattern detection:', e);
			citationsSource = 'pattern';
		} finally {
			isLoadingCitations = false;
		}
	}

	/**
	 * Render citation overlays on all currently rendered pages
	 */
	function renderCitationOverlaysOnAllPages() {
		if (!scrollContainer || citationSpans.length === 0) return;

		const pageWrappers = scrollContainer.querySelectorAll('.page-wrapper');
		for (const wrapper of pageWrappers) {
			const pageNum = parseInt((wrapper as HTMLElement).dataset.page || '0', 10);
			if (pageNum > 0 && renderedPages.has(pageNum)) {
				renderCitationOverlaysOnPage(wrapper as HTMLElement, pageNum);
			}
		}
	}

	/**
	 * Render citation span overlays on a specific page
	 * Uses precise bounding boxes from backend extraction
	 */
	function renderCitationOverlaysOnPage(pageWrapper: HTMLElement, pageNum: number) {
		// Get citations for this page
		const pageSpans = citationSpans.filter(s => s.pageNum === pageNum);
		if (pageSpans.length === 0) return;

		// Find or create overlay container
		const pageContainer = pageWrapper.querySelector('.page-container');
		if (!pageContainer) return;

		// Remove existing citation overlays
		const existingOverlays = pageContainer.querySelectorAll('.citation-overlay');
		existingOverlays.forEach(el => el.remove());

		// Create overlay container if not exists
		let overlayContainer = pageContainer.querySelector('.citation-overlay-container') as HTMLElement;
		if (!overlayContainer) {
			overlayContainer = document.createElement('div');
			overlayContainer.className = 'citation-overlay-container absolute top-0 left-0 w-full h-full pointer-events-none z-10';
			pageContainer.appendChild(overlayContainer);
		}

		// Get page dimensions from the canvas
		const canvas = pageContainer.querySelector('canvas');
		if (!canvas) return;

		const canvasWidth = parseFloat(canvas.style.width);
		const canvasHeight = parseFloat(canvas.style.height);

		// Standard PDF page dimensions (assume 612x792 for letter size)
		// This needs to match the PDF's media box
		const pdfWidth = 612;
		const pdfHeight = 792;

		// Scale factor from PDF coordinates to display coordinates
		const scaleX = canvasWidth / pdfWidth;
		const scaleY = canvasHeight / pdfHeight;

		// Create overlay for each citation span
		for (const span of pageSpans) {
			// Convert PDF coordinates (origin bottom-left) to display coordinates (origin top-left)
			const displayX1 = span.bbox.x1 * scaleX;
			const displayY1 = (pdfHeight - span.bbox.y2) * scaleY; // Flip Y
			const displayX2 = span.bbox.x2 * scaleX;
			const displayY2 = (pdfHeight - span.bbox.y1) * scaleY; // Flip Y

			const overlay = document.createElement('div');
			overlay.className = `citation-overlay pointer-events-auto cursor-pointer ${span.provenance}`;
			overlay.dataset.spanId = span.id;
			overlay.dataset.rawText = span.rawText;
			overlay.style.cssText = `
				position: absolute;
				left: ${displayX1}px;
				top: ${displayY1}px;
				width: ${displayX2 - displayX1}px;
				height: ${displayY2 - displayY1}px;
				background-color: ${span.provenance === 'annotation' ? 'rgba(34, 197, 94, 0.15)' : 'rgba(59, 130, 246, 0.1)'};
				border: 1px solid ${span.provenance === 'annotation' ? 'rgba(34, 197, 94, 0.4)' : 'rgba(59, 130, 246, 0.3)'};
				border-radius: 2px;
				transition: background-color 0.15s ease;
			`;
			overlay.title = `${span.rawText} (${span.provenance}, confidence: ${(span.confidence * 100).toFixed(0)}%)`;

			// Add hover effect
			overlay.addEventListener('mouseenter', () => {
				overlay.style.backgroundColor = span.provenance === 'annotation'
					? 'rgba(34, 197, 94, 0.3)'
					: 'rgba(59, 130, 246, 0.25)';
			});
			overlay.addEventListener('mouseleave', () => {
				overlay.style.backgroundColor = span.provenance === 'annotation'
					? 'rgba(34, 197, 94, 0.15)'
					: 'rgba(59, 130, 246, 0.1)';
			});

			// Handle click
			overlay.addEventListener('click', (e) => {
				e.preventDefault();
				e.stopPropagation();
				handleCitationOverlayClick(span);
			});

			overlayContainer.appendChild(overlay);
		}

		console.log(`[PDF] Rendered ${pageSpans.length} citation overlays on page ${pageNum}`);
	}

	/**
	 * Handle click on citation overlay - look up linked references or show modal
	 */
	function handleCitationOverlayClick(span: CitationSpan) {
		// If we have linked references, use them for lookup
		if (span.linkedRefIds.length > 0) {
			const refId = span.linkedRefIds[0];
			const linkedRef = parsedReferences.find(r => r.id === refId);

			if (linkedRef) {
				const query = linkedRef.title || linkedRef.authors || linkedRef.rawText;
				if (query && query.length >= 5) {
					citationQuery = query;
					showCitationModal = true;
					return;
				}
			}
		}

		// Fallback: try to look up by number from raw text
		const numMatch = span.rawText.match(/\d+/);
		if (numMatch) {
			const refNum = parseInt(numMatch[0], 10);
			const parsedRef = lookupReferenceByNumber(refNum);

			if (parsedRef) {
				const query = parsedRef.title || parsedRef.authors || parsedRef.rawText;
				if (query && query.length >= 5) {
					citationQuery = query;
					showCitationModal = true;
					return;
				}
			}
		}

		// Final fallback: use raw text for author-year style
		const cleanedText = span.rawText.replace(/[\[\]()]/g, '').trim();
		if (cleanedText.length >= 5 && !/^[\d\s,\-–]+$/.test(cleanedText)) {
			citationQuery = cleanedText;
			showCitationModal = true;
		}
	}

	/**
	 * Look up a reference by its number (e.g., from [1] citation)
	 */
	function lookupReferenceByNumber(refNum: number): Reference | undefined {
		return parsedReferences.find(ref => ref.number === refNum);
	}

	/**
	 * Normalize figure/table ID for consistent lookup
	 * "Fig. 1", "FIG. 1", "Figure 1" → "fig-1"
	 * "Table I" → "table-i" (roman preserved for later conversion)
	 */
	function normalizeId(id: string): string {
		return id.toLowerCase()
			.replace(/figure|fig\.?/gi, 'fig')
			.replace(/table|tbl\.?/gi, 'table')
			.replace(/algorithm|alg\.?/gi, 'alg')
			.replace(/equation|eq\.?/gi, 'eq')
			.replace(/section|sec\.?/gi, 'sec')
			.replace(/\s+/g, '-')
			.replace(/[^a-z0-9-]/g, '');
	}

	/**
	 * Convert Roman numerals to Arabic: I→1, II→2, IV→4, etc.
	 */
	function romanToArabic(roman: string): number {
		const map: Record<string, number> = { I: 1, V: 5, X: 10, L: 50, C: 100, D: 500, M: 1000 };
		let result = 0;
		for (let i = 0; i < roman.length; i++) {
			const current = map[roman[i].toUpperCase()] || 0;
			const next = map[roman[i + 1]?.toUpperCase()] || 0;
			result += current < next ? -current : current;
		}
		return result;
	}

	/**
	 * Detect figure type from ID string
	 */
	function detectFigureType(id: string): FigureLocation['type'] {
		const lower = id.toLowerCase();
		if (lower.includes('fig')) return 'figure';
		if (lower.includes('tab')) return 'table';
		if (lower.includes('alg')) return 'algorithm';
		if (lower.includes('eq')) return 'equation';
		if (lower.includes('sec')) return 'section';
		return 'figure';
	}

	// Section header patterns for outline detection
	const SECTION_PATTERNS = [
		// Numbered sections: "1. Introduction", "2.1 Background", "1 Introduction"
		/^(\d+(?:\.\d+)*\.?)\s+([A-Z][A-Za-z\s]+)$/,
		// Roman numeral sections: "I. INTRODUCTION", "II. RELATED WORK"
		/^([IVX]+)\.\s+([A-Z][A-Z\s]+)$/,
		// Common section names (standalone)
		/^(Abstract|Introduction|Conclusion|References|Bibliography|Acknowledgments?|Appendix)$/i,
		// "Section X" style
		/^(Section\s+\d+(?:\.\d+)?)[.:\s]+(.+)$/i,
	];

	/**
	 * Build figure/table location index by scanning entire PDF
	 * Also detects section headers for outline navigation
	 * Called after PDF load for efficient caption lookup
	 */
	async function buildFigureIndex(): Promise<void> {
		if (!pdfDoc || isBuildingFigureIndex) return;

		isBuildingFigureIndex = true;
		const newIndex = new Map<string, FigureLocation>();

		console.log('[PDF] Building figure index...');

		for (let pageNum = 1; pageNum <= pdfDoc.numPages; pageNum++) {
			try {
				const page = await pdfDoc.getPage(pageNum);
				const textContent = await page.getTextContent();
				const viewport = page.getViewport({ scale: 1 });

				// Group text items by line (based on Y position)
				const items = textContent.items as Array<{ str: string; transform: number[]; height?: number }>;
				const lines: Array<{ text: string; y: number; height?: number }> = [];
				let currentLine = '';
				let currentY = 0;
				let currentHeight = 0;

				for (const item of items) {
					const y = item.transform[5]; // Y position from transform matrix
					const height = item.height || 0;
					// New line if Y position differs significantly
					if (currentLine && Math.abs(y - currentY) > 3) {
						lines.push({ text: currentLine.trim(), y: currentY, height: currentHeight });
						currentLine = '';
					}
					currentLine += item.str + ' ';
					currentY = y;
					currentHeight = Math.max(currentHeight, height);
				}
				if (currentLine.trim()) {
					lines.push({ text: currentLine.trim(), y: currentY, height: currentHeight });
				}

				// Check each line for caption patterns
				for (const line of lines) {
					// Figure/Table/Algorithm captions
					for (const pattern of CAPTION_PATTERNS) {
						const match = pattern.exec(line.text);
						if (match) {
							const id = match[1];
							const normalizedId = normalizeId(id);
							const yPosition = viewport.height - line.y; // Convert to top-down coordinate

							// Only add if not already found (first occurrence wins)
							if (!newIndex.has(normalizedId)) {
								newIndex.set(normalizedId, {
									type: detectFigureType(id),
									id,
									normalizedId,
									pageNum,
									yPosition,
									caption: line.text.substring(match[0].length).trim().substring(0, 100)
								});

								// Also add Roman numeral variant if applicable
								if (/[IVX]+/i.test(id)) {
									const arabicId = normalizedId.replace(/[ivx]+/gi, (m) =>
										String(romanToArabic(m))
									);
									if (arabicId !== normalizedId && !newIndex.has(arabicId)) {
										newIndex.set(arabicId, {
											type: detectFigureType(id),
											id,
											normalizedId: arabicId,
											pageNum,
											yPosition,
											caption: line.text.substring(match[0].length).trim().substring(0, 100)
										});
									}
								}
							}
							break;
						}
					}

					// Section headers (check if text could be a section)
					for (const pattern of SECTION_PATTERNS) {
						const match = pattern.exec(line.text);
						if (match) {
							// Create a unique ID for sections
							const sectionNum = match[1] || line.text.substring(0, 20);
							const sectionTitle = match[2] || match[1] || line.text;
							const normalizedId = `sec-${sectionNum.toLowerCase().replace(/[^a-z0-9]/g, '-')}`;
							const yPosition = viewport.height - line.y;

							// Avoid duplicates and very short text
							if (!newIndex.has(normalizedId) && line.text.length >= 3) {
								newIndex.set(normalizedId, {
									type: 'section',
									id: line.text,
									normalizedId,
									pageNum,
									yPosition,
									caption: sectionTitle
								});
							}
							break;
						}
					}
				}
			} catch (e) {
				console.warn(`[PDF] Error scanning page ${pageNum} for figures:`, e);
			}
		}

		figureIndex = newIndex;
		isBuildingFigureIndex = false;
		console.log(`[PDF] Figure index built: ${newIndex.size} items found`);
	}

	/**
	 * Ensure a page is rendered before navigating to it
	 */
	async function ensurePageRendered(pageNum: number): Promise<void> {
		if (renderedPages.has(pageNum)) return;
		await renderPage(pageNum);
		// Small delay to ensure DOM is updated
		await new Promise(resolve => setTimeout(resolve, 100));
	}

	/**
	 * Highlight figure caption temporarily after navigation
	 */
	function highlightFigureCaption(location: FigureLocation): void {
		if (!scrollContainer) return;

		const pageWrapper = scrollContainer.querySelector(`[data-page="${location.pageNum}"]`);
		if (!pageWrapper) return;

		const textLayer = pageWrapper.querySelector('.textLayer');
		if (!textLayer) return;

		const spans = textLayer.querySelectorAll('span');
		for (const span of spans) {
			const spanText = span.textContent || '';
			// Match the figure ID in the caption
			if (spanText.toLowerCase().includes(location.id.toLowerCase().replace(/\s+/g, ' ').trim().substring(0, 10))) {
				span.classList.add('figure-highlight');
				setTimeout(() => {
					span.classList.remove('figure-highlight');
				}, 2000);
				break;
			}
		}
	}

	// Store page dimensions for stable layout
	let pageWidth = 0;
	let pageHeight = 0;

	async function renderAllPages() {
		if (!pdfDoc || !scrollContainer) return;

		// Clear container
		scrollContainer.innerHTML = '';
		renderedPages.clear();

		// Get first page dimensions to set placeholder sizes
		const firstPage = await pdfDoc.getPage(1);
		const viewport = firstPage.getViewport({ scale });
		pageWidth = viewport.width;
		pageHeight = viewport.height;

		// Create a wrapper div to maintain stable width for all pages
		const pagesWrapper = document.createElement('div');
		pagesWrapper.className = 'pages-wrapper';
		// Set min-width to ensure consistent horizontal layout
		pagesWrapper.style.minWidth = `${pageWidth}px`;
		pagesWrapper.style.width = '100%';
		pagesWrapper.style.display = 'flex';
		pagesWrapper.style.flexDirection = 'column';
		pagesWrapper.style.alignItems = 'center';

		// Create page wrappers with fixed dimensions as placeholders
		for (let i = 1; i <= totalPages; i++) {
			const pageWrapper = document.createElement('div');
			pageWrapper.className = 'page-wrapper relative mb-4 flex justify-center';
			pageWrapper.dataset.page = String(i);
			// Set minimum height on wrapper to maintain layout stability
			pageWrapper.style.minHeight = `${pageHeight}px`;
			pageWrapper.style.width = '100%';

			// Create placeholder with exact page dimensions and loading indicator
			const placeholder = document.createElement('div');
			placeholder.className = 'page-placeholder bg-white shadow-lg flex items-center justify-center';
			placeholder.style.width = `${pageWidth}px`;
			placeholder.style.height = `${pageHeight}px`;
			placeholder.innerHTML = `
				<div class="flex flex-col items-center text-neutral-400">
					<div class="h-8 w-8 animate-spin rounded-full border-2 border-neutral-300 border-t-blue-500"></div>
					<span class="mt-2 text-sm">Page ${i}</span>
				</div>
			`;
			pageWrapper.appendChild(placeholder);

			pagesWrapper.appendChild(pageWrapper);
		}

		scrollContainer.appendChild(pagesWrapper);

		// Restore scroll position BEFORE rendering (layout is now stable)
		if (pendingScrollRestore) {
			scrollContainer.scrollTop = pendingScrollRestore.top;
			scrollContainer.scrollLeft = pendingScrollRestore.left;
			pendingScrollRestore = null;
		}

		// Set up intersection observer for lazy rendering
		setupIntersectionObserver();

		// Render first few pages immediately
		const initialPages = Math.min(3, totalPages);
		for (let i = 1; i <= initialPages; i++) {
			await renderPage(i);
		}
	}

	function setupIntersectionObserver() {
		if (!scrollContainer) return;

		const observer = new IntersectionObserver(
			(entries) => {
				entries.forEach((entry) => {
					if (entry.isIntersecting) {
						const pageNum = parseInt((entry.target as HTMLElement).dataset.page || '0');
						if (pageNum > 0 && !renderedPages.has(pageNum)) {
							renderPage(pageNum);
						}
					}
				});
			},
			{
				root: scrollContainer,
				rootMargin: '200px 0px',
				threshold: 0.01
			}
		);

		scrollContainer.querySelectorAll('.page-wrapper').forEach((wrapper) => {
			observer.observe(wrapper);
		});
	}

	async function renderPage(pageNum: number) {
		if (!pdfDoc || !scrollContainer || renderedPages.has(pageNum)) return;

		const pageWrapper = scrollContainer.querySelector(`[data-page="${pageNum}"]`) as HTMLElement;
		if (!pageWrapper) return;

		renderedPages.add(pageNum);

		try {
			const page = await pdfDoc.getPage(pageNum);
			const viewport = page.getViewport({ scale });

			// HiDPI support - get device pixel ratio for sharp rendering
			const dpr = window.devicePixelRatio || 1;

			// Create container for canvas and text layer
			const pageContainer = document.createElement('div');
			pageContainer.className = 'page-container relative';
			pageContainer.style.width = `${viewport.width}px`;
			pageContainer.style.height = `${viewport.height}px`;

			// Create canvas with HiDPI support
			const canvas = document.createElement('canvas');
			// Set actual canvas size to scaled dimensions for sharp rendering
			canvas.width = Math.floor(viewport.width * dpr);
			canvas.height = Math.floor(viewport.height * dpr);
			// Set display size via CSS
			canvas.style.width = `${viewport.width}px`;
			canvas.style.height = `${viewport.height}px`;
			canvas.className = 'shadow-lg bg-white block';
			pageContainer.appendChild(canvas);

			// Create text layer container
			const textLayerDiv = document.createElement('div');
			textLayerDiv.className = 'textLayer absolute top-0 left-0';
			textLayerDiv.style.width = `${viewport.width}px`;
			textLayerDiv.style.height = `${viewport.height}px`;
			pageContainer.appendChild(textLayerDiv);

			// Clear existing content and add new
			pageWrapper.innerHTML = '';
			pageWrapper.appendChild(pageContainer);

			// Render canvas with HiDPI scaling
			const context = canvas.getContext('2d');
			if (context) {
				// Scale context for HiDPI
				context.scale(dpr, dpr);

				const renderTask = page.render({
					canvasContext: context,
					viewport: viewport,
					canvas: canvas
				});
				renderTasks.set(pageNum, renderTask);
				await renderTask.promise;
				renderTasks.delete(pageNum);
			}

			// Render text layer
			const textContent = await page.getTextContent();

			// Use PDF.js TextLayer
			if (pdfjsLib) {
				const { TextLayer } = await import('pdfjs-dist');
				const textLayer = new TextLayer({
					textContentSource: textContent,
					container: textLayerDiv,
					viewport: viewport
				});
				await textLayer.render();

				// Annotate clickable references (citations and figures)
				// Only use pattern-based annotation if backend citations not available
				if (citationsSource !== 'backend') {
					annotateTextLayer(textLayerDiv, pageNum);
				}
			}

			// Render backend citation overlays if available
			if (citationsSource === 'backend' && citationSpans.length > 0) {
				renderCitationOverlaysOnPage(pageWrapper, pageNum);
			}

		} catch (e) {
			if (e instanceof Error && e.message.includes('cancelled')) return;
			console.error(`Failed to render page ${pageNum}:`, e);
			renderedPages.delete(pageNum);
		}
	}

	/**
	 * Span info for aggregation - helps match patterns across multiple spans
	 * when PDF.js splits text like "Fig. 1" into "Fig." + " " + "1"
	 */
	interface SpanInfo {
		span: HTMLSpanElement;
		text: string;
		startPos: number; // Position in aggregated line text
		endPos: number;
	}

	/**
	 * Aggregate adjacent spans by Y position to form lines
	 * Returns array of lines, each containing spans with their positions
	 */
	function aggregateSpansToLines(textLayerDiv: HTMLElement): SpanInfo[][] {
		const allSpans = Array.from(textLayerDiv.querySelectorAll('span')) as HTMLSpanElement[];
		if (allSpans.length === 0) return [];

		const lines: SpanInfo[][] = [];
		let currentLine: SpanInfo[] = [];
		let lastBottom = 0;
		let charPos = 0;

		for (const span of allSpans) {
			const rect = span.getBoundingClientRect();
			const text = span.textContent || '';

			// New line if Y position differs significantly (>5px)
			if (currentLine.length > 0 && Math.abs(rect.bottom - lastBottom) > 5) {
				lines.push(currentLine);
				currentLine = [];
				charPos = 0;
			}

			currentLine.push({
				span,
				text,
				startPos: charPos,
				endPos: charPos + text.length
			});
			charPos += text.length;
			lastBottom = rect.bottom;
		}

		if (currentLine.length > 0) {
			lines.push(currentLine);
		}

		return lines;
	}

	/**
	 * Apply class to all spans that overlap with a match range
	 */
	function applyClassToSpansInRange(
		line: SpanInfo[],
		matchStart: number,
		matchEnd: number,
		className: string,
		tooltip: string
	): void {
		for (const spanInfo of line) {
			// Check if this span overlaps with the match range
			if (spanInfo.endPos > matchStart && spanInfo.startPos < matchEnd) {
				spanInfo.span.classList.add(className);
				if (tooltip && !spanInfo.span.title) {
					spanInfo.span.title = tooltip;
				}
			}
		}
	}

	// Annotate text layer spans with citation/figure classes for visual indication
	// Uses span aggregation to match patterns that span multiple elements
	function annotateTextLayer(textLayerDiv: HTMLElement, pageNum: number) {
		const isInReferencesSection = referencesStartPage !== null && pageNum >= referencesStartPage;

		// Aggregate spans into lines for cross-span pattern matching
		const lines = aggregateSpansToLines(textLayerDiv);

		for (const line of lines) {
			const lineText = line.map(s => s.text).join('');

			// === Citation patterns (exact match on entire span content) ===
			// These are typically single spans like "[1]" or "(Smith, 2024)"
			for (const spanInfo of line) {
				const text = spanInfo.text.trim();
				for (const pattern of CITATION_PATTERNS) {
					pattern.lastIndex = 0;
					if (pattern.test(text)) {
						spanInfo.span.classList.add('citation-ref');
						spanInfo.span.title = 'Click to look up citation';
						break;
					}
				}
			}

			// === Figure/Table patterns (can span multiple elements) ===
			// Match against aggregated line text, then apply to overlapping spans
			for (const pattern of FIGURE_PATTERNS) {
				pattern.lastIndex = 0;
				let match;
				while ((match = pattern.exec(lineText)) !== null) {
					const matchStart = match.index;
					const matchEnd = matchStart + match[0].length;
					const figRef = match[1] || match[0];

					applyClassToSpansInRange(
						line,
						matchStart,
						matchEnd,
						'figure-ref',
						`Click to jump to ${figRef}`
					);
				}
			}

			// === Reference entry patterns (in References section) ===
			if (isInReferencesSection) {
				for (const spanInfo of line) {
					const text = spanInfo.text;
					for (const pattern of REFERENCE_ENTRY_PATTERNS) {
						if (pattern.test(text)) {
							spanInfo.span.classList.add('reference-entry');
							const numMatch = text.match(/\d+/);
							if (numMatch) {
								const refNum = parseInt(numMatch[0], 10);
								const parsedRef = lookupReferenceByNumber(refNum);
								if (parsedRef) {
									spanInfo.span.title = `Click to search: ${parsedRef.title || parsedRef.authors}`;
								} else {
									spanInfo.span.title = 'Click to search for this reference';
								}
							}
							break;
						}
					}
				}
			}
		}
	}

	async function renderSinglePage(pageNum: number) {
		if (!pdfDoc || !scrollContainer) return;

		// Cancel all render tasks
		renderTasks.forEach((task) => task.cancel());
		renderTasks.clear();

		// Clear container
		scrollContainer.innerHTML = '';
		renderedPages.clear();

		// Create page wrapper
		const pageWrapper = document.createElement('div');
		pageWrapper.className = 'page-wrapper relative flex justify-center';
		pageWrapper.dataset.page = String(pageNum);
		scrollContainer.appendChild(pageWrapper);

		renderedPages.add(pageNum);

		try {
			const page = await pdfDoc.getPage(pageNum);
			const viewport = page.getViewport({ scale });

			// HiDPI support
			const dpr = window.devicePixelRatio || 1;

			// Create container
			const pageContainer = document.createElement('div');
			pageContainer.className = 'page-container relative';
			pageContainer.style.width = `${viewport.width}px`;
			pageContainer.style.height = `${viewport.height}px`;

			// Create canvas with HiDPI support
			const canvas = document.createElement('canvas');
			canvas.width = Math.floor(viewport.width * dpr);
			canvas.height = Math.floor(viewport.height * dpr);
			canvas.style.width = `${viewport.width}px`;
			canvas.style.height = `${viewport.height}px`;
			canvas.className = 'shadow-lg bg-white block';
			pageContainer.appendChild(canvas);

			// Create text layer
			const textLayerDiv = document.createElement('div');
			textLayerDiv.className = 'textLayer absolute top-0 left-0';
			textLayerDiv.style.width = `${viewport.width}px`;
			textLayerDiv.style.height = `${viewport.height}px`;
			pageContainer.appendChild(textLayerDiv);

			pageWrapper.appendChild(pageContainer);

			// Render canvas with HiDPI scaling
			const context = canvas.getContext('2d');
			if (context) {
				context.scale(dpr, dpr);
				const renderTask = page.render({
					canvasContext: context,
					viewport: viewport,
					canvas: canvas
				});
				await renderTask.promise;
			}

			// Render text layer
			const textContent = await page.getTextContent();
			if (pdfjsLib) {
				const { TextLayer } = await import('pdfjs-dist');
				const textLayer = new TextLayer({
					textContentSource: textContent,
					container: textLayerDiv,
					viewport: viewport
				});
				await textLayer.render();

				// Annotate clickable references (citations and figures)
				annotateTextLayer(textLayerDiv, pageNum);
			}

			onPageChange?.(currentPage, totalPages);
		} catch (e) {
			if (e instanceof Error && e.message.includes('cancelled')) return;
			console.error('Failed to render page:', e);
		}
	}

	function goToPage(page: number) {
		if (page < 1 || page > totalPages) return;
		currentPage = page;
		pageInputValue = String(page);

		if (viewMode === 'single') {
			renderSinglePage(currentPage);
		} else {
			// Scroll to page
			const pageWrapper = scrollContainer?.querySelector(`[data-page="${page}"]`);
			pageWrapper?.scrollIntoView({ behavior: 'smooth', block: 'start' });
		}
	}

	function handlePageInput(e: Event) {
		const target = e.target as HTMLInputElement;
		const page = parseInt(target.value);
		if (!isNaN(page) && page >= 1 && page <= totalPages) {
			goToPage(page);
		} else {
			pageInputValue = String(currentPage);
		}
	}

	function nextPage() {
		goToPage(currentPage + 1);
	}

	function prevPage() {
		goToPage(currentPage - 1);
	}

	function zoomIn() {
		scale = Math.min(scale + 0.25, 4.0);
		rerender();
	}

	function zoomOut() {
		scale = Math.max(scale - 0.25, 0.5);
		rerender();
	}

	async function rerender() {
		renderedPages.clear();
		if (viewMode === 'scroll') {
			await renderAllPages();
		} else {
			await renderSinglePage(currentPage);
		}
	}

	function fitWidth() {
		if (!pdfDoc || !container) return;
		pdfDoc.getPage(currentPage).then((page) => {
			const viewport = page.getViewport({ scale: 1 });
			scale = (container.clientWidth - 100) / viewport.width;
			rerender();
		});
	}

	function fitPage() {
		if (!pdfDoc || !container) return;
		pdfDoc.getPage(currentPage).then((page) => {
			const viewport = page.getViewport({ scale: 1 });
			const scaleX = (container.clientWidth - 100) / viewport.width;
			const scaleY = (container.clientHeight - 150) / viewport.height;
			scale = Math.min(scaleX, scaleY);
			rerender();
		});
	}

	async function switchViewMode(newMode: 'single' | 'scroll') {
		if (viewMode === newMode) return;
		viewMode = newMode;
		await rerender();
	}

	// Debounced save timeout for scroll state
	let scrollSaveTimer: ReturnType<typeof setTimeout> | null = null;
	let isUpdatingPage = false;

	// Handle scroll to update current page
	function handleScroll() {
		if (!scrollContainer || viewMode !== 'scroll' || isUpdatingPage) return;

		// Update current page based on scroll position
		const wrappers = scrollContainer.querySelectorAll('.page-wrapper');
		if (wrappers.length === 0) return;

		const containerRect = scrollContainer.getBoundingClientRect();
		let closestPage = currentPage;
		let closestDistance = Infinity;

		wrappers.forEach((wrapper) => {
			const rect = wrapper.getBoundingClientRect();
			// Skip empty wrappers (not rendered yet)
			if (rect.height === 0) return;

			const distance = Math.abs(rect.top - containerRect.top);

			if (rect.top <= containerRect.top + containerRect.height / 2 && distance < closestDistance) {
				closestDistance = distance;
				closestPage = parseInt((wrapper as HTMLElement).dataset.page || '1');
			}
		});

		if (closestPage !== currentPage && closestPage >= 1 && closestPage <= totalPages) {
			isUpdatingPage = true;
			currentPage = closestPage;
			pageInputValue = String(closestPage);
			onPageChange?.(currentPage, totalPages);
			// Reset flag after a short delay
			requestAnimationFrame(() => {
				isUpdatingPage = false;
			});
		}

		// Debounced save of scroll position
		if (tabId) {
			if (scrollSaveTimer) clearTimeout(scrollSaveTimer);
			scrollSaveTimer = setTimeout(() => {
				saveViewerState();
			}, 300);
		}
	}

	// Handle keyboard navigation
	function handleKeydown(event: KeyboardEvent) {
		if (event.target instanceof HTMLInputElement) return;

		if (viewMode === 'single') {
			if (event.key === 'ArrowRight' || event.key === 'PageDown') {
				event.preventDefault();
				nextPage();
			} else if (event.key === 'ArrowLeft' || event.key === 'PageUp') {
				event.preventDefault();
				prevPage();
			}
		}

		if ((event.ctrlKey || event.metaKey) && event.key === '=') {
			event.preventDefault();
			zoomIn();
		} else if ((event.ctrlKey || event.metaKey) && event.key === '-') {
			event.preventDefault();
			zoomOut();
		}
	}

	// Handle Ctrl+Wheel for zoom
	function handleWheel(event: WheelEvent) {
		if (event.ctrlKey || event.metaKey) {
			event.preventDefault();
			event.stopPropagation();

			if (event.deltaY < 0) {
				zoomIn();
			} else if (event.deltaY > 0) {
				zoomOut();
			}
		}
	}

	// Handle text selection
	function handleMouseUp() {
		const selection = window.getSelection();
		if (selection && selection.toString().trim()) {
			onTextSelect?.(selection.toString().trim());
		}
	}

	// Citation patterns for visual annotation (highlighting in text layer)
	const CITATION_PATTERNS = [
		/^\[\d+(?:,\s*\d+)*\]$/,                  // [1], [1,2,3]
		/^\[\d+\s*[-–]\s*\d+\]$/,                 // [1-5], [1–5]
		/\([A-Z][a-z]+(?:\s+(?:et\s+al\.?|&\s+[A-Z][a-z]+))?,\s*\d{4}\)$/,  // (Smith et al., 2024)
	];

	// IEEE + ACM unified Figure/Table reference patterns
	const FIGURE_PATTERNS = [
		// === Figure References ===
		// IEEE: Fig. 1, FIG. 1, Figure 1, FIGURE 1, Fig. 1(a), Fig. 1a
		/\b(FIG(?:URE)?\.?\s*\d+(?:\.\d+)?(?:\s*\([a-z]\)|\s*[a-z])?)/gi,
		// IEEE plural: Figs. 1-3, Figs. 1, 2, and 3
		/\b(FIGS?\.?\s*\d+(?:\s*[-–]\s*\d+)?(?:\s*,\s*\d+)*(?:\s*(?:and|&)\s*\d+)?)/gi,
		// ACM: Figure 1, Figures 1 and 2
		/\b(Figures?\s*\d+(?:\s*[-–]\s*\d+)?(?:\s*,\s*\d+)*(?:\s*(?:and|&)\s*\d+)?)/gi,

		// === Table References ===
		// IEEE: Table I, TABLE I (Roman numerals), Table 1
		/\b(TABLE\s*(?:[IVX]+|\d+)(?:\.\d+)?)/gi,
		// ACM: Table 1, Tables 1-3
		/\b(Tables?\s*\d+(?:\s*[-–]\s*\d+)?)/gi,

		// === Algorithm References ===
		/\b(Alg(?:orithm)?\.?\s*\d+)/gi,

		// === Equation References ===
		/\b(Eq(?:uation)?\.?\s*\(?\d+\)?)/gi,

		// === Section References ===
		/\b(Sec(?:tion)?\.?\s*(?:[IVX]+|\d+(?:\.\d+)*))/gi,
	];

	// Caption patterns for Figure/Table definition locations
	const CAPTION_PATTERNS = [
		// IEEE Figure caption: "Fig. 1. Title" or "Fig. 1: Title"
		/^(FIG(?:URE)?\.?\s*\d+(?:\.\d+)?(?:\s*\([a-z]\))?)[.:\s]/i,
		// ACM Figure caption: "Figure 1: Title"
		/^(Figure\s*\d+)[.:]/i,
		// IEEE Table caption: "TABLE I:" or "TABLE 1:"
		/^(TABLE\s*(?:[IVX]+|\d+))[.:]/i,
		// ACM Table caption: "Table 1:"
		/^(Table\s*\d+)[.:]/i,
		// Algorithm caption
		/^(Algorithm\s*\d+)[.:]/i,
	];

	// Reference entry patterns (for References section items)
	const REFERENCE_ENTRY_PATTERNS = [
		/^\[\d+\]/,      // [1] Author...
		/^\d+\.\s/,      // 1. Author...
		/^\(\d+\)/,      // (1) Author...
	];

	// Figure location index (built after PDF load)
	interface FigureLocation {
		type: 'figure' | 'table' | 'algorithm' | 'equation' | 'section';
		id: string;           // "Fig. 1", "Table I" etc.
		normalizedId: string; // "fig-1", "table-1" (normalized)
		pageNum: number;
		yPosition: number;    // Y coordinate within page
		caption?: string;
	}

	let figureIndex = $state<Map<string, FigureLocation>>(new Map());
	let isBuildingFigureIndex = $state(false);

	// Outline item type for FloatingOutline component
	interface OutlineItem {
		id: string;
		type: 'equation' | 'figure' | 'table' | 'section' | 'reference' | 'algorithm';
		title: string;
		content?: string;
		page: number;
		yPosition?: number;
	}

	// Derived outline data from figureIndex and references
	// Uses Map to deduplicate by title (first occurrence wins)
	let outlineFigures = $derived.by(() => {
		const items: OutlineItem[] = [];
		for (const [, loc] of figureIndex) {
			if (loc.type === 'figure') {
				items.push({
					id: loc.normalizedId,
					type: 'figure',
					title: loc.id,
					content: loc.caption,
					page: loc.pageNum,
					yPosition: loc.yPosition
				});
			}
		}
		const sorted = items.sort((a, b) => a.page - b.page || (a.yPosition || 0) - (b.yPosition || 0));
		// Deduplicate by title (keep first occurrence)
		return Array.from(new Map(sorted.map(item => [item.title, item])).values());
	});

	let outlineTables = $derived.by(() => {
		const items: OutlineItem[] = [];
		for (const [, loc] of figureIndex) {
			if (loc.type === 'table') {
				items.push({
					id: loc.normalizedId,
					type: 'table',
					title: loc.id,
					content: loc.caption,
					page: loc.pageNum,
					yPosition: loc.yPosition
				});
			}
		}
		const sorted = items.sort((a, b) => a.page - b.page || (a.yPosition || 0) - (b.yPosition || 0));
		return Array.from(new Map(sorted.map(item => [item.title, item])).values());
	});

	let outlineEquations = $derived.by(() => {
		const items: OutlineItem[] = [];
		for (const [, loc] of figureIndex) {
			if (loc.type === 'equation') {
				items.push({
					id: loc.normalizedId,
					type: 'equation',
					title: loc.id,
					content: loc.caption,
					page: loc.pageNum,
					yPosition: loc.yPosition
				});
			}
		}
		const sorted = items.sort((a, b) => a.page - b.page || (a.yPosition || 0) - (b.yPosition || 0));
		return Array.from(new Map(sorted.map(item => [item.title, item])).values());
	});

	let outlineSections = $derived.by(() => {
		const items: OutlineItem[] = [];
		for (const [, loc] of figureIndex) {
			if (loc.type === 'section') {
				items.push({
					id: loc.normalizedId,
					type: 'section',
					title: loc.id,
					content: loc.caption,
					page: loc.pageNum,
					yPosition: loc.yPosition
				});
			}
		}
		const sorted = items.sort((a, b) => a.page - b.page || (a.yPosition || 0) - (b.yPosition || 0));
		return Array.from(new Map(sorted.map(item => [item.title, item])).values());
	});

	let outlineReferences = $derived.by(() => {
		// Filter out invalid references (e.g., years parsed as reference numbers)
		return parsedReferences
			.filter(ref => ref.number > 0 && ref.number < 1000) // Valid ref numbers are typically < 1000
			.map(ref => ({
				id: `ref-${ref.number}`,
				type: 'reference' as const,
				title: `[${ref.number}] ${ref.authors || ''}`,
				content: ref.title || ref.rawText.substring(0, 80),
				page: ref.pageNum,
				yPosition: 0
			}));
	});

	// Handle outline item click - navigate to location or open modal
	function handleOutlineItemClick(item: OutlineItem) {
		if (item.type === 'reference') {
			// Open citation modal for reference lookup instead of navigating
			// Extract reference number from title (e.g., "[1] Author Name" -> 1)
			const refNumMatch = item.title.match(/^\[(\d+)\]/);
			const refNum = refNumMatch ? parseInt(refNumMatch[1]) : null;

			// Find the parsed reference by number
			const parsedRef = refNum ? parsedReferences.find(r => r.number === refNum) : null;

			// Use title or content for search query
			const searchQuery = parsedRef?.title || parsedRef?.authors || item.content || item.title;
			if (searchQuery && searchQuery.length >= 3) {
				citationQuery = searchQuery;
				showCitationModal = true;
			}
		} else {
			// Navigate to figure/table/section
			scrollToFigure(item.title);
		}
	}

	// Check if text is a standalone citation (must be the entire text or surrounded by punctuation)
	function isStandaloneCitation(text: string): { isCitation: boolean; query: string } {
		const trimmed = text.trim();

		// IEEE numeric style: [1], [1,2,3], [1-5], [1–5]
		// Must be the ENTIRE text content (not part of a sentence)
		const numericMatch = trimmed.match(/^\[(\d+(?:,\s*\d+)*)\]$/);
		if (numericMatch) {
			return { isCitation: true, query: trimmed };
		}

		const rangeMatch = trimmed.match(/^\[(\d+)\s*[-–]\s*(\d+)\]$/);
		if (rangeMatch) {
			return { isCitation: true, query: trimmed };
		}

		// Author-year style: (Smith et al., 2024), (Smith & Jones, 2024)
		// Must be the entire text or end of text
		const authorYearMatch = trimmed.match(/\(([A-Z][a-z]+(?:\s+(?:et\s+al\.?|&\s+[A-Z][a-z]+))?,\s*\d{4})\)$/);
		if (authorYearMatch) {
			return { isCitation: true, query: authorYearMatch[1] };
		}

		return { isCitation: false, query: '' };
	}

	// Handle click on text layer - figure references, citations, and reference entries
	function handleTextLayerClick(event: MouseEvent) {
		const target = event.target as HTMLElement;
		if (!target.closest('.textLayer')) return;

		// Get the exact text of the clicked element
		const text = target.textContent || '';
		const clickedText = text.trim();

		// Check for figure/table references (navigate to figure)
		for (const pattern of FIGURE_PATTERNS) {
			pattern.lastIndex = 0;
			const match = pattern.exec(clickedText);
			if (match) {
				const figRef = match[1];
				scrollToFigure(figRef);
				return;
			}
		}

		// Check if this is a reference entry in the References section (clickable to search)
		if (target.classList.contains('reference-entry')) {
			// Extract reference number and look up the parsed reference
			const numMatch = clickedText.match(/\d+/);
			if (numMatch) {
				const refNum = parseInt(numMatch[0], 10);
				const parsedRef = lookupReferenceByNumber(refNum);
				if (parsedRef) {
					// Use parsed reference info for search
					const query = parsedRef.title || parsedRef.authors || parsedRef.rawText;
					if (query && query.length >= 5) {
						citationQuery = query;
						showCitationModal = true;
						return;
					}
				}
			}
		}

		// Only show citation modal if the clicked text is EXACTLY a citation
		// (not text that happens to contain citation-like patterns)
		const citationCheck = isStandaloneCitation(clickedText);
		if (citationCheck.isCitation) {
			handleCitationClick(event, citationCheck.query);
		}
	}

	// Handle citation click - show lookup modal
	function handleCitationClick(event: MouseEvent, text: string) {
		event.preventDefault();
		event.stopPropagation();

		// Extract meaningful search query from citation context
		let query = text;

		// Clean up the query - remove brackets/parentheses
		const cleanedText = text.replace(/[\[\]()]/g, '').trim();

		// For numeric citations like [1], [1,2,3], [1-5]:
		// Try to look up in parsed references first
		if (/^\d+(?:,\s*\d+)*$/.test(cleanedText) || /^\d+\s*[-–]\s*\d+$/.test(cleanedText)) {
			// Extract the first number from the citation
			const firstNumMatch = cleanedText.match(/\d+/);
			if (firstNumMatch) {
				const refNum = parseInt(firstNumMatch[0], 10);
				const parsedRef = lookupReferenceByNumber(refNum);

				if (parsedRef) {
					// Use the parsed reference title/authors for search
					console.log(`[Citation] Found reference ${refNum}:`, parsedRef.title);
					query = parsedRef.title || parsedRef.authors || parsedRef.rawText;
				} else {
					console.log(`[Citation] Reference ${refNum} not found in parsed references`);
					// Fall back to context-based search
					query = getContextualQuery(text) || cleanedText;
				}
			}
		} else {
			// For author-year style citations, use as-is
			query = cleanedText;
		}

		// If query is still just numbers or too short, skip
		if (query.length < 5 || /^[\d\s,\-–]+$/.test(query)) {
			console.log('[Citation] Query too short or just numbers:', query);
			return;
		}

		// Show citation modal
		citationQuery = query;
		showCitationModal = true;
	}

	/**
	 * Get contextual search query from surrounding text (fallback for when references aren't parsed)
	 */
	function getContextualQuery(text: string): string | null {
		const selection = window.getSelection();
		if (!selection || selection.rangeCount === 0) return null;

		const range = selection.getRangeAt(0);
		const container = range.startContainer.parentElement;
		if (!container) return null;

		const parent = container.parentElement;
		if (!parent) return null;

		const siblings = Array.from(parent.children);
		const currentIndex = siblings.indexOf(container);

		// Collect text from surrounding elements
		let contextText = '';
		for (let i = Math.max(0, currentIndex - 3); i <= Math.min(siblings.length - 1, currentIndex + 3); i++) {
			contextText += ' ' + (siblings[i].textContent || '');
		}

		// Extract sentence containing the citation
		const sentences = contextText.split(/[.!?]+/);
		for (const sentence of sentences) {
			if (sentence.includes(text)) {
				// Use key terms from the sentence, removing citation markers
				return sentence.replace(/\[\d+(?:,\s*\d+)*\]/g, '').trim();
			}
		}

		return null;
	}

	/**
	 * Scroll to figure/table in the document using indexed locations
	 * Falls back to real-time search if not found in index
	 */
	async function scrollToFigure(figRef: string): Promise<boolean> {
		if (!scrollContainer || !pdfDoc) return false;

		const normalizedId = normalizeId(figRef);
		console.log(`[PDF] Looking up figure: "${figRef}" → normalized: "${normalizedId}"`);

		// 1. Try index lookup
		let location = figureIndex.get(normalizedId);

		// 2. Try Roman numeral conversion (Table I → table-1)
		if (!location && /[IVX]+/i.test(figRef)) {
			const arabicId = normalizedId.replace(/[ivx]+/gi, (m) =>
				String(romanToArabic(m))
			);
			console.log(`[PDF] Trying Roman→Arabic conversion: "${arabicId}"`);
			location = figureIndex.get(arabicId);
		}

		// 3. Fallback: real-time search through rendered pages
		if (!location) {
			console.log(`[PDF] Not in index, searching rendered pages...`);
			location = await searchFigureInRenderedPages(figRef);
		}

		if (!location) {
			console.warn(`[PDF] ${figRef} not found`);
			return false;
		}

		console.log(`[PDF] Found ${figRef} on page ${location.pageNum} at y=${location.yPosition}`);

		// 4. Ensure page is rendered
		await ensurePageRendered(location.pageNum);

		// 5. Scroll to page and position
		const pageWrapper = scrollContainer.querySelector(`[data-page="${location.pageNum}"]`) as HTMLElement;
		if (!pageWrapper) return false;

		// Calculate scroll position based on Y coordinate
		const pageRect = pageWrapper.getBoundingClientRect();
		const containerRect = scrollContainer.getBoundingClientRect();
		const pageTop = pageWrapper.offsetTop;

		// yPosition is from top of page (0 = top)
		// Use larger offset (300px) since captions are typically below figures
		// This ensures the figure itself is visible, not just the caption
		const scrollTarget = pageTop + (location.yPosition / 792) * pageRect.height - 300; // 792 is standard PDF page height

		scrollContainer.scrollTo({
			top: Math.max(0, scrollTarget),
			behavior: 'smooth'
		});

		// 6. Highlight caption after scroll completes
		setTimeout(() => {
			highlightFigureCaption(location!);
		}, 500);

		return true;
	}

	/**
	 * Fallback: Search for figure caption in currently rendered pages
	 */
	async function searchFigureInRenderedPages(figRef: string): Promise<FigureLocation | null> {
		if (!scrollContainer) return null;

		const numMatch = figRef.match(/\d+(?:\.\d+)?/);
		if (!numMatch) return null;

		const figNum = numMatch[0];
		const figType = detectFigureType(figRef);

		const textLayers = scrollContainer.querySelectorAll('.textLayer');
		for (const textLayer of textLayers) {
			const pageWrapper = textLayer.closest('.page-wrapper') as HTMLElement;
			const pageNum = parseInt(pageWrapper?.dataset.page || '0', 10);
			if (!pageNum) continue;

			const spans = textLayer.querySelectorAll('span');
			for (const span of spans) {
				const spanText = span.textContent?.toLowerCase() || '';

				// Check for caption patterns
				const captionPatterns = [
					new RegExp(`^${figType}\\s*${figNum}[.:]`, 'i'),
					new RegExp(`^fig(?:ure)?\\.?\\s*${figNum}[.:]`, 'i'),
					new RegExp(`^table\\s*${figNum}[.:]`, 'i'),
				];

				for (const pattern of captionPatterns) {
					if (pattern.test(spanText)) {
						// Found! Calculate Y position
						const rect = span.getBoundingClientRect();
						const pageRect = pageWrapper.getBoundingClientRect();
						const yPosition = rect.top - pageRect.top;

						return {
							type: figType,
							id: figRef,
							normalizedId: normalizeId(figRef),
							pageNum,
							yPosition,
							caption: spanText.substring(0, 100)
						};
					}
				}
			}
		}

		return null;
	}

	// Save viewer state to tab store
	function saveViewerState() {
		if (!tabId || !scrollContainer) return;

		updateViewerState(tabId, {
			scrollTop: scrollContainer.scrollTop,
			scrollLeft: scrollContainer.scrollLeft,
			currentPage,
			scale,
			viewMode
		});
	}

	// Track the last loaded URL to prevent duplicate loads
	let lastLoadedUrl = '';

	onMount(() => {
		window.addEventListener('keydown', handleKeydown);
		container?.addEventListener('wheel', handleWheel, { passive: false });
		window.addEventListener('beforeunload', saveViewerState);

		// Initial load
		if (pdfUrl && pdfUrl !== lastLoadedUrl) {
			lastLoadedUrl = pdfUrl;
			loadPdf();
		}
	});

	onDestroy(() => {
		saveViewerState();
		window.removeEventListener('keydown', handleKeydown);
		window.removeEventListener('beforeunload', saveViewerState);
		container?.removeEventListener('wheel', handleWheel);
		renderTasks.forEach((task) => task.cancel());
		if (pdfDoc) {
			pdfDoc.destroy();
		}
	});

	// Reload only when URL actually changes
	$effect(() => {
		if (pdfUrl && pdfUrl !== lastLoadedUrl) {
			lastLoadedUrl = pdfUrl;
			loadPdf();
		}
	});
</script>

<svelte:head>
	<style>
		/* PDF.js text layer styles */
		.textLayer {
			position: absolute;
			text-align: initial;
			inset: 0;
			overflow: hidden;
			opacity: 0.25;
			line-height: 1;
			-webkit-text-size-adjust: none;
			-moz-text-size-adjust: none;
			text-size-adjust: none;
			forced-color-adjust: none;
			transform-origin: 0 0;
			z-index: 2;
		}

		.textLayer :is(span, br) {
			color: transparent;
			position: absolute;
			white-space: pre;
			cursor: text;
			transform-origin: 0% 0%;
		}

		.textLayer span.markedContent {
			top: 0;
			height: 0;
		}

		.textLayer .highlight {
			margin: -1px;
			padding: 1px;
			background-color: rgb(180 0 170 / 40%);
			border-radius: 4px;
		}

		.textLayer .highlight.appended {
			position: initial;
		}

		.textLayer .highlight.begin {
			border-radius: 4px 0 0 4px;
		}

		.textLayer .highlight.end {
			border-radius: 0 4px 4px 0;
		}

		.textLayer .highlight.middle {
			border-radius: 0;
		}

		.textLayer .highlight.selected {
			background-color: rgb(0 100 0 / 40%);
		}

		.textLayer ::selection {
			background: rgb(0 0 255 / 30%);
		}

		.textLayer br::selection {
			background: transparent;
		}

		.textLayer .endOfContent {
			display: block;
			position: absolute;
			inset: 100% 0 0;
			z-index: -1;
			cursor: default;
			-webkit-user-select: none;
			-moz-user-select: none;
			user-select: none;
		}

		.textLayer .endOfContent.active {
			top: 0;
		}

		/* Clickable citation/figure reference styles */
		.textLayer span.citation-ref,
		.textLayer span.figure-ref {
			cursor: pointer !important;
			color: transparent;
			background-color: rgba(59, 130, 246, 0.1);
			border-radius: 2px;
			transition: background-color 0.15s ease;
		}

		.textLayer span.citation-ref:hover {
			background-color: rgba(59, 130, 246, 0.25);
		}

		.textLayer span.figure-ref:hover {
			background-color: rgba(34, 197, 94, 0.25);
		}

		/* Reference entry styles (items in References section) */
		.textLayer span.reference-entry {
			cursor: pointer !important;
			color: transparent;
			background-color: rgba(168, 85, 247, 0.1); /* Purple tint */
			border-radius: 2px;
			transition: background-color 0.15s ease;
		}

		.textLayer span.reference-entry:hover {
			background-color: rgba(168, 85, 247, 0.25);
		}

		/* Figure caption highlight animation (after navigation) */
		.textLayer span.figure-highlight {
			animation: figure-pulse 2s ease-out;
		}

		@keyframes figure-pulse {
			0% { background-color: rgba(251, 191, 36, 0.6); }
			100% { background-color: transparent; }
		}
	</style>
</svelte:head>

<div class="flex h-full flex-col bg-neutral-100 dark:bg-neutral-900" bind:this={container}>
	<!-- Toolbar -->
	<div class="flex items-center gap-1 border-b bg-white px-2 py-1.5 shadow-sm dark:bg-neutral-800">
		<!-- Page navigation -->
		<div class="flex items-center gap-1">
			<button
				class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 disabled:opacity-40 dark:text-neutral-300 dark:hover:bg-neutral-700"
				onclick={prevPage}
				disabled={currentPage <= 1}
				title="Previous page"
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M15 18l-6-6 6-6" />
				</svg>
			</button>

			<div class="flex items-center gap-1 text-sm">
				<input
					type="text"
					bind:value={pageInputValue}
					onchange={handlePageInput}
					onblur={handlePageInput}
					class="w-12 rounded border border-neutral-300 bg-white px-2 py-1 text-center text-sm focus:border-blue-500 focus:outline-none dark:border-neutral-600 dark:bg-neutral-700"
				/>
				<span class="text-neutral-500 dark:text-neutral-400">/ {totalPages}</span>
			</div>

			<button
				class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 disabled:opacity-40 dark:text-neutral-300 dark:hover:bg-neutral-700"
				onclick={nextPage}
				disabled={currentPage >= totalPages}
				title="Next page"
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M9 18l6-6-6-6" />
				</svg>
			</button>
		</div>

		<div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div>

		<!-- View mode toggle -->
		<div class="flex items-center gap-1 rounded-md border border-neutral-300 p-0.5 dark:border-neutral-600">
			<button
				class="rounded px-2 py-1 text-sm transition-colors {viewMode === 'single'
					? 'bg-neutral-200 font-medium text-neutral-800 dark:bg-neutral-600 dark:text-white'
					: 'text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200'}"
				onclick={() => switchViewMode('single')}
				title="Single page view"
			>
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<rect x="5" y="3" width="14" height="18" rx="2" />
				</svg>
			</button>
			<button
				class="rounded px-2 py-1 text-sm transition-colors {viewMode === 'scroll'
					? 'bg-neutral-200 font-medium text-neutral-800 dark:bg-neutral-600 dark:text-white'
					: 'text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200'}"
				onclick={() => switchViewMode('scroll')}
				title="Continuous scroll view"
			>
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<rect x="5" y="2" width="14" height="8" rx="1" />
					<rect x="5" y="14" width="14" height="8" rx="1" />
				</svg>
			</button>
		</div>

		<div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div>

		<!-- Zoom controls -->
		<div class="flex items-center gap-1">
			<button
				class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700"
				onclick={zoomOut}
				title="Zoom out (Ctrl+-)"
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<circle cx="11" cy="11" r="8" />
					<path d="M21 21l-4.35-4.35M8 11h6" />
				</svg>
			</button>

			<span class="min-w-14 text-center text-sm text-neutral-600 dark:text-neutral-300">
				{Math.round(scale * 100)}%
			</span>

			<button
				class="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700"
				onclick={zoomIn}
				title="Zoom in (Ctrl++)"
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<circle cx="11" cy="11" r="8" />
					<path d="M21 21l-4.35-4.35M11 8v6M8 11h6" />
				</svg>
			</button>
		</div>

		<div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div>

		<!-- Fit options -->
		<div class="flex items-center gap-1">
			<button
				class="rounded px-2 py-1 text-sm text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700"
				onclick={fitWidth}
				title="Fit to width"
			>
				Fit Width
			</button>
			<button
				class="rounded px-2 py-1 text-sm text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700"
				onclick={fitPage}
				title="Fit to page"
			>
				Fit Page
			</button>
		</div>

		<!-- References and Citations detection status -->
		<div class="flex items-center gap-2">
			{#if parsedReferences.length > 0}
				<span
					class="flex items-center gap-1 rounded px-2 py-1 text-xs {referencesSource === 'backend' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300' : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300'}"
					title={referencesSource === 'backend' ? 'References analyzed by backend (stored in database)' : 'References parsed by frontend (not stored)'}
				>
					<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M5 13l4 4L19 7" />
					</svg>
					{parsedReferences.length} refs
					{#if referencesSource === 'backend'}
						<span class="text-[10px] opacity-70">(DB)</span>
					{/if}
				</span>
			{:else if isDetectingReferences}
				<span class="flex items-center gap-1 rounded bg-blue-100 px-2 py-1 text-xs text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
					<svg class="h-3 w-3 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20" />
					</svg>
					Loading refs...
				</span>
			{/if}

			{#if citationSpans.length > 0}
				<span
					class="flex items-center gap-1 rounded px-2 py-1 text-xs {citationsSource === 'backend' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300' : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'}"
					title={citationsSource === 'backend' ? 'Citations extracted with precise bounding boxes (from PDF annotations)' : 'Citations detected by pattern matching'}
				>
					<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
					</svg>
					{citationSpans.length} cites
				</span>
			{:else if isLoadingCitations}
				<span class="flex items-center gap-1 rounded bg-blue-100 px-2 py-1 text-xs text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
					<svg class="h-3 w-3 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20" />
					</svg>
					Citations...
				</span>
			{/if}
		</div>

		<div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div>

		<!-- Reader/Blog mode toggle -->
		<div class="ml-auto flex items-center gap-1">
			<!-- Outline toggle button -->
			<button
				class="rounded p-1.5 transition-colors {showOutlinePanel
					? 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/50 dark:text-indigo-300'
					: 'text-neutral-600 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-700'}"
				onclick={() => showOutlinePanel = !showOutlinePanel}
				title="Toggle Outline Panel"
			>
				<List class="h-5 w-5" />
			</button>

			<div class="mx-2 h-5 w-px bg-neutral-300 dark:bg-neutral-600"></div>

			<button
				class="rounded bg-blue-100 px-3 py-1 text-sm font-medium text-blue-700 dark:bg-blue-900 dark:text-blue-300"
				title="Reader view (current)"
			>
				Reader
			</button>
			<button
				class="rounded px-3 py-1 text-sm text-neutral-500 hover:bg-neutral-100 dark:text-neutral-400 dark:hover:bg-neutral-700"
				title="Blog view (coming soon)"
				disabled
			>
				Blog
			</button>
		</div>
	</div>

	<!-- PDF Content -->
	<div
		class="flex-1 overflow-auto p-6"
		style="scroll-behavior: smooth;"
		bind:this={scrollContainer}
		onscroll={handleScroll}
		onmouseup={handleMouseUp}
		onclick={handleTextLayerClick}
	>
		{#if isLoading}
			<div class="flex h-full items-center justify-center">
				<div class="flex flex-col items-center gap-3">
					<div class="h-10 w-10 animate-spin rounded-full border-3 border-blue-500 border-t-transparent"></div>
					<p class="text-sm text-neutral-500 dark:text-neutral-400">Loading PDF...</p>
				</div>
			</div>
		{:else if error}
			<div class="flex h-full flex-col items-center justify-center text-neutral-500 dark:text-neutral-400">
				<svg class="mb-4 h-16 w-16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
					<path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
				</svg>
				<p class="text-lg font-medium">Failed to load PDF</p>
				<p class="mt-1 text-sm">{error}</p>
				<button
					class="mt-4 rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
					onclick={loadPdf}
				>
					Retry
				</button>
			</div>
		{/if}
	</div>
</div>

<!-- Citation Lookup Modal -->
{#if showCitationModal}
	<CitationModal
		query={citationQuery}
		onClose={() => {
			showCitationModal = false;
			citationQuery = '';
		}}
		onOpenPaper={(paperId) => {
			showCitationModal = false;
			citationQuery = '';
			onOpenPaper?.(paperId);
		}}
	/>
{/if}

<!-- Floating Outline Panel -->
{#if showOutlinePanel}
	<FloatingOutline
		sections={outlineSections}
		figures={outlineFigures}
		tables={outlineTables}
		equations={outlineEquations}
		references={outlineReferences}
		onClose={() => showOutlinePanel = false}
		onItemClick={handleOutlineItemClick}
	/>
{/if}
