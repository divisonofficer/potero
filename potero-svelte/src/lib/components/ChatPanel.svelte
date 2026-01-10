<script lang="ts">
	import { api, type ChatFileAttachment } from '$lib/api/client';
	import { toast } from '$lib/stores/toast';
	import type { Paper } from '$lib/types';
	import { Paperclip, X } from 'lucide-svelte';
	import { generateUUID } from '$lib/utils/uuid';

	interface Props {
		paper: Paper | null;
	}

	interface ChatMessage {
		id: string;
		role: 'user' | 'assistant';
		content: string;
		isLoading?: boolean;
	}

	let { paper }: Props = $props();

	let messages: ChatMessage[] = $state([]);
	let inputText = $state('');
	let isLoading = $state(false);
	let sessionId = $state<string | null>(null);
	let messagesContainer: HTMLDivElement | null = $state(null);

	// File attachment state
	let attachedFiles: ChatFileAttachment[] = $state([]);
	let isUploading = $state(false);
	let fileInput: HTMLInputElement;

	// Auto-scroll to bottom when new messages arrive
	$effect(() => {
		if (messages.length > 0 && messagesContainer) {
			messagesContainer.scrollTop = messagesContainer.scrollHeight;
		}
	});

	// Reset chat when paper changes
	$effect(() => {
		if (paper) {
			messages = [];
			sessionId = null;
			attachedFiles = [];
		}
	});

	async function handleFileSelect(event: Event) {
		const target = event.target as HTMLInputElement;
		const files = target.files;
		if (!files || files.length === 0) return;

		isUploading = true;
		for (const file of Array.from(files)) {
			const result = await api.uploadChatFile(file);
			if (result.success && result.data) {
				attachedFiles = [...attachedFiles, ...result.data.files];
				toast.success(`Attached ${file.name}`);
			} else {
				toast.error(`Failed to upload ${file.name}`);
			}
		}
		isUploading = false;
		target.value = '';
	}

	function removeFile(index: number) {
		attachedFiles = attachedFiles.filter((_, i) => i !== index);
	}

	async function sendMessage() {
		const text = inputText.trim();
		if (!text || isLoading) return;

		inputText = '';
		isLoading = true;

		// Add user message immediately
		const userMessage: ChatMessage = {
			id: generateUUID(),
			role: 'user',
			content: text
		};
		messages = [...messages, userMessage];

		// Add streaming placeholder for assistant
		const assistantId = generateUUID();
		messages = [
			...messages,
			{
				id: assistantId,
				role: 'assistant',
				content: '',
				isLoading: true
			}
		];

		let accumulatedContent = '';
		let finalMessageId = assistantId;

		try {
			await api.sendMessageStream(
				text,
				paper?.id,
				sessionId ?? undefined,
				(event) => {
					switch (event.type) {
						case 'session':
							sessionId = (event.data as { sessionId: string }).sessionId;
							break;

						case 'start':
							// Stream started
							break;

						case 'delta':
							// Incremental content update
							accumulatedContent = (event.data as { content: string }).content;
							messages = messages.map((msg) =>
								msg.id === assistantId
									? { ...msg, content: accumulatedContent, isLoading: true }
									: msg
							);
							break;

						case 'tool_call':
							// Tool execution started - show indicator
							const toolName = (event.data as { toolName: string }).toolName;
							console.log('[Chat] Tool call:', toolName);
							// Could show a "Executing tool: {toolName}" indicator here
							break;

						case 'tool_result':
							// Tool execution completed
							const result = event.data as { toolName: string; success: boolean; error?: string };
							console.log('[Chat] Tool result:', result);
							break;

						case 'done':
							// Final response with all tool executions
							const doneData = event.data as {
								messageId: string;
								content: string;
								toolExecutions: unknown[];
							};
							finalMessageId = doneData.messageId;
							accumulatedContent = doneData.content;

							messages = messages.map((msg) =>
								msg.id === assistantId
									? { id: finalMessageId, role: 'assistant' as const, content: accumulatedContent, isLoading: false }
									: msg
							);

							// Clear attached files after successful send
							attachedFiles = [];
							isLoading = false;
							break;

						case 'error':
							const errorMsg = (event.data as { message: string }).message;
							console.error('[Chat] Error:', errorMsg);
							toast.error(errorMsg);

							// Remove loading message
							messages = messages.filter((msg) => msg.id !== assistantId);
							isLoading = false;
							break;
					}
				}
			);
		} catch (error) {
			console.error('[Chat] Stream error:', error);
			// Remove loading message and show error
			messages = messages.filter((msg) => msg.id !== assistantId);
			toast.error('Failed to send message. Is the API key configured in Settings?');
			isLoading = false;
		}
	}

	function handleKeyDown(event: KeyboardEvent) {
		if (event.key === 'Enter' && !event.shiftKey) {
			event.preventDefault();
			sendMessage();
		}
	}

	function clearChat() {
		messages = [];
		sessionId = null;
	}

	// Suggested questions based on paper context
	const suggestedQuestions = $derived(
		paper
			? [
					'What is the main contribution of this paper?',
					'Summarize the methodology used.',
					'What are the key findings?',
					'What are the limitations mentioned?'
				]
			: ['Ask me anything about research papers.']
	);
