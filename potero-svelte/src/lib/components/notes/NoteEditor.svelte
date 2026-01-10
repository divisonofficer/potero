<script lang="ts">
	import { Bold, Italic, Code, Heading1, Heading2, Link as LinkIcon, X } from 'lucide-svelte';

	interface Props {
		title?: string;
		content?: string;
		onSave: (title: string, content: string) => void;
		onCancel: () => void;
		placeholder?: string;
	}

	let { title = '', content = '', onSave, onCancel, placeholder = 'Start writing...' }: Props = $props();

	let editTitle = $state(title);
	let editContent = $state(content);
	let textarea: HTMLTextAreaElement;

	function handleSave() {
		if (!editTitle.trim()) {
			alert('Please enter a title');
			return;
		}
		onSave(editTitle, editContent);
	}

	function insertMarkdown(prefix: string, suffix = '') {
		if (!textarea) return;

		const start = textarea.selectionStart;
		const end = textarea.selectionEnd;
		const selectedText = editContent.substring(start, end);
		const newText =
			editContent.substring(0, start) +
			prefix +
			selectedText +
			suffix +
			editContent.substring(end);

		editContent = newText;
		setTimeout(() => {
			textarea.focus();
			textarea.selectionStart = start + prefix.length;
			textarea.selectionEnd = start + prefix.length + selectedText.length;
		}, 0);
	}

	function insertWikiLink() {
		const linkText = prompt('Enter note title or paper:id');
		if (linkText) {
			insertMarkdown(`[[${linkText}]]`, '');
		}
	}
</script>

<div class="flex h-full flex-col bg-background">
	<!-- Toolbar -->
	<div class="flex items-center justify-between border-b bg-muted/30 px-4 py-2">
		<div class="flex items-center gap-1">
			<button
				class="rounded p-2 hover:bg-muted"
				onclick={() => insertMarkdown('**', '**')}
				title="Bold (Ctrl+B)"
				type="button"
			>
				<Bold class="h-4 w-4" />
			</button>

			<button
				class="rounded p-2 hover:bg-muted"
				onclick={() => insertMarkdown('*', '*')}
				title="Italic (Ctrl+I)"
				type="button"
			>
				<Italic class="h-4 w-4" />
			</button>

			<button
				class="rounded p-2 hover:bg-muted"
				onclick={() => insertMarkdown('`', '`')}
				title="Code"
				type="button"
			>
				<Code class="h-4 w-4" />
			</button>

			<div class="mx-1 h-6 w-px bg-border"></div>

			<button
				class="rounded p-2 hover:bg-muted"
				onclick={() => insertMarkdown('# ', '')}
				title="Heading 1"
				type="button"
			>
				<Heading1 class="h-4 w-4" />
			</button>

			<button
				class="rounded p-2 hover:bg-muted"
				onclick={() => insertMarkdown('## ', '')}
				title="Heading 2"
				type="button"
			>
				<Heading2 class="h-4 w-4" />
			</button>

			<div class="mx-1 h-6 w-px bg-border"></div>

			<button
				class="rounded p-2 hover:bg-muted"
				onclick={insertWikiLink}
				title="Insert Wiki Link [[...]]"
				type="button"
			>
				<LinkIcon class="h-4 w-4" />
			</button>
		</div>

		<div class="flex items-center gap-2">
			<button
				class="rounded-md px-4 py-1.5 text-sm hover:bg-muted"
				onclick={onCancel}
				type="button"
			>
				Cancel
			</button>
			<button
				class="rounded-md bg-primary px-4 py-1.5 text-sm text-primary-foreground hover:bg-primary/90"
				onclick={handleSave}
				type="button"
			>
				Save
			</button>
		</div>
	</div>

	<!-- Title input -->
	<div class="border-b px-4 py-3">
		<input
			type="text"
			placeholder="Note title..."
			bind:value={editTitle}
			class="w-full bg-transparent text-2xl font-bold outline-none placeholder:text-muted-foreground"
		/>
	</div>

	<!-- Content editor -->
	<textarea
		bind:this={textarea}
		bind:value={editContent}
		{placeholder}
		class="flex-1 resize-none bg-transparent px-4 py-3 font-mono text-sm outline-none placeholder:text-muted-foreground"
	></textarea>
</div>
