<script lang="ts">
	import { onMount } from 'svelte';
	import { Trash2, Link as LinkIcon, ArrowLeft } from 'lucide-svelte';
	import { currentNote, loadNote, updateNote, deleteNote } from '$lib/stores/notes';
	import BlockEditor from './BlockEditor.svelte';

	interface Props {
		noteId: string;
		onBack: () => void;
		onNavigateToNote: (noteId: string) => void;
		onNavigateToPaper: (paperId: string) => void;
	}

	let { noteId, onBack, onNavigateToNote, onNavigateToPaper }: Props = $props();

	let isDeleting = $state(false);
	let editTitle = $state('');
	let editContent = $state('');
	let titleInput: HTMLInputElement;
	let titleSaveTimeout: ReturnType<typeof setTimeout> | null = null;
	let isEditingTitle = $state(false);

	onMount(() => {
		loadNote(noteId);
	});

	// Sync with store when note changes
	$effect(() => {
		if ($currentNote) {
			editTitle = $currentNote.note.title;
			editContent = $currentNote.note.content;
		}
	});

	/**
	 * Handle title click - enter edit mode
	 */
	function handleTitleClick() {
		isEditingTitle = true;
		setTimeout(() => {
			titleInput?.focus();
			titleInput?.select();
		}, 0);
	}

	/**
	 * Handle title changes with auto-save
	 */
	function handleTitleChange(e: Event) {
		const target = e.target as HTMLInputElement;
		editTitle = target.value;

		if (titleSaveTimeout) clearTimeout(titleSaveTimeout);
		titleSaveTimeout = setTimeout(() => {
			if (editTitle.trim() && $currentNote) {
				updateNote($currentNote.note.id, editTitle, editContent, $currentNote.note.paperId);
			}
		}, 1500);
	}

	/**
	 * Handle title blur - exit edit mode
	 */
	function handleTitleBlur() {
		isEditingTitle = false;
		if (editTitle.trim() && $currentNote) {
			updateNote($currentNote.note.id, editTitle, editContent, $currentNote.note.paperId);
		}
	}

	/**
	 * Handle Enter key on title
	 */
	function handleTitleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter') {
			e.preventDefault();
			titleInput?.blur();
		}
	}

	/**
	 * Handle content changes from BlockEditor (auto-save)
	 */
	function handleContentSave(markdown: string) {
		editContent = markdown;
		if (editTitle.trim() && $currentNote) {
			updateNote($currentNote.note.id, editTitle, editContent, $currentNote.note.paperId);
		}
	}

	/**
	 * Handle delete
	 */
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

	/**
	 * Handle backlink clicks
	 */
	function handleBacklinkClick(backlinkNoteId: string) {
		onNavigateToNote(backlinkNoteId);
	}
</script>

{#if $currentNote}
	<div class="flex h-full flex-col">
		<!-- Header -->
		<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
			<div class="flex min-w-0 flex-1 items-center gap-2">
				<button
					class="rounded p-2 hover:bg-muted transition-colors"
					onclick={onBack}
					title="Back"
					type="button"
				>
					<ArrowLeft class="h-4 w-4" />
				</button>
				<div class="min-w-0 flex-1">
					<p class="text-xs text-muted-foreground">
						Updated {new Date($currentNote.note.updatedAt).toLocaleString()}
					</p>
				</div>
			</div>
			<div class="flex items-center gap-2">
				<button
					class="rounded p-2 hover:bg-destructive hover:text-destructive-foreground transition-colors"
					onclick={handleDelete}
					disabled={isDeleting}
					title="Delete"
					type="button"
				>
					<Trash2 class="h-4 w-4" />
				</button>
			</div>
		</div>

		<div class="flex flex-1 overflow-hidden">
			<!-- Main Content Area -->
			<div class="flex-1 overflow-auto">
				<div class="p-6">
					<!-- Editable Title -->
					{#if isEditingTitle}
						<input
							bind:this={titleInput}
							type="text"
							value={editTitle}
							oninput={handleTitleChange}
							onblur={handleTitleBlur}
							onkeydown={handleTitleKeydown}
							class="w-full bg-transparent text-3xl font-bold outline-none border-2 border-blue-400 rounded px-2 py-1"
							placeholder="Untitled note..."
						/>
					{:else}
						<h1
							class="text-3xl font-bold mb-6 cursor-text hover:bg-gray-50 dark:hover:bg-gray-800 rounded px-2 py-1 transition-colors"
							onclick={handleTitleClick}
							role="button"
							tabindex="0"
							onkeydown={(e) => {
								if (e.key === 'Enter' || e.key === ' ') {
									e.preventDefault();
									handleTitleClick();
								}
							}}
						>
							{editTitle || 'Untitled'}
						</h1>
					{/if}

					<!-- Block Editor (always visible, click to edit blocks) -->
					<BlockEditor initialContent={editContent} onSave={handleContentSave} autosave={true} />
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
									onclick={() => handleBacklinkClick(backlink.noteId)}
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
	</div>
{:else}
	<div class="flex h-full items-center justify-center">
		<div
			class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
		></div>
	</div>
{/if}
