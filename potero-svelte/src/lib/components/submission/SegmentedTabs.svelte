<script lang="ts">
	import { Workflow, FileText, Bot } from 'lucide-svelte';

	type TabId = 'workflow' | 'details' | 'ai-review';

	interface Tab {
		id: TabId;
		label: string;
		icon: typeof Workflow;
	}

	interface Props {
		activeTab: TabId;
		onTabChange: (tabId: TabId) => void;
	}

	let { activeTab, onTabChange }: Props = $props();

	const tabs: Tab[] = [
		{ id: 'workflow', label: 'Workflow', icon: Workflow },
		{ id: 'details', label: 'Submission Details', icon: FileText },
		{ id: 'ai-review', label: 'AI Review', icon: Bot }
	];
</script>

<div class="flex items-center gap-1" role="tablist">
	{#each tabs as tab}
		{@const IconComponent = tab.icon}
		<button
			class="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors
                   {activeTab === tab.id
				? 'text-primary'
				: 'text-muted-foreground hover:text-foreground hover:bg-muted'}"
			onclick={() => onTabChange(tab.id)}
			aria-selected={activeTab === tab.id}
			role="tab"
		>
			<IconComponent class="h-4 w-4" />
			{tab.label}
		</button>
	{/each}
</div>
