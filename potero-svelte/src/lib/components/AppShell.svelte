<script lang="ts">
	import type { Snippet } from 'svelte';
	import MacTitleBar from './ui/MacTitleBar.svelte';
	import MacSidebar from './ui/MacSidebar.svelte';

	interface Props {
		showTitleBar?: boolean;
		showSidebar?: boolean;
		sidebarCollapsed?: boolean;
		title?: string;
		class?: string;
		onClose?: () => void;
		onMinimize?: () => void;
		onMaximize?: () => void;
		sidebar?: Snippet;
		sidebarHeader?: Snippet;
		sidebarFooter?: Snippet;
		titleBarActions?: Snippet;
		titleBarContent?: Snippet;
		children: Snippet;
	}

	let {
		showTitleBar = false,
		showSidebar = false,
		sidebarCollapsed = false,
		title = 'Potero',
		class: className = '',
		onClose,
		onMinimize,
		onMaximize,
		sidebar,
		sidebarHeader,
		sidebarFooter,
		titleBarActions,
		titleBarContent,
		children
	}: Props = $props();
</script>

<div class="h-screen w-screen flex flex-col bg-background overflow-hidden {className}">
	{#if showTitleBar}
		<MacTitleBar
			{title}
			{onClose}
			{onMinimize}
			{onMaximize}
			actions={titleBarActions}
		>
			{#if titleBarContent}
				{@render titleBarContent()}
			{/if}
		</MacTitleBar>
	{/if}

	<div class="flex-1 flex overflow-hidden">
		{#if showSidebar && sidebar}
			<MacSidebar collapsed={sidebarCollapsed} header={sidebarHeader} footer={sidebarFooter}>
				{@render sidebar()}
			</MacSidebar>
		{/if}

		<main class="flex-1 overflow-auto">
			{@render children()}
		</main>
	</div>
</div>
