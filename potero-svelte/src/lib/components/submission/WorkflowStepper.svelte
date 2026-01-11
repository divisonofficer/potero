<script lang="ts">
	import type { WorkflowStage } from '$lib/types';
	import { FileText, Users, Package } from 'lucide-svelte';

	interface Props {
		currentStage: WorkflowStage;
		onStageClick?: (stage: WorkflowStage) => void;
	}

	let { currentStage, onStageClick }: Props = $props();

	const stages: { key: WorkflowStage; label: string; icon: typeof FileText }[] = [
		{ key: 'submission', label: 'Submission', icon: FileText },
		{ key: 'review', label: 'Review', icon: Users },
		{ key: 'production', label: 'Production', icon: Package }
	];

	let currentIndex = $derived(stages.findIndex((s) => s.key === currentStage));

	function handleStageClick(stage: WorkflowStage) {
		if (onStageClick) {
			onStageClick(stage);
		}
	}
</script>

<div class="flex items-center">
	{#each stages as stage, i}
		<button
			class="relative flex items-center focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 rounded-full"
			onclick={() => handleStageClick(stage.key)}
			aria-current={i === currentIndex ? 'step' : undefined}
			aria-label="{stage.label} step {i <= currentIndex ? '(completed)' : '(pending)'}"
		>
			<!-- Step pill -->
			<div
				class="flex items-center gap-2 px-4 py-2.5 transition-colors
                       {i === 0 ? 'rounded-l-full' : ''}
                       {i === stages.length - 1 ? 'rounded-r-full' : ''}
                       {i <= currentIndex
					? 'bg-primary text-primary-foreground'
					: 'bg-white text-muted-foreground border border-border'}"
			>
				<!-- Step number circle -->
				<span
					class="flex h-6 w-6  items-center justify-center rounded-full text-sm font-medium
                          {i <= currentIndex ? 'bg-white/20' : 'bg-muted'}"
				>
					{i + 1}
				</span>
				<span class="font-medium">{stage.label}</span>
			</div>

			<!-- Chevron connector (not for last item) -->
			{#if i < stages.length - 1}
				<svg
					class="h-11 w-5 -ml-px -mr-px relative z-10"
					viewBox="0 0 20 44"
					preserveAspectRatio="none"
				>
					<!-- Background chevron (border) -->
					<path
						d="M0,0 L15,22 L0,44 L20,44 L5,22 L20,0 Z"
						fill={i < currentIndex ? 'hsl(var(--primary))' : 'hsl(var(--border))'}
					/>
					<!-- Foreground chevron -->
					<path
						d="M1,1 L14,22 L1,43 L18,43 L6,22 L18,1 Z"
						fill={i < currentIndex ? 'hsl(var(--primary))' : 'white'}
					/>
				</svg>
			{/if}
		</button>
	{/each}
</div>

<style>
	button:hover:not([aria-current='step']) {
		filter: brightness(0.95);
	}
</style>
