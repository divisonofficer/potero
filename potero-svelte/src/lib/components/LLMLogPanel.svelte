<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type LLMLogEntry, type LLMUsageStats, type LLMStatus, type LLMLogDetail } from '$lib/api/client';

	let logs = $state<LLMLogEntry[]>([]);
	let stats = $state<LLMUsageStats | null>(null);
	let status = $state<LLMStatus | null>(null);
	let isLoading = $state(true);
	let selectedLog = $state<LLMLogDetail | null>(null);
	let filterPurpose = $state<string>('');
	let isRefreshing = $state(false);

	onMount(() => {
		loadData();
		// Auto-refresh every 10 seconds
		const interval = setInterval(() => {
			if (!selectedLog) {
				loadData(true);
			}
		}, 10000);
		return () => clearInterval(interval);
	});

	async function loadData(silent = false) {
		if (!silent) isLoading = true;
		isRefreshing = true;

		const [logsResult, statsResult, statusResult] = await Promise.all([
			api.getLLMLogs(50, filterPurpose || undefined),
			api.getLLMStats(),
			api.getLLMStatus()
		]);

		if (logsResult.success && logsResult.data) {
			logs = logsResult.data;
		}
		if (statsResult.success && statsResult.data) {
			stats = statsResult.data;
		}
		if (statusResult.success && statusResult.data) {
			status = statusResult.data;
		}

		isLoading = false;
		isRefreshing = false;
	}

	async function viewLogDetail(logId: string) {
		const result = await api.getLLMLogDetail(logId);
		if (result.success && result.data) {
			selectedLog = result.data;
		}
	}

	async function clearLogs() {
		if (confirm('Clear all LLM logs? This cannot be undone.')) {
			await api.clearLLMLogs();
			await loadData();
		}
	}

	function formatTimestamp(ts: string): string {
		const date = new Date(ts);
		return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
	}

	function formatDate(ts: string): string {
		const date = new Date(ts);
		return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
	}

	function getPurposeColor(purpose: string): string {
		const colors: Record<string, string> = {
			chat: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
			title_cleaning: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
			author_cleaning: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200',
			auto_tag: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
			summarize: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200'
		};
		return colors[purpose] || 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200';
	}

	const purposes = ['', 'chat', 'title_cleaning', 'author_cleaning', 'auto_tag', 'summarize'];
</script>

