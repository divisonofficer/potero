<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { updateViewerState } from '$lib/stores/tabs';
	import type { PdfViewerState } from '$lib/types';

	interface Props {
		pdfUrl: string;
		tabId?: string;
		initialState?: PdfViewerState;
		onPageChange?: (page: number, total: number) => void;
		onTextSelect?: (text: string) => void;
	}

	let { pdfUrl, tabId, initialState, onPageChange, onTextSelect }: Props = $props();

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
		} catch (e) {
			console.error('Failed to load PDF:', e);
			error = e instanceof Error ? e.message : 'Failed to load PDF';
		} finally {
			isLoading = false;
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
			}

		} catch (e) {
			if (e instanceof Error && e.message.includes('cancelled')) return;
			console.error(`Failed to render page ${pageNum}:`, e);
			renderedPages.delete(pageNum);
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

		<!-- Reader/Blog mode toggle -->
		<div class="ml-auto flex items-center gap-1">
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
