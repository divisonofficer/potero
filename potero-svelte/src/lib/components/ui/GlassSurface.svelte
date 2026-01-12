<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		variant?: 'default' | 'subtle' | 'strong';
		padding?: 'none' | 'sm' | 'md' | 'lg' | 'xl';
		radius?: 'none' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';
		shadow?: boolean;
		border?: boolean;
		class?: string;
		children: Snippet;
	}

	let {
		variant = 'default',
		padding = 'md',
		radius = 'lg',
		shadow = true,
		border = true,
		class: className = '',
		children
	}: Props = $props();

	const variantClasses: Record<string, string> = {
		default: 'glass',
		subtle: 'glass-subtle',
		strong: 'glass-strong'
	};

	const paddingClasses: Record<string, string> = {
		none: '',
		sm: 'p-3',
		md: 'p-4',
		lg: 'p-6',
		xl: 'p-8'
	};

	const radiusClasses: Record<string, string> = {
		none: '',
		sm: 'rounded-lg',
		md: 'rounded-xl',
		lg: 'rounded-2xl',
		xl: 'rounded-3xl',
		'2xl': 'rounded-[24px]',
		full: 'rounded-full'
	};

	let computedClasses = $derived(
		[
			variantClasses[variant],
			paddingClasses[padding],
			radiusClasses[radius],
			shadow ? 'shadow-glass' : '',
			!border ? 'border-0' : '',
			className
		]
			.filter(Boolean)
			.join(' ')
	);
</script>

<div class={computedClasses}>
	{@render children()}
</div>
