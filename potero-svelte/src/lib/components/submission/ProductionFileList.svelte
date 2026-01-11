<script lang="ts">
	import { FileText, Download } from 'lucide-svelte';
	import type { SubmissionFile } from '$lib/types';

	interface Props {
		files: SubmissionFile[];
		title?: string;
		onDownload?: (file: SubmissionFile) => void;
	}

	let { files, title = 'Production Ready Files', onDownload }: Props = $props();
</script>

<div class="rounded-lg border bg-white overflow-hidden">
	<!-- Header -->
	<div class="flex items-center gap-2 px-4 py-3 border-b bg-muted/30">
		<FileText class="h-4 w-4 text-primary" />
		<span class="font-medium text-sm">{title}</span>
	</div>

	<!-- File list -->
	<div class="divide-y">
		{#each files as file, i}
			<div
				class="flex items-center justify-between px-4 py-3 hover:bg-muted/30 transition-colors group"
			>
				<div class="flex items-center gap-3">
					<span class="text-sm text-muted-foreground w-6">{i + 1}</span>
					<span class="text-sm truncate max-w-xs" title={file.fileName}>
						{file.fileName}
					</span>
				</div>
				<button
					class="p-1.5 rounded hover:bg-muted transition-colors opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-foreground"
					onclick={() => onDownload?.(file)}
					title="Download"
				>
					<Download class="h-4 w-4" />
				</button>
			</div>
		{/each}

		{#if files.length === 0}
			<div class="flex items-center justify-center py-8 text-muted-foreground text-sm">
				No files available
			</div>
		{/if}
	</div>
</div>
