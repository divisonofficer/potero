<script lang="ts">
	import { marked } from 'marked';
	import { configureMarked, renderMarkdown } from '$lib/utils/markdown';
	import { setCaretPosition, moveCaretToEnd } from '$lib/utils/cursorUtils';
	import type { Block } from '$lib/types/block';

	interface Props {
		block: Block;
		isEditing: boolean;
		onEdit: () => void;
		onBlur: () => void;
		onChange: (content: string) => void;
	}

	let { block, isEditing, onEdit, onBlur, onChange }: Props = $props();

	// Configure marked with extensions (KaTeX, Prism, wiki links)
	configureMarked();

	// Rendered HTML for view mode
	const html = $derived(isEditing ? null : renderMarkdown(block.content));

	// Reference to contenteditable element
	let editElement: HTMLDivElement | null = $state(null);

	// Focus and move cursor to end when entering edit mode
	$effect(() => {
		if (isEditing && editElement) {
			editElement.focus();
			moveCaretToEnd(editElement);
		}
	});

	/**
	 * Handle input in contenteditable element
	 */
	function handleInput(e: Event) {
		const target = e.target as HTMLElement;
		const content = target.textContent || '';
		onChange(content);

		// TODO: Check for [[ trigger for autocomplete (Week 4)
	}

	/**
	 * Handle Enter key - save and exit edit mode
	 * Shift+Enter creates new line
	 */
	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter' && !e.shiftKey) {
			e.preventDefault();
			onBlur();
		}
		// TODO: Handle other shortcuts (Week 3)
	}

	/**
	 * Handle click in view mode - enter edit mode
	 */
	function handleClick() {
		if (!isEditing) {
			onEdit();
		}
	}

	/**
	 * Get CSS class for block type
	 */
	function getBlockClass(type: string): string {
		const baseClass = 'block-item';
		const typeClass = `block-${type}`;
		return `${baseClass} ${typeClass}`;
	}
</script>

{#if isEditing}
	<!-- Edit mode: contenteditable -->
	<div
		bind:this={editElement}
		class={`block-edit ${getBlockClass(block.type)} min-h-[1.5em] px-2 py-1 rounded border-2 border-blue-400 focus:outline-none`}
		contenteditable="true"
		role="textbox"
		tabindex="0"
		onblur={onBlur}
		oninput={handleInput}
		onkeydown={handleKeydown}
	>
		{block.content}
	</div>
{:else}
	<!-- View mode: rendered markdown -->
	<div
		class={`block-view ${getBlockClass(block.type)} prose dark:prose-invert max-w-none px-2 py-1 rounded cursor-text hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors`}
		onclick={handleClick}
		role="button"
		tabindex="0"
		onkeydown={(e) => {
			if (e.key === 'Enter' || e.key === ' ') {
				e.preventDefault();
				handleClick();
			}
		}}
	>
		{@html html}
	</div>
{/if}

<style>
	/* Ensure contenteditable preserves formatting */
	.block-edit {
		white-space: pre-wrap;
		word-wrap: break-word;
	}

	/* Heading styles in edit mode */
	.block-heading1 {
		font-size: 2em;
		font-weight: bold;
		line-height: 1.2;
	}

	.block-heading2 {
		font-size: 1.5em;
		font-weight: bold;
		line-height: 1.3;
	}

	.block-heading3 {
		font-size: 1.25em;
		font-weight: bold;
		line-height: 1.4;
	}

	.block-heading4 {
		font-size: 1.1em;
		font-weight: bold;
		line-height: 1.4;
	}

	.block-heading5,
	.block-heading6 {
		font-size: 1em;
		font-weight: bold;
		line-height: 1.5;
	}

	.block-code {
		font-family: 'Courier New', Courier, monospace;
		background-color: #f5f5f5;
	}

	:global(.dark) .block-code {
		background-color: #1e1e1e;
	}

	.block-blockquote {
		border-left: 4px solid #ddd;
		padding-left: 1em;
		color: #666;
		font-style: italic;
	}

	:global(.dark) .block-blockquote {
		border-left-color: #444;
		color: #aaa;
	}

	/* Ensure proper spacing in view mode */
	.block-view :global(p) {
		margin: 0;
	}

	.block-view :global(h1),
	.block-view :global(h2),
	.block-view :global(h3),
	.block-view :global(h4),
	.block-view :global(h5),
	.block-view :global(h6) {
		margin: 0;
	}

	.block-view :global(code) {
		background-color: #f5f5f5;
		padding: 2px 6px;
		border-radius: 3px;
		font-family: 'Courier New', Courier, monospace;
	}

	:global(.dark) .block-view :global(code) {
		background-color: #1e1e1e;
	}

	/* Remove default prose margins for tight block layout */
	.block-view.prose :global(*:first-child) {
		margin-top: 0;
	}

	.block-view.prose :global(*:last-child) {
		margin-bottom: 0;
	}
</style>