<div class="flex h-full flex-col">
	<!-- Header -->
	<div class="flex items-center justify-between border-b px-4 py-3">
		<h2 class="text-lg font-semibold">LLM Usage Log</h2>
		<div class="flex items-center gap-2">
			<!-- Filter -->
			<select
				bind:value={filterPurpose}
				onchange={() => loadData()}
				class="rounded-md border bg-background px-2 py-1 text-sm"
			>
				<option value="">All purposes</option>
				{#each purposes.slice(1) as purpose}
					<option value={purpose}>{purpose.replace('_', ' ')}</option>
				{/each}
			</select>
			<!-- Refresh button -->
			<button
				class="rounded p-1.5 text-muted-foreground hover:bg-muted"
				onclick={() => loadData()}
				disabled={isRefreshing}
				title="Refresh"
			>
				<svg class="h-4 w-4 {isRefreshing ? 'animate-spin' : ''}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M1 4v6h6M23 20v-6h-6" />
					<path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" />
				</svg>
			</button>
			<!-- Clear button -->
			<button
				class="rounded p-1.5 text-muted-foreground hover:bg-destructive hover:text-destructive-foreground"
				onclick={clearLogs}
				title="Clear logs"
			>
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
				</svg>
			</button>
		</div>
	</div>

	<!-- Status bar -->
	{#if status}
		<div class="flex items-center gap-4 border-b bg-muted/30 px-4 py-2 text-xs">
			<span class="flex items-center gap-1">
				<span class="h-2 w-2 rounded-full {status.configured ? 'bg-green-500' : 'bg-yellow-500'}"></span>
				{status.configured ? 'Configured' : 'Not configured'}
			</span>
			<span>Provider: <strong>{status.provider}</strong></span>
			{#if stats}
				<span class="ml-auto">
					{stats.totalCalls} calls | {stats.successfulCalls} success | ~{stats.totalInputTokensEstimate + stats.totalOutputTokensEstimate} tokens
				</span>
			{/if}
		</div>
	{/if}

	<!-- Content -->
	<div class="flex flex-1 overflow-hidden">
		<!-- Log list -->
		<div class="flex-1 overflow-auto">
			{#if isLoading}
				<div class="flex items-center justify-center py-12">
					<div class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
				</div>
			{:else if logs.length === 0}
				<div class="flex flex-col items-center justify-center py-12 text-muted-foreground">
					<svg class="mb-2 h-12 w-12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
					</svg>
					<p>No LLM calls yet</p>
					<p class="text-xs">LLM usage will appear here</p>
				</div>
			{:else}
				<div class="divide-y">
					{#each logs as log}
						<button
							class="w-full px-4 py-2 text-left hover:bg-muted/50 transition-colors {selectedLog?.id === log.id ? 'bg-muted' : ''}"
							onclick={() => viewLogDetail(log.id)}
						>
							<div class="flex items-center gap-2">
								<span class="rounded px-1.5 py-0.5 text-xs font-medium {getPurposeColor(log.purpose)}">
									{log.purpose.replace('_', ' ')}
								</span>
								<span class="text-xs text-muted-foreground">{log.provider}</span>
								<span class="ml-auto text-xs text-muted-foreground">
									{formatDate(log.timestamp)} {formatTimestamp(log.timestamp)}
								</span>
								{#if !log.success}
									<span class="rounded bg-destructive/10 px-1.5 py-0.5 text-xs text-destructive">failed</span>
								{/if}
							</div>
							<p class="mt-1 truncate text-sm">{log.inputPromptPreview}</p>
							<div class="mt-1 flex items-center gap-3 text-xs text-muted-foreground">
								<span>~{log.inputTokensEstimate} in</span>
								{#if log.outputTokensEstimate}
									<span>~{log.outputTokensEstimate} out</span>
								{/if}
								<span>{log.durationMs}ms</span>
								{#if log.paperTitle}
									<span class="truncate">Paper: {log.paperTitle}</span>
								{/if}
							</div>
						</button>
					{/each}
				</div>
			{/if}
		</div>

		<!-- Detail panel -->
		{#if selectedLog}
			<div class="w-96 border-l overflow-auto">
				<div class="sticky top-0 flex items-center justify-between border-b bg-background px-4 py-2">
					<h3 class="font-medium">Log Detail</h3>
					<button
						class="rounded p-1 hover:bg-muted"
						onclick={() => (selectedLog = null)}
						title="Close detail panel"
					>
						<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
							<path d="M18 6L6 18M6 6l12 12" />
						</svg>
					</button>
				</div>
				<div class="p-4 space-y-4">
					<!-- Meta info -->
					<div class="grid grid-cols-2 gap-2 text-sm">
						<div>
							<span class="text-xs text-muted-foreground">Purpose</span>
							<p class="font-medium">{selectedLog.purpose.replace('_', ' ')}</p>
						</div>
						<div>
							<span class="text-xs text-muted-foreground">Provider</span>
							<p class="font-medium">{selectedLog.provider}</p>
						</div>
						<div>
							<span class="text-xs text-muted-foreground">Duration</span>
							<p class="font-medium">{selectedLog.durationMs}ms</p>
						</div>
						<div>
							<span class="text-xs text-muted-foreground">Status</span>
							<p class="font-medium {selectedLog.success ? 'text-green-600' : 'text-destructive'}">
								{selectedLog.success ? 'Success' : 'Failed'}
							</p>
						</div>
						<div>
							<span class="text-xs text-muted-foreground">Input tokens</span>
							<p class="font-medium">~{selectedLog.inputTokensEstimate}</p>
						</div>
						<div>
							<span class="text-xs text-muted-foreground">Output tokens</span>
							<p class="font-medium">~{selectedLog.outputTokensEstimate ?? 0}</p>
						</div>
					</div>

					{#if selectedLog.paperTitle}
						<div>
							<span class="text-xs text-muted-foreground">Related Paper</span>
							<p class="text-sm">{selectedLog.paperTitle}</p>
						</div>
					{/if}

					{#if selectedLog.errorMessage}
						<div>
							<span class="text-xs text-muted-foreground">Error</span>
							<p class="rounded bg-destructive/10 p-2 text-sm text-destructive">{selectedLog.errorMessage}</p>
						</div>
					{/if}

					<!-- Input prompt -->
					<div>
						<span class="text-xs text-muted-foreground">Input Prompt</span>
						<pre class="mt-1 max-h-48 overflow-auto rounded bg-muted p-2 text-xs whitespace-pre-wrap">{selectedLog.inputPrompt}</pre>
					</div>

					<!-- Output response -->
					{#if selectedLog.outputResponse}
						<div>
							<span class="text-xs text-muted-foreground">Output Response</span>
							<pre class="mt-1 max-h-48 overflow-auto rounded bg-muted p-2 text-xs whitespace-pre-wrap">{selectedLog.outputResponse}</pre>
						</div>
					{/if}
				</div>
			</div>
		{/if}
	</div>
</div>
