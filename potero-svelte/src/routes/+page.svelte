<script lang="ts">
	import { onMount } from 'svelte';
	import { tabs, activeTab, activeTabId, closeTab, openSettings } from '$lib/stores/tabs';
	import {
		papers,
		filteredPapers,
		isLoading,
		error,
		searchQuery,
		viewStyle,
		initializeLibrary,
		importByDoi,
		importByArxiv,
		uploadPdfs,
		pendingUploadAnalysis,
		clearPendingUploadAnalysis,
		loadPapers
	} from '$lib/stores/library';
	import { api, type Settings } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';
	import type { Paper } from '$lib/types';
	import { browser } from '$app/environment';
	import ChatPanel from '$lib/components/ChatPanel.svelte';
	import SearchResultsDialog from '$lib/components/SearchResultsDialog.svelte';

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

	// Settings state
	let settings = $state<Settings>({
		llmApiKey: null,
		llmProvider: 'gpt',
		pdfStoragePath: null,
		theme: 'system'
	});
	let newApiKey = $state('');
	let isSavingSettings = $state(false);

	onMount(() => {
		initializeLibrary();
		loadSettings();
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
			theme: settings.theme
		};

		// Only send API key if user entered a new one
		if (newApiKey.trim()) {
			updateData.llmApiKey = newApiKey;
		}

		const result = await api.updateSettings(updateData);
		if (result.success && result.data) {
			settings = result.data;
			newApiKey = '';
		}
		isSavingSettings = false;
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
				class="flex h-8 items-center gap-2 rounded-t-md border-b-2 px-3 text-sm transition-colors
					{$activeTabId === tab.id
					? 'border-primary bg-background text-foreground'
					: 'border-transparent text-muted-foreground hover:text-foreground'}"
				onclick={() => activeTabId.set(tab.id)}
			>
				<span class="max-w-32 truncate">{tab.title}</span>
				{#if tab.id !== 'home'}
					<button
						class="ml-1 rounded p-0.5 hover:bg-muted"
						onclick={(e) => {
							e.stopPropagation();
							closeTab(tab.id);
						}}
					>
						<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
							<path d="M18 6L6 18M6 6l12 12" />
						</svg>
					</button>
				{/if}
			</button>
		{/each}

		<!-- Settings button -->
		<button
			class="ml-auto rounded p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
			onclick={() => openSettings()}
			title="Settings"
		>
			<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<circle cx="12" cy="12" r="3" />
				<path
					d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"
				/>
			</svg>
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
				{:else if $filteredPapers.length === 0}
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
					<!-- Paper grid -->
					<div
						class={$viewStyle === 'grid'
							? 'grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3'
							: $viewStyle === 'list'
								? 'flex flex-col gap-3'
								: 'flex flex-col gap-1'}
					>
						{#each $filteredPapers as paper (paper.id)}
							{#if $viewStyle === 'grid'}
								<!-- Grid view card -->
								<div
									class="cursor-pointer rounded-lg border bg-card p-4 transition-shadow hover:shadow-md"
									onclick={() => {
										import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
									}}
								>
									<h3 class="mb-2 line-clamp-2 font-semibold">{paper.title}</h3>
									<p class="mb-2 text-sm text-muted-foreground">
										{paper.authors.slice(0, 3).join(', ')}
										{paper.authors.length > 3 ? ` +${paper.authors.length - 3}` : ''}
									</p>
									<div class="flex items-center gap-2 text-xs text-muted-foreground">
										{#if paper.year}
											<span>{paper.year}</span>
										{/if}
										{#if paper.conference}
											<span class="rounded bg-muted px-2 py-0.5">{paper.conference}</span>
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
												<span class="rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">
													{tag}
												</span>
											{/each}
										</div>
									{/if}
								</div>
							{:else if $viewStyle === 'list'}
								<!-- List view -->
								<div
									class="flex cursor-pointer items-center gap-4 rounded-lg border bg-card p-4 transition-shadow hover:shadow-md"
									onclick={() => {
										import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
									}}
								>
									<div class="flex-1">
										<h3 class="font-semibold">{paper.title}</h3>
										<p class="mt-1 text-sm text-muted-foreground">
											{paper.authors.join(', ')}
										</p>
									</div>
									<div class="flex items-center gap-4 text-sm text-muted-foreground">
										{#if paper.year}
											<span>{paper.year}</span>
										{/if}
										{#if paper.conference}
											<span class="rounded bg-muted px-2 py-0.5">{paper.conference}</span>
										{/if}
										{#if paper.pdfUrl}
											<span class="text-primary">PDF</span>
										{:else}
											<span>{paper.citations} citations</span>
										{/if}
									</div>
								</div>
							{:else}
								<!-- Compact view -->
								<div
									class="flex cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm transition-colors hover:bg-muted"
									onclick={() => {
										import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
									}}
								>
									{#if paper.pdfUrl}
										<svg class="h-4 w-4 text-primary" viewBox="0 0 24 24" fill="currentColor">
											<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" />
										</svg>
									{/if}
									<span class="flex-1 truncate font-medium">{paper.title}</span>
									<span class="text-muted-foreground">{paper.authors[0]}</span>
									{#if paper.year}
										<span class="text-muted-foreground">{paper.year}</span>
									{/if}
								</div>
							{/if}
						{/each}
					</div>
				{/if}
			</div>
		</div>

		<!-- PDF Viewer tabs - render all, hide inactive -->
		{#each $tabs.filter(t => t.type === 'viewer') as tab (tab.id)}
			<div class="flex h-full {$activeTabId === tab.id ? '' : 'hidden'}">
				<!-- PDF Viewer -->
				<div class="flex flex-1 flex-col">
					{#if tab.paper?.pdfUrl && PdfViewer}
						<svelte:component
							this={PdfViewer}
							pdfUrl={tab.paper.pdfUrl}
							tabId={tab.id}
							initialState={tab.viewerState}
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
							<button
								class="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
								onclick={() => {
									// TODO: Upload PDF for this paper
									toast.info('PDF upload for existing papers coming soon');
								}}
							>
								Upload PDF
							</button>
						</div>
					{/if}
				</div>

				<!-- Chat panel -->
				<div class="w-80 border-l">
					<ChatPanel paper={tab.paper ?? null} />
				</div>
			</div>
		{/each}

		<!-- Settings tab -->
		<div class="h-full overflow-auto p-6 {$activeTab?.type === 'settings' ? '' : 'hidden'}">
			<h1 class="mb-6 text-2xl font-bold">Settings</h1>

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
