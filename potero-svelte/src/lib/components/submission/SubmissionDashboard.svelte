<script lang="ts">
	import type { SubmissionWorkflow, SubmissionFile, WorkflowStage } from '$lib/types';
	import { Download } from 'lucide-svelte';
	import Breadcrumb from './Breadcrumb.svelte';
	import WorkflowStepper from './WorkflowStepper.svelte';
	import SegmentedTabs from './SegmentedTabs.svelte';
	import SideNav from './SideNav.svelte';
	import DataTable from './DataTable.svelte';
	import ReviewerList from './ReviewerList.svelte';
	import ProductionFileList from './ProductionFileList.svelte';
	import { sideNavConfig } from '$lib/stores/submission';
	import {
		mockSubmissionFiles,
		mockProductionFiles,
		mockSideNavBadges
	} from '$lib/mocks/submission';

	interface Props {
		submission: SubmissionWorkflow;
		tabId: string;
	}

	let { submission, tabId }: Props = $props();

	// Local state
	let currentStage = $state<WorkflowStage>(submission.currentStage);
	let activeSection = $state<'workflow' | 'details' | 'ai-review'>('workflow');
	let activeSideNavItem = $state<string>(getDefaultSideNavItem(submission.currentStage));
	let selectedRoundNumber = $state<number>(submission.currentRoundNumber || 1);
	let expandedFileIds = $state<Set<string>>(new Set());

	// Derived state
	let currentRound = $derived(submission.rounds.find((r) => r.roundNumber === selectedRoundNumber) ?? null);
	let sideNavItems = $derived(getSideNavItems(currentStage));
	let badges = $derived(mockSideNavBadges[currentStage] || {});

	// File columns for data table
	const fileColumns = [
		{ key: 'fileName', header: 'File Name', width: '40%' },
		{ key: 'articleType', header: 'Article Type', width: '20%' },
		{ key: 'fileType', header: 'File Type', width: '15%' },
		{ key: 'fileFormat', header: 'File Format', width: '10%' }
	];

	function getDefaultSideNavItem(stage: WorkflowStage): string {
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

	function getSideNavItems(stage: WorkflowStage) {
		return sideNavConfig[stage] || [];
	}

	function handleStageClick(stage: WorkflowStage) {
		currentStage = stage;
		activeSideNavItem = getDefaultSideNavItem(stage);
	}

	function handleSideNavSelect(itemId: string) {
		activeSideNavItem = itemId;
	}

	function handleRoundChange(roundNumber: number) {
		selectedRoundNumber = roundNumber;
	}

	function toggleFileExpand(fileId: string) {
		const newIds = new Set(expandedFileIds);
		if (newIds.has(fileId)) {
			newIds.delete(fileId);
		} else {
			newIds.add(fileId);
		}
		expandedFileIds = newIds;
	}

	function handleDownload(file: SubmissionFile) {
		console.log('Download file:', file.fileName);
		// Implement download logic
	}

	function handleDownloadAll() {
		console.log('Download all files');
		// Implement download all logic
	}

	// Get content title based on current selection
	function getContentTitle(): string {
		const item = sideNavItems.find((i) => i.id === activeSideNavItem);
		return item?.label || 'Files';
	}
</script>

<div class="flex h-full flex-col bg-muted/30">
	<!-- Breadcrumb -->
	<Breadcrumb
		items={[
			{ label: 'Journals' },
			{ label: submission.journalName },
			{ label: submission.articleTitle }
		]}
		articleId={submission.articleId}
	/>

	<!-- Stepper + Tabs row -->
	<div class="flex items-center justify-between bg-white px-6 py-4 border-b">
		<WorkflowStepper currentStage={currentStage} onStageClick={handleStageClick} />
		<SegmentedTabs activeTab={activeSection} onTabChange={(tab) => (activeSection = tab)} />
	</div>

	<!-- Content area -->
	<div class="flex flex-1 overflow-hidden">
		<!-- Side navigation -->
		<SideNav
			items={sideNavItems}
			activeItem={activeSideNavItem}
			{badges}
			onItemSelect={handleSideNavSelect}
		/>

		<!-- Main content panel -->
		<main class="flex-1 overflow-auto p-6">
			{#if currentStage === 'submission'}
				<!-- Submission stage content -->
				<div class="space-y-4">
					<!-- Header with download button -->
					<div class="flex items-center justify-between">
						<div class="flex items-center gap-2">
							<svg
								xmlns="http://www.w3.org/2000/svg"
								width="18"
								height="18"
								viewBox="0 0 24 24"
								fill="none"
								stroke="currentColor"
								stroke-width="2"
								stroke-linecap="round"
								stroke-linejoin="round"
								class="text-primary"
							>
								<path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
								<polyline points="14 2 14 8 20 8" />
							</svg>
							<span class="font-medium">{getContentTitle()}</span>
						</div>
						<button
							class="flex items-center gap-2 rounded-full border-2 border-primary bg-white px-4 py-2 text-sm font-medium text-primary hover:bg-primary/5 transition-colors"
							onclick={handleDownloadAll}
						>
							<Download class="h-4 w-4" />
							DOWNLOAD ALL
						</button>
					</div>

					<!-- Data table -->
					<DataTable
						columns={fileColumns}
						data={mockSubmissionFiles}
						expandedIds={expandedFileIds}
						onToggleExpand={toggleFileExpand}
						onDownload={handleDownload}
					/>
				</div>
			{:else if currentStage === 'review'}
				<!-- Review stage content -->
				<ReviewerList
					rounds={submission.rounds}
					{currentRound}
					{selectedRoundNumber}
					onRoundChange={handleRoundChange}
				/>
			{:else if currentStage === 'production'}
				<!-- Production stage content -->
				<div class="space-y-4">
					<div class="flex items-center justify-between">
						<div class="flex items-center gap-2">
							<svg
								xmlns="http://www.w3.org/2000/svg"
								width="18"
								height="18"
								viewBox="0 0 24 24"
								fill="none"
								stroke="currentColor"
								stroke-width="2"
								stroke-linecap="round"
								stroke-linejoin="round"
								class="text-primary"
							>
								<path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
								<polyline points="14 2 14 8 20 8" />
							</svg>
							<span class="font-medium">{getContentTitle()}</span>
						</div>
					</div>

					<ProductionFileList files={mockProductionFiles} onDownload={handleDownload} />
				</div>
			{/if}
		</main>
	</div>
</div>
