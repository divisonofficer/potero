<script lang="ts">
	import { onMount } from 'svelte';
	import { Edit, Trash2, Link as LinkIcon, ArrowLeft } from 'lucide-svelte';
	import { currentNote, loadNote, updateNote, deleteNote } from '$lib/stores/notes';
	import NoteEditor from './NoteEditor.svelte';
	import { marked } from 'marked';

	interface Props {
		noteId: string;
		onBack: () => void;
		onNavigateToNote: (noteId: string) => void;
		onNavigateToPaper: (paperId: string) => void;
	}

	let { noteId, onBack, onNavigateToNote, onNavigateToPaper }: Props = $props();

	let isEditing = $state(false);
	let isDeleting = $state(false);

	onMount(() => {
		loadNote(noteId);
	});

	async function handleSave(title: string, content: string) {
		if (!$currentNote) return;
		const success = await updateNote($currentNote.note.id, title, content, $currentNote.note.paperId);
		if (success) {
			isEditing = false;
		}
	}

	async function handleDelete() {
		if (!$currentNote) return;
		if (!confirm(`Delete "${$currentNote.note.title}"?`)) return;

		isDeleting = true;
		const success = await deleteNote($currentNote.note.id);
		if (success) {
			onBack();
		}
		isDeleting = false;
	}

	function handleLinkClick(event: MouseEvent) {
		const target = event.target as HTMLElement;

		// Check if clicked on a link
		if (target.tagName === 'A' && target.hasAttribute('data-wiki-link')) {
			event.preventDefault();
			const linkText = target.getAttribute('data-wiki-link');
			if (!linkText) return;

			if (linkText.startsWith('paper:')) {
				const paperId = linkText.replace('paper:', '');
				onNavigateToPaper(paperId);
			} else {
				// Find the link in outgoing links
				const link = $currentNote?.outgoingLinks.find((l) => l.linkText === linkText);
				if (link?.targetNoteId) {
					onNavigateToNote(link.targetNoteId);
				} else {
					alert(`Note "${linkText}" not found. Create it first.`);
				}
			}
		}
	}

	function renderMarkdown(content: string): string {
		// Replace [[...]] with links
		const withLinks = content.replace(/\[\[([^\]]+)\]\]/g, (match, linkText) => {
			const link = $currentNote?.outgoingLinks.find((l) => l.linkText === linkText);
			const isResolved = link?.targetNoteId || link?.targetPaperId;
			const className = isResolved
				? 'inline-flex items-center rounded bg-primary/10 px-1.5 py-0.5 text-sm text-primary hover:bg-primary/20 cursor-pointer'
				: 'inline-flex items-center rounded bg-muted px-1.5 py-0.5 text-sm text-muted-foreground border border-dashed cursor-not-allowed';

			return `<a href="#" data-wiki-link="${linkText}" class="${className}">${match}</a>`;
		});

		return marked(withLinks) as string;
	}
</script>

{#if $currentNote}
	<div class="flex h-full flex-col">
		<!-- Header -->
		<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
			<div class="flex min-w-0 flex-1 items-center gap-2">
				<button
					class="rounded p-2 hover:bg-muted"
					onclick={onBack}
					title="Back"
					type="button"
				>
					<ArrowLeft class="h-4 w-4" />
				</button>
				<div class="min-w-0 flex-1">
					<h1 class="truncate text-lg font-semibold">{$currentNote.note.title}</h1>
					<p class="text-xs text-muted-foreground">
						Updated {new Date($currentNote.note.updatedAt).toLocaleString()}
					</p>
				</div>
			</div>
			<div class="flex items-center gap-2">
				<button
					class="rounded p-2 hover:bg-muted"
					onclick={() => (isEditing = !isEditing)}
					title="Edit"
					type="button"
				>
					<Edit class="h-4 w-4" />
				</button>
				<button
					class="rounded p-2 hover:bg-destructive hover:text-destructive-foreground"
					onclick={handleDelete}
					disabled={isDeleting}
					title="Delete"
					type="button"
				>
					<Trash2 class="h-4 w-4" />
				</button>
			</div>
		</div>

		{#if isEditing}
			<NoteEditor
				title={$currentNote.note.title}
				content={$currentNote.note.content}
				onSave={handleSave}
				onCancel={() => (isEditing = false)}
			/>
		{:else}
			<div class="flex flex-1 overflow-hidden">
				<!-- Content -->
				<div class="flex-1 overflow-auto">
					<div
						class="prose prose-sm max-w-none p-6"
						onclick={handleLinkClick}
						role="article"
						tabindex="0"
					>
						{@html renderMarkdown($currentNote.note.content)}
					</div>
				</div>

				<!-- Sidebar: Backlinks -->
				{#if $currentNote.backlinks.length > 0}
					<div class="w-64 overflow-auto border-l">
						<div class="p-4">
							<div class="mb-4 flex items-center gap-2">
								<LinkIcon class="h-4 w-4" />
								<h3 class="font-semibold">Backlinks</h3>
								<span class="text-xs text-muted-foreground">
									({$currentNote.backlinks.length})
								</span>
							</div>
							<div class="space-y-2">
								{#each $currentNote.backlinks as backlink}
									<button
										class="w-full rounded-lg border bg-card p-3 text-left transition-colors hover:bg-muted/50"
										onclick={() => onNavigateToNote(backlink.noteId)}
										type="button"
									>
										<p class="mb-1 text-sm font-medium">{backlink.noteTitle}</p>
										<p class="text-xs text-muted-foreground">
											Links as: [[{backlink.linkText}]]
										</p>
									</button>
								{/each}
							</div>
						</div>
					</div>
				{/if}
			</div>
		{/if}
	</div>
{:else}
	<div class="flex h-full items-center justify-center">
		<div
			class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
		></div>
	</div>
{/if}
