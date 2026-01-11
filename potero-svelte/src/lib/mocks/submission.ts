import type {
	SubmissionWorkflow,
	SubmissionFile,
	Reviewer,
	ReviewRound,
	SubmissionSideNavItem
} from '$lib/types';

// Mock submission workflow data matching the screenshot
export const mockSubmission: SubmissionWorkflow = {
	id: 'sub-001',
	paperId: 'paper-123',
	journalId: 'jolit',
	journalName: 'JOLIT',
	articleId: 'JOLIT-003',
	articleTitle: 'Modern Technology of Lorem Ipsum',
	currentStage: 'submission',
	status: 'submitted',
	submissionDate: '2025-01-10T10:30:00Z',
	currentRoundNumber: 1,
	rounds: [
		{
			roundNumber: 1,
			startDate: '2025-01-10T10:30:00Z',
			endDate: null,
			status: 'active',
			reviewers: [
				{
					id: 'rev-001',
					name: 'Seline Doe Reviewer',
					email: 'selinedoe_review@mailinator.com',
					affiliation: 'MIT',
					status: 'completed',
					invitedDate: '2025-01-11T09:00:00Z',
					responseDate: '2025-01-12T11:30:00Z',
					responseDueDate: '2025-05-30T07:56:00Z',
					reviewDueDate: '2025-06-15T23:59:59Z',
					recommendation: 'minor_revision',
					reviewSubmittedDate: '2025-01-20T16:45:00Z',
					comments: 'Good work overall with minor improvements needed.'
				},
				{
					id: 'rev-002',
					name: 'Prof. John Smith',
					email: 'john.smith@stanford.edu',
					affiliation: 'Stanford University',
					status: 'accepted',
					invitedDate: '2025-01-11T09:00:00Z',
					responseDate: '2025-01-13T08:15:00Z',
					responseDueDate: '2025-05-30T07:56:00Z',
					reviewDueDate: '2025-06-15T23:59:59Z',
					recommendation: null,
					reviewSubmittedDate: null,
					comments: null
				},
				{
					id: 'rev-003',
					name: 'Dr. Emily Chen',
					email: 'emily.chen@berkeley.edu',
					affiliation: 'UC Berkeley',
					status: 'invited',
					invitedDate: '2025-01-11T09:00:00Z',
					responseDate: null,
					responseDueDate: '2025-05-30T07:56:00Z',
					reviewDueDate: null,
					recommendation: null,
					reviewSubmittedDate: null,
					comments: null
				},
				{
					id: 'rev-004',
					name: 'Dr. Michael Brown',
					email: 'michael.brown@oxford.edu',
					affiliation: 'Oxford University',
					status: 'declined',
					invitedDate: '2025-01-11T09:00:00Z',
					responseDate: '2025-01-14T10:00:00Z',
					responseDueDate: '2025-05-30T07:56:00Z',
					reviewDueDate: null,
					recommendation: null,
					reviewSubmittedDate: null,
					comments: null
				}
			],
			decision: null
		}
	],
	createdAt: '2025-01-10T10:30:00Z',
	updatedAt: '2025-01-11T15:00:00Z'
};

// Mock submission with review stage active
export const mockSubmissionReview: SubmissionWorkflow = {
	...mockSubmission,
	id: 'sub-002',
	currentStage: 'review',
	status: 'under_review',
	currentRoundNumber: 1
};

// Mock submission with production stage active
export const mockSubmissionProduction: SubmissionWorkflow = {
	...mockSubmission,
	id: 'sub-003',
	currentStage: 'production',
	status: 'in_production',
	currentRoundNumber: 2,
	rounds: [
		...mockSubmission.rounds,
		{
			roundNumber: 2,
			startDate: '2025-02-01T10:00:00Z',
			endDate: '2025-02-15T14:00:00Z',
			status: 'completed',
			reviewers: [
				{
					id: 'rev-001',
					name: 'Seline Doe Reviewer',
					email: 'selinedoe_review@mailinator.com',
					affiliation: 'MIT',
					status: 'completed',
					invitedDate: '2025-02-01T10:00:00Z',
					responseDate: '2025-02-01T15:30:00Z',
					responseDueDate: '2025-02-10T23:59:59Z',
					reviewDueDate: '2025-02-15T23:59:59Z',
					recommendation: 'accept',
					reviewSubmittedDate: '2025-02-12T16:45:00Z',
					comments: 'All revisions have been addressed satisfactorily.'
				}
			],
			decision: {
				decision: 'accept',
				decisionDate: '2025-02-15T14:00:00Z',
				editorComments: 'Congratulations! Your paper has been accepted for publication.'
			}
		}
	]
};

