<script lang="ts">
	import { get } from 'svelte/store';
	import type { TagProfile, Paper } from '$lib/types';
	import { papers } from '$lib/stores/library';
	import { Tag, FileText, Calendar, Quote, ArrowLeft } from 'lucide-svelte';

	interface Props {
		tag: TagProfile;
		onOpenPaper?: (paper: Paper) => void;
		onClose?: () => void;
	}

	let { tag, onOpenPaper, onClose }: Props = $props();

	// Get papers with this tag
	let taggedPapers = $derived.by(() => {
		const allPapers = get(papers);
		return allPapers.filter(p => p.subject?.includes(tag.name));
	});
</script>

<div class="h-full overflow-auto">
	<!-- Header with gradient -->
	<div class="bg-gradient-to-r from-blue-500 to-cyan-600">
		<div class="px-8 py-6">
			{#if onClose}
				<button
					class="mb-4 flex items-center gap-2 rounded-lg bg-white/20 px-3 py-1.5 text-sm text-white hover:bg-white/30"
					onclick={onClose}
				>
					<ArrowLeft class="h-4 w-4" />
					Back
				</button>
			{/if}
			<div class="flex items-center gap-4">
				<div class="flex h-16 w-16 items-center justify-center rounded-full bg-white/20">
					<Tag class="h-10 w-10 text-white" />
				</div>
				<div>
					<p class="text-sm text-white/80">Tag</p>
					<h1 class="text-3xl font-bold text-white">{tag.name}</h1>
					<p class="text-sm text-white/70">{taggedPapers.length} papers</p>
				</div>
			</div>
		</div>
	</div>

	<div class="px-8 py-6">
		<!-- Stats -->
		<div class="mb-8 grid grid-cols-3 gap-4">
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-muted-foreground">
					<FileText class="h-5 w-5" />
					<span class="text-sm">Papers</span>
				</div>
				<p class="mt-2 text-3xl font-bold">{taggedPapers.length}</p>
			</div>
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-muted-foreground">
					<Calendar class="h-5 w-5" />
					<span class="text-sm">Latest Year</span>
				</div>
				<p class="mt-2 text-3xl font-bold">
					{taggedPapers.length > 0 ? Math.max(...taggedPapers.filter(p => p.year).map(p => p.year!)) : '-'}
				</p>
			</div>
			<div class="rounded-xl border bg-card p-4">
				<div class="flex items-center gap-2 text-muted-foreground">
					<Quote class="h-5 w-5" />
					<span class="text-sm">Total Citations</span>
				</div>
				<p class="mt-2 text-3xl font-bold">
					{taggedPapers.reduce((sum, p) => sum + (p.citations || 0), 0).toLocaleString()}
				</p>
			</div>
		</div>

		<!-- Papers List -->
		<div class="rounded-xl border bg-card p-6">
			<h2 class="mb-4 text-lg font-semibold">Papers</h2>
			<div class="space-y-3">
				{#each taggedPapers as paper (paper.id)}
					<button
						class="w-full rounded-lg border p-4 text-left hover:bg-muted transition-colors"
						onclick={() => onOpenPaper?.(paper)}
					>
						<h3 class="font-medium line-clamp-2">{paper.title}</h3>
						<p class="mt-1 text-sm text-muted-foreground">
							{paper.authors.slice(0, 3).join(', ')}
							{paper.authors.length > 3 ? ' et al.' : ''}
						</p>
						<div class="mt-2 flex items-center gap-3 text-xs text-muted-foreground">
							{#if paper.year}
								<span>{paper.year}</span>
							{/if}
							{#if paper.venue}
								<span class="rounded bg-muted px-2 py-0.5">{paper.venue}</span>
							{/if}
							{#if paper.citations}
								<span>{paper.citations} citations</span>
							{/if}
						</div>
					</button>
				{:else}
					<p class="text-center text-muted-foreground py-8">No papers found with this tag</p>
				{/each}
			</div>
		</div>
	</div>
</div>
