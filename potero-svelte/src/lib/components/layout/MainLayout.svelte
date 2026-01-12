<script lang="ts">
	import type { Snippet } from 'svelte';
	import { MacTitleBar } from '$lib/components/ui';
	import { Search, Plus, Settings } from 'lucide-svelte';

	interface Props {
		title?: string;
		showSidebar?: boolean;
		showInspector?: boolean;
		sidebarWidth?: number;
		inspectorWidth?: number;
		sidebar: Snippet;
		content: Snippet;
		inspector?: Snippet;
		statusBar?: Snippet;
		onSearch?: () => void;
		onAdd?: () => void;
		onSettings?: () => void;
	}

	let {
		title = 'Potero',
		showSidebar = true,
		showInspector = true,
		sidebarWidth = 240,
		inspectorWidth = 320,
		sidebar,
		content,
		inspector,
		statusBar,
		onSearch,
		onAdd,
		onSettings
	}: Props = $props();

	// Resizable panel state
	let isDraggingSidebar = $state(false);
	let isDraggingInspector = $state(false);
	let currentSidebarWidth = $state(sidebarWidth);
	let currentInspectorWidth = $state(inspectorWidth);

	function handleSidebarDragStart() {
		isDraggingSidebar = true;
	}

	function handleInspectorDragStart() {
		isDraggingInspector = true;
	}

	function handleMouseMove(e: MouseEvent) {
		if (isDraggingSidebar) {
			currentSidebarWidth = Math.max(180, Math.min(400, e.clientX));
		}
		if (isDraggingInspector) {
			currentInspectorWidth = Math.max(280, Math.min(500, window.innerWidth - e.clientX));
		}
	}

	function handleMouseUp() {
		isDraggingSidebar = false;
		isDraggingInspector = false;
	}
</script>

<svelte:window onmousemove={handleMouseMove} onmouseup={handleMouseUp} />

<div class="flex h-screen w-screen flex-col overflow-hidden bg-background">
	<!-- Title Bar -->
	<MacTitleBar {title} showTrafficLights>
		{#snippet actions()}
			<div class="flex items-center gap-1">
				{#if onSearch}
					<button
						onclick={onSearch}
						class="flex h-7 items-center gap-1.5 rounded-md px-2.5 text-sm text-muted-foreground transition-colors hover:bg-[hsl(var(--sidebar-item-hover))] hover:text-foreground"
						title="Search (⌘K)"
					>
						<Search class="h-4 w-4" />
					</button>
				{/if}
				{#if onAdd}
					<button
						onclick={onAdd}
						class="flex h-7 items-center gap-1.5 rounded-md px-2.5 text-sm text-muted-foreground transition-colors hover:bg-[hsl(var(--sidebar-item-hover))] hover:text-foreground"
						title="Add Paper"
					>
						<Plus class="h-4 w-4" />
					</button>
				{/if}
				{#if onSettings}
					<button
						onclick={onSettings}
						class="flex h-7 items-center gap-1.5 rounded-md px-2.5 text-sm text-muted-foreground transition-colors hover:bg-[hsl(var(--sidebar-item-hover))] hover:text-foreground"
						title="Settings"
					>
						<Settings class="h-4 w-4" />
					</button>
				{/if}
			</div>
		{/snippet}
	</MacTitleBar>

	<!-- Main Content Area -->
	<div class="flex flex-1 overflow-hidden">
		<!-- Sidebar -->
		{#if showSidebar}
			<aside
				class="h-full shrink-0 overflow-hidden border-r border-border/30 bg-[hsl(var(--sidebar-bg))] backdrop-blur-[var(--blur-lg)]"
				style="width: {currentSidebarWidth}px;"
			>
				{@render sidebar()}
			</aside>

			<!-- Sidebar Resize Handle -->
			<div
				class="w-1 shrink-0 cursor-col-resize bg-transparent hover:bg-primary/20 transition-colors {isDraggingSidebar ? 'bg-primary/30' : ''}"
				onmousedown={handleSidebarDragStart}
				role="separator"
				aria-orientation="vertical"
			></div>
		{/if}

		<!-- Content Area -->
		<main class="flex-1 overflow-hidden bg-background">
			{@render content()}
		</main>

		<!-- Inspector Resize Handle -->
		{#if showInspector && inspector}
			<div
				class="w-1 shrink-0 cursor-col-resize bg-transparent hover:bg-primary/20 transition-colors {isDraggingInspector ? 'bg-primary/30' : ''}"
				onmousedown={handleInspectorDragStart}
				role="separator"
				aria-orientation="vertical"
			></div>

			<!-- Inspector Panel -->
			<aside
				class="h-full shrink-0 overflow-hidden border-l border-border/30 bg-[hsl(var(--sidebar-bg))] backdrop-blur-[var(--blur-lg)]"
				style="width: {currentInspectorWidth}px;"
			>
				{@render inspector()}
			</aside>
		{/if}
	</div>

	<!-- Status Bar -->
	{#if statusBar}
		<footer class="h-6 shrink-0 border-t border-border/30 bg-[hsl(var(--sidebar-bg))] backdrop-blur-[var(--blur-sm)]">
			{@render statusBar()}
		</footer>
	{/if}
</div>

<style>
	/* Prevent text selection during resize */
	:global(body.resizing) {
		user-select: none;
		cursor: col-resize;
	}
</style>
