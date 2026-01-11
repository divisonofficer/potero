<script lang="ts">
	import {
		FileText,
		Edit,
		MessageSquare,
		Users,
		UserPlus,
		UserCheck,
		Clock,
		UserX,
		XCircle,
		AlertTriangle,
		RefreshCw,
		CheckCircle,
		CheckCircle2,
		Settings
	} from 'lucide-svelte';
	import type { SubmissionSideNavItem } from '$lib/types';

	interface NavItem extends SubmissionSideNavItem {
		isDivider?: boolean;
	}

	interface Props {
		items: NavItem[];
		activeItem: string;
		badges?: Record<string, number>;
		onItemSelect: (id: string) => void;
	}

	let { items, activeItem, badges = {}, onItemSelect }: Props = $props();

	const iconMap: Record<string, typeof FileText> = {
		FileText,
		Edit,
		MessageSquare,
		Users,
		UserPlus,
		UserCheck,
		Clock,
		UserX,
		XCircle,
		AlertTriangle,
		RefreshCw,
		CheckCircle,
		CheckCircle2,
		Settings
	};

	function getIcon(iconName?: string) {
		if (!iconName) return FileText;
		return iconMap[iconName] || FileText;
	}

	function getBadgeClass(type?: 'info' | 'warning' | 'success' | 'error') {
		switch (type) {
			case 'warning':
				return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300';
			case 'success':
				return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300';
			case 'error':
				return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300';
			case 'info':
			default:
				return 'bg-primary text-primary-foreground';
		}
	}
</script>

<nav class="w-56 shrink-0 border-r bg-white overflow-y-auto">
	<ul class="py-2">
		{#each items as item}
			{#if item.id.startsWith('divider')}
				<li class="my-2 border-t"></li>
			{:else}
				<li class="relative">
					<button
						class="w-full flex items-center justify-between px-4 py-2.5 text-sm transition-colors
                               {activeItem === item.id
							? 'bg-primary/10 text-primary font-medium'
							: 'text-foreground hover:bg-muted'}"
						onclick={() => onItemSelect(item.id)}
						aria-pressed={activeItem === item.id}
					>
						<span class="flex items-center gap-3">
							{#if getIcon(item.icon)}
								{@const IconComponent = getIcon(item.icon)}
								<IconComponent class="h-4 w-4 shrink-0" />
							{/if}
							<span class="truncate">{item.label}</span>
						</span>

						{#if badges[item.id] !== undefined}
							<span
								class="ml-2 inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-medium {getBadgeClass(
									item.badgeType
								)}"
							>
								{badges[item.id]}
							</span>
						{/if}
					</button>

					<!-- Selection indicator -->
					{#if activeItem === item.id}
						<div
							class="absolute left-0 top-1/2 -translate-y-1/2 h-8 w-1 rounded-r bg-primary"
						></div>
					{/if}
				</li>
			{/if}
		{/each}
	</ul>
</nav>
