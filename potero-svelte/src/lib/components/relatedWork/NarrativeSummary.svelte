<script lang="ts">
import { Copy, Check, ChevronDown, ChevronRight } from 'lucide-svelte';
import type { ComparisonNarrative } from '$lib/types';
import { renderMarkdown } from '$lib/utils/markdown';

interface Props {
	narrative: ComparisonNarrative;
}

let { narrative }: Props = $props();

// State for collapsible section
let isExpanded = $state(true);
let isCopied = $state(false);

// Rendered markdown
let contentHtml = $derived(renderMarkdown(narrative.content));

function toggleExpanded() {
	isExpanded = !isExpanded;
}

async function handleCopy() {
	try {
		// Build full text with insights
		let fullText = narrative.content;

		if (narrative.keyInsights.length > 0) {
			fullText += '\n\n## Key Insights\n\n';
			narrative.keyInsights.forEach((insight) => {
				fullText += `- ${insight}\n`;
			});
		}

		await navigator.clipboard.writeText(fullText);
		isCopied = true;

		// Reset copied state after 2 seconds
		setTimeout(() => {
			isCopied = false;
		}, 2000);
	} catch (err) {
		console.error('Failed to copy:', err);
	}
}
</script>

<div class="rounded-lg border border-border bg-card">
	<!-- Header -->
	<div class="flex w-full items-center justify-between bg-muted/20 px-4 py-3">
		<button
			onclick={toggleExpanded}
			class="flex flex-1 items-center gap-2 text-sm font-semibold hover:text-primary"
		>
			{#if isExpanded}
				<ChevronDown class="h-4 w-4" />
			{:else}
				<ChevronRight class="h-4 w-4" />
			{/if}
			<span>Narrative Summary</span>
		</button>
		<button
			onclick={handleCopy}
			class="flex items-center gap-1 rounded bg-background px-2 py-1 text-xs font-medium transition-colors hover:bg-muted"
		>
			{#if isCopied}
				<Check class="h-3 w-3 text-green-600" />
				<span class="text-green-600">Copied!</span>
			{:else}
				<Copy class="h-3 w-3" />
				<span>Copy</span>
			{/if}
		</button>
	</div>

	<!-- Content -->
	{#if isExpanded}
		<div class="p-4">
			<!-- Main Narrative Content (Rendered Markdown) -->
			<div class="prose prose-sm dark:prose-invert max-w-none">
				{@html contentHtml}
			</div>

			<!-- Key Insights Section -->
			{#if narrative.keyInsights.length > 0}
				<div class="mt-6 rounded-lg bg-primary/5 p-4">
					<h4 class="mb-3 font-semibold text-primary">Key Insights</h4>
					<ul class="space-y-2">
						{#each narrative.keyInsights as insight}
							<li class="flex items-start gap-2">
								<span class="mt-1 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-primary"></span>
								<span class="text-sm text-foreground">{insight}</span>
							</li>
						{/each}
					</ul>
				</div>
			{/if}

			<!-- Metadata -->
			<div class="mt-4 border-t border-border pt-3 text-xs text-muted-foreground">
				<p>Generated: {new Date(narrative.createdAt).toLocaleString()}</p>
			</div>
		</div>
	{/if}
</div>

