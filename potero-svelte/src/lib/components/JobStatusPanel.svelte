<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { api } from '$lib/api/client';

	interface Job {
		id: string;
		type: string;
		title: string;
		description: string;
		status: string;
		progress: number;
		progressMessage: string;
		createdAt: string;
		startedAt: string | null;
		completedAt: string | null;
		error: string | null;
		paperId: string | null;
	}

	let jobs = $state<Job[]>([]);
	let isExpanded = $state(false);
	let pollInterval: ReturnType<typeof setInterval> | null = null;

	// Only show active jobs (pending/running)
	let activeJobs = $derived(jobs.filter((j) => j.status === 'PENDING' || j.status === 'RUNNING'));

	// Recently completed jobs (last 30 seconds)
	let recentlyCompletedJobs = $derived(
		jobs.filter((j) => {
			if (j.status !== 'COMPLETED' && j.status !== 'FAILED') return false;
			if (!j.completedAt) return false;
			const completedTime = new Date(j.completedAt).getTime();
			const now = Date.now();
			return now - completedTime < 30000; // 30 seconds
		})
	);

	let visibleJobs = $derived([...activeJobs, ...recentlyCompletedJobs]);
	let hasJobs = $derived(visibleJobs.length > 0);

	async function fetchJobs() {
		const result = await api.request<Job[]>('GET', '/jobs/active');
		if (result.success && result.data) {
			jobs = result.data;
		}
	}

	function getStatusIcon(status: string) {
		switch (status) {
			case 'PENDING':
				return 'clock';
			case 'RUNNING':
				return 'spinner';
			case 'COMPLETED':
				return 'check';
			case 'FAILED':
				return 'x';
			case 'CANCELLED':
				return 'x';
			default:
				return 'clock';
		}
	}

	function getStatusColor(status: string) {
		switch (status) {
			case 'PENDING':
				return 'text-muted-foreground';
			case 'RUNNING':
				return 'text-primary';
			case 'COMPLETED':
				return 'text-green-500';
			case 'FAILED':
				return 'text-destructive';
			case 'CANCELLED':
				return 'text-muted-foreground';
			default:
				return 'text-muted-foreground';
		}
	}

	function getJobTypeLabel(type: string) {
		switch (type) {
			case 'PDF_ANALYSIS':
				return 'PDF Analysis';
			case 'AUTO_TAGGING':
				return 'Auto Tagging';
			case 'METADATA_LOOKUP':
				return 'Metadata Lookup';
			case 'TAG_MERGE':
				return 'Tag Merge';
			case 'BULK_IMPORT':
				return 'Bulk Import';
			default:
				return type;
		}
	}

	onMount(() => {
		fetchJobs();
		// Poll every 2 seconds
		pollInterval = setInterval(fetchJobs, 2000);
	});

	onDestroy(() => {
		if (pollInterval) {
			clearInterval(pollInterval);
		}
	});
</script>

<!-- Job Status Panel - Fixed position at bottom right -->
{#if hasJobs}
	<div class="fixed bottom-4 right-4 z-40 flex flex-col items-end gap-2">
		<!-- Collapsed view - just shows count -->
		{#if !isExpanded}
			<button
				class="flex items-center gap-2 rounded-lg bg-background border shadow-lg px-3 py-2 hover:bg-muted transition-colors"
				onclick={() => (isExpanded = true)}
			>
				{#if activeJobs.length > 0}
					<div class="h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
					<span class="text-sm font-medium">{activeJobs.length} task{activeJobs.length > 1 ? 's' : ''} running</span>
				{:else}
					<svg class="h-4 w-4 text-green-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<polyline points="20 6 9 17 4 12" />
					</svg>
					<span class="text-sm font-medium">{recentlyCompletedJobs.length} completed</span>
				{/if}
			</button>
		{:else}
			<!-- Expanded view -->
			<div class="w-80 rounded-lg bg-background border shadow-xl overflow-hidden">
				<!-- Header -->
				<div class="flex items-center justify-between border-b px-3 py-2 bg-muted/30">
					<span class="text-sm font-medium">Background Tasks</span>
					<button
						class="rounded p-1 hover:bg-muted"
						onclick={() => (isExpanded = false)}
					>
						<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
							<polyline points="18 15 12 9 6 15" />
						</svg>
					</button>
				</div>

				<!-- Job list -->
				<div class="max-h-64 overflow-auto">
					{#each visibleJobs as job (job.id)}
						<div class="border-b last:border-b-0 px-3 py-2">
							<div class="flex items-start gap-2">
								<!-- Status icon -->
								<div class="shrink-0 mt-0.5 {getStatusColor(job.status)}">
									{#if job.status === 'RUNNING'}
										<div class="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"></div>
									{:else if job.status === 'COMPLETED'}
										<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<polyline points="20 6 9 17 4 12" />
										</svg>
									{:else if job.status === 'FAILED'}
										<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<circle cx="12" cy="12" r="10" />
											<line x1="15" y1="9" x2="9" y2="15" />
											<line x1="9" y1="9" x2="15" y2="15" />
										</svg>
									{:else}
										<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<circle cx="12" cy="12" r="10" />
											<polyline points="12 6 12 12 16 14" />
										</svg>
									{/if}
								</div>

								<!-- Job info -->
								<div class="flex-1 min-w-0">
									<div class="flex items-center gap-2">
										<span class="text-xs text-muted-foreground">{getJobTypeLabel(job.type)}</span>
									</div>
									<p class="text-sm font-medium truncate">{job.title}</p>
									{#if job.progressMessage}
										<p class="text-xs text-muted-foreground truncate">{job.progressMessage}</p>
									{/if}
									{#if job.error}
										<p class="text-xs text-destructive truncate">{job.error}</p>
									{/if}
								</div>
							</div>

							<!-- Progress bar -->
							{#if job.status === 'RUNNING' && job.progress > 0}
								<div class="mt-2 h-1 rounded-full bg-muted overflow-hidden">
									<div
										class="h-full bg-primary transition-all duration-300"
										style="width: {job.progress}%"
									></div>
								</div>
							{/if}
						</div>
					{/each}
				</div>

				<!-- Footer with clear button -->
				{#if recentlyCompletedJobs.length > 0}
					<div class="border-t px-3 py-2 bg-muted/30">
						<button
							class="text-xs text-muted-foreground hover:text-foreground"
							onclick={async () => {
								await api.request('DELETE', '/jobs/completed');
								fetchJobs();
							}}
						>
							Clear completed
						</button>
					</div>
				{/if}
			</div>
		{/if}
	</div>
{/if}
