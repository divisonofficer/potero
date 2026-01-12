<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		variant?: 'default' | 'primary' | 'ghost' | 'destructive' | 'outline';
		size?: 'xs' | 'sm' | 'md' | 'lg';
		disabled?: boolean;
		loading?: boolean;
		type?: 'button' | 'submit' | 'reset';
		class?: string;
		onclick?: (e: MouseEvent) => void;
		children: Snippet;
		icon?: Snippet;
	}

	let {
		variant = 'default',
		size = 'md',
		disabled = false,
		loading = false,
		type = 'button',
		class: className = '',
		onclick,
		children,
		icon
	}: Props = $props();

	const baseClasses =
		'inline-flex items-center justify-center font-medium transition-all duration-[var(--transition-fast)] rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none press-effect';

	const variantClasses: Record<string, string> = {
		default: 'glass hover:bg-[hsl(var(--glass-bg-hover))] text-foreground shadow-glass',
		primary:
			'bg-primary text-primary-foreground hover:bg-[hsl(var(--primary-hover))] shadow-[0_2px_12px_hsl(var(--primary-glow))]',
		ghost: 'hover:bg-[hsl(var(--sidebar-item-hover))] text-foreground bg-transparent',
		destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90',
		outline: 'border border-border bg-transparent hover:bg-[hsl(var(--sidebar-item-hover))] text-foreground'
	};

	const sizeClasses: Record<string, string> = {
		xs: 'h-7 px-2.5 text-xs gap-1',
		sm: 'h-8 px-3 text-xs gap-1.5',
		md: 'h-10 px-4 text-sm gap-2',
		lg: 'h-12 px-6 text-base gap-2'
	};

	let computedClasses = $derived(
		[baseClasses, variantClasses[variant], sizeClasses[size], className].filter(Boolean).join(' ')
	);
</script>

<button class={computedClasses} {disabled} {type} {onclick} aria-busy={loading}>
	{#if loading}
		<svg
			class="animate-spin h-4 w-4"
			xmlns="http://www.w3.org/2000/svg"
			fill="none"
			viewBox="0 0 24 24"
		>
			<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"
			></circle>
			<path
				class="opacity-75"
				fill="currentColor"
				d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
			></path>
		</svg>
	{:else if icon}
		{@render icon()}
	{/if}
	{@render children()}
</button>
