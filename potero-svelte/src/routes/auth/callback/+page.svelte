<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api/client';

	let status = $state<'loading' | 'success' | 'error'>('loading');
	let message = $state('Processing SSO login...');

	onMount(async () => {
		try {
			// Parse URL fragment for access_token
			const hash = window.location.hash.substring(1); // Remove '#'
			const params = new URLSearchParams(hash);

			const accessToken = params.get('access_token');
			const expiresIn = params.get('expires_in');

			if (!accessToken) {
				throw new Error('No access token found in callback URL');
			}

			// Calculate expiry timestamp
			const expiresAt = expiresIn
				? Date.now() + parseInt(expiresIn) * 1000
				: undefined;

			// Save token to backend
			const result = await api.saveSSOToken(
				accessToken,
				'robi-gpt-dev',
				expiresAt
			);

			if (result.success) {
				status = 'success';
				message = 'SSO login successful! Redirecting to settings...';

				// Redirect to settings after 2 seconds
				setTimeout(() => {
					goto('/?tab=settings');
				}, 2000);
			} else {
				throw new Error(result.error || 'Failed to save SSO token');
			}
		} catch (error) {
			status = 'error';
			message = error instanceof Error ? error.message : 'Unknown error occurred';
			console.error('[SSO Callback] Error:', error);
		}
	});
</script>

<div class="flex h-screen items-center justify-center bg-background">
	<div class="w-full max-w-md rounded-lg border bg-card p-8 text-center shadow-lg">
		{#if status === 'loading'}
			<div class="mb-4 flex justify-center">
				<div class="h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
			</div>
			<h2 class="mb-2 text-xl font-semibold">Processing Login</h2>
			<p class="text-sm text-muted-foreground">{message}</p>
		{:else if status === 'success'}
			<div class="mb-4 flex justify-center">
				<svg class="h-12 w-12 text-green-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M5 13l4 4L19 7" />
				</svg>
			</div>
			<h2 class="mb-2 text-xl font-semibold text-green-600 dark:text-green-400">Success!</h2>
			<p class="text-sm text-muted-foreground">{message}</p>
		{:else}
			<div class="mb-4 flex justify-center">
				<svg class="h-12 w-12 text-red-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<circle cx="12" cy="12" r="10" />
					<line x1="15" y1="9" x2="9" y2="15" />
					<line x1="9" y1="9" x2="15" y2="15" />
				</svg>
			</div>
			<h2 class="mb-2 text-xl font-semibold text-red-600 dark:text-red-400">Error</h2>
			<p class="text-sm text-muted-foreground">{message}</p>
			<button
				class="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
				onclick={() => goto('/?tab=settings')}
			>
				Go to Settings
			</button>
		{/if}
	</div>
</div>
