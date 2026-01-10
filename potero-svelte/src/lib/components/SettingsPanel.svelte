<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type APIConfigDto } from '$lib/api/client';

	let apiConfigs: APIConfigDto[] = $state([]);
	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);

	onMount(async () => {
		await loadAPIConfigs();
	});

	async function loadAPIConfigs() {
		loading = true;
		error = null;

		const response = await api.getAPIConfigs();
		if (response.success && response.data) {
			apiConfigs = response.data;
		} else {
			error = response.error?.message || 'Failed to load API configurations';
		}

		loading = false;
	}

	async function toggleAPI(id: string, enabled: boolean) {
		saving = true;
		error = null;

		const response = await api.updateAPIConfig(id, enabled);
		if (response.success) {
			await loadAPIConfigs();
		} else {
			error = response.error?.message || 'Failed to update API configuration';
		}

		saving = false;
	}

	async function updateAPIKey(id: string, key: string) {
		if (!key || key.trim() === '') return;

		saving = true;
		error = null;

		const config = apiConfigs.find((c) => c.id === id);
		const response = await api.updateAPIConfig(id, config?.enabled ?? true, key);

		if (response.success) {
			await loadAPIConfigs();
		} else {
			error = response.error?.message || 'Failed to update API key';
		}

		saving = false;
	}

	// Group APIs by category
	let groupedAPIs = $derived(Object.entries(
		apiConfigs.reduce(
			(acc, apiConfig) => {
				if (!acc[apiConfig.category]) acc[apiConfig.category] = [];
				acc[apiConfig.category].push(apiConfig);
				return acc;
			},
			{} as Record<string, APIConfigDto[]>
		)
	));

	function getCategoryDisplayName(category: string): string {
		const names: Record<string, string> = {
			general: '🌐 General Purpose',
			lifesciences: '🧬 Life Sciences',
			computerscience: '💻 Computer Science',
			openaccess: '📖 Open Access'
		};
		return names[category] || category;
	}
</script>

<div class="settings-panel">
	<h2>Search API Configuration</h2>
	<p class="description">
		Configure which academic APIs to use for searching papers. Enable or disable each API and
		provide API keys where required.
	</p>

	{#if error}
		<div class="error-banner">
			{error}
		</div>
	{/if}

	{#if loading}
		<div class="loading">Loading API configurations...</div>
	{:else}
		{#each groupedAPIs as [category, apis]}
			<section class="api-category">
				<h3>{getCategoryDisplayName(category)}</h3>

				{#each apis as apiConfig}
					<div class="api-card">
						<div class="api-header">
							<label class="api-toggle">
								<input
									type="checkbox"
									checked={apiConfig.enabled}
									disabled={saving}
									onchange={(e) => {
										const target = e.target as HTMLInputElement;
										toggleAPI(apiConfig.id, target.checked);
									}}
								/>
								<span class="api-name">{apiConfig.name}</span>
							</label>

							{#if apiConfig.keyRegistrationUrl}
								<a
									href={apiConfig.keyRegistrationUrl}
									target="_blank"
									rel="noopener noreferrer"
									class="api-link"
								>
									Get API Key →
								</a>
							{/if}
						</div>

						<p class="api-description">{apiConfig.description}</p>

						{#if apiConfig.requiresKey && apiConfig.enabled}
							<div class="api-key-input">
								<label>
									API Key:
									<input
										type="password"
										placeholder={apiConfig.hasKey ? 'Key configured' : 'Enter API key'}
										disabled={saving}
										onchange={(e) => {
											const target = e.target as HTMLInputElement;
											const key = target.value.trim();
											if (key) {
												updateAPIKey(apiConfig.id, key);
												target.value = '';
											}
										}}
									/>
								</label>
								{#if apiConfig.keyMasked}
									<span class="key-hint">Current: {apiConfig.keyMasked}</span>
								{/if}
							</div>
						{/if}
					</div>
				{/each}
			</section>
		{/each}
	{/if}
</div>

<style>
	.settings-panel {
		padding: 2rem;
		max-width: 1200px;
		margin: 0 auto;
	}

	h2 {
		margin-bottom: 0.5rem;
		font-size: 1.75rem;
		font-weight: 600;
	}

	.description {
		color: var(--color-text-secondary, #666);
		margin-bottom: 2rem;
		font-size: 0.95rem;
	}

	.error-banner {
		background-color: var(--color-error-bg, #fee);
		color: var(--color-error-text, #c00);
		padding: 1rem;
		border-radius: 8px;
		margin-bottom: 1.5rem;
		border: 1px solid var(--color-error-border, #fcc);
	}

	.loading {
		text-align: center;
		padding: 3rem;
		color: var(--color-text-secondary, #666);
	}

	.api-category {
		margin-bottom: 2.5rem;
	}

	.api-category h3 {
		font-size: 1.25rem;
		font-weight: 600;
		margin-bottom: 1rem;
		color: var(--color-text-primary, #333);
	}

	.api-card {
		background: var(--color-surface, #fff);
		border: 1px solid var(--color-border, #e0e0e0);
		border-radius: 8px;
		padding: 1.25rem;
		margin-bottom: 1rem;
		transition: box-shadow 0.2s;
	}

	.api-card:hover {
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
	}

	.api-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.75rem;
	}

	.api-toggle {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		cursor: pointer;
		user-select: none;
	}

	.api-toggle input[type='checkbox'] {
		width: 18px;
		height: 18px;
		cursor: pointer;
	}

	.api-toggle input[type='checkbox']:disabled {
		cursor: not-allowed;
		opacity: 0.5;
	}

	.api-name {
		font-weight: 600;
		font-size: 1.05rem;
	}

	.api-link {
		color: var(--color-primary, #0066cc);
		text-decoration: none;
		font-size: 0.9rem;
		font-weight: 500;
	}

	.api-link:hover {
		text-decoration: underline;
	}

	.api-description {
		color: var(--color-text-secondary, #666);
		font-size: 0.9rem;
		margin: 0.5rem 0;
		line-height: 1.5;
	}

	.api-key-input {
		margin-top: 1rem;
		padding-top: 1rem;
		border-top: 1px solid var(--color-border-light, #f0f0f0);
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.api-key-input label {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		font-size: 0.9rem;
		font-weight: 500;
	}

	.api-key-input input {
		width: 100%;
		max-width: 500px;
		padding: 0.625rem;
		border: 1px solid var(--color-border, #ccc);
		border-radius: 6px;
		font-size: 0.95rem;
		transition: border-color 0.2s;
	}

	.api-key-input input:focus {
		outline: none;
		border-color: var(--color-primary, #0066cc);
		box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
	}

	.api-key-input input:disabled {
		background-color: var(--color-surface-disabled, #f5f5f5);
		cursor: not-allowed;
	}

	.key-hint {
		font-size: 0.85rem;
		color: var(--color-text-tertiary, #888);
		font-family: monospace;
	}
</style>
