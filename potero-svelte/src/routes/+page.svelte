<script lang="ts">
	import { onMount } from 'svelte';
	import { tabs, activeTab, activeTabId, closeTab, openSettings, isChatPanelOpen, toggleChatPanel, openPaper, updateTabPaper, openNotesList, openNote, isNotePanelOpen, notePanelPaperId, notePanelNoteId, openNotePanel, closeNotePanel } from '$lib/stores/tabs';
	import {
		papers,
		filteredPapers,
		isLoading,
		error,
		searchQuery,
		viewStyle,
		sortBy,
		initializeLibrary,
		importByDoi,
		importByArxiv,
		uploadPdfs,
		pendingUploadAnalysis,
		clearPendingUploadAnalysis,
		loadPapers,
		reanalyzePaper,
		deletePaper,
		onlineSearchResults,
		isSearchingOnline,
		searchOnlineIfNeeded,
		importFromSearchResult
	} from '$lib/stores/library';
	import { api, type Settings } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';
	import type { Paper, ResearchNote } from '$lib/types';
	import { createNote } from '$lib/stores/notes';
	import { browser } from '$app/environment';
	import FloatingChatPanel from '$lib/components/chat/FloatingChatPanel.svelte';
	import FloatingNotePanel from '$lib/components/notes/FloatingNotePanel.svelte';
	import SearchResultsDialog from '$lib/components/SearchResultsDialog.svelte';
	import JobStatusPanel from '$lib/components/JobStatusPanel.svelte';
	import LLMLogPanel from '$lib/components/LLMLogPanel.svelte';
	import AuthorModal from '$lib/components/AuthorModal.svelte';
	import AuthorProfileView from '$lib/components/AuthorProfileView.svelte';
	import SettingsPanel from '$lib/components/SettingsPanel.svelte';
	import NoteList from '$lib/components/notes/NoteList.svelte';
	import NoteViewer from '$lib/components/notes/NoteViewer.svelte';
	import { formatVenue } from '$lib/utils/venueAbbreviation';
	import { online } from 'svelte/reactivity/window';
	import {
		Library,
		FileText,
		Settings as SettingsIcon,
		User,
		Tag,
		Building2,
		Search,
		X,
		ChevronDown,
		BookOpen
	} from 'lucide-svelte';

	// Tab icon mapping
	const tabIcons: Record<string, typeof Library> = {
		home: Library,
		viewer: FileText,
		settings: SettingsIcon,
		author: User,
		tag: Tag,
		journal: Building2,
		notes: BookOpen,
		'note-viewer': FileText
	};

	// LLM log panel state
	let showLLMLogPanel = $state(false);

	// Settings tab state
	let settingsActiveTab = $state<'llm' | 'search' | 'system'>('llm');

	// Author modal state
	let selectedAuthorName = $state<string | null>(null);
	let selectedAuthorPapers = $state<Paper[]>([]);

	function openAuthorModal(authorName: string) {
		// Find all papers by this author
		const authorPapers = $papers.filter((p) =>
			p.authors.some((a) => a.toLowerCase() === authorName.toLowerCase())
		);
		selectedAuthorName = authorName;
		selectedAuthorPapers = authorPapers;
	}

	function closeAuthorModal() {
		selectedAuthorName = null;
		selectedAuthorPapers = [];
	}

	// Open a paper by ID (used by CitationModal when paper exists in library)
	async function openPaperById(paperId: string) {
		// First check if paper is in current papers list
		let paper = $papers.find(p => p.id === paperId);

		if (!paper) {
			// Paper not in local state, try to fetch from API
			const response = await api.getPaper(paperId);
			if (response.success && response.data) {
				paper = response.data as Paper;
			}
		}

		if (paper) {
			openPaper(paper);
		} else {
			toast.error('Paper not found');
		}
	}

	// Dynamic import for PDF viewer (client-side only due to pdfjs)
	let PdfViewer: typeof import('$lib/components/PdfViewer.svelte').default | null = $state(null);

	if (browser) {
		import('$lib/components/PdfViewer.svelte').then(module => {
			PdfViewer = module.default;
		});
	}

	// Import dialog state
	let showImportDialog = $state(false);
	let importType = $state<'doi' | 'arxiv' | 'file'>('file');
	let importValue = $state('');
	let isImporting = $state(false);
	let fileInput: HTMLInputElement;

	// Drag and drop state
	let isDragging = $state(false);

	// Delete confirmation state
	let paperToDelete = $state<Paper | null>(null);
	let isDeleting = $state(false);

	async function handleDeletePaper() {
		if (!paperToDelete) return;
		isDeleting = true;
		const success = await deletePaper(paperToDelete.id);
		if (success) {
			toast.success(`Deleted "${paperToDelete.title}"`);
		} else {
			toast.error('Failed to delete paper');
		}
		paperToDelete = null;
		isDeleting = false;
	}

	// Settings state
	let settings = $state<Settings>({
		llmApiKey: null,
		llmProvider: 'gpt',
		pdfStoragePath: null,
		theme: 'system',
		semanticScholarApiKey: null
	});
	let newApiKey = $state('');
	let newSemanticScholarApiKey = $state('');
	let ssoAccessToken = $state('');
	let ssoSiteName = $state('robi-gpt-dev');
	let isSavingSettings = $state(false);
	let isSavingSSO = $state(false);

	// PDF download state
	let isDownloadingPdf = $state(false);
	let downloadingPaperId = $state<string | null>(null);

	// Bulk reanalyze state
	let isBulkReanalyzing = $state(false);

	/**
	 * Download PDF for a paper from online sources
	 */
	async function handleDownloadPdf(paperId: string) {
		isDownloadingPdf = true;
		downloadingPaperId = paperId;

		try {
			console.log(`[Download PDF] Attempting to download for paper: ${paperId}`);

			const result = await api.downloadPdf(paperId);

			if (result.success && result.data) {
				toast.success('PDF downloaded successfully');

				// Reload papers to update UI
				await loadPapers();

				// Update the current tab's paper if it's the same paper
				const currentTab = $tabs.find(t => t.type === 'viewer' && t.paper?.id === paperId);
				if (currentTab) {
					// Get updated paper info
					const updatedPaper = await api.getPaper(paperId);
					if (updatedPaper.success && updatedPaper.data) {
						updateTabPaper(currentTab.id, updatedPaper.data);
					}
				}

				console.log(`[Download PDF] Success: ${result.data.pdfPath}`);
			} else {
				throw new Error(result.error || 'Failed to download PDF');
			}
		} catch (err) {
			console.error('[Download PDF] Error:', err);
			const errorMessage = err instanceof Error ? err.message : 'Failed to download PDF';
			toast.error(errorMessage);
		} finally {
			isDownloadingPdf = false;
			downloadingPaperId = null;
		}
	}

	async function handleBulkReanalyzeAll() {
		isBulkReanalyzing = true;
		try {
			const response = await api.bulkReanalyze({ criteria: ['all'] });
			if (response.success && response.data) {
				toast.info(`Started analyzing ${response.data.totalPapers} papers. Check progress in the task panel.`);
			} else {
				toast.error('Failed to start bulk analysis');
			}
		} catch (e) {
			toast.error('Failed to start bulk analysis');
		}
		isBulkReanalyzing = false;
	}

	async function handleBulkReanalyzeMissing() {
		isBulkReanalyzing = true;
		try {
			const response = await api.bulkReanalyze({
				criteria: ['missing_thumbnail', 'missing_venue', 'missing_doi', 'missing_abstract']
			});
			if (response.success && response.data) {
				toast.info(`Started analyzing ${response.data.totalPapers} papers with missing data. Check progress in the task panel.`);
			} else {
				toast.error('Failed to start bulk analysis');
			}
		} catch (e) {
			toast.error('Failed to start bulk analysis');
		}
		isBulkReanalyzing = false;
	}

	onMount(() => {
		initializeLibrary();
		loadSettings();
	});

	// Trigger online search when local results are few
	$effect(() => {
		const query = $searchQuery;
		const localCount = $filteredPapers.length;
		searchOnlineIfNeeded(query, localCount);
	});

	async function loadSettings() {
		const result = await api.getSettings();
		if (result.success && result.data) {
			settings = result.data;
		}
	}

	async function saveSettings() {
		isSavingSettings = true;
		const updateData: Partial<Settings> = {
			llmProvider: settings.llmProvider,
			theme: settings.theme,
			enableSciHub: settings.enableSciHub
		};

		// Only send API key if user entered a new one
		if (newApiKey.trim()) {
			updateData.llmApiKey = newApiKey;
		}

		// Only send Semantic Scholar API key if user entered a new one
		if (newSemanticScholarApiKey.trim()) {
			updateData.semanticScholarApiKey = newSemanticScholarApiKey;
		}

		const result = await api.updateSettings(updateData);
		if (result.success && result.data) {
			settings = result.data;
			newApiKey = '';
			newSemanticScholarApiKey = '';
			toast.success('Settings saved');
		} else {
			toast.error('Failed to save settings');
		}
		isSavingSettings = false;
	}

	async function saveSSO() {
		if (!ssoAccessToken.trim()) {
			toast.error('Please enter an SSO access token');
			return;
		}

		isSavingSSO = true;
		const result = await api.saveSSOToken(
			ssoAccessToken,
			ssoSiteName || 'robi-gpt-dev'
		);

		if (result.success && result.data) {
			settings = result.data;
			ssoAccessToken = '';
			toast.success('SSO token saved successfully');
		} else {
			toast.error('Failed to save SSO token');
		}
		isSavingSSO = false;
	}

	/**
	 * Handle SSO login flow
	 * - In Electron: Opens modal window with auto token extraction
	 * - In Browser: Redirects to SSO login page
	 */
	async function handleSSOLogin() {
		isSavingSSO = true;
		const result = await api.loginSSO();

		if (result.success && result.accessToken) {
			// In Electron: Token was extracted automatically
			const expiresAt = result.expiresIn
				? Date.now() + result.expiresIn * 1000
				: undefined;

			const saveResult = await api.saveSSOToken(
				result.accessToken,
				ssoSiteName || 'robi-gpt-dev',
				expiresAt
			);

			if (saveResult.success && saveResult.data) {
				settings = saveResult.data;
				toast.success('SSO login successful!');
			} else {
				toast.error('Failed to save SSO token');
			}
		} else if (result.error) {
			toast.error(`SSO login failed: ${result.error}`);
		}
		// If browser redirect, this code won't execute

		isSavingSSO = false;
	}

	async function handleImport() {
		if (importType === 'file') {
			fileInput?.click();
			return;
		}

		if (!importValue.trim()) return;

		isImporting = true;
		const result =
			importType === 'doi' ? await importByDoi(importValue) : await importByArxiv(importValue);

		if (result) {
			showImportDialog = false;
			importValue = '';
		}
		isImporting = false;
	}

	async function handleFileSelect(event: Event) {
		const target = event.target as HTMLInputElement;
		const files = target.files;
		if (files && files.length > 0) {
			isImporting = true;
			const result = await uploadPdfs(files);
			if (result.successCount > 0) {
				showImportDialog = false;
			}
			isImporting = false;
		}
		// Reset input
		target.value = '';
	}

	function handleDragOver(event: DragEvent) {
		event.preventDefault();
		event.stopPropagation();
		if (event.dataTransfer) {
			event.dataTransfer.dropEffect = 'copy';
		}
		isDragging = true;
	}

	function handleDragLeave(event: DragEvent) {
		event.preventDefault();
		event.stopPropagation();
		// Only set isDragging to false if we're leaving the main container
		const relatedTarget = event.relatedTarget as Node | null;
		const currentTarget = event.currentTarget as Node;
		if (!relatedTarget || !currentTarget.contains(relatedTarget)) {
			isDragging = false;
		}
	}

	async function handleDrop(event: DragEvent) {
		event.preventDefault();
		event.stopPropagation();
		isDragging = false;

		const files = event.dataTransfer?.files;
		if (files && files.length > 0) {
			const pdfFiles = Array.from(files).filter(
				(f) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf')
			);
			if (pdfFiles.length > 0) {
				// Check file sizes before upload (50MB limit)
				const maxSize = 50 * 1024 * 1024;
				const oversizedFiles = pdfFiles.filter((f) => f.size > maxSize);
				if (oversizedFiles.length > 0) {
					toast.error(
						`File too large: ${oversizedFiles[0].name} (${Math.round(oversizedFiles[0].size / 1024 / 1024)}MB). Max size is 50MB.`
					);
					return;
				}

				const result = await uploadPdfs(pdfFiles);
				if (result.successCount > 0) {
					toast.success(`Successfully uploaded ${result.successCount} file${result.successCount > 1 ? 's' : ''}`);
				} else if ($error) {
					toast.error($error);
				}
			} else {
				toast.warning('Please drop PDF files only');
			}
		}
	}
