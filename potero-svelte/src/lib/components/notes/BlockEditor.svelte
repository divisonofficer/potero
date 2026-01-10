<script lang="ts">
	import BlockList from './BlockList.svelte';
	import { parseMarkdownToBlocks, blocksToMarkdown } from '$lib/utils/blockParser';
	import type { Block } from '$lib/types/block';

	interface Props {
		initialContent: string;
		onSave: (content: string) => void;
		autosave?: boolean;
		autosaveDelay?: number; // ms
	}

	let { initialContent, onSave, autosave = true, autosaveDelay = 1500 }: Props = $props();

	// Block state - initialize immediately, not in onMount
	let blocks = $state<Block[]>(parseMarkdownToBlocks(initialContent));
	let isDirty = $state(false);
	let saveTimeout: ReturnType<typeof setTimeout> | null = null;

	// Re-parse blocks when initialContent changes (e.g., after loading template)
	$effect(() => {
		blocks = parseMarkdownToBlocks(initialContent);
	});

	/**
	 * Auto-save handler with debounce
	 */
	function handleBlockChange() {
		isDirty = true;

		if (!autosave) return;

		if (saveTimeout) clearTimeout(saveTimeout);
		saveTimeout = setTimeout(() => {
			const markdown = blocksToMarkdown(blocks);
			onSave(markdown);
			isDirty = false;
		}, autosaveDelay);
	}

	/**
	 * Manual save (Cmd+S or Ctrl+S)
	 */
	function handleManualSave() {
		if (saveTimeout) clearTimeout(saveTimeout);
		const markdown = blocksToMarkdown(blocks);
		onSave(markdown);
		isDirty = false;
	}

	/**
	 * Keyboard shortcuts
	 */
	function handleKeydown(e: KeyboardEvent) {
		if ((e.metaKey || e.ctrlKey) && e.key === 's') {
			e.preventDefault();
			handleManualSave();
		}
	}
</script>

<svelte:window onkeydown={handleKeydown} />

<div class="block-editor relative">
	<BlockList bind:blocks={blocks} onChange={handleBlockChange} />

	{#if isDirty}
		<div
			class="save-indicator fixed bottom-4 right-4 bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 px-4 py-2 rounded-lg shadow-lg text-sm flex items-center gap-2"
		>
			<svg
				class="animate-spin h-4 w-4"
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
			<span>Saving...</span>
		</div>
	{/if}
</div>

<style>
	.block-editor {
		min-height: 300px;
	}
</style>
