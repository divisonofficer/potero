<script lang="ts">
	import BlockEditor from './BlockEditor.svelte';
	import { X } from 'lucide-svelte';

	interface Props {
		title?: string;
		content?: string;
		onSave: (title: string, content: string) => void;
		onCancel: () => void;
		placeholder?: string;
		isLoading?: boolean;
	}

	let {
		title = '',
		content = '',
		onSave,
		onCancel,
		placeholder = 'Start writing...',
		isLoading = false
	}: Props = $props();

	let editTitle = $state(title);
	let editContent = $state(content);
	let titleInput: HTMLInputElement;

	// Title debounce timer
	let titleSaveTimeout: ReturnType<typeof setTimeout> | null = null;

	// Sync with props when they change (e.g., after loading template)
	$effect(() => {
		editTitle = title;
		editContent = content;
	});

	/**
	 * Handle title changes with debounced auto-save
	 */
	function handleTitleChange(e: Event) {
		const target = e.target as HTMLInputElement;
		editTitle = target.value;

		// Auto-save title after 1.5s
		if (titleSaveTimeout) clearTimeout(titleSaveTimeout);
		titleSaveTimeout = setTimeout(() => {
			if (editTitle.trim()) {
				onSave(editTitle, editContent);
			}
		}, 1500);
	}

	/**
	 * Handle content changes from BlockEditor (already debounced)
	 */
	function handleContentSave(markdown: string) {
		editContent = markdown;
		if (editTitle.trim()) {
			onSave(editTitle, editContent);
		}
	}

	/**
	 * Manual save on Cmd/Ctrl+S
	 */
	function handleKeydown(e: KeyboardEvent) {
		if ((e.metaKey || e.ctrlKey) && e.key === 's') {
			e.preventDefault();
			if (editTitle.trim()) {
				onSave(editTitle, editContent);
			}
		}
	}
</script>

<svelte:window onkeydown={handleKeydown} />

<div class="flex h-full flex-col bg-background">
	<!-- Header with close button -->
	<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-2">
		<div class="text-sm text-muted-foreground">
			{#if isLoading}
				<span class="flex items-center gap-2">
					<svg
						class="h-4 w-4 animate-spin"
						xmlns="http://www.w3.org/2000/svg"
						fill="none"
						viewBox="0 0 24 24"
					>
						<circle
							class="opacity-25"
							cx="12"
							cy="12"
							r="10"
							stroke="currentColor"
							stroke-width="4"
						></circle>
						<path
							class="opacity-75"
							fill="currentColor"
							d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
						></path>
					</svg>
					Loading template...
				</span>
			{:else}
				<span class="italic">Click any block to edit • Auto-saves as you type</span>
			{/if}
		</div>

		<button
			class="rounded-md p-1.5 hover:bg-muted transition-colors"
			onclick={onCancel}
			type="button"
			title="Close (Esc)"
		>
			<X class="h-4 w-4" />
		</button>
	</div>

	<!-- Title input with auto-save -->
	<div class="border-b px-4 py-3">
		<input
			bind:this={titleInput}
			type="text"
			placeholder="Untitled note..."
			value={editTitle}
			oninput={handleTitleChange}
			class="w-full bg-transparent text-2xl font-bold outline-none placeholder:text-muted-foreground"
		/>
	</div>

	<!-- Block Editor with inline editing + auto-save -->
	<div class="flex-1 overflow-auto px-4 py-3">
		<BlockEditor initialContent={editContent} onSave={handleContentSave} autosave={true} />
	</div>

	<!-- Help text at bottom -->
	<div
		class="border-t bg-muted/20 px-4 py-2 text-xs text-muted-foreground flex items-center justify-between"
	>
		<div class="flex items-center gap-4">
			<span><kbd class="kbd">Enter</kbd> to save block</span>
			<span><kbd class="kbd">Shift+Enter</kbd> for new line</span>
			<span><kbd class="kbd">[[</kbd> for wiki links</span>
		</div>
		<span class="italic">Cmd/Ctrl+S to save manually</span>
	</div>
</div>

<style>
	.kbd {
		padding: 2px 6px;
		background-color: hsl(var(--muted));
		border: 1px solid hsl(var(--border));
		border-radius: 4px;
		font-family: monospace;
		font-size: 0.85em;
	}
</style>
