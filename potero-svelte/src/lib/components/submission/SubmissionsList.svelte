<script lang="ts">
	import type { SubmissionWorkflow, WorkflowStage } from '$lib/types';
	import { Search, Filter, Plus, FileText, Users, Package } from 'lucide-svelte';
	import { mockSubmissionsList } from '$lib/mocks/submission';

	interface Props {
		onOpenSubmission: (submission: SubmissionWorkflow) => void;
	}

	let { onOpenSubmission }: Props = $props();

	let searchQuery = $state('');
	let stageFilter = $state<WorkflowStage | 'all'>('all');

	let filteredSubmissions = $derived(() => {
		let result = mockSubmissionsList;

		// Filter by stage
		if (stageFilter !== 'all') {
			result = result.filter((s) => s.currentStage === stageFilter);
		}

		// Filter by search query
		if (searchQuery.trim()) {
			const query = searchQuery.toLowerCase();
			result = result.filter(
				(s) =>
					s.articleTitle.toLowerCase().includes(query) ||
					s.articleId.toLowerCase().includes(query) ||
					s.journalName.toLowerCase().includes(query)
			);
		}

		return result;
	});

	function getStageColor(stage: WorkflowStage): string {
		switch (stage) {
			case 'submission':
				return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300';
			case 'review':
				return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300';
			case 'production':
				return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300';
		}
	}

	function getStatusColor(status: string): string {
		switch (status) {
			case 'draft':
				return 'bg-gray-100 text-gray-700';
			case 'submitted':
				return 'bg-blue-100 text-blue-700';
			case 'under_review':
				return 'bg-yellow-100 text-yellow-700';
			case 'revision_requested':
				return 'bg-orange-100 text-orange-700';
			case 'revised':
				return 'bg-blue-100 text-blue-700';
			case 'accepted':
				return 'bg-green-100 text-green-700';
			case 'in_production':
				return 'bg-purple-100 text-purple-700';
			case 'published':
				return 'bg-green-100 text-green-700';
			default:
				return 'bg-gray-100 text-gray-700';
		}
	}

	function formatStatus(status: string): string {
		return status
			.split('_')
			.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
			.join(' ');
	}

	function formatDate(dateString: string | null): string {
		if (!dateString) return '-';
		return new Date(dateString).toLocaleDateString('en-US', {
			month: 'short',
			day: 'numeric',
			year: 'numeric'
		});
	}
</script>

<div class="h-full flex flex-col p-6">
	<!-- Header -->
	<div class="flex items-center justify-between mb-6">
		<div>
			<h1 class="text-2xl font-semibold">Submissions</h1>
			<p class="text-muted-foreground text-sm mt-1">Manage your paper submissions</p>
		</div>
		<button
			class="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
		>
			<Plus class="h-4 w-4" />
			New Submission
		</button>
	</div>

	<!-- Filters -->
	<div class="flex items-center gap-4 mb-6">
		<!-- Search -->
		<div class="relative flex-1 max-w-md">
			<Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
			<input
				type="text"
				placeholder="Search submissions..."
				bind:value={searchQuery}
				class="w-full rounded-lg border bg-white pl-10 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
			/>
		</div>

		<!-- Stage filter -->
		<div class="flex items-center gap-2">
			<Filter class="h-4 w-4 text-muted-foreground" />
			<select
				bind:value={stageFilter}
				class="rounded-lg border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
			>
				<option value="all">All Stages</option>
				<option value="submission">Submission</option>
				<option value="review">Review</option>
				<option value="production">Production</option>
			</select>
		</div>
	</div>

	<!-- Submissions list -->
	<div class="flex-1 overflow-auto">
		<div class="rounded-lg border bg-white overflow-hidden">
			<!-- Table header -->
			<div class="grid grid-cols-12 gap-4 px-4 py-3 border-b bg-muted/30 text-xs font-medium text-muted-foreground">
				<div class="col-span-1">ID</div>
				<div class="col-span-4">Title</div>
				<div class="col-span-2">Journal</div>
				<div class="col-span-2">Stage</div>
				<div class="col-span-2">Status</div>
				<div class="col-span-1">Date</div>
			</div>

			<!-- Table body -->
			<div class="divide-y">
				{#each filteredSubmissions() as submission}
					<button
						class="w-full grid grid-cols-12 gap-4 px-4 py-4 text-sm hover:bg-muted/30 transition-colors text-left"
						onclick={() => onOpenSubmission(submission)}
					>
						<div class="col-span-1 font-mono text-muted-foreground">{submission.articleId}</div>
						<div class="col-span-4 font-medium truncate" title={submission.articleTitle}>
							{submission.articleTitle}
						</div>
						<div class="col-span-2 text-muted-foreground">{submission.journalName}</div>
						<div class="col-span-2">
							<span
								class="inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium capitalize {getStageColor(
									submission.currentStage
								)}"
							>
								{#if submission.currentStage === 'submission'}
									<FileText class="h-3 w-3" />
								{:else if submission.currentStage === 'review'}
									<Users class="h-3 w-3" />
								{:else}
									<Package class="h-3 w-3" />
								{/if}
								{submission.currentStage}
							</span>
						</div>
						<div class="col-span-2">
							<span
								class="inline-flex items-center rounded-full px-2 py-1 text-xs font-medium {getStatusColor(
									submission.status
								)}"
							>
								{formatStatus(submission.status)}
							</span>
						</div>
						<div class="col-span-1 text-muted-foreground text-xs">
							{formatDate(submission.submissionDate)}
						</div>
					</button>
				{/each}

				{#if filteredSubmissions().length === 0}
					<div class="flex flex-col items-center justify-center py-12 text-muted-foreground">
						<FileText class="h-12 w-12 mb-4 opacity-50" />
						<p class="text-sm">No submissions found</p>
					</div>
				{/if}
			</div>
		</div>
	</div>
</div>
