<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		collapsed?: boolean;
		width?: string;
		collapsedWidth?: string;
		class?: string;
		children: Snippet;
		header?: Snippet;
		footer?: Snippet;
	}

	let {
		collapsed = false,
		width = 'var(--sidebar-width)',
		collapsedWidth = 'var(--sidebar-collapsed-width)',
		class: className = '',
		children,
		header,
		footer
	}: Props = $props();

	let computedWidth = $derived(collapsed ? collapsedWidth : width);
</script>

<aside
	class="h-full flex flex-col bg-[hsl(var(--sidebar-bg))] backdrop-blur-[var(--blur-lg)] border-r border-border/30 transition-[width] duration-[var(--transition-normal)] overflow-hidden {className}"
	style="width: {computedWidth};"
	role="navigation"
	aria-label="Sidebar navigation"
>
	{#if header}
		<div class="shrink-0 px-4 py-3 border-b border-border/30">
			{@render header()}
		</div>
	{/if}

	<nav class="flex-1 overflow-y-auto overflow-x-hidden py-2 px-2">
		{@render children()}
	</nav>

	{#if footer}
		<div class="shrink-0 px-4 py-3 border-t border-border/30">
			{@render footer()}
		</div>
	{/if}
</aside>
