<script lang="ts">
	import Block from './Block.svelte';
	import type { Block as BlockType } from '$lib/types/block';

	interface Props {
		blocks: BlockType[];
		onChange: () => void;
	}

	let { blocks = $bindable(), onChange }: Props = $props();

	// Track which block is currently being edited
	let editingBlockId = $state<string | null>(null);

	/**
	 * Start editing a block
	 */
	function handleEdit(blockId: string) {
		editingBlockId = blockId;
	}

	/**
	 * Stop editing current block
	 */
	function handleBlur() {
		editingBlockId = null;
		onChange();
	}

	/**
	 * Update block content
	 */
	function handleBlockChange(blockId: string, content: string) {
		const blockIndex = blocks.findIndex((b) => b.id === blockId);
		if (blockIndex !== -1) {
			blocks[blockIndex].content = content;
			// Trigger reactivity
			blocks = [...blocks];
		}
	}

	/**
	 * Generate unique block ID (with fallback)
	 */
	function generateBlockId(): string {
		if (typeof crypto !== 'undefined' && crypto.randomUUID) {
			return `block-${crypto.randomUUID()}`;
		}
		return `block-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
	}

	/**
	 * Add a new empty paragraph block when empty state is clicked
	 */
	function handleEmptyStateClick() {
		const newBlock: BlockType = {
			id: generateBlockId(),
			type: 'paragraph',
			content: ''
		};
		blocks = [newBlock];
		editingBlockId = newBlock.id;
		onChange();
	}
</script>

<div class="block-list space-y-1">
	{#each blocks as block (block.id)}
		<Block
			{block}
			isEditing={editingBlockId === block.id}
			onEdit={() => handleEdit(block.id)}
			onBlur={handleBlur}
			onChange={(content) => handleBlockChange(block.id, content)}
		/>
	{/each}

	{#if blocks.length === 0}
		<div
			class="empty-state text-gray-400 dark:text-gray-600 italic p-4 text-center cursor-text hover:bg-gray-50 dark:hover:bg-gray-800 rounded transition-colors"
			onclick={handleEmptyStateClick}
			role="button"
			tabindex="0"
			onkeydown={(e) => {
				if (e.key === 'Enter' || e.key === ' ') {
					e.preventDefault();
					handleEmptyStateClick();
				}
			}}
		>
			Click here to start typing...
		</div>
	{/if}
</div>

<style>
	.block-list {
		min-height: 200px;
	}
</style>
