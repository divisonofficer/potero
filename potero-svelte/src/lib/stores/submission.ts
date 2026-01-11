import { writable, derived } from 'svelte/store';
import type {
	SubmissionWorkflow,
	SubmissionFile,
	WorkflowStage,
	ReviewRound,
	ReviewerStats
} from '$lib/types';

// Core state
export const submissions = writable<SubmissionWorkflow[]>([]);
export const currentSubmission = writable<SubmissionWorkflow | null>(null);
export const submissionFiles = writable<SubmissionFile[]>([]);
export const selectedRoundNumber = writable<number>(1);
export const isLoading = writable(false);
export const error = writable<string | null>(null);

// UI state
export const activeSection = writable<'workflow' | 'details' | 'ai-review'>('workflow');
export const activeSideNavItem = writable<string>('submission-files');
export const expandedFileIds = writable<Set<string>>(new Set());

// Derived: current round data
export const currentRound = derived(
	[currentSubmission, selectedRoundNumber],
	([$submission, $roundNumber]): ReviewRound | null => {
		if (!$submission) return null;
		return $submission.rounds.find((r) => r.roundNumber === $roundNumber) ?? null;
	}
);

// Derived: all rounds for current submission
export const allRounds = derived(currentSubmission, ($submission): ReviewRound[] => {
	if (!$submission) return [];
	return $submission.rounds;
});

// Derived: reviewer statistics for current round
export const reviewerStats = derived(currentRound, ($round): ReviewerStats | null => {
	if (!$round) return null;
	const reviewers = $round.reviewers;
	return {
		total: reviewers.length,
		invited: reviewers.filter((r) => r.status === 'invited').length,
		accepted: reviewers.filter((r) => r.status === 'accepted').length,
		notResponded: reviewers.filter((r) => r.status === 'not_responded').length,
		declined: reviewers.filter((r) => r.status === 'declined').length,
		cancelled: reviewers.filter((r) => r.status === 'cancelled').length,
		overdue: reviewers.filter((r) => r.status === 'overdue').length,
		revisionRequested: reviewers.filter((r) => r.status === 'revision_requested').length,
		revised: reviewers.filter((r) => r.status === 'revised').length,
		completed: reviewers.filter((r) => r.status === 'completed').length
	};
});

// Derived: reviewers filtered by status
export const reviewersByStatus = derived(currentRound, ($round) => {
	if (!$round) return {};
	const reviewers = $round.reviewers;
	return {
		all: reviewers,
		queue: reviewers.filter((r) => ['invited', 'accepted'].includes(r.status)),
		individual: reviewers.filter((r) => r.status === 'completed')
	};
});

// Derived: files grouped by type
export const filesByType = derived(submissionFiles, ($files) => {
	const grouped: Record<string, SubmissionFile[]> = {};
	for (const file of $files) {
		const type = file.fileType;
		if (!grouped[type]) {
			grouped[type] = [];
		}
		grouped[type].push(file);
	}
	return grouped;
});

// Actions
export function setSubmission(submission: SubmissionWorkflow): void {
	currentSubmission.set(submission);
	selectedRoundNumber.set(submission.currentRoundNumber);
	activeSideNavItem.set(getSideNavItemForStage(submission.currentStage));
}

export function setSubmissions(list: SubmissionWorkflow[]): void {
	submissions.set(list);
}

export function setSubmissionFiles(files: SubmissionFile[]): void {
	submissionFiles.set(files);
}

export function selectRound(roundNumber: number): void {
	selectedRoundNumber.set(roundNumber);
}

export function setActiveSection(section: 'workflow' | 'details' | 'ai-review'): void {
	activeSection.set(section);
}

export function setActiveSideNavItem(itemId: string): void {
	activeSideNavItem.set(itemId);
}

export function toggleFileExpanded(fileId: string): void {
	expandedFileIds.update((ids) => {
		const newIds = new Set(ids);
		if (newIds.has(fileId)) {
			newIds.delete(fileId);
		} else {
			newIds.add(fileId);
		}
		return newIds;
	});
}

export function setActiveStage(stage: WorkflowStage): void {
	currentSubmission.update((sub) => {
		if (!sub) return sub;
		return { ...sub, currentStage: stage };
	});
	activeSideNavItem.set(getSideNavItemForStage(stage));
}

export function resetSubmissionState(): void {
	currentSubmission.set(null);
	submissionFiles.set([]);
	selectedRoundNumber.set(1);
	activeSection.set('workflow');
	activeSideNavItem.set('submission-files');
	expandedFileIds.set(new Set());
	error.set(null);
}

// Helper to get default side nav item for each stage
function getSideNavItemForStage(stage: WorkflowStage): string {
	switch (stage) {
		case 'submission':
			return 'submission-files';
		case 'review':
			return 'all-reviews';
		case 'production':
			return 'production-ready';
		default:
			return 'submission-files';
	}
}

// Side nav items configuration for each stage
export const sideNavConfig = {
	submission: [
		{ id: 'submission-files', label: 'Submission Files', icon: 'FileText' },
		{ id: 'requested-changes', label: 'Requested Changes', icon: 'Edit' },
		{ id: 'pre-review-discussion', label: 'Pre-Review Discussion', icon: 'MessageSquare' }
	],
	review: [
		{ id: 'all-reviews', label: 'All Reviews', icon: 'Users' },
		{ id: 'reviewers-invited', label: 'Reviewers Invited', icon: 'UserPlus' },
		{ id: 'invitation-accepted', label: 'Invitation Accepted', icon: 'UserCheck' },
		{ id: 'not-responded', label: 'Not Responded', icon: 'Clock' },
		{ id: 'invitation-declined', label: 'Invitation Declined', icon: 'UserX' },
		{ id: 'invitation-cancelled', label: 'Invitation Cancelled', icon: 'XCircle' },
		{ id: 'overdue-reviews', label: 'Overdue Reviews', icon: 'AlertTriangle' },
		{ id: 'revision-requested', label: 'Revision Requested', icon: 'RefreshCw' },
		{ id: 'revised-review', label: 'Revised Review', icon: 'CheckCircle' },
		{ id: 'review-completed', label: 'Review Completed', icon: 'CheckCircle2' },
		{ id: 'divider-1', label: '', icon: '' },
		{ id: 'review-files', label: 'Review Files', icon: 'FileText' },
		{ id: 'peer-review-discussion', label: 'Peer Review Discussion', icon: 'MessageSquare' },
		{ id: 'participants', label: 'Participants', icon: 'Users' }
	],
	production: [
		{ id: 'production-ready', label: 'Production Ready Files', icon: 'FileText' },
		{ id: 'production-discussions', label: 'Production Discussions', icon: 'MessageSquare' },
		{ id: 'production-jobs', label: 'Production Jobs', icon: 'Settings' },
		{ id: 'production-completed', label: 'Production Completed Files', icon: 'CheckCircle' },
		{ id: 'participants', label: 'Participants', icon: 'Users' }
	]
};
