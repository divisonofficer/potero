<script lang="ts">
	import { ArrowLeft, ArrowRight, Check } from 'lucide-svelte';
	import {
		onboardingState,
		ONBOARDING_STEPS,
		nextStep,
		prevStep,
		canProceed,
		completeOnboarding
	} from '$lib/stores/onboarding';
	import StepIndicator from './StepIndicator.svelte';
	import WelcomeStep from './WelcomeStep.svelte';
	import SSOLoginStep from './SSOLoginStep.svelte';
	import SearchEnginesStep from './SearchEnginesStep.svelte';
	import PDFEnginesStep from './PDFEnginesStep.svelte';
	import StoragePathStep from './StoragePathStep.svelte';

	interface Props {
		onComplete: () => void;
	}

	let { onComplete }: Props = $props();

	let currentStep = $derived($onboardingState.currentStep);
	let isLastStep = $derived(currentStep === ONBOARDING_STEPS.length - 1);
	let isCompleting = $state(false);
	let canGoNext = $derived(canProceed());

	async function handleNext() {
		if (isLastStep) {
			isCompleting = true;
			const success = await completeOnboarding();
			isCompleting = false;

			if (success) {
				onComplete();
			}
		} else {
			nextStep();
		}
	}

	function handleBack() {
		prevStep();
	}
</script>

<div class="onboarding-overlay">
	<div class="onboarding-container">
		<div class="wizard-card">
			<!-- Step Indicator -->
			<div class="step-indicator-wrapper">
				<StepIndicator
					steps={ONBOARDING_STEPS}
					{currentStep}
				/>
			</div>

			<!-- Step Content -->
			<div class="step-content">
				{#if currentStep === 0}
					<WelcomeStep />
				{:else if currentStep === 1}
					<SSOLoginStep />
				{:else if currentStep === 2}
					<SearchEnginesStep />
				{:else if currentStep === 3}
					<PDFEnginesStep />
				{:else if currentStep === 4}
					<StoragePathStep />
				{/if}
			</div>

			<!-- Navigation Buttons -->
			<div class="navigation">
				<button
					class="nav-button back"
					disabled={currentStep === 0}
					onclick={handleBack}
				>
					<ArrowLeft size={18} />
					<span>Back</span>
				</button>

				<button
					class="nav-button next"
					disabled={!canGoNext || isCompleting}
					onclick={handleNext}
				>
					{#if isCompleting}
						<span>Completing...</span>
					{:else if isLastStep}
						<span>Complete Setup</span>
						<Check size={18} />
					{:else}
						<span>Next</span>
						<ArrowRight size={18} />
					{/if}
				</button>
			</div>
		</div>
	</div>
</div>

<style>
	.onboarding-overlay {
		position: fixed;
		inset: 0;
		z-index: 100;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(0, 0, 0, 0.6);
		backdrop-filter: blur(8px);
	}

	.onboarding-container {
		width: 100%;
		max-width: 700px;
		max-height: 90vh;
		margin: 1rem;
		overflow: hidden;
	}

	.wizard-card {
		background: var(--color-surface, #fff);
		border-radius: 20px;
		box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
		overflow: hidden;
		display: flex;
		flex-direction: column;
		max-height: 85vh;
	}

	.step-indicator-wrapper {
		padding: 1.5rem 2rem 0;
		background: var(--color-surface-secondary, #f8f9fa);
		border-bottom: 1px solid var(--color-border, #e0e0e0);
	}

	.step-content {
		flex: 1;
		overflow-y: auto;
		padding: 1.5rem 2rem;
		min-height: 400px;
	}

	.navigation {
		display: flex;
		justify-content: space-between;
		padding: 1.5rem 2rem;
		background: var(--color-surface-secondary, #f8f9fa);
		border-top: 1px solid var(--color-border, #e0e0e0);
	}

	.nav-button {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.875rem 1.5rem;
		border-radius: 10px;
		font-size: 0.95rem;
		font-weight: 600;
		cursor: pointer;
		transition: all 0.2s ease;
		border: none;
	}

	.nav-button.back {
		background: var(--color-surface, #fff);
		color: var(--color-text-secondary, #666);
		border: 1px solid var(--color-border, #e0e0e0);
	}

	.nav-button.back:hover:not(:disabled) {
		background: var(--color-surface-secondary, #f0f0f0);
		color: var(--color-text-primary, #333);
	}

	.nav-button.back:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.nav-button.next {
		background: var(--color-primary, #0066cc);
		color: white;
		box-shadow: 0 4px 12px rgba(0, 102, 204, 0.3);
	}

	.nav-button.next:hover:not(:disabled) {
		background: var(--color-primary-dark, #0055aa);
		transform: translateY(-1px);
		box-shadow: 0 6px 16px rgba(0, 102, 204, 0.4);
	}

	.nav-button.next:disabled {
		opacity: 0.6;
		cursor: not-allowed;
		transform: none;
		box-shadow: none;
	}

	@media (max-width: 640px) {
		.onboarding-container {
			margin: 0.5rem;
		}

		.wizard-card {
			border-radius: 16px;
		}

		.step-indicator-wrapper,
		.step-content,
		.navigation {
			padding-left: 1rem;
			padding-right: 1rem;
		}

		.nav-button {
			padding: 0.75rem 1rem;
			font-size: 0.9rem;
		}
	}
</style>
