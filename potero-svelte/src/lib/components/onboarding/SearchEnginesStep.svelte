<script lang="ts">
	import { Search } from 'lucide-svelte';
	import { onboardingState, setSearchEngine } from '$lib/stores/onboarding';

	// Available search engines
	const searchEngines = [
		{
			id: 'semanticscholar',
			name: 'Semantic Scholar',
			description: 'Comprehensive scholarly database with 200M+ papers. Fast and reliable.',
			recommended: true,
			category: 'General'
		},
		{
			id: 'openalex',
			name: 'OpenAlex',
			description: 'Open catalog of scholarly works. Good for discovering open access papers.',
			recommended: false,
			category: 'General'
		},
		{
			id: 'pubmed',
			name: 'PubMed',
			description: 'Biomedical literature from NCBI. Essential for life sciences research.',
			recommended: false,
			category: 'Life Sciences'
		},
		{
			id: 'dblp',
			name: 'DBLP',
			description: 'Computer science bibliography. Great for finding CS papers.',
			recommended: false,
			category: 'Computer Science'
		}
	];

	// Derived from store
	let enabledEngines = $derived($onboardingState.searchEngines);
	let atLeastOneEnabled = $derived(Object.values(enabledEngines).some((v) => v));

	function handleToggle(id: string, enabled: boolean) {
		setSearchEngine(id, enabled);
	}
</script>

<div class="search-engines-step">
	<div class="icon-container">
		<Search size={48} />
	</div>

	<h2 class="title">Search Engines</h2>
	<p class="description">
		Choose which academic databases to search. You can enable multiple engines for comprehensive results.
	</p>

	<div class="engines-grid">
		{#each searchEngines as engine}
			<label
				class="engine-card"
				class:enabled={enabledEngines[engine.id]}
			>
				<input
					type="checkbox"
					checked={enabledEngines[engine.id]}
					onchange={(e) => handleToggle(engine.id, (e.target as HTMLInputElement).checked)}
				/>
				<div class="engine-content">
					<div class="engine-header">
						<span class="engine-name">{engine.name}</span>
						{#if engine.recommended}
							<span class="recommended-badge">Recommended</span>
						{/if}
					</div>
					<p class="engine-description">{engine.description}</p>
					<span class="engine-category">{engine.category}</span>
				</div>
				<div class="checkbox-indicator"></div>
			</label>
		{/each}
	</div>

	{#if !atLeastOneEnabled}
		<p class="validation-message">Please enable at least one search engine to continue.</p>
	{/if}
</div>

<style>
	.search-engines-step {
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

	.engines-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
		gap: 1rem;
		max-width: 600px;
		margin: 0 auto;
	}

	.engine-card {
		display: flex;
		align-items: flex-start;
		gap: 1rem;
		padding: 1rem;
		background: var(--color-surface, #fff);
		border: 2px solid var(--color-border, #e0e0e0);
		border-radius: 12px;
		cursor: pointer;
		transition: all 0.2s ease;
		text-align: left;
		position: relative;
	}

	.engine-card:hover {
		border-color: var(--color-primary, #0066cc);
		box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
	}

	.engine-card.enabled {
		border-color: var(--color-primary, #0066cc);
		background: rgba(0, 102, 204, 0.05);
	}

	.engine-card input[type='checkbox'] {
		position: absolute;
		opacity: 0;
		pointer-events: none;
	}

	.checkbox-indicator {
		width: 24px;
		height: 24px;
		border: 2px solid var(--color-border, #ccc);
		border-radius: 6px;
		flex-shrink: 0;
		transition: all 0.2s ease;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.engine-card.enabled .checkbox-indicator {
		background: var(--color-primary, #0066cc);
		border-color: var(--color-primary, #0066cc);
	}

	.engine-card.enabled .checkbox-indicator::after {
		content: '';
		width: 6px;
		height: 10px;
		border: solid white;
		border-width: 0 2px 2px 0;
		transform: rotate(45deg);
		margin-bottom: 2px;
	}

	.engine-content {
		flex: 1;
		min-width: 0;
	}

	.engine-header {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 0.25rem;
	}

	.engine-name {
		font-weight: 600;
		font-size: 1rem;
		color: var(--color-text-primary, #333);
	}

	.recommended-badge {
		font-size: 0.7rem;
		padding: 0.15rem 0.5rem;
		background: #e6f7ff;
		color: #0066cc;
		border-radius: 4px;
		font-weight: 500;
	}

	.engine-description {
		font-size: 0.85rem;
		color: var(--color-text-secondary, #666);
		margin: 0 0 0.5rem;
		line-height: 1.4;
	}

	.engine-category {
		font-size: 0.75rem;
		color: var(--color-text-tertiary, #888);
		padding: 0.15rem 0.5rem;
		background: var(--color-surface-secondary, #f5f5f5);
		border-radius: 4px;
	}

	.validation-message {
		color: #dc2626;
		font-size: 0.9rem;
		margin-top: 1rem;
	}
</style>