</script>

<div class="flex h-full flex-col bg-card">
	<!-- Header -->
	<div class="flex items-center justify-between border-b p-3">
		<div>
			<h3 class="font-semibold">Chat with Paper</h3>
			{#if paper}
				<p class="line-clamp-1 text-xs text-muted-foreground">{paper.title}</p>
			{/if}
		</div>
		{#if messages.length > 0}
			<button
				class="rounded p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
				onclick={clearChat}
				title="Clear chat"
			>
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
				</svg>
			</button>
		{/if}
	</div>

	<!-- Messages -->
	<div class="flex-1 overflow-auto p-3" bind:this={messagesContainer}>
		{#if messages.length === 0}
			<div class="flex flex-col items-center justify-center py-8 text-center">
				<svg
					class="mb-3 h-12 w-12 text-muted-foreground/50"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="1.5"
				>
					<path
						d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z"
					/>
				</svg>
				<p class="mb-4 text-sm text-muted-foreground">Ask questions about this paper</p>

				<!-- Suggested questions -->
				<div class="flex flex-col gap-2">
					{#each suggestedQuestions.slice(0, 3) as question}
						<button
							class="rounded-lg border px-3 py-2 text-left text-xs transition-colors hover:bg-muted"
							onclick={() => {
								inputText = question;
								sendMessage();
							}}
						>
							{question}
						</button>
					{/each}
				</div>
			</div>
		{:else}
			<div class="space-y-4">
				{#each messages as message (message.id)}
					<div
						class="flex gap-3 {message.role === 'user' ? 'flex-row-reverse' : ''}"
					>
						<!-- Avatar -->
						<div
							class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full {message.role === 'user'
								? 'bg-primary text-primary-foreground'
								: 'bg-muted'}"
						>
							{#if message.role === 'user'}
								<svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
									<path
										d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"
									/>
								</svg>
							{:else}
								<svg class="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
									<path
										d="M9.5 3A6.5 6.5 0 0116 9.5c0 1.61-.59 3.09-1.56 4.23l.27.27h.79l5 5-1.5 1.5-5-5v-.79l-.27-.27A6.516 6.516 0 019.5 16 6.5 6.5 0 013 9.5 6.5 6.5 0 019.5 3m0 2C7 5 5 7 5 9.5S7 14 9.5 14 14 12 14 9.5 12 5 9.5 5z"
									/>
								</svg>
							{/if}
						</div>

						<!-- Message bubble -->
						<div
							class="max-w-[85%] rounded-lg px-3 py-2 {message.role === 'user'
								? 'bg-primary text-primary-foreground'
								: 'bg-muted'}"
						>
							{#if message.isLoading}
								<div class="flex items-center gap-1">
									<span class="h-2 w-2 animate-bounce rounded-full bg-current"></span>
									<span class="h-2 w-2 animate-bounce rounded-full bg-current" style="animation-delay: 0.1s"></span>
									<span class="h-2 w-2 animate-bounce rounded-full bg-current" style="animation-delay: 0.2s"></span>
								</div>
							{:else}
								<p class="whitespace-pre-wrap text-sm">{message.content}</p>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		{/if}
	</div>

	<!-- Input -->
	<div class="border-t p-3">
		<!-- Hidden file input -->
		<input
			type="file"
			multiple
			bind:this={fileInput}
			onchange={handleFileSelect}
			class="hidden"
		/>

		<!-- Attached files preview -->
		{#if attachedFiles.length > 0}
			<div class="mb-2 flex flex-wrap gap-1">
				{#each attachedFiles as file, i}
					<span class="inline-flex items-center gap-1 rounded bg-primary/10 px-2 py-1 text-xs text-primary">
						<svg class="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
							<path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9l-7-7z" />
							<path d="M13 2v7h7" />
						</svg>
						{file.name}
						<button
							onclick={() => removeFile(i)}
							class="ml-0.5 rounded hover:bg-primary/20"
							title="Remove file"
						>
							<X class="h-3 w-3" />
						</button>
					</span>
				{/each}
			</div>
		{/if}

		<div class="flex gap-2">
			<!-- Attach button -->
			<button
				class="shrink-0 rounded-md border p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-50"
				onclick={() => fileInput?.click()}
				disabled={isUploading || isLoading}
				title="Attach file"
			>
				{#if isUploading}
					<svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20" />
					</svg>
				{:else}
					<Paperclip class="h-4 w-4" />
				{/if}
			</button>

			<textarea
				placeholder="Ask a question..."
				bind:value={inputText}
				onkeydown={handleKeyDown}
				disabled={isLoading}
				rows="1"
				class="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-50"
			></textarea>
			<button
				class="shrink-0 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
				disabled={isLoading || !inputText.trim()}
				onclick={sendMessage}
			>
				<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
				</svg>
			</button>
		</div>
		{#if !paper}
			<p class="mt-2 text-xs text-muted-foreground">
				Open a paper to get context-aware answers
			</p>
		{/if}
	</div>
</div>