// Mock submission files matching the screenshot
export const mockSubmissionFiles: SubmissionFile[] = [
	{
		id: 'file-001',
		submissionId: 'sub-001',
		fileName: 'JOLIT-003-Manuscript - New.pdf',
		articleType: 'Research Article',
		fileType: 'Manuscript',
		fileFormat: 'PDF',
		version: 2,
		uploadedAt: '2025-01-10T10:30:00Z',
		children: [
			{
				id: 'file-001-1',
				submissionId: 'sub-001',
				fileName: 'JOLIT-003-Manuscript.pdf',
				articleType: 'Research Article',
				fileType: 'Manuscript',
				fileFormat: 'PDF',
				version: 1,
				uploadedAt: '2025-01-08T14:20:00Z'
			}
		]
	},
	{
		id: 'file-002',
		submissionId: 'sub-001',
		fileName: 'JOLIT-003-3d-render-abstract-p...',
		articleType: 'Research Article',
		fileType: 'Figures',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-01-10T10:31:00Z'
	},
	{
		id: 'file-003',
		submissionId: 'sub-001',
		fileName: 'JOLIT-003-professional-interio...',
		articleType: 'Research Article',
		fileType: 'Figures',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-01-10T10:32:00Z'
	},
	{
		id: 'file-004',
		submissionId: 'sub-001',
		fileName: 'JOLIT-003-3d-abstract-backgrou...',
		articleType: 'Research Article',
		fileType: 'Additional Files',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-01-10T10:33:00Z'
	},
	{
		id: 'file-005',
		submissionId: 'sub-001',
		fileName: 'JOLIT-003-professional-interio...',
		articleType: 'Research Article',
		fileType: 'Additional Files',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-01-10T10:34:00Z'
	},
	{
		id: 'file-006',
		submissionId: 'sub-001',
		fileName: 'JOLIT-003-Additional files.jpg',
		articleType: 'Research Article',
		fileType: 'Additional Files',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-01-10T10:35:00Z'
	}
];

// Mock production files
export const mockProductionFiles: SubmissionFile[] = [
	{
		id: 'prod-001',
		submissionId: 'sub-003',
		fileName: 'JOLIT-003-3d-render-abstract-p...',
		articleType: 'Research Article',
		fileType: 'Production Ready',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-02-16T09:00:00Z'
	},
	{
		id: 'prod-002',
		submissionId: 'sub-003',
		fileName: 'JOLIT-003-professional-interi...',
		articleType: 'Research Article',
		fileType: 'Production Ready',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-02-16T09:01:00Z'
	},
	{
		id: 'prod-003',
		submissionId: 'sub-003',
		fileName: 'JOLIT-003-Manuscript - New.pdf',
		articleType: 'Research Article',
		fileType: 'Production Ready',
		fileFormat: 'PDF',
		version: 1,
		uploadedAt: '2025-02-16T09:02:00Z'
	},
	{
		id: 'prod-004',
		submissionId: 'sub-003',
		fileName: 'JOLIT-003-3d-abstract-backgrou...',
		articleType: 'Research Article',
		fileType: 'Production Ready',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-02-16T09:03:00Z'
	},
	{
		id: 'prod-005',
		submissionId: 'sub-003',
		fileName: 'JOLIT-003-professional-interi...',
		articleType: 'Research Article',
		fileType: 'Production Ready',
		fileFormat: 'JPG',
		version: 1,
		uploadedAt: '2025-02-16T09:04:00Z'
	}
];

// Side nav items with badge counts for each stage
export const mockSideNavBadges = {
	submission: {
		'submission-files': 6,
		'requested-changes': 1,
		'pre-review-discussion': 1
	},
	review: {
		'reviewers-invited': 4,
		'invitation-accepted': 1,
		'review-completed': 1,
		'review-files': 3,
		'peer-review-discussion': 0,
		'participants': 4
	},
	production: {
		'production-jobs': 0,
		'participants': 4
	}
};

// List of all submissions for the submissions list view
export const mockSubmissionsList: SubmissionWorkflow[] = [
	mockSubmission,
	mockSubmissionReview,
	mockSubmissionProduction,
	{
		id: 'sub-004',
		paperId: 'paper-456',
		journalId: 'jolit',
		journalName: 'JOLIT',
		articleId: 'JOLIT-004',
		articleTitle: 'Advanced Machine Learning Techniques for NLP',
		currentStage: 'review',
		status: 'revision_requested',
		submissionDate: '2025-01-05T08:00:00Z',
		currentRoundNumber: 1,
		rounds: [],
		createdAt: '2025-01-05T08:00:00Z',
		updatedAt: '2025-01-15T12:00:00Z'
	},
	{
		id: 'sub-005',
		paperId: 'paper-789',
		journalId: 'acmchi',
		journalName: 'ACM CHI',
		articleId: 'CHI-2025-001',
		articleTitle: 'User Experience Design in Virtual Reality',
		currentStage: 'submission',
		status: 'draft',
		submissionDate: null,
		currentRoundNumber: 0,
		rounds: [],
		createdAt: '2025-01-08T14:00:00Z',
		updatedAt: '2025-01-08T14:00:00Z'
	}
];

// Reviewer info with files for review panel
export const mockReviewerWithFiles = {
	reviewer: mockSubmission.rounds[0].reviewers[0],
	sentFiles: [
		{ name: 'JOLIT-003-3d-abstract-background-with-flow-waves.jpg', type: 'Additional Files' },
		{ name: 'JOLIT-003-professional-interior-designer.jpg', type: 'Additional Files' },
		{ name: 'JOLIT-003-Manuscript - v1-New.pdf', type: 'Anonymous Manuscript' }
	],
	submittedForm: {
		fileName: 'JOLIT-003-Reviewer-form.pdf',
		submittedAt: '2025-01-20T16:45:00Z'
	}
};
