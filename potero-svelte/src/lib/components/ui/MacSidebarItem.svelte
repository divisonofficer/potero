<script lang="ts">
	import type { Component } from 'svelte';

	interface Props {
		active?: boolean;
		icon?: Component;
		label: string;
		badge?: number | string;
		badgeVariant?: 'default' | 'primary' | 'warning' | 'error' | 'success';
		collapsed?: boolean;
		disabled?: boolean;
		class?: string;
		onclick?: () => void;
	}

	let {
		active = false,
		icon: Icon,
		label,
		badge,
		badgeVariant = 'default',
		collapsed = false,
		disabled = false,
		class: className = '',
		onclick
	}: Props = $props();

	const badgeVariantClasses: Record<string, string> = {
		default: 'bg-muted text-muted-foreground',
		primary: 'bg-primary/10 text-primary',
		warning: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
		error: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
		success: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
	};
</script>

<button
	class="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-all duration-[var(--transition-fast)]
		   {active
		? 'bg-[hsl(var(--sidebar-item-active))] text-primary font-medium'
		: 'text-foreground/80 hover:bg-[hsl(var(--sidebar-item-hover))]'}
		   {disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
		   {collapsed ? 'justify-center' : ''}
		   {className}"
	{onclick}
	{disabled}
	aria-pressed={active}
	aria-label={collapsed ? label : undefined}
	title={collapsed ? label : undefined}
>
	{#if Icon}
		<Icon class="h-4 w-4 shrink-0 {active ? 'text-primary' : 'text-muted-foreground'}" />
	{/if}

	{#if !collapsed}
		<span class="truncate flex-1 text-left">{label}</span>

		{#if badge !== undefined}
			<span
				class="ml-auto text-xs px-2 py-0.5 rounded-full font-medium {badgeVariantClasses[
					badgeVariant
				]}"
			>
				{badge}
			</span>
		{/if}
	{/if}
</button>
