<script lang="ts">
	import { Shield, Check, AlertCircle, Loader2, Key, ExternalLink } from 'lucide-svelte';
	import { api } from '$lib/api/client';
	import { setSSOComplete, setApiKey, onboardingState } from '$lib/stores/onboarding';

	let isLoggingIn = $state(false);
	let isFetchingApiKey = $state(false);
	let error = $state<string | null>(null);
	let apiKeyStatus = $state<'none' | 'fetching' | 'found' | 'not_found' | 'error'>('none');

	// Derived from store
	let ssoCompleted = $derived($onboardingState.ssoCompleted);
	let apiKeyObtained = $derived($onboardingState.apiKey !== undefined && $onboardingState.apiKey !== '');

	async function fetchAndSaveApiKey(accessToken: string) {
		apiKeyStatus = 'fetching';
		isFetchingApiKey = true;

		try {
			const result = await api.fetchGenAIApiKeys(accessToken);

			if (result.success && result.hasKeys && result.apiKey) {
				// Save API key to backend settings
				await api.updateSettings({ llmApiKey: result.apiKey });
				setApiKey(result.apiKey);
				apiKeyStatus = 'found';
			} else if (result.success && !result.hasKeys) {
				apiKeyStatus = 'not_found';
			} else {
				apiKeyStatus = 'error';
				error = result.error || 'Failed to fetch API keys';
			}
		} catch (e) {
			apiKeyStatus = 'error';
			error = e instanceof Error ? e.message : 'Failed to fetch API keys';
		} finally {
			isFetchingApiKey = false;
		}
	}

	function openApiKeyPage() {
		api.openExternalUrl('https://genai.postech.ac.kr/api-key');
	}

	async function retryFetchApiKey() {
		const token = $onboardingState.ssoToken;
		if (token) {
			await fetchAndSaveApiKey(token);
		}
	}

	async function handleSSOLogin() {
		isLoggingIn = true;
		error = null;

		try {
			const result = await api.loginSSO();

			if (result.success && result.accessToken) {
				const expiresAt = result.expiresIn
					? Date.now() + result.expiresIn * 1000
					: undefined;

				// Save to backend immediately
				const saveResult = await api.saveSSOToken(
					result.accessToken,
					'robi-gpt-dev',
					expiresAt
				);

				if (saveResult.success) {
					setSSOComplete(result.accessToken, expiresAt);

					// Automatically fetch API key after successful SSO login
					await fetchAndSaveApiKey(result.accessToken);
				} else {
					error = 'Failed to save SSO token';
				}
			} else if (result.error) {
				error = result.error;
			}
		} catch (e) {
			error = e instanceof Error ? e.message : 'SSO login failed';
		} finally {
			isLoggingIn = false;
		}
	}
</script>

