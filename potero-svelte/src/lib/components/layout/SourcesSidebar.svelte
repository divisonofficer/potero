<script lang="ts">
	import type { Readable } from 'svelte/store';
	import { MacSidebarItem } from '$lib/components/ui';
	import {
		Library,
		Clock,
		Star,
		Tag,
		User,
		BookOpen,
		FolderOpen,
		ChevronDown,
		ChevronRight,
		FileText,
		Inbox
	} from 'lucide-svelte';
	import type { SourceType } from '$lib/stores/appState';

	interface SidebarDataType {
		paperCount: number;
		recentCount: number;
		favoriteCount: number;
		tags: Array<{ id: string; name: string; count: number }>;
		authors: Array<{ name: string; count: number }>;
		journals: Array<{ name: string; count: number }>;
	}

	interface Props {
		sidebarData: Readable<SidebarDataType>;
		selectedSource: SourceType;
		selectedSourceId?: string;
		onSelectSource: (source: SourceType, sourceId?: string) => void;
	}

	let {
		sidebarData,
		selectedSource,
		selectedSourceId,
		onSelectSource
	}: Props = $props();

	// Collapsible section state
	let showTags = $state(true);
	let showAuthors = $state(false);
	let showJournals = $state(false);

	function isActive(source: SourceType, sourceId?: string): boolean {
		if (sourceId) {
			return selectedSource === source && selectedSourceId === sourceId;
		}
		return selectedSource === source;
	}
</script>

<div class="flex h-full flex-col overflow-hidden">
	<!-- Scrollable Content -->
	<div class="flex-1 overflow-y-auto py-2 px-2">
		<!-- Library Section -->
		<div class="mb-4">
			<div class="px-2 py-1 text-xs font-semibold uppercase text-muted-foreground/70">
				Library
			</div>
			<div class="space-y-0.5">
				<MacSidebarItem
					icon={Library}
					label="All Papers"
					badge={$sidebarData.paperCount}
					active={isActive('all')}
					onclick={() => onSelectSource('all')}
				/>
				<MacSidebarItem
					icon={Clock}
					label="Recent"
					badge={$sidebarData.recentCount}
					active={isActive('recent')}
					onclick={() => onSelectSource('recent')}
				/>
				<MacSidebarItem
					icon={Star}
					label="Favorites"
					badge={$sidebarData.favoriteCount}
					badgeVariant="warning"
					active={isActive('favorites')}
					onclick={() => onSelectSource('favorites')}
				/>
				<MacSidebarItem
					icon={Inbox}
					label="Unread"
					active={isActive('unread')}
					onclick={() => onSelectSource('unread')}
				/>
			</div>
		</div>

		<!-- Tags Section -->
		<div class="mb-4">
			<button
				class="flex w-full items-center gap-1 px-2 py-1 text-xs font-semibold uppercase text-muted-foreground/70 hover:text-muted-foreground"
				onclick={() => (showTags = !showTags)}
			>
				{#if showTags}
					<ChevronDown class="h-3 w-3" />
				{:else}
					<ChevronRight class="h-3 w-3" />
				{/if}
				Tags
			</button>
			{#if showTags}
				<div class="space-y-0.5">
					{#each $sidebarData.tags.slice(0, 10) as tag (tag.id)}
						<MacSidebarItem
							icon={Tag}
							label={tag.name}
							badge={tag.count}
							active={isActive('tag', tag.id)}
							onclick={() => onSelectSource('tag', tag.id)}
						/>
					{/each}
					{#if $sidebarData.tags.length > 10}
						<button
							class="w-full px-3 py-1 text-xs text-muted-foreground hover:text-foreground"
						>
							Show all {$sidebarData.tags.length} tags...
						</button>
					{/if}
				</div>
			{/if}
		</div>

		<!-- Authors Section -->
		<div class="mb-4">
			<button
				class="flex w-full items-center gap-1 px-2 py-1 text-xs font-semibold uppercase text-muted-foreground/70 hover:text-muted-foreground"
				onclick={() => (showAuthors = !showAuthors)}
			>
				{#if showAuthors}
					<ChevronDown class="h-3 w-3" />
				{:else}
					<ChevronRight class="h-3 w-3" />
				{/if}
				Authors
			</button>
			{#if showAuthors}
				<div class="space-y-0.5">
					{#each $sidebarData.authors.slice(0, 8) as author (author.name)}
						<MacSidebarItem
							icon={User}
							label={author.name}
							badge={author.count}
							active={isActive('author', author.name)}
							onclick={() => onSelectSource('author', author.name)}
						/>
					{/each}
					{#if $sidebarData.authors.length > 8}
						<button
							class="w-full px-3 py-1 text-xs text-muted-foreground hover:text-foreground"
						>
							Show all {$sidebarData.authors.length} authors...
						</button>
					{/if}
				</div>
			{/if}
		</div>

		<!-- Journals Section -->
		<div class="mb-4">
			<button
				class="flex w-full items-center gap-1 px-2 py-1 text-xs font-semibold uppercase text-muted-foreground/70 hover:text-muted-foreground"
				onclick={() => (showJournals = !showJournals)}
			>
				{#if showJournals}
					<ChevronDown class="h-3 w-3" />
				{:else}
					<ChevronRight class="h-3 w-3" />
				{/if}
				Journals & Venues
			</button>
			{#if showJournals}
				<div class="space-y-0.5">
					{#each $sidebarData.journals.slice(0, 8) as journal (journal.name)}
						<MacSidebarItem
							icon={BookOpen}
							label={journal.name}
							badge={journal.count}
							active={isActive('journal', journal.name)}
							onclick={() => onSelectSource('journal', journal.name)}
						/>
					{/each}
					{#if $sidebarData.journals.length > 8}
						<button
							class="w-full px-3 py-1 text-xs text-muted-foreground hover:text-foreground"
						>
							Show all {$sidebarData.journals.length} venues...
						</button>
					{/if}
				</div>
			{/if}
		</div>

		<!-- Collections Section -->
		<div class="mb-4">
			<div class="px-2 py-1 text-xs font-semibold uppercase text-muted-foreground/70">
				Collections
			</div>
			<div class="space-y-0.5">
				<MacSidebarItem
					icon={FolderOpen}
					label="Submissions"
					active={isActive('submissions')}
					onclick={() => onSelectSource('submissions')}
				/>
				<MacSidebarItem
					icon={FileText}
					label="Notes"
					active={isActive('notes')}
					onclick={() => onSelectSource('notes')}
				/>
			</div>
		</div>
	</div>
</div>
