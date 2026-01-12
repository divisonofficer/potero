<script lang="ts">
	import type { Readable } from 'svelte/store';
	import type { Paper } from '$lib/types';
	import {
		FileText,
		ExternalLink,
		MessageSquare,
		GitCompare,
		Tag,
		Calendar,
		Quote,
		BookOpen,
		User,
		Hash,
		StickyNote
	} from 'lucide-svelte';

	interface Props {
		paper: Readable<Paper | null>;
		onOpenPdf?: (paper: Paper) => void;
		onOpenChat?: () => void;
		onOpenRelatedWork?: () => void;
		onOpenNotes?: (paperId: string) => void;
		onTagClick?: (tag: string) => void;
		onAuthorClick?: (author: string) => void;
	}

	let { paper, onOpenPdf, onOpenChat, onOpenRelatedWork, onOpenNotes, onTagClick, onAuthorClick }: Props =
		$props();

	let showFullAbstract = $state(false);
</script>

<div class="flex h-full flex-col overflow-hidden">
	{#if $paper}
		<!-- Paper Info -->
		<div class="flex-1 overflow-y-auto p-4">
			<!-- Title -->
			<h2 class="text-lg font-semibold leading-tight text-foreground">
				{$paper.title}
			</h2>

			<!-- Authors -->
			<div class="mt-3">
				<div class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground mb-1.5">
					<User class="h-3.5 w-3.5" />
					Authors
				</div>
				<div class="flex flex-wrap gap-1">
					{#each $paper.authors as author}
						<button
							class="rounded-md bg-muted/50 px-2 py-0.5 text-sm text-foreground/80 hover:bg-muted hover:text-foreground transition-colors"
							onclick={() => onAuthorClick?.(author)}
						>
							{author}
						</button>
					{/each}
				</div>
			</div>

			<!-- Metadata Grid -->
			<div class="mt-4 grid grid-cols-2 gap-3">
				<!-- Year -->
				<div class="glass-subtle rounded-lg p-2.5">
					<div class="flex items-center gap-1.5 text-xs text-muted-foreground mb-0.5">
						<Calendar class="h-3.5 w-3.5" />
						Year
					</div>
					<div class="font-medium">{$paper.year || 'Unknown'}</div>
				</div>

				<!-- Citations -->
				<div class="glass-subtle rounded-lg p-2.5">
					<div class="flex items-center gap-1.5 text-xs text-muted-foreground mb-0.5">
						<Quote class="h-3.5 w-3.5" />
						Citations
					</div>
					<div class="font-medium">{$paper.citations?.toLocaleString() || 0}</div>
				</div>

				<!-- Venue -->
				{#if $paper.venue}
					<div class="glass-subtle rounded-lg p-2.5 col-span-2">
						<div class="flex items-center gap-1.5 text-xs text-muted-foreground mb-0.5">
							<BookOpen class="h-3.5 w-3.5" />
							Venue
						</div>
						<div class="font-medium text-sm truncate">{$paper.venue}</div>
					</div>
				{/if}
			</div>

			<!-- Tags -->
			{#if $paper.subject && $paper.subject.length > 0}
				<div class="mt-4">
					<div class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground mb-2">
						<Tag class="h-3.5 w-3.5" />
						Tags
					</div>
					<div class="flex flex-wrap gap-1.5">
						{#each $paper.subject as tag}
							<button
								class="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary hover:bg-primary/20 transition-colors"
								onclick={() => onTagClick?.(tag)}
							>
								{tag}
							</button>
						{/each}
					</div>
				</div>
			{/if}

			<!-- Abstract -->
			{#if $paper.abstract}
				<div class="mt-4">
					<div class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground mb-2">
						<Hash class="h-3.5 w-3.5" />
						Abstract
					</div>
					<p class="text-sm text-foreground/80 leading-relaxed {showFullAbstract ? '' : 'line-clamp-4'}">
						{$paper.abstract}
					</p>
					{#if $paper.abstract.length > 200}
						<button
							class="mt-1 text-xs text-primary hover:underline"
							onclick={() => (showFullAbstract = !showFullAbstract)}
						>
							{showFullAbstract ? 'Show less' : 'Show more'}
						</button>
					{/if}
				</div>
			{/if}

			<!-- External Links -->
			<div class="mt-4 flex flex-wrap gap-2">
				{#if $paper.doi}
					<a
						href="https://doi.org/{$paper.doi}"
						target="_blank"
						rel="noopener noreferrer"
						class="inline-flex items-center gap-1 rounded-md bg-muted/50 px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
					>
						<ExternalLink class="h-3 w-3" />
						DOI
					</a>
				{/if}
				{#if $paper.arxiv}
					<a
						href="https://arxiv.org/abs/{$paper.arxiv}"
						target="_blank"
						rel="noopener noreferrer"
						class="inline-flex items-center gap-1 rounded-md bg-muted/50 px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
					>
						<ExternalLink class="h-3 w-3" />
						arXiv
					</a>
				{/if}
			</div>
		</div>

		<!-- Quick Actions -->
		<div class="shrink-0 border-t border-border/30 p-3">
			<div class="grid grid-cols-1 gap-2">
				<button
					class="flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-[0_2px_8px_hsl(var(--primary-glow))] hover:bg-primary/90 transition-colors"
					onclick={() => $paper && onOpenPdf?.($paper)}
				>
					<FileText class="h-4 w-4" />
					Open PDF
				</button>
				<div class="grid grid-cols-3 gap-2">
					<button
						class="flex items-center justify-center gap-1 rounded-lg glass px-2 py-2 text-xs font-medium text-foreground hover:bg-[hsl(var(--glass-bg-hover))] transition-colors"
						onclick={onOpenChat}
					>
						<MessageSquare class="h-3.5 w-3.5" />
						Chat
					</button>
					<button
						class="flex items-center justify-center gap-1 rounded-lg glass px-2 py-2 text-xs font-medium text-foreground hover:bg-[hsl(var(--glass-bg-hover))] transition-colors"
						onclick={onOpenRelatedWork}
					>
						<GitCompare class="h-3.5 w-3.5" />
						Related
					</button>
					<button
						class="flex items-center justify-center gap-1 rounded-lg glass px-2 py-2 text-xs font-medium text-foreground hover:bg-[hsl(var(--glass-bg-hover))] transition-colors"
						onclick={() => $paper && onOpenNotes?.($paper.id)}
					>
						<StickyNote class="h-3.5 w-3.5" />
						Notes
					</button>
				</div>
			</div>
		</div>
	{:else}
		<!-- Empty State -->
		<div class="flex h-full flex-col items-center justify-center p-6 text-center">
			<div class="rounded-full bg-muted/50 p-4 mb-4">
				<FileText class="h-8 w-8 text-muted-foreground" />
			</div>
			<h3 class="text-sm font-medium text-foreground">No Paper Selected</h3>
			<p class="mt-1 text-xs text-muted-foreground">
				Select a paper from the list to view its details
			</p>
		</div>
	{/if}
</div>
