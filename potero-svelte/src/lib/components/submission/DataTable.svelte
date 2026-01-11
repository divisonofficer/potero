<script lang="ts">
	import { ChevronDown, ChevronRight, Download, MoreHorizontal } from 'lucide-svelte';
	import type { SubmissionFile } from '$lib/types';

	interface Column {
		key: string;
		header: string;
		width?: string;
	}

	interface Props {
		columns: Column[];
		data: SubmissionFile[];
		expandedIds?: Set<string>;
		onToggleExpand?: (id: string) => void;
		onDownload?: (file: SubmissionFile) => void;
	}

	let {
		columns,
		data,
		expandedIds = new Set(),
		onToggleExpand,
		onDownload
	}: Props = $props();

	function hasChildren(file: SubmissionFile): boolean {
		return Boolean(file.children && file.children.length > 0);
	}

	function getCellValue(file: SubmissionFile, key: string): string {
		switch (key) {
			case 'fileName':
				return file.fileName;
			case 'articleType':
				return file.articleType;
			case 'fileType':
				return file.fileType;
			case 'fileFormat':
				return file.fileFormat;
			default:
				return '';
		}
	}
</script>

<div class="overflow-x-auto rounded-lg border bg-white">
	<table class="w-full text-sm">
		<thead class="border-b bg-muted/30">
			<tr>
				<th class="w-12 p-3 text-left text-xs font-medium text-muted-foreground">#</th>
				{#each columns as col}
					<th
						class="p-3 text-left text-xs font-medium text-muted-foreground"
						style:width={col.width}
					>
						{col.header}
					</th>
				{/each}
				<th class="w-24 p-3 text-left text-xs font-medium text-muted-foreground">Action(s)</th>
			</tr>
		</thead>
		<tbody class="divide-y">
			{#each data as file, i}
				<!-- Main row -->
				<tr class="hover:bg-muted/30 transition-colors group">
					<td class="p-3 text-muted-foreground">
						<div class="flex items-center gap-1">
							{#if hasChildren(file)}
								<button
									class="p-1 hover:bg-muted rounded transition-colors"
									onclick={() => onToggleExpand?.(file.id)}
									aria-expanded={expandedIds.has(file.id)}
									aria-label={expandedIds.has(file.id) ? 'Collapse' : 'Expand'}
								>
									{#if expandedIds.has(file.id)}
										<ChevronDown class="h-4 w-4" />
									{:else}
										<ChevronRight class="h-4 w-4" />
									{/if}
								</button>
							{:else}
								<span class="w-6"></span>
							{/if}
							{i + 1}
						</div>
					</td>
					{#each columns as col}
						<td class="p-3">
							{#if col.key === 'fileName'}
								<div class="flex items-center gap-2">
									<span class="truncate max-w-xs" title={file.fileName}>
										{getCellValue(file, col.key)}
									</span>
									{#if hasChildren(file)}
										<ChevronDown
											class="h-4 w-4 text-muted-foreground transition-transform {expandedIds.has(
												file.id
											)
												? 'rotate-180'
												: ''}"
										/>
									{/if}
								</div>
							{:else}
								{getCellValue(file, col.key)}
							{/if}
						</td>
					{/each}
					<td class="p-3">
						<div class="flex items-center gap-1">
							<button
								class="p-1.5 hover:bg-muted rounded transition-colors text-muted-foreground hover:text-foreground"
								onclick={() => onDownload?.(file)}
								title="Download"
							>
								<Download class="h-4 w-4" />
							</button>
							<button
								class="p-1.5 hover:bg-muted rounded transition-colors text-muted-foreground hover:text-foreground"
								title="More actions"
							>
								<MoreHorizontal class="h-4 w-4" />
							</button>
						</div>
					</td>
				</tr>

				<!-- Expanded children rows -->
				{#if hasChildren(file) && expandedIds.has(file.id)}
					{#each file.children as child, j}
						<tr class="bg-muted/20 hover:bg-muted/40 transition-colors">
							<td class="p-3 text-muted-foreground pl-10">
								<span class="text-muted-foreground">--</span>
							</td>
							{#each columns as col}
								<td class="p-3 text-muted-foreground">
									{#if col.key === 'fileName'}
										<div class="flex items-center gap-2 pl-4">
											<span class="truncate max-w-xs" title={child.fileName}>
												{getCellValue(child, col.key)}
											</span>
										</div>
									{:else}
										{getCellValue(child, col.key)}
									{/if}
								</td>
							{/each}
							<td class="p-3">
								<div class="flex items-center gap-1">
									<button
										class="p-1.5 hover:bg-muted rounded transition-colors text-muted-foreground hover:text-foreground"
										onclick={() => onDownload?.(child)}
										title="Download"
									>
										<Download class="h-4 w-4" />
									</button>
								</div>
							</td>
						</tr>
					{/each}
				{/if}
			{/each}
		</tbody>
	</table>
</div>
