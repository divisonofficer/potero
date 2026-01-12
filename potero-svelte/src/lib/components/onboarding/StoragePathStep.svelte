<script lang="ts">
	import { FolderOpen, HardDrive, Database } from 'lucide-svelte';
	import { onboardingState, setStoragePath } from '$lib/stores/onboarding';
	import { FolderPicker } from '$lib/components/shared';

	// Derived from store
	let storagePath = $derived($onboardingState.storagePath);
	let isValid = $derived(storagePath.trim().length > 0);

	function handlePathSelect(path: string) {
		setStoragePath(path);
	}
</script>

<div class="storage-step">
	<div class="icon-container">
		<FolderOpen size={48} />
	</div>

	<h2 class="title">Storage Location</h2>
	<p class="description">
		Choose where to store your PDF files and database. This folder will contain all your research papers.
	</p>

	<div class="storage-form">
		<FolderPicker
			value={storagePath}
			label="Storage Folder"
			placeholder="C:\Users\Documents\Potero"
			onSelect={handlePathSelect}
		/>
	</div>

	{#if !isValid}
		<p class="validation-message">Please select a storage folder to continue.</p>
	{/if}

	<div class="info-cards">
		<div class="info-card">
			<div class="info-icon">
				<HardDrive size={20} />
			</div>
			<div class="info-content">
				<h4>PDF Files</h4>
				<p>Downloaded and imported PDFs will be stored in a 'pdfs' subfolder</p>
			</div>
		</div>

		<div class="info-card">
			<div class="info-icon">
				<Database size={20} />
			</div>
			<div class="info-content">
				<h4>Database</h4>
				<p>Paper metadata, notes, and settings are stored in a local SQLite database</p>
			</div>
		</div>
	</div>
</div>

<style>
	.storage-step {
		text-align: center;
		padding: 1rem;
		animation: fadeIn 0.5s ease;
	}

	@keyframes fadeIn {
		from {
			opacity: 0;
			transform: translateY(10px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.icon-container {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 80px;
		height: 80px;
		border-radius: 20px;
		background: linear-gradient(135deg, #0066cc, #0044aa);
		color: white;
		margin-bottom: 1.5rem;
	}

	.title {
		font-size: 1.5rem;
		font-weight: 600;
		color: var(--color-text-primary, #333);
		margin: 0 0 0.5rem;
	}

	.description {
		font-size: 0.95rem;
		color: var(--color-text-secondary, #666);
		margin: 0 0 1.5rem;
		max-width: 450px;
		margin-left: auto;
		margin-right: auto;
	}

	.storage-form {
		max-width: 500px;
		margin: 0 auto 1.5rem;
		text-align: left;
	}

	.validation-message {
		color: #dc2626;
		font-size: 0.9rem;
		margin-top: 0.5rem;
	}

	.info-cards {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 1rem;
		max-width: 500px;
		margin: 1.5rem auto 0;
	}

	.info-card {
		display: flex;
		align-items: flex-start;
		gap: 0.75rem;
		padding: 1rem;
		background: var(--color-surface-secondary, #f8f9fa);
		border-radius: 12px;
		text-align: left;
	}

	.info-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		height: 40px;
		border-radius: 10px;
		background: white;
		color: var(--color-primary, #0066cc);
		flex-shrink: 0;
	}

	.info-content h4 {
		font-size: 0.9rem;
		font-weight: 600;
		color: var(--color-text-primary, #333);
		margin: 0 0 0.25rem;
	}

	.info-content p {
		font-size: 0.8rem;
		color: var(--color-text-secondary, #666);
		margin: 0;
		line-height: 1.4;
	}
</style>
