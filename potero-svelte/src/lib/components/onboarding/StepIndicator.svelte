<script lang="ts">
	import { Check } from 'lucide-svelte';

	interface Step {
		id: string;
		title: string;
		description?: string;
	}

	interface Props {
		steps: readonly Step[];
		currentStep: number;
		completedSteps?: boolean[];
	}

	let { steps, currentStep, completedSteps = [] }: Props = $props();

	function isCompleted(index: number): boolean {
		return completedSteps[index] ?? index < currentStep;
	}
</script>

<div class="step-indicator">
	{#each steps as step, index}
		<div class="step" class:active={index === currentStep} class:completed={isCompleted(index)}>
			<div class="step-circle">
				{#if isCompleted(index)}
					<Check size={16} />
				{:else}
					{index + 1}
				{/if}
			</div>
			<div class="step-label">
				<span class="step-title">{step.title}</span>
			</div>
		</div>
		{#if index < steps.length - 1}
			<div class="step-line" class:completed={isCompleted(index)}></div>
		{/if}
	{/each}
</div>

<style>
	.step-indicator {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0;
		padding: 1rem 0;
	}

	.step {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.5rem;
		position: relative;
	}

	.step-circle {
		width: 36px;
		height: 36px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 0.9rem;
		font-weight: 600;
		background: var(--color-surface-secondary, #f0f0f0);
		color: var(--color-text-tertiary, #888);
		border: 2px solid transparent;
		transition: all 0.3s ease;
	}

	.step.active .step-circle {
		background: var(--color-primary, #0066cc);
		color: white;
		border-color: var(--color-primary, #0066cc);
		box-shadow: 0 0 0 4px rgba(0, 102, 204, 0.2);
	}

	.step.completed .step-circle {
		background: #22c55e;
		color: white;
		border-color: #22c55e;
	}

	.step-label {
		text-align: center;
		min-width: 80px;
	}

	.step-title {
		font-size: 0.75rem;
		font-weight: 500;
		color: var(--color-text-tertiary, #888);
		transition: color 0.3s ease;
	}

	.step.active .step-title {
		color: var(--color-primary, #0066cc);
		font-weight: 600;
	}

	.step.completed .step-title {
		color: #22c55e;
	}

	.step-line {
		width: 40px;
		height: 2px;
		background: var(--color-border, #e0e0e0);
		margin: 0 0.25rem;
		margin-bottom: 1.5rem;
		transition: background-color 0.3s ease;
	}

	.step-line.completed {
		background: #22c55e;
	}

	@media (max-width: 640px) {
		.step-label {
			display: none;
		}

		.step-line {
			margin-bottom: 0;
		}
	}
</style>
