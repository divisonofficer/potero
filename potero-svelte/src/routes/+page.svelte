<script lang="ts">
	import { tabs, activeTab, activeTabId, closeTab } from '$lib/stores/tabs';
	import { papers } from '$lib/stores/library';
	import type { Paper } from '$lib/types';

	// Mock data for initial development
	const mockPapers: Paper[] = [
		{
			id: '1',
			title: 'Attention Is All You Need',
			authors: ['Ashish Vaswani', 'Noam Shazeer', 'Niki Parmar'],
			year: 2017,
			conference: 'NeurIPS',
			subject: ['Deep Learning', 'NLP', 'Transformers'],
			abstract:
				'We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely.',
			pdfUrl: null,
			thumbnailUrl: null,
			citations: 15234,
			doi: '10.48550/arXiv.1706.03762',
			hasBlogView: true
		},
		{
			id: '2',
			title: 'BERT: Pre-training of Deep Bidirectional Transformers',
			authors: ['Jacob Devlin', 'Ming-Wei Chang', 'Kenton Lee'],
			year: 2019,
			conference: 'NAACL',
			subject: ['Deep Learning', 'NLP', 'Transformers'],
			abstract:
				'We introduce a new language representation model called BERT, which stands for Bidirectional Encoder Representations from Transformers.',
			pdfUrl: null,
			thumbnailUrl: null,
			citations: 8923,
			doi: '10.18653/v1/N19-1423',
			hasBlogView: false
		},
		{
			id: '3',
			title: 'ImageNet Classification with Deep Convolutional Neural Networks',
			authors: ['Alex Krizhevsky', 'Ilya Sutskever', 'Geoffrey Hinton'],
			year: 2012,
			conference: 'NeurIPS',
			subject: ['Deep Learning', 'Computer Vision', 'CNN'],
			abstract:
				'We trained a large, deep convolutional neural network to classify the 1.2 million high-resolution images in the ImageNet LSVRC-2010 contest.',
			pdfUrl: null,
			thumbnailUrl: null,
			citations: 12500,
			doi: null,
			hasBlogView: false
		}
	];

	// Initialize papers store with mock data
	papers.set(mockPapers);
</script>

<div class="flex h-full flex-col">
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
	</div>

	<!-- Content Area -->
	<div class="flex-1 overflow-hidden">
		{#if $activeTab?.type === 'home'}
			<div class="h-full overflow-auto p-6">
				<h1 class="mb-6 text-2xl font-bold">Paper Library</h1>

				<!-- Search and filters -->
				<div class="mb-6 flex gap-4">
					<input
						type="text"
						placeholder="Search papers..."
						class="flex-1 rounded-md border bg-background px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
					/>
					<button class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90">
						Add Paper
					</button>
				</div>

				<!-- Paper grid -->
				<div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
					{#each $papers as paper (paper.id)}
						<div
							class="rounded-lg border bg-card p-4 transition-shadow hover:shadow-md cursor-pointer"
							onclick={() => {
								import('$lib/stores/tabs').then(({ openPaper }) => openPaper(paper));
							}}
						>
							<h3 class="mb-2 font-semibold line-clamp-2">{paper.title}</h3>
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
								<span class="ml-auto">{paper.citations} citations</span>
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
					{/each}
				</div>
			</div>
		{:else if $activeTab?.type === 'viewer'}
			<div class="flex h-full">
				<!-- PDF Viewer placeholder -->
				<div class="flex-1 flex items-center justify-center bg-muted/20">
					<div class="text-center">
						<h2 class="text-xl font-semibold mb-2">{$activeTab.paper?.title}</h2>
						<p class="text-muted-foreground">PDF Viewer coming soon...</p>
					</div>
				</div>

				<!-- Chat panel -->
				<div class="w-80 border-l bg-card flex flex-col">
					<div class="border-b p-3">
						<h3 class="font-semibold">Chat with Paper</h3>
					</div>
					<div class="flex-1 overflow-auto p-3">
						<p class="text-sm text-muted-foreground">Ask questions about this paper...</p>
					</div>
					<div class="border-t p-3">
						<div class="flex gap-2">
							<input
								type="text"
								placeholder="Ask a question..."
								class="flex-1 rounded-md border bg-background px-3 py-2 text-sm"
							/>
							<button class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground">
								Send
							</button>
						</div>
					</div>
				</div>
			</div>
		{:else if $activeTab?.type === 'settings'}
			<div class="h-full overflow-auto p-6">
				<h1 class="mb-6 text-2xl font-bold">Settings</h1>
				<p class="text-muted-foreground">Settings panel coming soon...</p>
			</div>
		{/if}
	</div>
</div>
