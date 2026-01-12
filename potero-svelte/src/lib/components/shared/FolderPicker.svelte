<script lang="ts">
	import { Folder } from 'lucide-svelte';

	interface Props {
		value: string;
		label?: string;
		placeholder?: string;
		disabled?: boolean;
		onSelect: (path: string) => void;
	}

	let {
		value,
		label = 'Storage Path',
		placeholder = 'Select a folder...',
		disabled = false,
		onSelect
	}: Props = $props();

	let isSelecting = $state(false);

	async function handleBrowse() {
		if (disabled || isSelecting) return;

		// Check if running in Electron
		if (typeof window !== 'undefined' && window.electronAPI?.selectDirectory) {
			isSelecting = true;
			try {
				const selectedPath = await window.electronAPI.selectDirectory();
				if (selectedPath) {
					onSelect(selectedPath);
				}
			} catch (error) {
				console.error('Failed to select directory:', error);
			} finally {
				isSelecting = false;
			}
		} else {
			// Fallback for non-Electron environment (manual input)
			console.warn('Directory picker not available in browser environment');
		}
	}

	function handleInputChange(e: Event) {
		const target = e.target as HTMLInputElement;
		onSelect(target.value);
	}
</script>

<div class="folder-picker">
	{#if label}
		<label class="folder-label">{label}</label>
	{/if}
	<div class="folder-input-group">
		<div class="folder-icon">
			<Folder size={18} />
		</div>
		<input
			type="text"
			{value}
			{placeholder}
			{disabled}
			onchange={handleInputChange}
			class="folder-input"
		/>
		<button
			type="button"
			class="browse-button"
			disabled={disabled || isSelecting}
			onclick={handleBrowse}
		>
			{isSelecting ? 'Selecting...' : 'Browse'}
		</button>
	</div>
	<p class="folder-hint">
		Choose where to store PDF files and database. Default: Documents/Potero
	</p>
</div>

<style>
	.folder-picker {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.folder-label {
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--color-text-primary, #333);
	}

	.folder-input-group {
		display: flex;
		align-items: stretch;
		border: 1px solid var(--color-border, #ccc);
		border-radius: 8px;
		overflow: hidden;
		background: var(--color-surface, #fff);
		transition: border-color 0.2s, box-shadow 0.2s;
	}

	.folder-input-group:focus-within {
		border-color: var(--color-primary, #0066cc);
		box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
	}

	.folder-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 0.75rem;
		background: var(--color-surface-secondary, #f5f5f5);
		color: var(--color-text-secondary, #666);
		border-right: 1px solid var(--color-border, #ccc);
	}

	.folder-input {
		flex: 1;
		padding: 0.75rem;
		border: none;
		font-size: 0.95rem;
		background: transparent;
		min-width: 0;
	}

	.folder-input:focus {
		outline: none;
	}

	.folder-input:disabled {
		background-color: var(--color-surface-disabled, #f5f5f5);
		cursor: not-allowed;
	}

	.browse-button {
		padding: 0.75rem 1.25rem;
		background: var(--color-primary, #0066cc);
		color: white;
		border: none;
		font-size: 0.9rem;
		font-weight: 500;
		cursor: pointer;
		transition: background-color 0.2s;
	}

	.browse-button:hover:not(:disabled) {
		background: var(--color-primary-dark, #0055aa);
	}

	.browse-button:disabled {
		background: var(--color-surface-disabled, #ccc);
		cursor: not-allowed;
	}

	.folder-hint {
		font-size: 0.8rem;
		color: var(--color-text-tertiary, #888);
		margin: 0;
	}
</style>
