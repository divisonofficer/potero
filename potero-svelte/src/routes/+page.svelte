<script lang="ts">
	import { onMount } from 'svelte';
	import { get } from 'svelte/store';
	import {
		papers,
		filteredPapers as libraryFilteredPapers,
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
		reextractPaper,
		deletePaper,
		onlineSearchResults,
		isSearchingOnline,
		searchOnlineIfNeeded,
		importFromSearchResult
	} from '$lib/stores/library';
	import {
		appState,
		filteredPapers,
		selectedPaper,
		sidebarData,
		selectSource,
		selectPaper,
		clearPaperSelection,
		setViewMode,
		setSortBy,
		toggleSortDirection,
		setSearchQuery,
		toggleInspector,
		toggleSidebar,
		openViewer,
		closeViewer,
		type SourceType
	} from '$lib/stores/appState';
	import { tabs, activeTab, activeTabId, openPaper, closeTab, goHome, openSettings, openNotesList, openNote, openRelatedWork, openSubmissionsList, openSubmissionWorkflow, openTagProfile, openJournalProfile, openAuthorProfile, isChatPanelOpen, toggleChatPanel, isNotePanelOpen, notePanelPaperId, notePanelNoteId, closeNotePanel } from '$lib/stores/tabs';
	import { api, type Settings } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';
	import type { Paper, ResearchNote, TagProfile, AuthorProfile, JournalProfile } from '$lib/types';
	import { createNote } from '$lib/stores/notes';
	import { browser } from '$app/environment';
	import { MainLayout, SourcesSidebar, PaperBrowser, InspectorPanel, StatusBar, type LibraryFilter } from '$lib/components/layout';
	import FloatingChatPanel from '$lib/components/chat/FloatingChatPanel.svelte';
	import FloatingNotePanel from '$lib/components/notes/FloatingNotePanel.svelte';
	import FloatingSearchModal from '$lib/components/FloatingSearchModal.svelte';
	import SearchResultsDialog from '$lib/components/SearchResultsDialog.svelte';
	import { OnboardingWizard } from '$lib/components/onboarding';
	import { checkOnboardingRequired, startOnboarding } from '$lib/stores/onboarding';
	import JobStatusPanel from '$lib/components/JobStatusPanel.svelte';
	import LLMLogPanel from '$lib/components/LLMLogPanel.svelte';
	import AuthorModal from '$lib/components/AuthorModal.svelte';
	import AuthorProfileView from '$lib/components/AuthorProfileView.svelte';
	import TagProfileView from '$lib/components/TagProfileView.svelte';
	import JournalProfileView from '$lib/components/JournalProfileView.svelte';
	import SettingsPanel from '$lib/components/SettingsPanel.svelte';
	import NoteList from '$lib/components/notes/NoteList.svelte';
	import NoteViewer from '$lib/components/notes/NoteViewer.svelte';
	import RelatedWorkView from '$lib/components/relatedWork/RelatedWorkView.svelte';
	import { SubmissionDashboard, SubmissionsList } from '$lib/components/submission';
	import { formatVenue } from '$lib/utils/venueAbbreviation';
	import { Network, ChevronDown, X, Home, FileText, Tag, User, BookOpen, StickyNote, FolderOpen } from 'lucide-svelte';

	// Onboarding state
	let showOnboarding = $state(false);

	// LLM log panel state
	let showLLMLogPanel = $state(false);

	// Library filter for home tab (All, Recent, Favorites, Unread)
	let libraryFilter = $state<LibraryFilter>('all');

	// Floating search modal state
	let showFloatingSearch = $state(false);

	// Settings panel state
	let showSettings = $state(false);
	let settingsActiveTab = $state<'llm' | 'search' | 'system'>('llm');

	// Analyze dropdown state (per paper)
	let analyzeDropdownOpen = $state<Record<string, boolean>>({});

	// Preprocessing status cache (per paper)
	let preprocessingStatusCache = $state<Record<string, { hasCache: boolean; status: string | null }>>({});

	// Author modal state
	let selectedAuthorName = $state<string | null>(null);
	let selectedAuthorPapers = $state<Paper[]>([]);

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

	// Dynamic import for PDF viewer (client-side only due to pdfjs)
	let PdfViewer: typeof import('$lib/components/PdfViewer.svelte').default | null = $state(null);

	// Current viewing paper (derived from activeTab)
	let viewingPaper = $derived.by(() => {
		const tab = $activeTab;
		if (tab?.type === 'viewer' && tab.paper) {
			return tab.paper;
		}
		return null;
	});

	// Filtered papers based on libraryFilter
	let homeFilteredPapers = $derived.by(() => {
		const allFilteredPapers = get(filteredPapers);
		switch (libraryFilter) {
			case 'recent':
				return allFilteredPapers.slice(0, 30);
			case 'favorites':
				return allFilteredPapers.filter(p => p.favorite);
			case 'unread':
				return allFilteredPapers.filter(p => !p.read);
			case 'all':
			default:
				return allFilteredPapers;
		}
	});

	// Tab icon mapping
	function getTabIcon(type: string) {
		switch (type) {
			case 'home': return Home;
			case 'viewer': return FileText;
			case 'tag': return Tag;
			case 'author': return User;
			case 'journal': return BookOpen;
			case 'notes': return StickyNote;
			case 'submissions-list': return FolderOpen;
			case 'related-work': return Network;
			default: return FileText;
		}
	}

	if (browser) {
		import('$lib/components/PdfViewer.svelte').then(module => {
			PdfViewer = module.default;
		});
	}

	// Fetch preprocessing status for a paper
	async function fetchPreprocessingStatus(paperId: string) {
		try {
			const response = await fetch(`/api/upload/preprocessing-status/${paperId}`);
			if (response.ok) {
				const data = await response.json();
				preprocessingStatusCache[paperId] = {
					hasCache: data.data.hasCache,
					status: data.data.status
				};
			}
		} catch (error) {
			console.error('Failed to fetch preprocessing status:', error);
		}
	}

	function openAuthorModal(authorName: string) {
		const allPapers = get(papers);
		const authorPapers = allPapers.filter((p) =>
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
		const allPapers = get(papers);
		let paper = allPapers.find(p => p.id === paperId);

		if (!paper) {
			const response = await api.getPaper(paperId);
			if (response.success && response.data) {
				paper = response.data as Paper;
			}
		}

		if (paper) {
			handleOpenPaper(paper);
		} else {
			toast.error('Paper not found');
		}
	}

	// Handle paper selection from sidebar or browser
	function handleSelectPaper(paperId: string, multi = false) {
		selectPaper(paperId, multi);
	}

	// Handle opening a paper in viewer (uses tabs store)
	function handleOpenPaper(paper: Paper) {
		openPaper(paper);
		selectPaper(paper.id);
	}

	// Handle sidebar source selection (for home tab filtering)
	function handleSelectSource(source: SourceType, sourceId?: string) {
		selectSource(source, sourceId);
	}

	// Handle library filter change (All, Recent, Favorites, Unread)
	function handleFilterChange(filter: LibraryFilter) {
		libraryFilter = filter;
	}

	// Handle opening a tag tab from sidebar
	function handleOpenTag(tagName: string, paperCount: number) {
		const tagProfile: TagProfile = { name: tagName, paperCount };
		openTagProfile(tagProfile);
	}

	// Handle opening an author tab from sidebar
	function handleOpenAuthor(authorName: string, paperCount: number) {
		const authorProfile: AuthorProfile = { name: authorName, paperCount };
		openAuthorProfile(authorProfile);
	}

	// Handle opening a journal tab from sidebar
	function handleOpenJournal(journalName: string, paperCount: number) {
		const journalProfile: JournalProfile = { name: journalName, paperCount };
		openJournalProfile(journalProfile);
	}

	// Handle PDF download
	async function handleDownloadPdf(paperId: string) {
		isDownloadingPdf = true;
		downloadingPaperId = paperId;

		try {
			const result = await api.downloadPdf(paperId);

			if (result.success && result.data) {
				toast.success('PDF downloaded successfully');
				await loadPapers();
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

	async function handleDeletePaper() {
		if (!paperToDelete) return;
		isDeleting = true;
		const success = await deletePaper(paperToDelete.id);
		if (success) {
			toast.success(`Deleted "${paperToDelete.title}"`);
			clearPaperSelection();
		} else {
			toast.error('Failed to delete paper');
		}
		paperToDelete = null;
		isDeleting = false;
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

	onMount(async () => {
		initializeLibrary();
		await loadSettings();

		// Check if onboarding is needed
		const needsOnboarding = await checkOnboardingRequired();
		if (needsOnboarding) {
			startOnboarding();
			showOnboarding = true;
		}
	});

	// Trigger online search when local results are few
	$effect(() => {
		const state = get(appState);
		const query = state.searchQuery;
		const localCount = get(filteredPapers).length;
		searchOnlineIfNeeded(query, localCount);
	});

	async function loadSettings() {
		const result = await api.getSettings();
		if (result.success && result.data) {
			settings = result.data;
		}
	}

	async function handleOnboardingComplete() {
		showOnboarding = false;
		await loadSettings();
		toast.success('Setup complete! Welcome to Potero.');
	}

	async function saveSettings() {
		isSavingSettings = true;
		const updateData: Partial<Settings> = {
			llmProvider: settings.llmProvider,
			theme: settings.theme,
			enableSciHub: settings.enableSciHub
		};

		if (newApiKey.trim()) {
			updateData.llmApiKey = newApiKey;
		}

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

	async function handleSSOLogin() {
		isSavingSSO = true;
		const result = await api.loginSSO();

		if (result.success && result.accessToken) {
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
				} else if (get(error)) {
					toast.error(get(error)!);
				}
			} else {
				toast.warning('Please drop PDF files only');
			}
		}
	}

	// Quick actions from inspector
	function handleOpenRelatedWork() {
		const paper = get(selectedPaper);
		if (paper) {
			openRelatedWork(paper);
		}
	}

	function handleOpenChat() {
		toggleChatPanel();
	}

	function handleOpenNotes(paperId: string) {
		import('$lib/stores/tabs').then(({ openNotePanel }) => {
			openNotePanel(paperId);
		});
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

<!-- Main Layout -->
<div
	ondragenter={handleDragOver}
	ondragover={handleDragOver}
	ondragleave={handleDragLeave}
	ondrop={handleDrop}
	role="region"
	aria-label="Drop zone for PDF files"
>
	<MainLayout
		title="Potero"
		showSidebar={$appState.showSidebar && $activeTab?.type === 'home'}
		showInspector={$appState.showInspector && $activeTab?.type === 'home'}
		onSearch={() => showFloatingSearch = true}
		onAdd={() => showImportDialog = true}
		onSettings={() => showSettings = true}
	>
		{#snippet tabBar()}
			<div class="flex items-center gap-0.5">
				{#each $tabs as tab (tab.id)}
					{@const TabIcon = getTabIcon(tab.type)}
					<button
						class="group relative flex items-center gap-2 px-4 py-2 text-sm transition-all shrink-0 rounded-t-lg
							{$activeTabId === tab.id
								? 'bg-background/80 text-foreground shadow-sm'
								: 'text-muted-foreground hover:text-foreground hover:bg-background/40'}"
						style="-webkit-app-region: no-drag"
						onclick={() => activeTabId.set(tab.id)}
					>
						<TabIcon class="h-4 w-4 shrink-0" />
						<span class="max-w-[140px] truncate font-medium">{tab.title}</span>
						{#if tab.id !== 'home'}
							<button
								class="ml-1 rounded-full p-0.5 opacity-0 group-hover:opacity-100 hover:bg-muted transition-all"
								onclick={(e) => { e.stopPropagation(); closeTab(tab.id); }}
							>
								<X class="h-3.5 w-3.5" />
							</button>
						{/if}
						<!-- Active indicator line -->
						{#if $activeTabId === tab.id}
							<div class="absolute bottom-0 left-2 right-2 h-0.5 bg-primary rounded-full"></div>
						{/if}
					</button>
				{/each}
			</div>
		{/snippet}

		{#snippet sidebar()}
			<SourcesSidebar
				{sidebarData}
				{activeTab}
				{libraryFilter}
				onFilterChange={handleFilterChange}
				onGoHome={goHome}
				onOpenTag={handleOpenTag}
				onOpenAuthor={handleOpenAuthor}
				onOpenJournal={handleOpenJournal}
				onOpenSubmissions={openSubmissionsList}
				onOpenNotes={openNotesList}
			/>
		{/snippet}

		{#snippet content()}
			{#if $activeTab?.type === 'viewer' && viewingPaper}
				<!-- PDF Viewer Mode -->
				<div class="flex h-full flex-col">
					<!-- Paper info bar with action buttons -->
					<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-2">
						<div class="flex items-center gap-2 min-w-0 flex-1">
							<button
								class="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
								onclick={() => { if ($activeTab) closeTab($activeTab.id); }}
								title="Close Tab"
							>
								<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
									<path d="M18 6L6 18M6 6l12 12" />
								</svg>
							</button>
							<h2 class="truncate text-sm font-medium">{viewingPaper?.title}</h2>
							{#if viewingPaper?.year}
								<span class="shrink-0 text-xs text-muted-foreground">({viewingPaper.year})</span>
							{/if}
						</div>
						<div class="flex items-center gap-1 shrink-0">
							<!-- Chat toggle button -->
							<button
								class="rounded px-2 py-1 text-xs transition-colors flex items-center gap-1 {$isChatPanelOpen
									? 'bg-primary text-primary-foreground'
									: 'text-muted-foreground hover:bg-muted hover:text-foreground'}"
								onclick={() => toggleChatPanel()}
								title={$isChatPanelOpen ? 'Close Chat' : 'Chat with Paper'}
							>
								<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
									<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
								</svg>
								Chat
							</button>

							<!-- Related Work Button -->
							<button
								class="rounded px-2 py-1 text-xs text-muted-foreground hover:bg-muted hover:text-foreground flex items-center gap-1"
								onclick={() => {
									if (viewingPaper) {
										openRelatedWork(viewingPaper);
									}
								}}
								title="Find and compare related work"
							>
								<Network class="h-4 w-4" />
								Related
							</button>

							<!-- Actions Dropdown Menu -->
							<div class="relative">
								<button
									class="rounded px-2 py-1 text-xs text-muted-foreground hover:bg-muted hover:text-foreground flex items-center gap-1"
									onclick={() => {
										const paperId = viewingPaper?.id ?? '';
										analyzeDropdownOpen[paperId] = !analyzeDropdownOpen[paperId];
										if (analyzeDropdownOpen[paperId] && paperId) {
											fetchPreprocessingStatus(paperId);
										}
									}}
									title="PDF 작업 옵션"
								>
									<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
										<path d="M1 4v6h6M23 20v-6h-6" />
										<path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" />
									</svg>
									Actions
									<ChevronDown class="h-3 w-3" />
								</button>

								{#if viewingPaper && analyzeDropdownOpen[viewingPaper.id]}
									<div
										class="absolute right-0 mt-1 w-48 rounded-md shadow-lg glass shadow-glass border z-50"
										onclick={() => { if (viewingPaper) analyzeDropdownOpen[viewingPaper.id] = false; }}
									>
										<div class="py-1">
											<button
												class="w-full text-left px-4 py-2 text-xs hover:bg-muted flex items-center gap-2"
												onclick={async (e) => {
													e.stopPropagation();
													if (!viewingPaper?.id) return;
													analyzeDropdownOpen[viewingPaper.id] = false;
													const jobId = await reanalyzePaper(viewingPaper.id);
													if (jobId) {
														toast.info('Analysis started. Check progress in the task panel.');
													} else {
														toast.error('Failed to start analysis');
													}
												}}
											>
												<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
													<path d="M1 4v6h6M23 20v-6h-6" />
													<path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" />
												</svg>
												<div>
													<div class="font-medium">Re-analyze</div>
													<div class="text-muted-foreground">Update metadata & refs</div>
												</div>
											</button>
											<button
												class="w-full text-left px-4 py-2 text-xs hover:bg-muted flex items-center gap-2"
												onclick={async (e) => {
													e.stopPropagation();
													if (!viewingPaper?.id) return;
													analyzeDropdownOpen[viewingPaper.id] = false;
													const jobId = await reextractPaper(viewingPaper.id);
													if (jobId) {
														toast.info('Re-extraction started. Check progress in the task panel.');
													} else {
														toast.error('Failed to start re-extraction');
													}
												}}
											>
												<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
													<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
													<polyline points="7 10 12 15 17 10" />
													<line x1="12" y1="15" x2="12" y2="3" />
												</svg>
												<div class="flex-1">
													<div class="font-medium">Re-extract</div>
													<div class="text-muted-foreground">Force OCR & text extraction</div>
												</div>
												{#if viewingPaper?.id && preprocessingStatusCache[viewingPaper.id]}
													{@const status = preprocessingStatusCache[viewingPaper.id]}
													<div
														class="w-2 h-2 rounded-full"
														class:bg-green-500={status.hasCache && status.status === 'completed'}
														class:bg-yellow-500={status.hasCache && status.status === 'processing'}
														class:bg-red-500={status.hasCache && status.status === 'failed'}
														class:bg-gray-400={!status.hasCache}
														title={status.hasCache ? `Cached (${status.status})` : 'Not cached'}
													></div>
												{/if}
											</button>
										</div>
									</div>
								{/if}
							</div>
						</div>
					</div>

					{#if viewingPaper?.pdfUrl && PdfViewer}
						{#key $activeTab?.id}
							<svelte:component
								this={PdfViewer}
								pdfUrl={viewingPaper.pdfUrl}
								paperId={viewingPaper.id}
								paper={viewingPaper}
								tabId={$activeTab?.id}
								initialState={$activeTab?.viewerState}
								onOpenPaper={openPaperById}
							/>
						{/key}
					{:else if viewingPaper?.pdfUrl && !PdfViewer}
						<div class="flex flex-1 items-center justify-center bg-muted/20">
							<div class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
						</div>
					{:else}
						<div class="flex flex-1 flex-col items-center justify-center bg-muted/20">
							<svg class="mb-4 h-16 w-16 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
								<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
							</svg>
							<h2 class="mb-2 text-xl font-semibold">{viewingPaper?.title}</h2>
							<p class="text-muted-foreground">No PDF file attached</p>
							{#if viewingPaper?.abstract}
								<p class="mt-4 max-w-2xl text-center text-sm text-muted-foreground">
									{viewingPaper.abstract}
								</p>
							{/if}
							<div class="mt-4 flex gap-2">
								<button
									class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
									onclick={() => handleDownloadPdf(viewingPaper?.id ?? '')}
									disabled={isDownloadingPdf && downloadingPaperId === viewingPaper?.id}
								>
									{isDownloadingPdf && downloadingPaperId === viewingPaper?.id ? 'Downloading...' : 'Download PDF'}
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
			{:else if $activeTab?.type === 'home'}
				<!-- Home - Paper Browser Mode -->
				<PaperBrowser
					papers={filteredPapers}
					selectedPaperIds={$appState.selectedPaperIds}
					viewMode={$appState.viewMode}
					sortBy={$appState.sortBy}
					sortDirection={$appState.sortDirection}
					searchQuery={$appState.searchQuery}
					isLoading={$isLoading}
					onSelectPaper={handleSelectPaper}
					onOpenPaper={handleOpenPaper}
					onDeletePaper={(paper) => paperToDelete = paper}
					onViewModeChange={setViewMode}
					onSortChange={setSortBy}
					onToggleSortDirection={toggleSortDirection}
					onSearchChange={setSearchQuery}
					onAddPaper={() => showImportDialog = true}
					onRefresh={async () => {
						await loadPapers(false);
						toast.success('Library refreshed');
					}}
				/>
			{:else if $activeTab?.type === 'tag' && $activeTab.tag}
				<!-- Tag Profile -->
				<TagProfileView
					tag={$activeTab.tag}
					onOpenPaper={handleOpenPaper}
					onClose={() => closeTab($activeTab?.id ?? '')}
				/>
			{:else if $activeTab?.type === 'author' && $activeTab.author}
				<!-- Author Profile -->
				<AuthorProfileView
					author={$activeTab.author}
					onOpenPaper={handleOpenPaper}
					onClose={() => closeTab($activeTab?.id ?? '')}
				/>
			{:else if $activeTab?.type === 'journal' && $activeTab.journal}
				<!-- Journal Profile -->
				<JournalProfileView
					journal={$activeTab.journal}
					onOpenPaper={handleOpenPaper}
					onClose={() => closeTab($activeTab?.id ?? '')}
				/>
			{:else if $activeTab?.type === 'notes'}
				<!-- Notes List -->
				<NoteList
					onOpenNote={(note) => openNote(note)}
					onCreateNote={async () => {
						const newNote = await createNote({ title: 'New Note', content: '', tags: [] });
						if (newNote) openNote(newNote);
					}}
				/>
			{:else if $activeTab?.type === 'note-viewer' && $activeTab.note}
				<!-- Note Viewer -->
				<NoteViewer
					note={$activeTab.note}
					onClose={() => closeTab($activeTab?.id ?? '')}
				/>
			{:else if $activeTab?.type === 'related-work' && $activeTab.paper}
				<!-- Related Work -->
				<RelatedWorkView
					sourcePaper={$activeTab.paper}
					onOpenPaper={handleOpenPaper}
					onClose={() => closeTab($activeTab?.id ?? '')}
				/>
			{:else if $activeTab?.type === 'submissions-list'}
				<!-- Submissions List -->
				<SubmissionsList
					onOpenSubmission={(submission) => openSubmissionWorkflow(submission)}
				/>
			{:else if $activeTab?.type === 'submission' && $activeTab.submission}
				<!-- Submission Dashboard -->
				<SubmissionDashboard
					submission={$activeTab.submission}
					onClose={() => closeTab($activeTab?.id ?? '')}
				/>
			{:else}
				<!-- Fallback -->
				<div class="flex h-full items-center justify-center text-muted-foreground">
					<p>Select a view from the sidebar</p>
				</div>
			{/if}
		{/snippet}

		{#snippet inspector()}
			<InspectorPanel
				paper={selectedPaper}
				onOpenPdf={handleOpenPaper}
				onOpenRelatedWork={handleOpenRelatedWork}
				onOpenChat={handleOpenChat}
				onOpenNotes={handleOpenNotes}
				onTagClick={(tag) => handleSelectSource('tag', tag)}
				onAuthorClick={openAuthorModal}
			/>
		{/snippet}

		{#snippet statusBar()}
			<StatusBar
				totalCount={$sidebarData.paperCount}
				selectedCount={$appState.selectedPaperIds.length}
			/>
		{/snippet}
	</MainLayout>
</div>

<!-- Drop overlay -->
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

<!-- Import Dialog -->
{#if showImportDialog}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
		<div class="w-full max-w-md rounded-xl glass shadow-glass-lg p-6">
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

<!-- Settings Panel -->
{#if showSettings}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
		<div class="w-full max-w-3xl max-h-[85vh] rounded-xl glass shadow-glass-lg overflow-hidden flex flex-col">
			<div class="border-b bg-muted/30 px-6 py-4 flex items-center justify-between">
				<h1 class="text-lg font-bold">Settings</h1>
				<button
					class="rounded p-1 hover:bg-muted"
					onclick={() => showSettings = false}
				>
					<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M18 6L6 18M6 6l12 12" />
					</svg>
				</button>
			</div>

			<!-- Settings Tabs -->
			<div class="border-b bg-muted/30 px-6">
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

					<section class="mb-8">
						<h2 class="mb-4 text-lg font-semibold">POSTECH SSO Authentication</h2>
						<div class="space-y-4 rounded-lg border bg-card p-4">
							<p class="text-sm text-muted-foreground">
								Authenticate with POSTECH SSO to enable file attachments in chat.
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
						</div>
					</section>

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
					<SettingsPanel />

					<section class="mb-8">
						<h2 class="mb-4 text-lg font-semibold">PDF Download Options</h2>
						<div class="space-y-4 rounded-lg border bg-card p-4">
							<div class="flex items-center justify-between rounded-lg border bg-background p-4">
								<div class="flex-1">
									<p class="font-medium">Enable Sci-Hub</p>
									<p class="mt-1 text-sm text-muted-foreground">
										Use Sci-Hub as a fallback for finding PDFs.
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
						</div>
					</section>

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
									{isBulkReanalyzing ? 'Analyzing...' : 'Analyze All Papers'}
								</button>
								<button
									class="rounded-md border px-4 py-2 text-sm hover:bg-muted disabled:opacity-50"
									disabled={isBulkReanalyzing}
									onclick={handleBulkReanalyzeMissing}
								>
									Analyze Missing Only
								</button>
							</div>
						</div>
					</section>

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
{/if}

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
		<div class="w-full max-w-md rounded-xl glass shadow-glass-lg p-6">
			<h2 class="mb-2 text-lg font-semibold">Delete Paper</h2>
			<p class="mb-4 text-sm text-muted-foreground">
				Are you sure you want to delete <span class="font-medium text-foreground">"{paperToDelete.title}"</span>?
			</p>
			<p class="mb-6 text-xs text-destructive">
				This will permanently remove the paper and its PDF file from your library.
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
		<div class="relative h-[80vh] w-full max-w-4xl rounded-xl glass shadow-glass-lg overflow-hidden">
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

<!-- Floating Chat Panel -->
{#if $isChatPanelOpen && viewingPaper}
	<FloatingChatPanel
		paper={viewingPaper}
		onClose={() => toggleChatPanel()}
	/>
{/if}

<!-- Floating Search Modal -->
{#if showFloatingSearch}
	<FloatingSearchModal onClose={() => showFloatingSearch = false} />
{/if}

<!-- Onboarding Wizard -->
{#if showOnboarding}
	<OnboardingWizard onComplete={handleOnboardingComplete} />
{/if}

<svelte:window onkeydown={(e) => {
	if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
		e.preventDefault();
		showFloatingSearch = true;
	}
}} />
