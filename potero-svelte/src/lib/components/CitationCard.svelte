<script lang="ts">
	import { api } from '$lib/api/client';
	import type { LinkedReference } from '$lib/api/client';
	import type { Paper } from '$lib/types';
	import { BookOpen, Plus, Search, X } from 'lucide-svelte';

	interface Props {
		linkedRef: LinkedReference;
		pos: { x: number; y: number };
		onClose: () => void;
		onOpenModal: () => void;
		onOpenPaper?: (paperId: string) => void;
	}

	let { linkedRef, pos, onClose, onOpenModal, onOpenPaper }: Props = $props();

	let existingPaper = $state<Paper | null>(null);
	let isChecking = $state(false);
	let isAdding = $state(false);

	$effect(() => {
		// Re-check whenever the linked reference changes
		void linkedRef;
		checkLibrary();
	});

	async function checkLibrary() {
		isChecking = true;
		existingPaper = null;
		if (linkedRef.doi) {
			const r = await api.findPaperByDoi(linkedRef.doi);
			if (r.success && r.data) {
				existingPaper = r.data;
			}
		}
		isChecking = false;
	}

	async function handleAdd() {
		if (!linkedRef.doi) return;
		isAdding = true;
		try {
			const r = await api.importByDoi(linkedRef.doi);
			if (r.success && r.data) existingPaper = r.data;
		} finally {
			isAdding = false;
		}
	}
</script>

<!-- Backdrop -->
<div class="fixed inset-0 z-40" onclick={onClose} role="presentation"></div>

<!-- Card -->
<div
	class="fixed z-50 w-[380px] rounded-xl border border-border bg-popover shadow-xl"
	style="left: {pos.x}px; top: {pos.y}px;"
	role="dialog"
	onclick={(e) => e.stopPropagation()}
>
	<!-- Header -->
	<div class="flex items-start justify-between gap-2 border-b p-3">
		<p class="line-clamp-2 text-sm font-medium leading-snug text-foreground">
			{linkedRef.title ?? 'Untitled Reference'}
		</p>
		<button onclick={onClose} class="shrink-0 rounded p-0.5 text-muted-foreground hover:bg-muted hover:text-foreground">
			<X size={14} />
		</button>
	</div>

	<!-- Meta -->
	<div class="space-y-0.5 px-3 py-2 text-xs text-muted-foreground">
		{#if linkedRef.authors}
			<p class="line-clamp-1">{linkedRef.authors}</p>
		{/if}
		<div class="flex flex-wrap gap-x-3 gap-y-0.5">
			{#if linkedRef.venue}<span>{linkedRef.venue}</span>{/if}
			{#if linkedRef.year}<span>{linkedRef.year}</span>{/if}
		</div>
	</div>

	<!-- Actions -->
	<div class="flex items-center gap-2 border-t px-3 py-2">
		{#if isChecking}
			<div class="flex items-center gap-1.5 text-xs text-muted-foreground">
				<div class="h-3 w-3 animate-spin rounded-full border-2 border-muted-foreground border-t-transparent"></div>
				Checking…
			</div>
		{:else if existingPaper}
			<span class="flex items-center gap-1 rounded bg-green-100 px-2 py-1 text-xs text-green-700 dark:bg-green-900/30 dark:text-green-300">
				✓ In Library
			</span>
			<button
				class="ml-auto flex items-center gap-1 rounded px-2 py-1 text-xs text-primary hover:bg-primary/10"
				onclick={() => { onOpenPaper?.(existingPaper!.id); onClose(); }}
			>
				<BookOpen size={12} /> Open
			</button>
		{:else}
			<button
				class="flex items-center gap-1 rounded bg-primary px-2 py-1 text-xs text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
				disabled={isAdding || !linkedRef.doi}
				onclick={handleAdd}
				title={linkedRef.doi ? 'Add to library' : 'No DOI available'}
			>
				{#if isAdding}
					<div class="h-3 w-3 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent"></div>
				{:else}
					<Plus size={12} />
				{/if}
				Add to Library
			</button>
		{/if}

		<button
			class="ml-auto flex items-center gap-1 rounded px-2 py-1 text-xs text-muted-foreground hover:bg-muted"
			onclick={() => { onOpenModal(); onClose(); }}
		>
			<Search size={12} /> Search Online
		</button>
	</div>
</div>
