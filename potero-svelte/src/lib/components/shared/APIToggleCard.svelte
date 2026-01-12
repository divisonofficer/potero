<script lang="ts">
	interface Props {
		id: string;
		name: string;
		description: string;
		enabled: boolean;
		requiresKey?: boolean;
		hasKey?: boolean;
		keyMasked?: string | null;
		keyRegistrationUrl?: string | null;
		disabled?: boolean;
		onToggle: (enabled: boolean) => void;
		onKeyUpdate?: (key: string) => void;
	}

	let {
		id,
		name,
		description,
		enabled,
		requiresKey = false,
		hasKey = false,
		keyMasked = null,
		keyRegistrationUrl = null,
		disabled = false,
		onToggle,
		onKeyUpdate
	}: Props = $props();

	function handleToggle(e: Event) {
		const target = e.target as HTMLInputElement;
		onToggle(target.checked);
	}

	function handleKeyChange(e: Event) {
		const target = e.target as HTMLInputElement;
		const key = target.value.trim();
		if (key && onKeyUpdate) {
			onKeyUpdate(key);
			target.value = '';
		}
	}
</script>

<div class="api-card {enabled ? 'enabled' : ''}">
	<div class="api-header">
		<label class="api-toggle">
			<input
				type="checkbox"
				checked={enabled}
				{disabled}
				onchange={handleToggle}
			/>
			<span class="api-name">{name}</span>
		</label>

		{#if keyRegistrationUrl}
			<a
				href={keyRegistrationUrl}
				target="_blank"
				rel="noopener noreferrer"
				class="api-link"
			>
				Get API Key
			</a>
		{/if}
	</div>

	<p class="api-description">{description}</p>

	{#if (requiresKey || keyRegistrationUrl) && enabled && onKeyUpdate}
		<div class="api-key-input">
			<label>
				API Key{#if !requiresKey} (Optional){/if}:
				<input
					type="password"
					placeholder={hasKey ? 'Key configured' : 'Enter API key'}
					{disabled}
					onchange={handleKeyChange}
				/>
			</label>
			{#if keyMasked}
				<span class="key-hint">Current: {keyMasked}</span>
			{/if}
		</div>
	{/if}
</div>

<style>
	.api-card {
		background: var(--color-surface, #fff);
		border: 1px solid var(--color-border, #e0e0e0);
		border-radius: 12px;
		padding: 1.25rem;
		transition: all 0.2s ease;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
		position: relative;
	}

	.api-card:hover {
		box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
		transform: translateY(-2px);
	}

	/* Status indicator for enabled/disabled APIs */
	.api-card::before {
		content: '';
		position: absolute;
		top: 1rem;
		right: 1rem;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background-color: #ef4444;
		box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
	}

	.api-card.enabled::before {
		background-color: #22c55e;
		box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2);
	}

	.api-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.75rem;
	}

	.api-toggle {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		cursor: pointer;
		user-select: none;
	}

	.api-toggle input[type='checkbox'] {
		width: 20px;
		height: 20px;
		cursor: pointer;
		accent-color: var(--color-primary, #0066cc);
		transition: all 0.2s;
	}

	.api-toggle input[type='checkbox']:hover:not(:disabled) {
		transform: scale(1.1);
	}

	.api-toggle input[type='checkbox']:disabled {
		cursor: not-allowed;
		opacity: 0.5;
	}

	.api-name {
		font-weight: 600;
		font-size: 1.05rem;
	}

	.api-link {
		color: var(--color-primary, #0066cc);
		text-decoration: none;
		font-size: 0.9rem;
		font-weight: 500;
	}

	.api-link:hover {
		text-decoration: underline;
	}

	.api-description {
		color: var(--color-text-secondary, #666);
		font-size: 0.9rem;
		margin: 0.5rem 0;
		line-height: 1.5;
	}

	.api-key-input {
		margin-top: 1rem;
		padding-top: 1rem;
		border-top: 1px solid var(--color-border-light, #f0f0f0);
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.api-key-input label {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		font-size: 0.9rem;
		font-weight: 500;
	}

	.api-key-input input {
		width: 100%;
		max-width: 500px;
		padding: 0.625rem;
		border: 1px solid var(--color-border, #ccc);
		border-radius: 6px;
		font-size: 0.95rem;
		transition: border-color 0.2s;
	}

	.api-key-input input:focus {
		outline: none;
		border-color: var(--color-primary, #0066cc);
		box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
	}

	.api-key-input input:disabled {
		background-color: var(--color-surface-disabled, #f5f5f5);
		cursor: not-allowed;
	}

	.key-hint {
		font-size: 0.85rem;
		color: var(--color-text-tertiary, #888);
		font-family: monospace;
	}
</style>