</script>

<!-- Hidden file input -->
<input
	type="file"
	accept=".pdf,application/pdf"
	multiple
	bind:this={fileInput}
	onchange={handleFileSelect}
	class="hidden"
/>

<!-- Import Dialog -->
{#if showImportDialog}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
		<div class="w-full max-w-md rounded-lg bg-background p-6 shadow-xl">
			<h2 class="mb-4 text-lg font-semibold">Add Paper</h2>

			<div class="mb-4 flex gap-2">
				<button
					class="flex-1 rounded-md px-4 py-2 text-sm transition-colors
						{importType === 'file' ? 'bg-primary text-primary-foreground' : 'bg-muted'}"
					onclick={() => (importType = 'file')}
				>
					PDF File
				</button>
				<button
					class="flex-1 rounded-md px-4 py-2 text-sm transition-colors
						{importType === 'doi' ? 'bg-primary text-primary-foreground' : 'bg-muted'}"
					onclick={() => (importType = 'doi')}
				>
					DOI
				</button>
				<button
					class="flex-1 rounded-md px-4 py-2 text-sm transition-colors
						{importType === 'arxiv' ? 'bg-primary text-primary-foreground' : 'bg-muted'}"
					onclick={() => (importType = 'arxiv')}
				>
					arXiv
				</button>
			</div>

			{#if importType === 'file'}
				<div
					class="mb-4 flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 transition-colors
						{isDragging ? 'border-primary bg-primary/10' : 'border-muted-foreground/25'}"
					ondragenter={handleDragOver}
					ondragover={handleDragOver}
					ondragleave={handleDragLeave}
					ondrop={handleDrop}
					role="button"
					tabindex="0"
					aria-label="Drop zone for PDF files"
				>
					<svg class="mb-2 h-12 w-12 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<path d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
					</svg>
					<p class="mb-2 text-sm text-muted-foreground">Drag & drop PDF files here</p>
					<button
						class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
						onclick={() => fileInput?.click()}
					>
						Choose Files
					</button>
				</div>
			{:else}
				<input
					type="text"
					placeholder={importType === 'doi' ? 'Enter DOI (e.g., 10.1000/xyz123)' : 'Enter arXiv ID (e.g., 2301.00001)'}
					bind:value={importValue}
					class="mb-4 w-full rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
				/>
			{/if}

			{#if $error}
				<p class="mb-4 text-sm text-destructive">{$error}</p>
			{/if}

			<div class="flex justify-end gap-2">
				<button
					class="rounded-md px-4 py-2 text-sm hover:bg-muted"
					onclick={() => {
						showImportDialog = false;
						importValue = '';
					}}
				>
					Cancel
				</button>
				{#if importType !== 'file'}
					<button
						class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
						disabled={isImporting || !importValue.trim()}
						onclick={handleImport}
					>
						{isImporting ? 'Importing...' : 'Import'}
					</button>
				{/if}
			</div>
		</div>
	</div>
{/if}

<!-- Drop overlay - shows only on Library (home) tab when dragging files -->
{#if isDragging && $activeTab?.type === 'home'}
	<div
		class="pointer-events-none fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
	>
		<div class="flex flex-col items-center rounded-2xl border-4 border-dashed border-primary bg-background p-12 shadow-2xl">
			<svg class="mb-4 h-16 w-16 text-primary animate-bounce" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" />
			</svg>
			<p class="text-xl font-bold text-foreground">Drop PDF files here</p>
			<p class="mt-2 text-sm text-muted-foreground">Files will be added to your library</p>
		</div>
	</div>
{/if}

<div
	class="flex h-full flex-col"
	ondragenter={handleDragOver}
	ondragover={handleDragOver}
	ondragleave={handleDragLeave}
	ondrop={handleDrop}
	role="region"
	aria-label="Drop zone for PDF files"
>
	<!-- Tab Bar -->
	<div class="flex h-10 items-center border-b bg-muted/50 px-2">
		{#each $tabs as tab (tab.id)}
			<button
				class="flex h-8 items-center gap-1.5 rounded-t-md border-b-2 px-3 text-sm transition-colors
					{$activeTabId === tab.id
					? 'border-primary bg-background text-foreground'
					: 'border-transparent text-muted-foreground hover:text-foreground'}"
				onclick={() => activeTabId.set(tab.id)}
			>
				{#if tabIcons[tab.type]}
					<svelte:component this={tabIcons[tab.type]} class="h-4 w-4 shrink-0" />
				{/if}
				<span class="max-w-32 truncate">{tab.title}</span>
				{#if tab.id !== 'home'}
					<button
						class="ml-1 rounded p-0.5 hover:bg-muted"
						onclick={(e) => {
							e.stopPropagation();
							closeTab(tab.id);
						}}
					>
						<X class="h-3 w-3" />
					</button>
				{/if}
			</button>
		{/each}

		<!-- Chat toggle button (only show when PDF viewer is active) -->
		{#if $activeTab?.type === 'viewer'}
			<button
				class="ml-auto rounded p-1.5 transition-colors {$isChatPanelOpen
					? 'bg-primary text-primary-foreground'
					: 'text-muted-foreground hover:bg-muted hover:text-foreground'}"
				onclick={() => toggleChatPanel()}
				title={$isChatPanelOpen ? 'Close Chat' : 'Chat with Paper'}
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
				</svg>
			</button>
		{:else}
			<div class="ml-auto"></div>
		{/if}

		<!-- Quick Search button -->
		<button
			class="rounded p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
			onclick={() => {
				// Focus the search input if on home tab, or scroll to it
				if ($activeTab?.type === 'home') {
					const searchInput = document.querySelector('input[placeholder="Search papers..."]') as HTMLInputElement;
					if (searchInput) {
						searchInput.focus();
						searchInput.select();
					}
				} else {
					// Switch to home tab and focus search
					activeTabId.set('home');
					setTimeout(() => {
						const searchInput = document.querySelector('input[placeholder="Search papers..."]') as HTMLInputElement;
						if (searchInput) {
							searchInput.focus();
							searchInput.select();
						}
					}, 100);
				}
			}}
			title="Quick Search (Ctrl+K)"
		>
			<Search class="h-5 w-5" />
		</button>

		<!-- Notes button -->
		<button
			class="rounded p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
			onclick={() => openNotesList()}
			title="Research Notes"
		>
			<BookOpen class="h-5 w-5" />
		</button>

		<!-- Settings button -->
		<button
			class="rounded p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
			onclick={() => openSettings()}
			title="Settings"
		>
			<SettingsIcon class="h-5 w-5" />
		</button>
	</div>

	<!-- Content Area -->
	<div class="flex-1 overflow-hidden">
		<!-- Home tab -->
		<div class="h-full {$activeTab?.type === 'home' ? '' : 'hidden'}">
			<div class="h-full overflow-auto p-6">
				<h1 class="mb-6 text-2xl font-bold">Paper Library</h1>

				<!-- Search and filters -->
				<div class="mb-6 flex gap-4">
					<input
						type="text"
						placeholder="Search papers..."
						bind:value={$searchQuery}
						class="flex-1 rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
					/>

					<!-- Sort dropdown -->
					<select
						bind:value={$sortBy}
						class="rounded-md border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
					>
						<option value="recent">Most Recent</option>
						<option value="citations">Most Cited</option>
						<option value="title">Title A-Z</option>
					</select>

					<!-- View style buttons -->
					<div class="flex rounded-md border">
						<button
							class="px-3 py-2 text-sm transition-colors {$viewStyle === 'grid'
								? 'bg-primary text-primary-foreground'
								: 'hover:bg-muted'}"
							onclick={() => viewStyle.set('grid')}
							title="Grid view"
						>
							<svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
								<rect x="3" y="3" width="7" height="7" rx="1" />
								<rect x="14" y="3" width="7" height="7" rx="1" />
								<rect x="3" y="14" width="7" height="7" rx="1" />
								<rect x="14" y="14" width="7" height="7" rx="1" />
							</svg>
						</button>
						<button
							class="px-3 py-2 text-sm transition-colors {$viewStyle === 'list'
								? 'bg-primary text-primary-foreground'
								: 'hover:bg-muted'}"
							onclick={() => viewStyle.set('list')}
							title="List view"
						>
							<svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
								<rect x="3" y="4" width="18" height="4" rx="1" />
								<rect x="3" y="10" width="18" height="4" rx="1" />
								<rect x="3" y="16" width="18" height="4" rx="1" />
							</svg>
						</button>
						<button
							class="px-3 py-2 text-sm transition-colors {$viewStyle === 'compact'
								? 'bg-primary text-primary-foreground'
								: 'hover:bg-muted'}"
							onclick={() => viewStyle.set('compact')}
							title="Compact view"
						>
							<svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
								<rect x="3" y="5" width="18" height="2" rx="1" />
								<rect x="3" y="9" width="18" height="2" rx="1" />
								<rect x="3" y="13" width="18" height="2" rx="1" />
								<rect x="3" y="17" width="18" height="2" rx="1" />
							</svg>
						</button>
					</div>

					<button
						class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
						onclick={() => (showImportDialog = true)}
					>
						Add Paper
					</button>
				</div>

				<!-- Loading state -->
				{#if $isLoading}
					<div class="flex items-center justify-center py-12">
						<div class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
					</div>
				{:else if $filteredPapers.length === 0 && $onlineSearchResults.length === 0}
					<div
						class="flex flex-col items-center justify-center rounded-lg border-2 border-dashed py-16 text-muted-foreground transition-colors
							{isDragging ? 'border-primary bg-primary/10' : 'border-muted-foreground/25'}"
					>
						<svg class="mb-4 h-16 w-16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
							<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
						</svg>
						<p class="text-lg font-medium">No papers yet</p>
						<p class="mt-1 text-sm">Drop PDF files here or click Add Paper to get started</p>
						<button
							class="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
							onclick={() => (showImportDialog = true)}
						>
							Add Paper
						</button>
					</div>
				{:else}
					<!-- Compact/Table View -->
					{#if $viewStyle === 'compact'}
						<div class="overflow-x-auto rounded-lg border">
							<table class="w-full text-sm">
								<thead class="border-b bg-muted/50 text-left text-xs uppercase text-muted-foreground">
									<tr>
										<th class="w-8 p-3"></th>
										<th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => sortBy.set('title')}>
											<span class="flex items-center gap-1">
												Title
												{#if $sortBy === 'title'}
													<ChevronDown class="h-3 w-3" />
												{/if}
											</span>
										</th>
										<th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => sortBy.set('recent')}>
											Author
										</th>
										<th class="p-3 hidden md:table-cell">Conference</th>
										<th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => sortBy.set('recent')}>
											<span class="flex items-center gap-1">
												Year
												{#if $sortBy === 'recent'}
													<ChevronDown class="h-3 w-3" />
												{/if}
											</span>
										</th>
										<th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => sortBy.set('citations')}>
											<span class="flex items-center gap-1">
												Citations
												{#if $sortBy === 'citations'}
													<ChevronDown class="h-3 w-3" />
												{/if}
											</span>
										</th>
										<th class="w-10 p-3"></th>
									</tr>
								</thead>
								<tbody>
									{#each $filteredPapers as paper (paper.id)}
										<tr
											class="group cursor-pointer border-b transition-colors hover:bg-muted/50"
											onclick={() => {
												import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
											}}
										>
											<td class="p-3">
												{#if paper.pdfUrl}
													<svg class="h-4 w-4 text-primary" viewBox="0 0 24 24" fill="currentColor">
														<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" />
													</svg>
												{:else}
													<FileText class="h-4 w-4 text-muted-foreground" />
												{/if}
											</td>
											<td class="p-3">
												<div class="font-medium line-clamp-1">{paper.title}</div>
												{#if paper.subject && paper.subject.length > 0}
													<div class="mt-0.5 flex gap-1">
														{#each paper.subject.slice(0, 2) as tag}
															<button
																class="text-xs bg-primary/10 text-primary px-1.5 py-0.5 rounded hover:bg-primary/20 transition-colors"
																onclick={(e) => {
																	e.stopPropagation();
																	import('$lib/stores/tabs').then(({ openTagProfile }) => {
																		const _papers = $papers.filter(p => p.subject.includes(tag));
																		openTagProfile({
																			name: tag,
																			color: '#6366f1',
																			paperCount: _papers.length,
																			papers: _papers,
																			relatedTags: []
																		});
																	});
																}}
															>{tag}</button>
														{/each}
													</div>
												{/if}
											</td>
											<td class="p-3 text-muted-foreground">
												{paper.authors?.[0] ?? 'Unknown'}
												{#if (paper.authors?.length ?? 0) > 1}
													<span class="text-xs">+{(paper.authors?.length ?? 1) - 1}</span>
												{/if}
											</td>
											<td class="p-3 hidden md:table-cell">
												{#if paper.conference}
													{@const venueInfo = formatVenue(paper.conference)}
													<button
														class="rounded bg-muted px-2 py-0.5 text-xs hover:bg-muted/80 transition-colors"
														title={venueInfo.full ?? undefined}
														onclick={(e) => {
															e.stopPropagation();
															import('$lib/stores/tabs').then(({ openJournalProfile }) => {
																const conferencePapers = $papers.filter(p => p.conference === paper.conference);
																const years = conferencePapers.map(p => p.year).filter((y): y is number => y !== null);
																openJournalProfile({
																	name: venueInfo.full ?? paper.conference ?? '',
																	abbreviation: venueInfo.display ?? null,
																	paperCount: conferencePapers.length,
																	papers: conferencePapers,
																	years
																});
															});
														}}
													>{venueInfo.display}</button>
												{:else}
													<span class="text-muted-foreground">-</span>
												{/if}
											</td>
											<td class="p-3">{paper.year ?? '-'}</td>
											<td class="p-3">
												{#if paper.citations}
													<span class="text-muted-foreground">{paper.citations}</span>
												{:else}
													<span class="text-muted-foreground">-</span>
												{/if}
											</td>
											<td class="p-3">
												<button
													class="rounded p-1 text-muted-foreground opacity-0 transition-opacity hover:bg-destructive hover:text-destructive-foreground group-hover:opacity-100"
													onclick={(e) => {
														e.stopPropagation();
														paperToDelete = paper;
													}}
													title="Delete paper"
												>
													<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
														<path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
													</svg>
												</button>
											</td>
										</tr>
									{/each}
								</tbody>
							</table>
						</div>
					{:else}
						<!-- Paper grid/list -->
						<div
							class={$viewStyle === 'grid'
								? 'grid grid-cols-3 gap-4 md:grid-cols-4 lg:grid-cols-5'
								: 'flex flex-col gap-3'}
						>
						{#each $filteredPapers as paper (paper.id)}
							{#if $viewStyle === 'grid'}
								<!-- Grid view card with thumbnail -->
								<div
									class="group relative cursor-pointer rounded-lg border bg-card overflow-hidden transition-shadow hover:shadow-md"
									onclick={() => {
										import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
									}}
								>
									<!-- Thumbnail -->
									{#if paper.thumbnailUrl}
										<div class="relative h-32 w-full bg-muted overflow-hidden">
											<img
												src={api.getThumbnailUrl(paper.id)}
												alt=""
												class="h-full w-full object-cover"
												onerror={(e) => {
													const target = e.currentTarget as HTMLImageElement;
													target.style.display = 'none';
												}}
											/>
										</div>
									{:else if paper.pdfUrl}
										<!-- Placeholder for papers with PDF but no thumbnail yet -->
										<div class="relative h-32 w-full bg-gradient-to-br from-neutral-100 to-neutral-200 dark:from-neutral-800 dark:to-neutral-700 flex items-center justify-center">
											<svg class="h-12 w-12 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
												<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
											</svg>
										</div>
									{/if}
									<!-- Content -->
									<div class="p-4">
										<!-- Delete button (appears on hover) -->
										<button
											class="absolute right-2 top-2 rounded p-1 text-muted-foreground bg-background/80 opacity-0 transition-opacity hover:bg-destructive hover:text-destructive-foreground group-hover:opacity-100"
											onclick={(e) => {
												e.stopPropagation();
												paperToDelete = paper;
											}}
											title="Delete paper"
										>
											<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
											</svg>
										</button>
										<h3 class="mb-2 line-clamp-2 pr-6 font-semibold">{paper.title}</h3>
										<p class="mb-2 text-sm text-muted-foreground">
											{#each paper.authors.slice(0, 3) as author, i}
												<button
													class="hover:text-primary hover:underline"
													onclick={(e) => {
														e.stopPropagation();
														openAuthorModal(author);
													}}
												>
													{author}
												</button>{#if i < Math.min(paper.authors.length, 3) - 1}, {/if}
											{/each}
											{#if paper.authors.length > 3}
												<span> +{paper.authors.length - 3}</span>
											{/if}
										</p>
										<div class="flex items-center gap-2 text-xs text-muted-foreground">
											{#if paper.year}
												<span>{paper.year}</span>
											{/if}
											{#if paper.conference}
												{@const venueInfo = formatVenue(paper.conference)}
												<button
													class="rounded bg-muted px-2 py-0.5 hover:bg-muted/80 transition-colors"
													title={venueInfo.full ?? undefined}
													onclick={(e) => {
														e.stopPropagation();
														import('$lib/stores/tabs').then(({ openJournalProfile }) => {
															const conferencePapers = $papers.filter(p => p.conference === paper.conference);
															const years = conferencePapers.map(p => p.year).filter((y): y is number => y !== null);
															openJournalProfile({
																name: venueInfo.full ?? paper.conference ?? '',
																abbreviation: venueInfo.display ?? null,
																paperCount: conferencePapers.length,
																papers: conferencePapers,
																years
															});
														});
													}}
												>
													{venueInfo.display}
												</button>
											{/if}
											{#if paper.pdfUrl}
												<span class="ml-auto text-primary">PDF</span>
											{:else}
												<span class="ml-auto">{paper.citations} citations</span>
											{/if}
										</div>
										{#if paper.subject.length > 0}
											<div class="mt-2 flex flex-wrap gap-1">
												{#each paper.subject.slice(0, 3) as tag}
													<button
														class="rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary hover:bg-primary/20 transition-colors"
														onclick={(e) => {
															e.stopPropagation();
															import('$lib/stores/tabs').then(({ openTagProfile }) => {
																const tagPapers = $papers.filter(p => p.subject.includes(tag));
																openTagProfile({
																	name: tag,
																	color: '#6366f1',
																	paperCount: tagPapers.length,
																	papers: tagPapers,
																	relatedTags: []
																});
															});
														}}
													>
														{tag}
													</button>
												{/each}
											</div>
										{/if}
									</div>
								</div>
							{:else if $viewStyle === 'list'}
								<!-- List view with small thumbnail -->
								<div
									class="group flex cursor-pointer items-center gap-4 rounded-lg border bg-card p-3 transition-shadow hover:shadow-md"
									onclick={() => {
										import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
									}}
								>
									<!-- Small thumbnail -->
									<div class="shrink-0 h-20 w-14 rounded overflow-hidden bg-muted">
										{#if paper.thumbnailUrl}
											<img
												src={api.getThumbnailUrl(paper.id)}
												alt=""
												class="h-full w-full object-cover"
												onerror={(e) => {
													const target = e.currentTarget as HTMLImageElement;
													target.style.display = 'none';
												}}
											/>
										{:else}
											<div class="h-full w-full flex items-center justify-center bg-gradient-to-br from-neutral-100 to-neutral-200 dark:from-neutral-800 dark:to-neutral-700">
												<svg class="h-6 w-6 text-neutral-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
													<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
												</svg>
											</div>
										{/if}
									</div>
									<div class="flex-1 min-w-0">
										<h3 class="font-semibold truncate">{paper.title}</h3>
										<p class="mt-0.5 text-sm text-muted-foreground truncate">
											{paper.authors.join(', ')}
										</p>
										{#if paper.abstract}
											<p class="mt-1 text-xs text-muted-foreground/70 line-clamp-2">
												{paper.abstract}
											</p>
										{/if}
									</div>
									<div class="flex items-center gap-4 text-sm text-muted-foreground shrink-0">
										{#if paper.year}
											<span>{paper.year}</span>
										{/if}
										{#if paper.conference}
											{@const venueInfo = formatVenue(paper.conference)}
											<button
												class="rounded bg-muted px-2 py-0.5 hover:bg-muted/80 transition-colors"
												title={venueInfo.full ?? undefined}
												onclick={(e) => {
													e.stopPropagation();
													import('$lib/stores/tabs').then(({ openJournalProfile }) => {
														const conferencePapers = $papers.filter(p => p.conference === paper.conference);
														const years = conferencePapers.map(p => p.year).filter((y): y is number => y !== null);
														openJournalProfile({
															name: venueInfo.full ?? paper.conference ?? '',
															abbreviation: venueInfo.display ?? null,
															paperCount: conferencePapers.length,
															papers: conferencePapers,
															years
														});
													});
												}}
											>{venueInfo.display}</button>
										{/if}
										{#if paper.pdfUrl}
											<span class="text-primary">PDF</span>
										{:else}
											<span>{paper.citations} citations</span>
										{/if}
										<!-- Delete button -->
										<button
											class="rounded p-1 text-muted-foreground opacity-0 transition-opacity hover:bg-destructive hover:text-destructive-foreground group-hover:opacity-100"
											onclick={(e) => {
												e.stopPropagation();
												paperToDelete = paper;
											}}
											title="Delete paper"
										>
											<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
												<path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
											</svg>
										</button>
									</div>
								</div>
							{/if}
						{/each}
						</div>
					{/if}

					<!-- Online Search Results Section -->
					{#if $searchQuery.length >= 3 && $filteredPapers.length < 5}
						<div class="mt-8">
							<div class="mb-4 flex items-center justify-between">
								<h2 class="text-lg font-medium">Available to Download</h2>
								{#if $isSearchingOnline}
									<div class="flex items-center gap-2 text-sm text-muted-foreground">
										<div class="h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
										Searching online...
									</div>
								{/if}
							</div>
							
							{#if $onlineSearchResults.length > 0}
								<div class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
									{#each $onlineSearchResults.slice(0, 6) as result}
										<div class="rounded-lg border border-dashed border-primary/30 bg-primary/5 p-4">
											<h3 class="mb-2 line-clamp-2 font-medium text-sm">{result.title}</h3>
											<p class="mb-2 text-xs text-muted-foreground">
												{result.authors.slice(0, 2).join(', ')}
												{result.authors.length > 2 ? ` +${result.authors.length - 2}` : ''}
											</p>
											<div class="flex items-center gap-2 text-xs text-muted-foreground">
												{#if result.year}
													<span>{result.year}</span>
												{/if}
												{#if result.venue}
													<span class="rounded bg-muted px-1.5 py-0.5">{result.venue}</span>
												{/if}
												{#if result.citationCount}
													<span>{result.citationCount} citations</span>
												{/if}
											</div>
											<div class="mt-3 flex items-center gap-2">
												<button
													class="flex-1 rounded bg-primary px-3 py-1.5 text-xs text-primary-foreground hover:bg-primary/90"
													onclick={async () => {
														const paper = await importFromSearchResult(result);
														if (paper) {
															toast.success(`Added "${paper.title}" to library`);
														} else {
															toast.error('Failed to import paper');
														}
													}}
												>
													Add to Library
												</button>
												{#if result.pdfUrl}
													<a
														href={result.pdfUrl}
														target="_blank"
														rel="noopener noreferrer"
														class="rounded border px-3 py-1.5 text-xs hover:bg-muted"
														onclick={(e) => e.stopPropagation()}
													>
														PDF
													</a>
												{/if}
											</div>
										</div>
									{/each}
								</div>
							{:else if !$isSearchingOnline}
								<p class="text-sm text-muted-foreground">No online results found for "{$searchQuery}"</p>
							{/if}
						</div>
					{/if}
				{/if}
			</div>
		</div>

		<!-- PDF Viewer tabs - render all, hide inactive -->
		{#each $tabs.filter(t => t.type === 'viewer') as tab (tab.id)}
			<div class="flex h-full {$activeTabId === tab.id ? '' : 'hidden'}">
				<!-- PDF Viewer -->
				<div class="flex flex-1 flex-col">
					<!-- Paper info bar with action buttons -->
					<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-2">
						<div class="flex items-center gap-2 min-w-0 flex-1">
							<h2 class="truncate text-sm font-medium">{tab.paper?.title}</h2>
							{#if tab.paper?.year}
								<span class="shrink-0 text-xs text-muted-foreground">({tab.paper.year})</span>
							{/if}
						</div>
						<div class="flex items-center gap-1 shrink-0">
							<!-- Unified Analyze button (metadata + references + auto-tag) -->
							<button
								class="rounded px-2 py-1 text-xs text-muted-foreground hover:bg-muted hover:text-foreground"
								onclick={async () => {
									if (!tab.paper?.id) return;
									const jobId = await reanalyzePaper(tab.paper.id);
									if (jobId) {
										toast.info('Analysis started (metadata, references, tags). Check progress in the task panel.');
									} else {
										toast.error('Failed to start analysis');
									}
								}}
								title="Analyze PDF: update metadata, extract references, auto-generate tags"
							>
								<svg class="h-4 w-4 inline mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
									<path d="M1 4v6h6M23 20v-6h-6" />
									<path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" />
								</svg>
								Analyze
							</button>
						</div>
					</div>

					{#if tab.paper?.pdfUrl && PdfViewer}
						<svelte:component
							this={PdfViewer}
							pdfUrl={tab.paper.pdfUrl}
							paperId={tab.paper.id}
							tabId={tab.id}
							initialState={tab.viewerState}
							onOpenPaper={openPaperById}
						/>
					{:else if tab.paper?.pdfUrl && !PdfViewer}
						<div class="flex flex-1 items-center justify-center bg-muted/20">
							<div class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
						</div>
					{:else}
						<div class="flex flex-1 flex-col items-center justify-center bg-muted/20">
							<svg class="mb-4 h-16 w-16 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
								<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
							</svg>
							<h2 class="mb-2 text-xl font-semibold">{tab.paper?.title}</h2>
							<p class="text-muted-foreground">No PDF file attached</p>
							{#if tab.paper?.abstract}
								<p class="mt-4 max-w-2xl text-center text-sm text-muted-foreground">
									{tab.paper.abstract}
								</p>
							{/if}
							<div class="mt-4 flex gap-2">
								<button
									class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
									onclick={() => handleDownloadPdf(tab.paper?.id ?? '')}
									disabled={isDownloadingPdf && downloadingPaperId === tab.paper?.id}
								>
									{isDownloadingPdf && downloadingPaperId === tab.paper?.id ? 'Downloading...' : 'Download PDF'}
								</button>
								<button
									class="rounded-md border bg-background px-4 py-2 text-sm hover:bg-muted"
									onclick={() => {
										toast.info('PDF upload for existing papers coming soon');
									}}
								>
									Upload PDF
								</button>
							</div>
						</div>
					{/if}
				</div>
			</div>

			<!-- Floating Chat Panel (rendered outside main container) -->
			{#if $isChatPanelOpen}
				<FloatingChatPanel
					paper={tab.paper ?? null}
					onClose={() => toggleChatPanel()}
				/>
			{/if}
		{/each}

		<!-- Author profile tabs -->
		{#each $tabs.filter(t => t.type === 'author') as tab (tab.id)}
			<div class="h-full {$activeTabId === tab.id ? '' : 'hidden'}">
				{#if tab.author}
					<AuthorProfileView author={tab.author} />
				{/if}
			</div>
		{/each}

		<!-- Notes list tab -->
		{#if $activeTab?.type === 'notes'}
			<NoteList
				onSelectNote={(note) => openNote(note)}
				onCreateNote={async () => {
					const title = prompt('Note title:');
					if (!title) return;
					const note = await createNote(title, '', null);
					if (note) openNote(note);
				}}
			/>
		{/if}

		<!-- Note viewer tabs -->
		{#each $tabs.filter(t => t.type === 'note-viewer') as tab (tab.id)}
			<div class="h-full {$activeTabId === tab.id ? '' : 'hidden'}">
				{#if tab.note}
					<NoteViewer
						noteId={tab.note.id}
						onBack={() => openNotesList()}
						onNavigateToNote={async (id) => {
							const res = await api.getNote(id);
							if (res.success && res.data) {
								openNote(res.data.note);
							}
						}}
						onNavigateToPaper={async (id) => {
							const res = await api.getPaper(id);
							if (res.success && res.data) {
								openPaper(res.data as Paper);
							}
						}}
					/>
				{/if}
			</div>
		{/each}

		<!-- Tag profile tabs -->
		{#each $tabs.filter(t => t.type === 'tag') as tab (tab.id)}
			<div class="h-full overflow-auto p-6 {$activeTabId === tab.id ? '' : 'hidden'}">
				{#if tab.tag}
					<div class="mb-6">
						<div class="flex items-center gap-3 mb-2">
							<Tag class="h-6 w-6 text-primary" />
							<h1 class="text-2xl font-bold">{tab.tag.name}</h1>
							<span class="rounded-full bg-muted px-3 py-1 text-sm text-muted-foreground">
								{tab.tag.paperCount} papers
							</span>
						</div>
					</div>

					<!-- Papers with this tag -->
					<div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
						{#each tab.tag.papers as paper (paper.id)}
							<div
								class="cursor-pointer rounded-lg border bg-card p-4 transition-shadow hover:shadow-md"
								onclick={() => {
									import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
								}}
							>
								<h3 class="font-medium line-clamp-2">{paper.title}</h3>
								<p class="mt-1 text-sm text-muted-foreground line-clamp-1">
									{paper.authors.join(', ')}
								</p>
								<div class="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
									{#if paper.year}
										<span>{paper.year}</span>
									{/if}
									{#if paper.conference}
										<span class="rounded bg-muted px-1.5 py-0.5">{paper.conference}</span>
									{/if}
								</div>
							</div>
						{/each}
					</div>

					{#if tab.tag.papers.length === 0}
						<div class="flex flex-col items-center justify-center py-12 text-muted-foreground">
							<Tag class="h-12 w-12 mb-4" />
							<p>No papers with this tag yet</p>
						</div>
					{/if}
				{/if}
			</div>
		{/each}

		<!-- Journal/Conference profile tabs -->
		{#each $tabs.filter(t => t.type === 'journal') as tab (tab.id)}
			<div class="h-full overflow-auto p-6 {$activeTabId === tab.id ? '' : 'hidden'}">
				{#if tab.journal}
					<div class="mb-6">
						<div class="flex items-center gap-3 mb-2">
							<Building2 class="h-6 w-6 text-primary" />
							<div>
								<h1 class="text-2xl font-bold">{tab.journal.name}</h1>
								{#if tab.journal.abbreviation && tab.journal.abbreviation !== tab.journal.name}
									<p class="text-sm text-muted-foreground">{tab.journal.abbreviation}</p>
								{/if}
							</div>
							<span class="rounded-full bg-muted px-3 py-1 text-sm text-muted-foreground">
								{tab.journal.paperCount} papers
							</span>
						</div>
						{#if tab.journal.years.length > 0}
							<p class="text-sm text-muted-foreground">
								Years: {Math.min(...tab.journal.years)} - {Math.max(...tab.journal.years)}
							</p>
						{/if}
					</div>

					<!-- Papers from this venue -->
					<div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
						{#each tab.journal.papers as paper (paper.id)}
							<div
								class="cursor-pointer rounded-lg border bg-card p-4 transition-shadow hover:shadow-md"
								onclick={() => {
									import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
								}}
							>
								<h3 class="font-medium line-clamp-2">{paper.title}</h3>
								<p class="mt-1 text-sm text-muted-foreground line-clamp-1">
									{paper.authors.join(', ')}
								</p>
								<div class="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
									{#if paper.year}
										<span>{paper.year}</span>
									{/if}
									{#if paper.citations}
										<span>{paper.citations} citations</span>
									{/if}
								</div>
							</div>
						{/each}
					</div>

					{#if tab.journal.papers.length === 0}
						<div class="flex flex-col items-center justify-center py-12 text-muted-foreground">
							<Building2 class="h-12 w-12 mb-4" />
							<p>No papers from this venue yet</p>
						</div>
					{/if}
				{/if}
			</div>
		{/each}

		<!-- Settings tab -->
		<div class="h-full flex flex-col {$activeTab?.type === 'settings' ? '' : 'hidden'}">
			<div class="border-b bg-muted/30 px-6 py-4">
				<h1 class="mb-4 text-2xl font-bold">Settings</h1>

				<!-- Settings Tabs -->
				<div class="flex gap-1">
					<button
						class="rounded-t-md px-4 py-2 text-sm font-medium transition-colors {settingsActiveTab === 'llm'
							? 'bg-background text-foreground border-b-2 border-primary'
							: 'text-muted-foreground hover:text-foreground hover:bg-muted/50'}"
						onclick={() => (settingsActiveTab = 'llm')}
					>
						LLM & API
					</button>
					<button
						class="rounded-t-md px-4 py-2 text-sm font-medium transition-colors {settingsActiveTab === 'search'
							? 'bg-background text-foreground border-b-2 border-primary'
							: 'text-muted-foreground hover:text-foreground hover:bg-muted/50'}"
						onclick={() => (settingsActiveTab = 'search')}
					>
						Search Engines
					</button>
					<button
						class="rounded-t-md px-4 py-2 text-sm font-medium transition-colors {settingsActiveTab === 'system'
							? 'bg-background text-foreground border-b-2 border-primary'
							: 'text-muted-foreground hover:text-foreground hover:bg-muted/50'}"
						onclick={() => (settingsActiveTab = 'system')}
					>
						System
					</button>
				</div>
			</div>

			<!-- Settings Tab Content -->
			<div class="flex-1 overflow-auto p-6">
				<!-- LLM & API Tab -->
				{#if settingsActiveTab === 'llm'}
					<!-- LLM Configuration -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">LLM Configuration</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<div>
						<label for="api-key" class="mb-2 block text-sm font-medium">API Key</label>
						<input
							id="api-key"
							type="password"
							placeholder={settings.llmApiKey ? 'API key is set (enter new to change)' : 'Enter your POSTECH GenAI API key'}
							bind:value={newApiKey}
							class="w-full rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
						/>
						{#if settings.llmApiKey}
							<p class="mt-1 text-xs text-muted-foreground">
								Current key: {settings.llmApiKey}
							</p>
						{:else}
							<p class="mt-1 text-xs text-muted-foreground">
								Get your API key from POSTECH GenAI Portal
							</p>
						{/if}
					</div>

					<div>
						<label for="model" class="mb-2 block text-sm font-medium">Model</label>
						<select
							id="model"
							bind:value={settings.llmProvider}
							class="w-full rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
						>
							<option value="gpt">GPT</option>
							<option value="gemini">Gemini</option>
							<option value="claude">Claude</option>
						</select>
					</div>

					<button
						class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
						disabled={isSavingSettings}
						onclick={saveSettings}
					>
						{isSavingSettings ? 'Saving...' : 'Save Settings'}
					</button>
				</div>
			</section>

			<!-- POSTECH SSO Authentication -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">POSTECH SSO Authentication</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<p class="text-sm text-muted-foreground">
						Authenticate with POSTECH SSO to enable file attachments in chat. Files will be uploaded directly to the GenAI server for better analysis.
					</p>

					{#if settings.ssoConfigured}
						<div class="flex items-center gap-2 rounded-md bg-green-50 px-3 py-2 text-sm text-green-700 dark:bg-green-900/20 dark:text-green-400">
							<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M5 13l4 4L19 7" />
							</svg>
							<span>SSO Connected</span>
							{#if settings.ssoTokenExpiresAt}
								<span class="text-xs opacity-75">
									(expires {new Date(settings.ssoTokenExpiresAt).toLocaleDateString()})
								</span>
							{/if}
						</div>
					{:else}
						<div class="flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700 dark:bg-amber-900/20 dark:text-amber-400">
							<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
							</svg>
							<span>Not connected - file attachments disabled</span>
						</div>
					{/if}

					<div>
						<label for="sso-token" class="mb-2 block text-sm font-medium">Access Token</label>
						<input
							id="sso-token"
							type="password"
							placeholder="Paste your SSO access token here"
							bind:value={ssoAccessToken}
							class="w-full rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
						/>
						<p class="mt-1 text-xs text-muted-foreground">
							Get token from GenAI callback URL after SSO login (look for <code class="rounded bg-muted px-1">access_token=...</code> in the URL fragment)
						</p>
					</div>

					<div>
						<label for="sso-site-name" class="mb-2 block text-sm font-medium">Site Name</label>
						<input
							id="sso-site-name"
							type="text"
							placeholder="robi-gpt-dev"
							bind:value={ssoSiteName}
							class="w-full rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
						/>
						<p class="mt-1 text-xs text-muted-foreground">
							Site name for file upload API (default: robi-gpt-dev)
						</p>
					</div>

					<div class="flex gap-2">
						<button
							class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
							disabled={isSavingSSO}
							onclick={handleSSOLogin}
						>
							{isSavingSSO ? 'Logging in...' : 'Login with SSO'}
						</button>
						<button
							class="rounded-md border bg-background px-4 py-2 text-sm hover:bg-muted disabled:opacity-50"
							disabled={isSavingSSO || !ssoAccessToken.trim()}
							onclick={saveSSO}
						>
							{isSavingSSO ? 'Saving...' : 'Save Token Manually'}
						</button>
					</div>
					<p class="text-xs text-muted-foreground">
						<strong>Tip:</strong> "Login with SSO" will open a popup window and automatically extract the token (Electron) or redirect to SSO login (browser).
					</p>
				</div>
			</section>

			<!-- LLM Usage Log -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">LLM Usage</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<p class="text-sm text-muted-foreground">
						View LLM API usage logs for debugging and monitoring.
					</p>
					<button
						class="rounded-md border px-4 py-2 text-sm hover:bg-muted"
						onclick={() => (showLLMLogPanel = true)}
					>
						View LLM Logs
					</button>
				</div>
			</section>
				{/if}

				<!-- Search Engines Tab -->
				{#if settingsActiveTab === 'search'}
					<!-- Academic Search APIs -->
					<SettingsPanel />

					<!-- Semantic Scholar API -->
					<section class="mb-8 mt-8">
						<h2 class="mb-4 text-lg font-semibold">Semantic Scholar API</h2>
						<div class="space-y-4 rounded-lg border bg-card p-4">
							<div>
								<label for="semantic-scholar-api-key" class="mb-2 block text-sm font-medium">API Key (Optional)</label>
								<input
									id="semantic-scholar-api-key"
									type="password"
									placeholder={settings.semanticScholarApiKey ? 'API key is set (enter new to change)' : 'Enter your Semantic Scholar API key'}
									bind:value={newSemanticScholarApiKey}
									class="w-full rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
								/>
								{#if settings.semanticScholarApiKey}
									<p class="mt-1 text-xs text-muted-foreground">
										Current key: {settings.semanticScholarApiKey}
									</p>
								{:else}
									<p class="mt-1 text-xs text-muted-foreground">
										Without an API key, rate limits are stricter (100 req/5 min). Get a free key from
										<a href="https://www.semanticscholar.org/product/api" target="_blank" rel="noopener" class="text-primary hover:underline">
											Semantic Scholar API
										</a>
										for higher limits.
									</p>
								{/if}
							</div>

							<button
								class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
								disabled={isSavingSettings}
								onclick={saveSettings}
							>
								{isSavingSettings ? 'Saving...' : 'Save Settings'}
							</button>
						</div>
					</section>

					<!-- PDF Download Options -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">PDF Download Options</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<p class="text-sm text-muted-foreground">
						Configure which sources to use when downloading PDFs from online search results.
					</p>

					<div class="flex items-center justify-between rounded-lg border bg-background p-4">
						<div class="flex-1">
							<p class="font-medium">Enable Sci-Hub</p>
							<p class="mt-1 text-sm text-muted-foreground">
								Use Sci-Hub as a fallback for finding PDFs. Legal gray area - use at your own risk.
							</p>
						</div>
						<label class="relative inline-flex cursor-pointer items-center">
							<input
								type="checkbox"
								class="peer sr-only"
								checked={settings.enableSciHub ?? false}
								onchange={(e) => {
									settings = { ...settings, enableSciHub: e.currentTarget.checked };
								}}
							/>
							<div class="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary peer-focus:ring-offset-2 dark:border-gray-600 dark:bg-gray-700"></div>
						</label>
					</div>

					<div class="text-xs text-muted-foreground">
						<p class="font-medium mb-1">Download priority order:</p>
						<ol class="list-decimal list-inside space-y-0.5">
							<li>arXiv (if available)</li>
							<li>Direct URL from search results</li>
							<li>Unpaywall (legal open access)</li>
							<li>Semantic Scholar</li>
							<li>Sci-Hub (if enabled above)</li>
						</ol>
					</div>

					<button
						class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
						onclick={saveSettings}
					>
						Save Settings
					</button>
				</div>
			</section>
				{/if}

				<!-- System Tab -->
				{#if settingsActiveTab === 'system'}
					<!-- Storage -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">Storage</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<div>
						<label for="pdf-path" class="mb-2 block text-sm font-medium">PDF Storage Path</label>
						<div class="flex gap-2">
							<input
								id="pdf-path"
								type="text"
								placeholder="~/.potero/pdfs"
								value={settings.pdfStoragePath ?? '~/.potero/pdfs'}
								class="flex-1 rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
								readonly
							/>
							<button class="rounded-md border px-4 py-2 text-sm hover:bg-muted">Browse</button>
						</div>
					</div>

					<div>
						<p class="text-sm font-medium">Database</p>
						<p class="mt-1 text-sm text-muted-foreground">~/.potero/potero.db</p>
					</div>
				</div>
			</section>

			<!-- Sync (Future) -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">Cloud Sync</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<p class="text-sm text-muted-foreground">
						Cloud sync with Google Drive and OneDrive coming soon.
					</p>
					<button
						class="rounded-md border px-4 py-2 text-sm hover:bg-muted disabled:opacity-50"
						disabled
					>
						Connect Google Drive
					</button>
				</div>
			</section>

			<!-- Library Maintenance -->
			<section class="mb-8">
				<h2 class="mb-4 text-lg font-semibold">Library Maintenance</h2>
				<div class="space-y-4 rounded-lg border bg-card p-4">
					<p class="text-sm text-muted-foreground">
						Re-analyze all papers to update metadata, generate thumbnails, extract references, and auto-generate tags.
					</p>
					<div class="flex flex-wrap gap-2">
						<button
							class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
							disabled={isBulkReanalyzing}
							onclick={handleBulkReanalyzeAll}
						>
							{#if isBulkReanalyzing}
								<span class="flex items-center gap-2">
									<svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20" />
									</svg>
									Analyzing...
								</span>
							{:else}
								Analyze All Papers
							{/if}
						</button>
						<button
							class="rounded-md border px-4 py-2 text-sm hover:bg-muted disabled:opacity-50"
							disabled={isBulkReanalyzing}
							onclick={handleBulkReanalyzeMissing}
						>
							Analyze Missing Only
						</button>
					</div>
					<p class="text-xs text-muted-foreground">
						This will run in the background. Check progress in the task panel.
					</p>
				</div>
			</section>

			<!-- About -->
			<section>
				<h2 class="mb-4 text-lg font-semibold">About</h2>
				<div class="rounded-lg border bg-card p-4">
					<p class="font-medium">Potero</p>
					<p class="mt-1 text-sm text-muted-foreground">
						Serverless Research Reference Manager
					</p>
					<p class="mt-2 text-xs text-muted-foreground">Version 0.1.0 (Development)</p>
				</div>
			</section>
				{/if}
			</div>
		</div>
	</div>
</div>

<!-- Search Results Dialog for metadata confirmation -->
{#if $pendingUploadAnalysis}
	<SearchResultsDialog
		paperId={$pendingUploadAnalysis.paperId}
		searchQuery={$pendingUploadAnalysis.searchQuery}
		results={$pendingUploadAnalysis.searchResults}
		onConfirm={async () => {
			clearPendingUploadAnalysis();
			await loadPapers();
		}}
		onCancel={() => {
			clearPendingUploadAnalysis();
		}}
	/>
{/if}

<!-- Job Status Panel (bottom right, like Google Drive) -->
<JobStatusPanel />

<!-- Delete Confirmation Dialog -->
{#if paperToDelete}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
		<div class="w-full max-w-md rounded-lg bg-background p-6 shadow-xl">
			<h2 class="mb-2 text-lg font-semibold">Delete Paper</h2>
			<p class="mb-4 text-sm text-muted-foreground">
				Are you sure you want to delete <span class="font-medium text-foreground">"{paperToDelete.title}"</span>?
			</p>
			<p class="mb-6 text-xs text-destructive">
				This will permanently remove the paper and its PDF file from your library. This action cannot be undone.
			</p>
			<div class="flex justify-end gap-2">
				<button
					class="rounded-md px-4 py-2 text-sm hover:bg-muted"
					onclick={() => (paperToDelete = null)}
					disabled={isDeleting}
				>
					Cancel
				</button>
				<button
					class="rounded-md bg-destructive px-4 py-2 text-sm text-destructive-foreground hover:bg-destructive/90 disabled:opacity-50"
					disabled={isDeleting}
					onclick={handleDeletePaper}
				>
					{isDeleting ? 'Deleting...' : 'Delete'}
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- LLM Log Panel Modal -->
{#if showLLMLogPanel}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
		<div class="relative h-[80vh] w-full max-w-4xl rounded-lg bg-background shadow-xl overflow-hidden">
			<button
				class="absolute right-4 top-4 z-10 rounded p-1 hover:bg-muted"
				onclick={() => (showLLMLogPanel = false)}
			>
				<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M18 6L6 18M6 6l12 12" />
				</svg>
			</button>
			<LLMLogPanel />
		</div>
	</div>
{/if}

<!-- Author Modal -->
{#if selectedAuthorName}
	<AuthorModal
		authorName={selectedAuthorName}
		papers={selectedAuthorPapers}
		onClose={closeAuthorModal}
	/>
{/if}

<!-- Floating Note Panel -->
{#if $isNotePanelOpen}
	<FloatingNotePanel
		paperId={$notePanelPaperId}
		initialNoteId={$notePanelNoteId}
		onClose={closeNotePanel}
	/>
{/if}
