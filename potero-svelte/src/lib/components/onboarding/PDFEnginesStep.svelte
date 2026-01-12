<script lang="ts">
	import { FileText } from 'lucide-svelte';
	import { onboardingState, setPDFEngine } from '$lib/stores/onboarding';
	import { EngineCard } from '$lib/components/shared';

	// PDF engine definitions
	const pdfEngines = [
		{
			id: 'grobid' as const,
			name: 'GROBID',
			description: 'Machine learning-based extraction. Most accurate for citation metadata.',
			badge: { type: 'recommended' as const, text: 'Recommended' },
			features: ['High accuracy', 'Citation spans', 'ML-powered']
		},
		{
			id: 'pdftotext' as const,
			name: 'pdftotext (Poppler)',
			description: 'Fallback text extraction. Better for PDFs with font encoding issues.',
			badge: { type: 'fallback' as const, text: 'Fallback' },
			features: ['Garbled PDF fix', 'Fast extraction']
		},
		{
			id: 'ocr' as const,
			name: 'OCR (Tesseract)',
			description: 'Optical character recognition for scanned or image-based PDFs.',
			badge: { type: 'slow' as const, text: 'Slow' },
			features: ['Scanned PDFs', 'Last resort']
		}
	];

	// Derived from store
	let enabledEngines = $derived($onboardingState.pdfEngines);
	let atLeastOneEnabled = $derived(Object.values(enabledEngines).some((v) => v));

	function handleToggle(id: 'grobid' | 'pdftotext' | 'ocr', enabled: boolean) {
		setPDFEngine(id, enabled);
	}
</script>

<div class="pdf-engines-step">
	<div class="icon-container">
		<FileText size={48} />
	</div>

	<h2 class="title">PDF Extraction Engines</h2>
	<p class="description">
		Choose which engines to use for extracting references from PDFs.
		Engines are tried in order: GROBID, then pdftotext, then OCR.
	</p>

	<div class="engines-grid">
		{#each pdfEngines as engine}
			<EngineCard
				name={engine.name}
				description={engine.description}
				enabled={enabledEngines[engine.id]}
				badge={engine.badge}
				features={engine.features}
				onToggle={(enabled) => handleToggle(engine.id, enabled)}
			/>
		{/each}
	</div>

	{#if !atLeastOneEnabled}
		<p class="validation-message">Please enable at least one extraction engine to continue.</p>
	{/if}

	<div class="info-box">
		<p>
			<strong>Tip:</strong> Enable GROBID and pdftotext for the best experience.
			OCR is only needed for scanned documents.
		</p>
	</div>
</div>

<style>
	.pdf-engines-step {
		text-align: center;
		padding: 1rem;
		animation: fadeIn 0.5s ease;
	}

	@keyframes fadeIn {
		from {
			opacity: 0;
			transform: translateY(10px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.icon-container {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 80px;
		height: 80px;
		border-radius: 20px;
		background: linear-gradient(135deg, #0066cc, #0044aa);
		color: white;
		margin-bottom: 1.5rem;
	}

	.title {
		font-size: 1.5rem;
		font-weight: 600;
		color: var(--color-text-primary, #333);
		margin: 0 0 0.5rem;
	}

	.description {
		font-size: 0.95rem;
		color: var(--color-text-secondary, #666);
		margin: 0 0 1.5rem;
		max-width: 500px;
		margin-left: auto;
		margin-right: auto;
	}

	.engines-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
		gap: 1rem;
		max-width: 900px;
		margin: 0 auto 1.5rem;
	}

	.validation-message {
		color: #dc2626;
		font-size: 0.9rem;
		margin-top: 1rem;
	}

	.info-box {
		max-width: 500px;
		margin: 1rem auto 0;
		padding: 1rem;
		background: #f0f9ff;
		border-radius: 12px;
		border: 1px solid #bae6fd;
	}

	.info-box p {
		margin: 0;
		font-size: 0.9rem;
		color: #0369a1;
	}

	.info-box strong {
		color: #0c4a6e;
	}
</style>
