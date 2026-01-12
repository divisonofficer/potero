<script lang="ts">
	interface Props {
		name: string;
		description: string;
		enabled: boolean;
		badge?: { type: 'recommended' | 'fallback' | 'slow'; text: string };
		features?: string[];
		disabled?: boolean;
		onToggle: (enabled: boolean) => void;
	}

	let {
		name,
		description,
		enabled,
		badge,
		features = [],
		disabled = false,
		onToggle
	}: Props = $props();

	function handleToggle(e: Event) {
		const target = e.target as HTMLInputElement;
		onToggle(target.checked);
	}
</script>

<div class="engine-card">
	<div class="engine-header">
		<label class="engine-toggle">
			<input
				type="checkbox"
				checked={enabled}
				{disabled}
				onchange={handleToggle}
			/>
			<span class="engine-name">{name}</span>
		</label>
		{#if badge}
			<span class="engine-badge {badge.type}">{badge.text}</span>
		{/if}
	</div>
	<p class="engine-description">{description}</p>
	{#if features.length > 0}
		<div class="engine-features">
			{#each features as feature}
				<span class="feature">{feature}</span>
			{/each}
		</div>
	{/if}
</div>

<style>
	.engine-card {
		background: var(--color-surface, #fff);
		border: 1px solid var(--color-border, #e0e0e0);
		border-radius: 12px;
		padding: 1.25rem;
		transition: all 0.2s ease;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
	}

	.engine-card:hover {
		box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
		border-color: var(--color-primary, #0066cc);
		transform: translateY(-2px);
	}

	.engine-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.75rem;
	}

	.engine-toggle {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		cursor: pointer;
		user-select: none;
	}

	.engine-toggle input[type='checkbox'] {
		width: 20px;
		height: 20px;
		cursor: pointer;
		accent-color: var(--color-primary, #0066cc);
		transition: all 0.2s;
	}

	.engine-toggle input[type='checkbox']:hover:not(:disabled) {
		transform: scale(1.1);
	}

	.engine-toggle input[type='checkbox']:disabled {
		cursor: not-allowed;
		opacity: 0.5;
	}

	.engine-name {
		font-weight: 600;
		font-size: 1.05rem;
	}

	.engine-badge {
		padding: 0.25rem 0.75rem;
		border-radius: 12px;
		font-size: 0.8rem;
		font-weight: 500;
	}

	.engine-badge.recommended {
		background-color: #e6f7ff;
		color: #0066cc;
	}

	.engine-badge.fallback {
		background-color: #fff7e6;
		color: #cc7a00;
	}

	.engine-badge.slow {
		background-color: #fff0f0;
		color: #cc0000;
	}

	.engine-description {
		color: var(--color-text-secondary, #666);
		font-size: 0.9rem;
		margin: 0.5rem 0;
		line-height: 1.5;
	}

	.engine-features {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		margin-top: 0.75rem;
	}

	.feature {
		padding: 0.25rem 0.625rem;
		background-color: var(--color-surface-secondary, #f5f5f5);
		border-radius: 6px;
		font-size: 0.8rem;
		color: var(--color-text-tertiary, #666);
	}
</style>
