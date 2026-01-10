<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type APIConfigDto, type Settings } from '$lib/api/client';

	let apiConfigs: APIConfigDto[] = $state([]);
	let settings: Settings | null = $state(null);
	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);

	onMount(async () => {
		await loadAPIConfigs();
		await loadSettings();
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

	async function loadSettings() {
		loading = true;
		error = null;

		const response = await api.getSettings();
		if (response.success && response.data) {
			settings = response.data;
		} else {
			error = response.error?.message || 'Failed to load settings';
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

	async function toggleReferenceEngine(engine: 'grobid' | 'pdftotext' | 'ocr', enabled: boolean) {
		if (!settings) return;

		saving = true;
		error = null;

		const updateData: Partial<Settings> = {};
		if (engine === 'grobid') updateData.grobidEnabled = enabled;
		else if (engine === 'pdftotext') updateData.pdftotextEnabled = enabled;
		else if (engine === 'ocr') updateData.ocrEnabled = enabled;

		const response = await api.updateSettings(updateData);
		if (response.success && response.data) {
			settings = response.data;
		} else {
			error = response.error?.message || 'Failed to update reference engine settings';
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
	<h2>Settings</h2>
	<p class="description">
		Configure application settings including reference extraction engines and search APIs.
	</p>

	{#if error}
		<div class="error-banner">
			{error}
		</div>
	{/if}

	{#if loading}
		<div class="loading">Loading settings...</div>
	{:else}
		<!-- Reference Extraction Engines -->
		{#if settings}
			<section class="settings-section">
				<h3>📄 Reference Extraction Engines</h3>
				<p class="section-description">
					Select which engines to use for extracting references from PDFs. Multiple engines can be enabled.
					Engines are tried in order: GROBID → arXiv fallback → pdftotext → OCR → LLM parsing.
				</p>

				<div class="engine-grid">
					<!-- GROBID -->
					<div class="engine-card">
						<div class="engine-header">
							<label class="engine-toggle">
								<input
									type="checkbox"
									checked={settings.grobidEnabled ?? true}
									disabled={saving}
									onchange={(e) => {
										const target = e.target as HTMLInputElement;
										toggleReferenceEngine('grobid', target.checked);
									}}
								/>
								<span class="engine-name">GROBID</span>
							</label>
							<span class="engine-badge recommended">⚡ Recommended</span>
						</div>
						<p class="engine-description">
							Machine learning-based extraction. Most accurate for citation metadata and location information.
						</p>
						<div class="engine-features">
							<span class="feature">High accuracy</span>
							<span class="feature">Extracts citation spans</span>
							<span class="feature">Requires GROBID server</span>
						</div>
					</div>

					<!-- pdftotext -->
					<div class="engine-card">
						<div class="engine-header">
							<label class="engine-toggle">
								<input
									type="checkbox"
									checked={settings.pdftotextEnabled ?? true}
									disabled={saving}
									onchange={(e) => {
										const target = e.target as HTMLInputElement;
										toggleReferenceEngine('pdftotext', target.checked);
									}}
								/>
								<span class="engine-name">pdftotext (Poppler)</span>
							</label>
							<span class="engine-badge fallback">🔄 Fallback</span>
						</div>
						<p class="engine-description">
							Fallback text extraction. Better at handling PDFs with font encoding issues (ToUnicode CMap problems).
						</p>
						<div class="engine-features">
							<span class="feature">Good for garbled PDFs</span>
							<span class="feature">Fast extraction</span>
							<span class="feature">Requires poppler-utils</span>
						</div>
					</div>

					<!-- OCR -->
					<div class="engine-card">
						<div class="engine-header">
							<label class="engine-toggle">
								<input
									type="checkbox"
									checked={settings.ocrEnabled ?? false}
									disabled={saving}
									onchange={(e) => {
										const target = e.target as HTMLInputElement;
										toggleReferenceEngine('ocr', target.checked);
									}}
								/>
								<span class="engine-name">OCR (Tesseract)</span>
							</label>
							<span class="engine-badge slow">🐢 Slow</span>
						</div>
						<p class="engine-description">
							Optical character recognition for image-based or severely corrupted PDFs. Used as last resort.
						</p>
						<div class="engine-features">
							<span class="feature">For scanned PDFs</span>
							<span class="feature">Slowest option</span>
							<span class="feature">Requires Tesseract</span>
						</div>
					</div>
				</div>
			</section>
		{/if}

		<!-- Search API Configuration -->
		<section class="settings-section">
			<h3>🔍 Search API Configuration</h3>
			<p class="section-description">
				Configure which academic APIs to use for searching papers. Enable or disable each API and
				provide API keys where required.
			</p>

		{#each groupedAPIs as [category, apis]}
			<section class="api-category">
				<h3>{getCategoryDisplayName(category)}</h3>

				{#each apis as apiConfig}
					<div class="api-card {apiConfig.enabled ? 'enabled' : ''}">
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

						{#if (apiConfig.requiresKey || apiConfig.keyRegistrationUrl) && apiConfig.enabled}
							<div class="api-key-input">
								<label>
									API Key{#if !apiConfig.requiresKey} (Optional){/if}:
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
		</section>
	{/if}
</div>

<style>
	.settings-panel {
		padding: 2rem;
		max-width: 1200px;
		margin: 0 auto;
		background: linear-gradient(to bottom, rgba(255, 255, 255, 0), rgba(249, 250, 251, 0.5));
		min-height: 100vh;
	}

	.settings-section {
		margin-bottom: 3rem;
	}

	.settings-section h3 {
		font-size: 1.5rem;
		font-weight: 600;
		margin-bottom: 0.75rem;
		color: var(--color-text-primary, #333);
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.section-description {
		color: var(--color-text-secondary, #666);
		margin-bottom: 1.5rem;
		font-size: 0.95rem;
		line-height: 1.6;
	}

	.engine-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
		gap: 1rem;
	}

	.engine-card {
		background: var(--color-surface, #fff);
		border: 1px solid var(--color-border, #e0e0e0);
		border-radius: 12px;
		padding: 1.25rem;
		transition: all 0.2s ease;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
	}

	.engine-card:hover {
		box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
		border-color: var(--color-primary, #0066cc);
		transform: translateY(-2px);
	}

	.engine-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.75rem;
	}

	.engine-toggle {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		cursor: pointer;
		user-select: none;
	}

	.engine-toggle input[type='checkbox'] {
		width: 20px;
		height: 20px;
		cursor: pointer;
		accent-color: var(--color-primary, #0066cc);
		transition: all 0.2s;
	}

	.engine-toggle input[type='checkbox']:hover:not(:disabled) {
		transform: scale(1.1);
	}

	.engine-toggle input[type='checkbox']:disabled {
		cursor: not-allowed;
		opacity: 0.5;
	}

	.engine-name {
		font-weight: 600;
		font-size: 1.05rem;
	}

	.engine-badge {
		padding: 0.25rem 0.75rem;
		border-radius: 12px;
		font-size: 0.8rem;
		font-weight: 500;
	}

	.engine-badge.recommended {
		background-color: #e6f7ff;
		color: #0066cc;
	}

	.engine-badge.fallback {
		background-color: #fff7e6;
		color: #cc7a00;
	}

	.engine-badge.slow {
		background-color: #fff0f0;
		color: #cc0000;
	}

	.engine-description {
		color: var(--color-text-secondary, #666);
		font-size: 0.9rem;
		margin: 0.5rem 0;
		line-height: 1.5;
	}

	.engine-features {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		margin-top: 0.75rem;
	}

	.feature {
		padding: 0.25rem 0.625rem;
		background-color: var(--color-surface-secondary, #f5f5f5);
		border-radius: 6px;
		font-size: 0.8rem;
		color: var(--color-text-tertiary, #666);
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
		border-radius: 12px;
		padding: 1.25rem;
		margin-bottom: 1rem;
		transition: all 0.2s ease;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
		position: relative;
	}

	.api-card:hover {
		box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
		transform: translateY(-2px);
	}

	/* Status indicator for enabled/disabled APIs */
	.api-card::before {
		content: '';
		position: absolute;
		top: 1rem;
		right: 1rem;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background-color: #ef4444;
		box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
	}

	.api-card.enabled::before {
		background-color: #22c55e;
		box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2);
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
		width: 20px;
		height: 20px;
		cursor: pointer;
		accent-color: var(--color-primary, #0066cc);
		transition: all 0.2s;
	}

	.api-toggle input[type='checkbox']:hover:not(:disabled) {
		transform: scale(1.1);
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
