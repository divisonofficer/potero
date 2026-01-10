<script lang="ts">
	import { onMount } from 'svelte';
	import { FileText, Plus, Search } from 'lucide-svelte';
	import type { ResearchNote } from '$lib/types';
	import { notes, filteredNotes, searchQuery, loadNotes } from '$lib/stores/notes';

	interface Props {
		onSelectNote: (note: ResearchNote) => void;
		onCreateNote: () => void;
	}

	let { onSelectNote, onCreateNote }: Props = $props();

	onMount(() => {
		loadNotes();
	});

	function formatDate(dateStr: string): string {
		const date = new Date(dateStr);
		const now = new Date();
		const diffMs = now.getTime() - date.getTime();
		const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

		if (diffDays === 0) return 'Today';
		if (diffDays === 1) return 'Yesterday';
		if (diffDays < 7) return `${diffDays} days ago`;
		return date.toLocaleDateString();
	}

	function getPreview(content: string): string {
		// Remove markdown syntax and get first 100 chars
		const text = content
			.replace(/#{1,6}\s/g, '') // Remove headers
			.replace(/\*\*(.+?)\*\*/g, '$1') // Remove bold
			.replace(/\*(.+?)\*/g, '$1') // Remove italic
			.replace(/\[\[(.+?)\]\]/g, '$1') // Remove wiki links
			.replace(/`(.+?)`/g, '$1') // Remove code
			.trim();

		return text.length > 100 ? text.slice(0, 100) + '...' : text;
	}
</script>

<div class="flex h-full flex-col">
	<!-- Header -->
	<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
		<h2 class="text-lg font-semibold">Research Notes</h2>
		<button
			class="flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground hover:bg-primary/90"
			onclick={onCreateNote}
			type="button"
		>
			<Plus class="h-4 w-4" />
			New Note
		</button>
	</div>

	<!-- Search -->
	<div class="border-b px-4 py-3">
		<div class="relative">
			<Search
				class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
			/>
			<input
				type="text"
				placeholder="Search notes..."
				bind:value={$searchQuery}
				class="w-full rounded-md border bg-background py-2 pl-9 pr-4 text-sm outline-none focus:ring-2 focus:ring-primary"
			/>
		</div>
	</div>

	<!-- Note list -->
	<div class="flex-1 overflow-auto">
		{#if $filteredNotes.length === 0}
			<div class="flex flex-col items-center justify-center py-12 text-muted-foreground">
				<FileText class="mb-4 h-12 w-12" />
				<p>No notes yet</p>
				<button
					class="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
					onclick={onCreateNote}
					type="button"
				>
					Create your first note
				</button>
			</div>
		{:else}
			<div class="divide-y">
				{#each $filteredNotes as note (note.id)}
					<button
						class="w-full p-4 text-left transition-colors hover:bg-muted/50"
						onclick={() => onSelectNote(note)}
						type="button"
					>
						<h3 class="mb-1 font-medium">{note.title}</h3>
						<p class="mb-2 line-clamp-2 text-sm text-muted-foreground">
							{getPreview(note.content)}
						</p>
						<div class="flex items-center gap-2 text-xs text-muted-foreground">
							<span>{formatDate(note.updatedAt)}</span>
							{#if note.paperId}
								<span class="rounded bg-primary/10 px-2 py-0.5 text-primary">
									Linked to paper
								</span>
							{/if}
						</div>
					</button>
				{/each}
			</div>
		{/if}
	</div>
</div>
