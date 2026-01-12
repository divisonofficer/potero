<script lang="ts">
	import { RefreshCw, Check, AlertCircle } from 'lucide-svelte';

	interface Props {
		totalCount: number;
		selectedCount: number;
		syncStatus?: 'synced' | 'syncing' | 'error';
		taskStatus?: string;
	}

	let { totalCount, selectedCount, syncStatus = 'synced', taskStatus }: Props = $props();
</script>

<div class="flex h-full items-center justify-between px-3 text-xs text-muted-foreground">
	<!-- Left: Paper Count -->
	<div class="flex items-center gap-4">
		<span>{totalCount.toLocaleString()} papers</span>
		{#if selectedCount > 0}
			<span class="text-foreground">{selectedCount} selected</span>
		{/if}
	</div>

	<!-- Right: Sync Status & Tasks -->
	<div class="flex items-center gap-4">
		{#if taskStatus}
			<span class="flex items-center gap-1.5">
				<RefreshCw class="h-3 w-3 animate-spin" />
				{taskStatus}
			</span>
		{/if}

		<span class="flex items-center gap-1.5">
			{#if syncStatus === 'syncing'}
				<RefreshCw class="h-3 w-3 animate-spin text-primary" />
				Syncing...
			{:else if syncStatus === 'error'}
				<AlertCircle class="h-3 w-3 text-destructive" />
				Sync Error
			{:else}
				<Check class="h-3 w-3 text-green-500" />
				Synced
			{/if}
		</span>
	</div>
</div>
