<script lang="ts">
	import { ChevronRight } from 'lucide-svelte';

	interface BreadcrumbItem {
		label: string;
		href?: string;
	}

	interface Props {
		items: BreadcrumbItem[];
		articleId?: string;
	}

	let { items, articleId }: Props = $props();
</script>

<div class="flex items-center justify-between border-b bg-white px-6 py-3">
	<nav class="flex items-center gap-1 text-sm">
		{#each items as item, i}
			{#if i > 0}
				<ChevronRight class="h-4 w-4 text-muted-foreground" />
			{/if}
			{#if item.href && i < items.length - 1}
				<a href={item.href} class="text-muted-foreground hover:text-foreground transition-colors">
					{item.label}
				</a>
			{:else if i === items.length - 1}
				<span class="font-medium text-primary">{item.label}</span>
			{:else}
				<span class="text-muted-foreground">{item.label}</span>
			{/if}
		{/each}
	</nav>

	{#if articleId}
		<div class="flex items-center gap-2 text-sm text-muted-foreground">
			<span>(Article Id : {articleId})</span>
			<button
				class="rounded p-1 hover:bg-muted transition-colors"
				title="Copy Article ID"
				onclick={() => navigator.clipboard.writeText(articleId)}
			>
				<svg
					xmlns="http://www.w3.org/2000/svg"
					width="14"
					height="14"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
					stroke-linejoin="round"
				>
					<rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
					<path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
				</svg>
			</button>
		</div>
	{/if}
</div>