<div class="sso-step">
	<div class="icon-container">
		<Shield size={48} />
	</div>

	<h2 class="title">POSTECH SSO Login</h2>
	<p class="description">
		Connect your POSTECH account to use the AI chat features and access the GenAI API.
	</p>

	{#if ssoCompleted}
		<div class="success-banner">
			<Check size={20} />
			<span>SSO login successful!</span>
		</div>

		<!-- API Key Status Section -->
		<div class="api-key-section">
			{#if apiKeyStatus === 'fetching' || isFetchingApiKey}
				<div class="api-key-status fetching">
					<Loader2 size={18} class="spinner" />
					<span>Fetching API key...</span>
				</div>
			{:else if apiKeyStatus === 'found' || apiKeyObtained}
				<div class="api-key-status success">
					<Key size={18} />
					<span>API key obtained successfully!</span>
				</div>
			{:else if apiKeyStatus === 'not_found'}
				<div class="api-key-status warning">
					<AlertCircle size={18} />
					<div class="api-key-warning-content">
						<p>No API key found. You need to generate one.</p>
						<div class="api-key-actions">
							<button class="generate-key-button" onclick={openApiKeyPage}>
								<ExternalLink size={16} />
								<span>Generate API Key</span>
							</button>
							<button class="retry-button" onclick={retryFetchApiKey}>
								<span>Retry</span>
							</button>
						</div>
					</div>
				</div>
			{:else if apiKeyStatus === 'error'}
				<div class="api-key-status error">
					<AlertCircle size={18} />
					<div class="api-key-error-content">
						<p>Failed to fetch API key</p>
						<button class="retry-button" onclick={retryFetchApiKey}>
							<span>Retry</span>
						</button>
					</div>
				</div>
			{/if}
		</div>
	{:else}
		<div class="login-section">
			<button
				class="login-button"
				disabled={isLoggingIn}
				onclick={handleSSOLogin}
			>
				{#if isLoggingIn}
					<Loader2 size={20} class="spinner" />
					<span>Logging in...</span>
				{:else}
					<Shield size={20} />
					<span>Login with POSTECH SSO</span>
				{/if}
			</button>

			{#if error}
				<div class="error-message">
					<AlertCircle size={16} />
					<span>{error}</span>
				</div>
			{/if}
		</div>
	{/if}

	<div class="info-box">
		<h4>What happens when you login?</h4>
		<ul>
			<li>A popup window will open for POSTECH authentication</li>
			<li>Your access token will be securely stored locally</li>
			<li>You'll be able to chat with papers using AI</li>
		</ul>
	</div>
</div>

<style>
	.sso-step {
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
		margin: 0 0 2rem;
		max-width: 400px;
		margin-left: auto;
		margin-right: auto;
	}

	.login-section {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1rem;
		margin-bottom: 2rem;
	}

	.login-button {
		display: inline-flex;
		align-items: center;
		gap: 0.75rem;
		padding: 1rem 2rem;
		background: var(--color-primary, #0066cc);
		color: white;
		border: none;
		border-radius: 12px;
		font-size: 1rem;
		font-weight: 600;
		cursor: pointer;
		transition: all 0.2s ease;
		box-shadow: 0 4px 12px rgba(0, 102, 204, 0.3);
	}

	.login-button:hover:not(:disabled) {
		background: var(--color-primary-dark, #0055aa);
		transform: translateY(-2px);
		box-shadow: 0 6px 16px rgba(0, 102, 204, 0.4);
	}

	.login-button:disabled {
		opacity: 0.7;
		cursor: not-allowed;
	}

	.login-button :global(.spinner) {
		animation: spin 1s linear infinite;
	}

	@keyframes spin {
		from {
			transform: rotate(0deg);
		}
		to {
			transform: rotate(360deg);
		}
	}

	.success-banner {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 1rem 1.5rem;
		background: #dcfce7;
		color: #166534;
		border-radius: 12px;
		font-weight: 500;
		margin-bottom: 2rem;
	}

	.error-message {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.75rem 1rem;
		background: #fef2f2;
		color: #dc2626;
		border-radius: 8px;
		font-size: 0.9rem;
	}

	.info-box {
		text-align: left;
		max-width: 400px;
		margin: 0 auto;
		padding: 1rem;
		background: var(--color-surface-secondary, #f8f9fa);
		border-radius: 12px;
	}

	.info-box h4 {
		font-size: 0.9rem;
		font-weight: 600;
		color: var(--color-text-primary, #333);
		margin: 0 0 0.75rem;
	}

	.info-box ul {
		margin: 0;
		padding-left: 1.25rem;
		font-size: 0.85rem;
		color: var(--color-text-secondary, #666);
	}

	.info-box li {
		margin-bottom: 0.5rem;
	}

	.info-box li:last-child {
		margin-bottom: 0;
	}

	/* API Key Section Styles */
	.api-key-section {
		margin-bottom: 1.5rem;
	}

	.api-key-status {
		display: flex;
		align-items: flex-start;
		gap: 0.75rem;
		padding: 1rem 1.5rem;
		border-radius: 12px;
		max-width: 400px;
		margin: 0 auto;
	}

	.api-key-status.fetching {
		background: #f0f9ff;
		color: #0369a1;
	}

	.api-key-status.fetching :global(.spinner) {
		animation: spin 1s linear infinite;
	}

	.api-key-status.success {
		background: #dcfce7;
		color: #166534;
	}

	.api-key-status.warning {
		background: #fef3c7;
		color: #92400e;
		text-align: left;
	}

	.api-key-status.error {
		background: #fef2f2;
		color: #dc2626;
		text-align: left;
	}

	.api-key-warning-content,
	.api-key-error-content {
		flex: 1;
	}

	.api-key-warning-content p,
	.api-key-error-content p {
		margin: 0 0 0.75rem;
		font-size: 0.9rem;
	}

	.api-key-actions {
		display: flex;
		gap: 0.5rem;
		flex-wrap: wrap;
	}

	.generate-key-button {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.5rem 1rem;
		background: #0066cc;
		color: white;
		border: none;
		border-radius: 8px;
		font-size: 0.85rem;
		font-weight: 500;
		cursor: pointer;
		transition: all 0.2s ease;
	}

	.generate-key-button:hover {
		background: #0055aa;
	}

	.retry-button {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.5rem 1rem;
		background: transparent;
		color: inherit;
		border: 1px solid currentColor;
		border-radius: 8px;
		font-size: 0.85rem;
		font-weight: 500;
		cursor: pointer;
		transition: all 0.2s ease;
	}

	.retry-button:hover {
		background: rgba(0, 0, 0, 0.05);
	}
</style>
