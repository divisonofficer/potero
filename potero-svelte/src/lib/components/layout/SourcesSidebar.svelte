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
	import type { Tab } from '$lib/types';

	// Filter type for library view
	export type LibraryFilter = 'all' | 'recent' | 'favorites' | 'unread';

	interface SidebarDataType {
		paperCount: number;
		recentCount: number;
		favoriteCount: number;
		unreadCount?: number;
		tags: Array<{ id: string; name: string; count: number }>;
		authors: Array<{ name: string; count: number }>;
		journals: Array<{ name: string; count: number }>;
	}

	interface Props {
		sidebarData: Readable<SidebarDataType>;
		activeTab: Readable<Tab | undefined>;
		libraryFilter: LibraryFilter;
		onFilterChange: (filter: LibraryFilter) => void;
		onGoHome: () => void;
		onOpenTag: (tagName: string, paperCount: number) => void;
		onOpenAuthor: (authorName: string, paperCount: number) => void;
		onOpenJournal: (journalName: string, paperCount: number) => void;
		onOpenSubmissions: () => void;
		onOpenNotes: () => void;
	}

	let {
		sidebarData,
		activeTab,
		libraryFilter,
		onFilterChange,
		onGoHome,
		onOpenTag,
		onOpenAuthor,
		onOpenJournal,
		onOpenSubmissions,
		onOpenNotes
	}: Props = $props();

	// Collapsible section state
	let showTags = $state(true);
	let showAuthors = $state(false);
	let showJournals = $state(false);

	// Check if home tab is active with specific filter
	function isLibraryActive(filter: LibraryFilter): boolean {
		return $activeTab?.type === 'home' && libraryFilter === filter;
	}

	// Check if specific tab type is active
	function isTabActive(type: string, id?: string): boolean {
		if (!$activeTab) return false;
		if (type === 'tag') return $activeTab.type === 'tag' && $activeTab.tag?.name === id;
		if (type === 'author') return $activeTab.type === 'author' && $activeTab.author?.name === id;
		if (type === 'journal') return $activeTab.type === 'journal' && $activeTab.journal?.name === id;
		return $activeTab.type === type;
	}

	function handleLibraryClick(filter: LibraryFilter) {
		onGoHome();
		onFilterChange(filter);
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
					active={isLibraryActive('all')}
					onclick={() => handleLibraryClick('all')}
				/>
				<MacSidebarItem
					icon={Clock}
					label="Recent"
					badge={$sidebarData.recentCount}
					active={isLibraryActive('recent')}
					onclick={() => handleLibraryClick('recent')}
				/>
				<MacSidebarItem
					icon={Star}
					label="Favorites"
					badge={$sidebarData.favoriteCount}
					badgeVariant="warning"
					active={isLibraryActive('favorites')}
					onclick={() => handleLibraryClick('favorites')}
				/>
				<MacSidebarItem
					icon={Inbox}
					label="Unread"
					badge={$sidebarData.unreadCount}
					active={isLibraryActive('unread')}
					onclick={() => handleLibraryClick('unread')}
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
							active={isTabActive('tag', tag.name)}
							onclick={() => onOpenTag(tag.name, tag.count)}
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
							active={isTabActive('author', author.name)}
							onclick={() => onOpenAuthor(author.name, author.count)}
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
							active={isTabActive('journal', journal.name)}
							onclick={() => onOpenJournal(journal.name, journal.count)}
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
					active={isTabActive('submissions-list')}
					onclick={onOpenSubmissions}
				/>
				<MacSidebarItem
					icon={FileText}
					label="Notes"
					active={isTabActive('notes')}
					onclick={onOpenNotes}
				/>
			</div>
		</div>
	</div>
</div>
