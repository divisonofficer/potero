<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title?: string;
		showTrafficLights?: boolean;
		transparent?: boolean;
		onClose?: () => void;
		onMinimize?: () => void;
		onMaximize?: () => void;
		class?: string;
		children?: Snippet;
		actions?: Snippet;
	}

	let {
		title = '',
		showTrafficLights = true,
		transparent = false,
		onClose,
		onMinimize,
		onMaximize,
		class: className = '',
		children,
		actions
	}: Props = $props();

	let isHovering = $state(false);
</script>

<header
	class="h-[var(--window-header-height)] flex items-center justify-between px-4 border-b transition-colors duration-[var(--transition-fast)]
		   {transparent ? 'bg-transparent border-transparent' : 'glass-subtle border-border/30'}
		   {className}"
	role="banner"
>
	<div class="flex items-center gap-3">
		{#if showTrafficLights}
			<!-- svelte-ignore a11y_no_static_element_interactions -->
			<div
				class="flex items-center gap-2 group"
				onmouseenter={() => (isHovering = true)}
				onmouseleave={() => (isHovering = false)}
			>
				<!-- Close button (red) -->
				<button
					class="w-3 h-3 rounded-full bg-[#FF5F57] transition-all duration-[var(--transition-fast)]
						   hover:brightness-90 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-[#FF5F57]
						   flex items-center justify-center"
					onclick={onClose}
					aria-label="Close window"
				>
					{#if isHovering}
						<svg class="w-2 h-2 text-[#4d0000]" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2">
							<path d="M3 3l6 6M9 3l-6 6" />
						</svg>
					{/if}
				</button>

				<!-- Minimize button (yellow) -->
				<button
					class="w-3 h-3 rounded-full bg-[#FEBC2E] transition-all duration-[var(--transition-fast)]
						   hover:brightness-90 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-[#FEBC2E]
						   flex items-center justify-center"
					onclick={onMinimize}
					aria-label="Minimize window"
				>
					{#if isHovering}
						<svg class="w-2 h-2 text-[#995700]" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2">
							<path d="M2 6h8" />
						</svg>
					{/if}
				</button>

				<!-- Maximize button (green) -->
				<button
					class="w-3 h-3 rounded-full bg-[#28C840] transition-all duration-[var(--transition-fast)]
						   hover:brightness-90 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-[#28C840]
						   flex items-center justify-center"
					onclick={onMaximize}
					aria-label="Maximize window"
				>
					{#if isHovering}
						<svg class="w-2 h-2 text-[#006500]" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5">
							<path d="M2 4l4-2 4 2M2 8l4 2 4-2M2 4v4M10 4v4" />
						</svg>
					{/if}
				</button>
			</div>
		{/if}

		{#if title}
			<span class="text-sm font-medium text-foreground/80 ml-2 select-none">{title}</span>
		{/if}

		{#if children}
			<div class="flex items-center">
				{@render children()}
			</div>
		{/if}
	</div>

	{#if actions}
		<div class="flex items-center gap-2">
			{@render actions()}
		</div>
	{/if}
</header>
