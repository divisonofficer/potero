<script lang="ts">
	import {
		User,
		Calendar,
		Clock,
		CheckCircle,
		XCircle,
		AlertCircle,
		Mail,
		FileText,
		ChevronDown,
		ChevronUp
	} from 'lucide-svelte';
	import type { Reviewer } from '$lib/types';

	interface Props {
		reviewer: Reviewer;
		index: number;
		isExpanded?: boolean;
		onToggleExpand?: () => void;
	}

	let { reviewer, index, isExpanded = false, onToggleExpand }: Props = $props();

	let statusConfig = $derived(() => {
		switch (reviewer.status) {
			case 'invited':
				return { icon: Clock, color: 'text-yellow-500', bg: 'bg-yellow-50', label: 'Invited' };
			case 'accepted':
				return {
					icon: AlertCircle,
					color: 'text-blue-500',
					bg: 'bg-blue-50',
					label: 'In Progress'
				};
			case 'not_responded':
				return { icon: Clock, color: 'text-gray-500', bg: 'bg-gray-50', label: 'Not Responded' };
			case 'declined':
				return { icon: XCircle, color: 'text-red-500', bg: 'bg-red-50', label: 'Declined' };
			case 'cancelled':
				return { icon: XCircle, color: 'text-gray-500', bg: 'bg-gray-50', label: 'Cancelled' };
			case 'overdue':
				return {
					icon: AlertCircle,
					color: 'text-red-500',
					bg: 'bg-red-50',
					label: 'Overdue'
				};
			case 'revision_requested':
				return {
					icon: AlertCircle,
					color: 'text-orange-500',
					bg: 'bg-orange-50',
					label: 'Revision Requested'
				};
			case 'revised':
				return {
					icon: CheckCircle,
					color: 'text-blue-500',
					bg: 'bg-blue-50',
					label: 'Revised'
				};
			case 'completed':
				return {
					icon: CheckCircle,
					color: 'text-green-500',
					bg: 'bg-green-50',
					label: 'Completed'
				};
			default:
				return { icon: Clock, color: 'text-gray-500', bg: 'bg-gray-50', label: 'Unknown' };
		}
	});

	function formatDate(dateString: string | null): string {
		if (!dateString) return '-';
		return new Date(dateString).toLocaleDateString('en-US', {
			day: '2-digit',
			month: 'short',
			year: 'numeric',
			hour: '2-digit',
			minute: '2-digit'
		});
	}

	function getRecommendationLabel(rec: string | null): string {
		if (!rec) return '-';
		const labels: Record<string, string> = {
			accept: 'Accept',
			minor_revision: 'Minor Revision',
			major_revision: 'Major Revision',
			reject: 'Reject'
		};
		return labels[rec] || rec;
	}
</script>

<div class="border-b last:border-b-0">
	<!-- Main row -->
	<div
		class="flex items-center justify-between px-4 py-3 hover:bg-muted/30 transition-colors cursor-pointer"
		onclick={onToggleExpand}
		role="button"
		tabindex="0"
		onkeydown={(e) => e.key === 'Enter' && onToggleExpand?.()}
	>
		<div class="flex items-center gap-4">
			<span class="text-sm text-muted-foreground w-6">{index}</span>
			<div class="flex items-center gap-3">
				<div
					class="flex h-8 w-8 items-center justify-center rounded-full {statusConfig()
						.bg} {statusConfig().color}"
				>
					<User class="h-4 w-4" />
				</div>
				<div>
					<div class="flex items-center gap-2">
						<span class="font-medium text-sm">{reviewer.name}</span>
						{#if reviewer.status === 'completed'}
							<CheckCircle class="h-4 w-4 text-green-500" />
						{/if}
					</div>
				</div>
			</div>
		</div>

		<div class="flex items-center gap-4">
			<div class="text-right text-sm">
				<div class="text-muted-foreground">Response Due Date</div>
				<div>{formatDate(reviewer.responseDueDate)}</div>
			</div>
			{#if isExpanded}
				<ChevronUp class="h-5 w-5 text-muted-foreground" />
			{:else}
				<ChevronDown class="h-5 w-5 text-muted-foreground" />
			{/if}
		</div>
	</div>

	<!-- Expanded content -->
	{#if isExpanded}
		<div class="px-4 pb-4 pt-2 bg-muted/20">
			<!-- Tabs -->
			<div class="flex gap-2 mb-4">
				<button
					class="px-3 py-1.5 text-xs font-medium rounded-md bg-primary text-primary-foreground"
				>
					Information
				</button>
				<button
					class="px-3 py-1.5 text-xs font-medium rounded-md border text-muted-foreground hover:bg-muted transition-colors"
				>
					Revision (1)
				</button>
				<button
					class="px-3 py-1.5 text-xs font-medium rounded-md border text-muted-foreground hover:bg-muted transition-colors"
				>
					Discussion
				</button>
			</div>

			<!-- Reviewer Email -->
			<div class="mb-4">
				<div class="flex items-center gap-2 text-sm text-muted-foreground mb-1">
					<Mail class="h-4 w-4 text-primary" />
					<span class="font-medium">REVIEWER EMAIL</span>
				</div>
				<div class="text-sm pl-6">{reviewer.email}</div>
			</div>

			<!-- Review Sent Files -->
			<div class="mb-4">
				<div class="flex items-center gap-2 text-sm text-muted-foreground mb-2">
					<FileText class="h-4 w-4 text-primary" />
					<span class="font-medium">REVIEW SENT FILES</span>
				</div>
				<ul class="text-sm pl-6 space-y-1">
					<li>
						<span class="text-muted-foreground">Additional Files -</span> JOLIT-003-3d-abstract-background-with-flow-waves.jpg
						<span class="text-primary">(1)</span>
					</li>
					<li>
						<span class="text-muted-foreground">Additional Files -</span> JOLIT-003-professional-interior-designer.jpg
					</li>
					<li>
						<span class="text-muted-foreground">Anonymous Manuscript -</span> JOLIT-003-Manuscript -
						v1-New.pdf
					</li>
				</ul>
			</div>

			<!-- Reviewer Submitted Review Form -->
			{#if reviewer.status === 'completed'}
				<div>
					<div class="flex items-center gap-2 text-sm text-muted-foreground mb-2">
						<CheckCircle class="h-4 w-4 text-primary" />
						<span class="font-medium">REVIEWER SUBMITTED REVIEW FORM</span>
					</div>
					<div class="text-sm pl-6 text-primary">JOLIT-003-Reviewer-form.pdf</div>
				</div>
			{/if}
		</div>
	{/if}
</div>
